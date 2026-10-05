package com.curated.app.features.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import com.curated.app.core.map.CuratedPlacePin
import com.curated.app.core.map.DEFAULT_WORLD_CAMERA
import com.curated.app.core.map.MapPin
import com.curated.app.designsystem.components.AnimatedListItem
import com.curated.app.designsystem.components.CuratedFilterChip
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.TripCardSkeleton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.curated.app.core.map.ClusteredMap
import com.curated.app.core.model.BudgetTag
import com.curated.app.core.model.SeasonTag
import com.curated.app.designsystem.Spacing
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.features.home.FeedItemCard
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore") },
                actions = {
                    IconButton(onClick = {
                        viewModel.setViewMode(if (state.viewMode == ExploreViewMode.MAP) ExploreViewMode.LIST else ExploreViewMode.MAP)
                    }) {
                        Icon(
                            if (state.viewMode == ExploreViewMode.MAP) Icons.AutoMirrored.Outlined.List else Icons.Outlined.Map,
                            contentDescription = "Toggle map/list"
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.align(Alignment.Center)
                )
                state.viewMode == ExploreViewMode.MAP -> ExploreMap(
                    state = state,
                    onTripClick = onTripClick,
                    onAuthorClick = onAuthorClick,
                    onLikeToggle = viewModel::toggleLike,
                    onSaveToggle = viewModel::toggleSave
                )
                state.isLoading && state.listItems.isEmpty() -> Column(modifier = Modifier.fillMaxSize()) {
                    repeat(3) {
                        TripCardSkeleton(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
                    }
                }
                state.listItems.isEmpty() -> EmptyState(
                    headline = "No trips match yet",
                    body = "Try widening your filters or searching a different destination.",
                    icon = Icons.Outlined.Search,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    // Clear the floating pill, so the last card is not stuck behind it.
                    contentPadding = PaddingValues(bottom = SearchPillClearance)
                ) {
                    itemsIndexed(state.listItems, key = { _, item -> item.trip.id }) { index, item ->
                        AnimatedListItem(index = index) {
                            FeedItemCard(
                                item = item,
                                onClick = { onTripClick(item.trip.id) },
                                onAuthorClick = { onAuthorClick(item.trip.authorId) },
                                onLikeToggle = { viewModel.toggleLike(item.trip.id) },
                                onSaveToggle = { viewModel.toggleSave(item.trip.id) }
                            )
                        }
                    }
                }
            }

            SearchPill(
                query = state.searchQuery,
                activeFilterCount = state.filters.activeCount,
                onClick = { showSearch = true },
                modifier = Modifier
                    .align(if (state.viewMode == ExploreViewMode.MAP) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }
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

/** How much room the pill needs at the bottom of a scrolling list. */
private val SearchPillClearance = 88.dp

/**
 * The only search affordance on Explore. The field used to sit permanently at the
 * top, which meant the first thing anyone saw was an empty text box rather than
 * the world. This keeps the map whole and puts search in thumb reach.
 */
@Composable
private fun SearchPill(
    query: String,
    activeFilterCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when {
        query.isNotBlank() && activeFilterCount > 0 ->
            "$query - $activeFilterCount ${if (activeFilterCount == 1) "filter" else "filters"}"
        query.isNotBlank() -> query
        activeFilterCount > 0 ->
            "$activeFilterCount ${if (activeFilterCount == 1) "filter" else "filters"}"
        else -> "Search destinations or trips"
    }
    val isPlaceholder = query.isBlank() && activeFilterCount == 0
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier.height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(horizontal = Spacing.md)
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Search, destination shortcuts and every filter, opened from the pill. */
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

        ExploreFilterBar(
            filters = state.filters,
            onTripLengthChange = viewModel::setTripLength,
            onBudgetTagChange = viewModel::setBudgetTag,
            onSeasonTagChange = viewModel::setSeasonTag,
            onFollowScopeChange = viewModel::setFollowScope
        )
    }
}

/**
 * Every filter in one horizontally scrolling row. Four stacked rows used to eat
 * ~200dp, which (with the search field, suggestions, and keyboard) left the
 * results area almost no height on a phone.
 */
@Composable
private fun ExploreFilterBar(
    filters: ExploreFilters,
    onTripLengthChange: (TripLength?) -> Unit,
    onBudgetTagChange: (BudgetTag?) -> Unit,
    onSeasonTagChange: (SeasonTag?) -> Unit,
    onFollowScopeChange: (FollowScope) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CuratedFilterChip(
            selected = filters.followScope == FollowScope.FOLLOWING,
            onClick = {
                onFollowScopeChange(
                    if (filters.followScope == FollowScope.FOLLOWING) FollowScope.EVERYONE else FollowScope.FOLLOWING
                )
            },
            label = "Following only"
        )
        FilterDivider()
        TripLength.entries.forEach { length ->
            CuratedFilterChip(
                selected = filters.tripLength == length,
                onClick = { onTripLengthChange(if (filters.tripLength == length) null else length) },
                label = length.label()
            )
        }
        FilterDivider()
        BudgetTag.entries.forEach { tag ->
            CuratedFilterChip(
                selected = filters.budgetTag == tag,
                onClick = { onBudgetTagChange(if (filters.budgetTag == tag) null else tag) },
                label = tag.label()
            )
        }
        FilterDivider()
        SeasonTag.entries.forEach { tag ->
            CuratedFilterChip(
                selected = filters.seasonTag == tag,
                onClick = { onSeasonTagChange(if (filters.seasonTag == tag) null else tag) },
                label = tag.label()
            )
        }
    }
}

@Composable
private fun FilterDivider() {
    VerticalDivider(
        modifier = Modifier.height(20.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

private fun TripLength.label() = when (this) {
    TripLength.SHORT -> "1–3 days"
    TripLength.MEDIUM -> "4–7 days"
    TripLength.LONG -> "8+ days"
}

private fun BudgetTag.label() = when (this) {
    BudgetTag.BUDGET -> "Budget"
    BudgetTag.MID_RANGE -> "Mid-range"
    BudgetTag.LUXURY -> "Luxury"
}

private fun SeasonTag.label() = when (this) {
    SeasonTag.SPRING -> "Spring"
    SeasonTag.SUMMER -> "Summer"
    SeasonTag.FALL -> "Fall"
    SeasonTag.WINTER -> "Winter"
}

/**
 * The map with its sheet. The camera opens fitted to every trip pin, once; the
 * sheet's row follows whatever part of the map is in view, refreshed each time
 * the camera settles. Tapping a pin brings its trip's card into the row.
 */
@Composable
private fun ExploreMap(
    state: ExploreUiState,
    onTripClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onLikeToggle: (String) -> Unit,
    onSaveToggle: (String) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState { position = DEFAULT_WORLD_CAMERA }
    val rowState = rememberLazyListState()
    var mapLoaded by remember { mutableStateOf(false) }
    // Saved, so coming back to Explore doesn't snap the camera away from where it was left.
    var fitted by rememberSaveable { mutableStateOf(false) }
    var userMoved by rememberSaveable { mutableStateOf(false) }
    var inViewIds by remember { mutableStateOf<Set<String>?>(null) }
    var selectedTripId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            userMoved = true
        }
    }

    // Fit once, after the first pins arrive - never after the user has moved the map.
    LaunchedEffect(mapLoaded, state.tripPins, state.isMapLoading) {
        if (!mapLoaded || fitted || userMoved || state.isMapLoading) return@LaunchedEffect
        val fit = MapFit.of(state.tripPins.map { it.latitude to it.longitude }) ?: return@LaunchedEffect
        val update = when (fit) {
            is MapFit.Point -> CameraUpdateFactory.newLatLngZoom(LatLng(fit.latitude, fit.longitude), MapFit.CITY_ZOOM)
            is MapFit.Box -> CameraUpdateFactory.newLatLngBounds(
                LatLngBounds(LatLng(fit.south, fit.west), LatLng(fit.north, fit.east)),
                with(density) { FitPadding.roundToPx() }
            )
        }
        runCatching { cameraPositionState.animate(update) }
        fitted = true
    }

    // Which trips are in view, each time the camera settles.
    LaunchedEffect(cameraPositionState.isMoving, mapLoaded, state.tripPins) {
        if (!mapLoaded || cameraPositionState.isMoving) return@LaunchedEffect
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        inViewIds = state.tripPins
            .filter { bounds.contains(LatLng(it.latitude, it.longitude)) }
            .map { it.trip.id }
            .toSet()
    }

    // The list's order is the ranking, so the row keeps it.
    val inView = inViewIds?.let { ids -> state.listItems.filter { it.trip.id in ids } } ?: state.listItems

    BottomSheetScaffold(
        sheetPeekHeight = ExploreSheetPeekHeight,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        sheetContent = {
            ExploreSheetContent(
                inView = inView,
                all = state.listItems,
                isLoading = state.isLoading || state.isMapLoading,
                selectedTripId = selectedTripId,
                rowState = rowState,
                onTripClick = onTripClick,
                onAuthorClick = onAuthorClick,
                onLikeToggle = onLikeToggle,
                onSaveToggle = onSaveToggle
            )
        }
    ) {
        ClusteredMap(
            pins = state.tripPins.map { MapPin(id = it.trip.id, position = LatLng(it.latitude, it.longitude), title = it.trip.title) },
            cameraPositionState = cameraPositionState,
            // Fitting and "in this area" stop at the peeking sheet. The top isn't
            // padded: a pin beside the search pill is still on screen, so it counts.
            contentPadding = PaddingValues(bottom = ExploreSheetPeekHeight),
            onMapLoaded = { mapLoaded = true },
            onPinClick = { pin ->
                selectedTripId = pin.id
                val index = inView.indexOfFirst { it.trip.id == pin.id }
                if (index >= 0) scope.launch { rowState.animateScrollToItem(index) }
            },
            pinContent = { CuratedPlacePin() }
        )
    }
}

/** Room around the pins when the camera fits them - enough to clear the search pill at the top. */
private val FitPadding = 72.dp
