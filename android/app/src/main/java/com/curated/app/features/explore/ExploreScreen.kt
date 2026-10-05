package com.curated.app.features.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curated.app.core.map.ClusteredMap
import com.curated.app.core.map.CuratedPlacePin
import com.curated.app.core.map.DEFAULT_WORLD_CAMERA
import com.curated.app.core.map.MapPin
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.CuratedFilterChip
import com.curated.app.designsystem.components.ErrorState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

/**
 * Explore: the search field and filter chips pinned at the top, the map
 * under them, and a sheet over the map's bottom that lists the trips -
 * those in view while it peeks, all of them pulled up.
 */
@Composable
fun ExploreScreen(
    onTripClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.factory(context))
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    var showSearch by remember { mutableStateOf(false) }
    var openPicker by remember { mutableStateOf<PickerFilter?>(null) }

    // The first load, and a fresh one coming back to Explore - unless the last is under a minute old.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = Spacing.sm, bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    SearchField(
                        query = state.searchQuery,
                        onClick = { showSearch = true },
                        modifier = Modifier.padding(horizontal = Spacing.md)
                    )
                    ExploreFilterChipRow(
                        filters = state.filters,
                        onToggleFollowing = {
                            viewModel.setFollowScope(
                                if (state.filters.followScope == FollowScope.FOLLOWING) FollowScope.EVERYONE else FollowScope.FOLLOWING
                            )
                        },
                        onOpenPicker = { openPicker = it },
                        onClearAll = viewModel::clearFilters
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.error != null) {
                ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                ExploreMap(
                    state = state,
                    onClearFilters = viewModel::clearFilters,
                    onClearSearch = { viewModel.setSearchQuery("") },
                    onTripClick = onTripClick,
                    onAuthorClick = onAuthorClick,
                    onLikeToggle = viewModel::toggleLike,
                    onSaveToggle = viewModel::toggleSave
                )
            }
        }
    }

    openPicker?.let { filter ->
        FilterPickerSheet(
            filter = filter,
            filters = state.filters,
            onSelectDuration = viewModel::setTripLength,
            onSelectBudget = viewModel::setBudgetTag,
            onSelectSeason = viewModel::setSeasonTag,
            onDismiss = { openPicker = null }
        )
    }

    if (showSearch) {
        ModalBottomSheet(
            onDismissRequest = { showSearch = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SearchSheet(
                state = state,
                viewModel = viewModel,
                onDone = {
                    focusManager.clearFocus()
                    showSearch = false
                }
            )
        }
    }
}

/**
 * The search field at the top of Explore. It reads like a field but opens the
 * search sheet, where typing gets the keyboard and destination suggestions
 * room to work without squeezing the map.
 */
@Composable
private fun SearchField(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(horizontal = Spacing.md)
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                query.ifBlank { "Search destinations or trips" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Search and destination shortcuts, opened from the search field. */
@Composable
private fun SearchSheet(
    state: ExploreUiState,
    viewModel: ExploreViewModel,
    onDone: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Search destinations or trips") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.refresh()
                onDone()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .focusRequester(focusRequester)
        )

        if (state.destinationSuggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(state.destinationSuggestions, key = { it }) { destination ->
                    CuratedFilterChip(
                        selected = false,
                        onClick = {
                            viewModel.selectDestinationSuggestion(destination)
                            onDone()
                        },
                        label = destination,
                        leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) }
                    )
                }
            }
        }
    }
}

/**
 * The map with its sheet.
 *
 * The camera fits the pins when a new set of results arrives - first load
 * (unless the user got there first) and each change of search or filters -
 * but not on a reload of the same results, and not while scoped to an area.
 *
 * The sheet's row follows whatever is in view, refreshed as the camera
 * settles. Once the user moves somewhere new, "Search this area" offers to
 * scope the row and the full list to that view; the chip then names the
 * scope, and its ✕ lifts it.
 */
@Composable
private fun ExploreMap(
    state: ExploreUiState,
    onClearFilters: () -> Unit,
    onClearSearch: () -> Unit,
    onTripClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onLikeToggle: (String) -> Unit,
    onSaveToggle: (String) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState { position = DEFAULT_WORLD_CAMERA }
    val rowState = rememberLazyListState()
    val listState = rememberLazyListState()
    var mapLoaded by remember { mutableStateOf(false) }
    // The results the camera was last fitted to. Saved, so coming back to Explore
    // doesn't snap the camera away from where it was left.
    var fittedKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Set while the camera is moving for the app's own fit, so that move isn't mistaken for the user's.
    var fitting by remember { mutableStateOf(false) }
    var userMoved by rememberSaveable { mutableStateOf(false) }
    // The view results were last loaded or scoped for; moving away from it offers "Search this area".
    var baseRegion by remember { mutableStateOf<MapRegion?>(null) }
    var movedSinceBase by remember { mutableStateOf(false) }
    var offerAreaSearch by remember { mutableStateOf(false) }
    var area by rememberSaveable(stateSaver = MapRegionSaver) { mutableStateOf<MapRegion?>(null) }
    var inViewIds by remember { mutableStateOf<Set<String>?>(null) }
    var selectedTripId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) {
            // Pans, zooms and cluster taps all count; only the app's own fit doesn't.
            if (!fitting) {
                userMoved = true
                movedSinceBase = true
            }
            return@LaunchedEffect
        }
        if (!mapLoaded) return@LaunchedEffect
        val now = cameraPositionState.visibleRegion() ?: return@LaunchedEffect
        inViewIds = state.tripPins.filter { now.contains(it.latitude, it.longitude) }.map { it.trip.id }.toSet()
        val base = baseRegion
        if (base == null) {
            baseRegion = now
        } else if (movedSinceBase) {
            offerAreaSearch = now.differsFrom(base)
        }
    }

    // The pins changed without the camera moving (a reload, new filters while scoped): recount what's in view.
    LaunchedEffect(state.tripPins, mapLoaded) {
        if (!mapLoaded || cameraPositionState.isMoving) return@LaunchedEffect
        val now = cameraPositionState.visibleRegion() ?: return@LaunchedEffect
        inViewIds = state.tripPins.filter { now.contains(it.latitude, it.longitude) }.map { it.trip.id }.toSet()
    }

    LaunchedEffect(mapLoaded, state.pinsKey, state.isMapLoading) {
        val key = state.pinsKey
        if (!mapLoaded || state.isMapLoading || key == null || key == fittedKey) return@LaunchedEffect
        val firstLoad = fittedKey == null
        fittedKey = key
        if ((firstLoad && userMoved) || area != null) return@LaunchedEffect
        val fit = MapFit.of(state.tripPins.map { it.latitude to it.longitude }) ?: return@LaunchedEffect
        val update = when (fit) {
            is MapFit.Point -> CameraUpdateFactory.newLatLngZoom(LatLng(fit.latitude, fit.longitude), MapFit.CITY_ZOOM)
            is MapFit.Box -> CameraUpdateFactory.newLatLngBounds(
                LatLngBounds(LatLng(fit.south, fit.west), LatLng(fit.north, fit.east)),
                with(density) { FitPadding.roundToPx() }
            )
        }
        fitting = true
        runCatching { cameraPositionState.animate(update) }
        fitting = false
        // The fitted view is the new "loaded" region.
        baseRegion = cameraPositionState.visibleRegion()
        movedSinceBase = false
        offerAreaSearch = false
    }

    // New results start at the top: the old offset would land partway down a
    // different list, with its count and first trips scrolled away.
    // Saved, so coming back from a trip (which recreates this) keeps the position.
    var listResultsKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.pinsKey, area) {
        val key = "${state.pinsKey}|$area"
        if (state.pinsKey == null || key == listResultsKey) return@LaunchedEffect
        val firstResults = listResultsKey == null
        listResultsKey = key
        if (firstResults) return@LaunchedEffect
        listState.scrollToItem(0)
        rowState.scrollToItem(0)
    }

    val scoped = area
    // The list's order is the ranking; the row and the scoped list keep it.
    val pinnedIn = { region: MapRegion ->
        state.tripPins.filter { region.contains(it.latitude, it.longitude) }.map { it.trip.id }.toSet()
    }
    val all = if (scoped == null) state.listItems else pinnedIn(scoped).let { ids -> state.listItems.filter { it.trip.id in ids } }
    val inView = when {
        scoped != null -> all
        inViewIds != null -> state.listItems.filter { it.trip.id in inViewIds!! }
        else -> state.listItems
    }

    BottomSheetScaffold(
        sheetPeekHeight = ExploreSheetPeekHeight,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        sheetContent = {
            ExploreSheetContent(
                inView = inView,
                all = all,
                isLoading = state.isLoading || state.isMapLoading,
                hasFilters = state.filters.activeCount > 0,
                query = state.searchQuery,
                onClearFilters = onClearFilters,
                onClearSearch = onClearSearch,
                selectedTripId = selectedTripId,
                rowState = rowState,
                listState = listState,
                onTripClick = onTripClick,
                onAuthorClick = onAuthorClick,
                onLikeToggle = onLikeToggle,
                onSaveToggle = onSaveToggle
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ClusteredMap(
                pins = state.tripPins.map { MapPin(id = it.trip.id, position = LatLng(it.latitude, it.longitude), title = it.trip.title) },
                cameraPositionState = cameraPositionState,
                // Fitting and "in this area" stop at the peeking sheet.
                contentPadding = PaddingValues(bottom = ExploreSheetPeekHeight),
                onMapLoaded = { mapLoaded = true },
                onPinClick = { pin ->
                    selectedTripId = pin.id
                    val index = inView.indexOfFirst { it.trip.id == pin.id }
                    if (index >= 0) scope.launch { rowState.animateScrollToItem(index) }
                },
                pinContent = { CuratedPlacePin() }
            )
            AreaChip(
                scoped = scoped != null,
                offerSearch = offerAreaSearch,
                onSearchArea = {
                    val now = cameraPositionState.visibleRegion() ?: return@AreaChip
                    area = now
                    baseRegion = now
                    movedSinceBase = false
                    offerAreaSearch = false
                },
                onLeaveArea = { area = null },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.sm)
            )
        }
    }
}

/**
 * "Search this area" once the map has moved somewhere new; "Area: this map
 * view ✕" while the results are scoped to it. Moving again while scoped
 * offers to rescope, with the ✕ still there to leave.
 */
@Composable
private fun AreaChip(
    scoped: Boolean,
    offerSearch: Boolean,
    onSearchArea: () -> Unit,
    onLeaveArea: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!scoped && !offerSearch) return
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (offerSearch) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
        contentColor = if (offerSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = if (offerSearch) onSearchArea else onLeaveArea,
                color = Color.Transparent,
                contentColor = LocalContentColor.current
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.padding(start = Spacing.md, end = if (scoped) Spacing.xs else Spacing.md, top = Spacing.sm, bottom = Spacing.sm)
                ) {
                    if (offerSearch) Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (offerSearch) "Search this area" else "Area: this map view",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            if (scoped) {
                Surface(
                    onClick = onLeaveArea,
                    shape = RoundedCornerShape(percent = 50),
                    color = Color.Transparent,
                    contentColor = LocalContentColor.current
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Show trips everywhere",
                        modifier = Modifier.padding(Spacing.sm).size(18.dp)
                    )
                }
            }
        }
    }
}

private fun CameraPositionState.visibleRegion(): MapRegion? =
    projection?.visibleRegion?.latLngBounds?.let {
        MapRegion(south = it.southwest.latitude, west = it.southwest.longitude, north = it.northeast.latitude, east = it.northeast.longitude)
    }

private val MapRegionSaver = Saver<MapRegion?, List<Double>>(
    save = { region -> region?.let { listOf(it.south, it.west, it.north, it.east) } ?: emptyList() },
    restore = { values -> if (values.size == 4) MapRegion(values[0], values[1], values[2], values[3]) else null }
)

/** Room around the pins when the camera fits them - enough to clear the area chip at the top. */
private val FitPadding = 72.dp
