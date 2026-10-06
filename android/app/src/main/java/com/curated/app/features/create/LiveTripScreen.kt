package com.curated.app.features.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.format.Noun
import com.curated.app.core.format.countText
import com.curated.app.core.format.shortDayText
import com.curated.app.core.model.Stop
import com.curated.app.core.model.StopCategory
import com.curated.app.core.model.TripVisibility
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.Tag
import com.curated.app.designsystem.components.TagStyle
import com.curated.app.features.trip.StopPreviewRow
import com.curated.app.features.trip.TripVisibilityPicker
import com.curated.app.features.trip.stopPreview
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * A trip in progress: one card per day so far, each posted or not, and the way
 * out - End trip. Posting and editing a day happen on [PostDayScreen].
 */
@Composable
fun LiveTripScreen(
    viewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onOpenDay: (Int) -> Unit,
    onEnded: (String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<StopWithPhotos?>(null) }
    var showEndSheet by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // The day whose card asked for photos or a post, so its result shows on that card.
    var actedOnDay by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.endedTrip) {
        state.endedTrip?.let { trip ->
            viewModel.reset()
            onEnded(trip.id)
        }
    }

    // Posting from a card sets justPostedDay, which Post Day closes itself on.
    // Cleared here, or opening that day later would close it straight away.
    LaunchedEffect(state.justPostedDay) {
        if (state.justPostedDay != null) viewModel.consumeJustPosted()
    }

    // The same import Post Day's "Add photos from this day" runs.
    val pickDayPhotos = rememberGeoPhotoPicker { uris ->
        actedOnDay?.let { day -> viewModel.importPhotosForDay(context, uris, day) }
    }

    val addPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        val stopId = editing?.stop?.id
        if (stopId != null && uris.isNotEmpty()) viewModel.addPhotosToStop(stopId, context, uris)
    }

    // Only the day-less stops are edited from here; a day's own stops are edited on its screen.
    editing?.let { item ->
        EditStopSheet(
            item = item,
            dayCount = state.dayCount,
            currentDay = null,
            onSave = { name, category, caption, tips, time ->
                viewModel.updateStop(item.stop.id, name, category, caption, tips, time)
                editing = null
            },
            onAddPhotos = { addPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onMoveToDay = { day ->
                viewModel.moveStopToDay(item.stop.id, fromDay = 0, toDay = day)
                editing = null
            },
            onDelete = {
                viewModel.deleteStop(item.stop.id)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    if (showEndSheet) {
        EndTripSheet(
            unpostedDays = state.daysEndTripWillPublish,
            isEnding = state.isEnding,
            canEnd = state.stopCount > 0,
            onEnd = viewModel::endTrip,
            onReviewDays = { showEndSheet = false },
            onDismiss = { showEndSheet = false }
        )
    }

    if (showSettings) {
        TripSettingsDialog(
            currentTitle = state.title,
            currentVisibility = state.visibility,
            onSave = { title, visibility ->
                viewModel.renameTrip(title)
                viewModel.setVisibility(visibility)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }

    LiveTripContent(
        state = state,
        actedOnDay = actedOnDay,
        onBack = onBack,
        onOpenSettings = { showSettings = true },
        onOpenDay = onOpenDay,
        onPostDay = { day ->
            actedOnDay = day
            viewModel.clearPostProblem()
            viewModel.postDay(day)
        },
        onAddPhotos = { day ->
            actedOnDay = day
            viewModel.clearPostProblem()
            pickDayPhotos()
        },
        onEditUnassigned = { editing = it },
        onEndTrip = { showEndSheet = true }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTripContent(
    state: CreateWizardState,
    actedOnDay: Int?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDay: (Int) -> Unit,
    onPostDay: (Int) -> Unit,
    onAddPhotos: (Int) -> Unit,
    onEditUnassigned: (StopWithPhotos) -> Unit,
    onEndTrip: () -> Unit
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title.ifBlank { "Your trip" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(state.destination, "Day ${state.dayCount}").filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Title and visibility were set for you when the trip started; they change here.
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Trip settings")
                    }
                    Tag("LIVE", style = TagStyle.Accent, modifier = Modifier.padding(end = Spacing.md))
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item(key = "visibility") {
                        Text(
                            liveVisibilityLine(state.visibility),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (state.unassignedStops.isNotEmpty()) {
                        item(key = "unassigned") {
                            UnassignedCard(stops = state.unassignedStops, onEdit = onEditUnassigned)
                        }
                    }
                    // Newest day first: while travelling, today is what you're working on.
                    items((state.dayCount downTo 1).toList(), key = { "day-$it" }) { day ->
                        val date = state.dateOf(day)
                        val isActedOn = day == actedOnDay
                        LiveDayCard(
                            day = day,
                            dateLabel = date?.shortDayText(),
                            isToday = date == today,
                            stops = state.stopsOn(day),
                            isPosted = state.isPosted(day),
                            isPosting = state.postingDay == day,
                            isImportingPhotos = isActedOn && state.isImportingPhotos,
                            note = if (isActedOn) postProblemText(state.postProblem) ?: state.importSummary else null,
                            onOpen = { onOpenDay(day) },
                            onPost = { onPostDay(day) },
                            onAddPhotos = { onAddPhotos(day) }
                        )
                    }
                    item(key = "end") {
                        EndTripRow(isEnding = state.isEnding, onEndTrip = onEndTrip)
                    }
                }
            }
        }
    }
}

/** Who can see the trip, worded for a trip that's posted a day at a time. */
private fun liveVisibilityLine(visibility: TripVisibility): String = when (visibility) {
    TripVisibility.PUBLIC -> "Public · Others see each day only after you post it."
    TripVisibility.UNLISTED -> "Unlisted · Only people with the link, and only days you've posted."
    TripVisibility.PRIVATE -> "Private · Only you can see this trip."
}

/** The way out, kept quiet at the end of the list rather than pinned under every day. */
@Composable
private fun EndTripRow(isEnding: Boolean, onEndTrip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Back home?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onEndTrip, enabled = !isEnding) { Text("End trip") }
    }
}

@Composable
private fun TripSettingsDialog(
    currentTitle: String,
    currentVisibility: TripVisibility,
    onSave: (String, TripVisibility) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    var visibility by remember { mutableStateOf(currentVisibility) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trip settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                TripVisibilityPicker(selected = visibility, onSelect = { visibility = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, visibility) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * One day of the trip: what's on it, whether anyone else can see it yet, and
 * the next thing to do with it. Tapping the card opens the day.
 */
@Composable
private fun LiveDayCard(
    day: Int,
    dateLabel: String?,
    isToday: Boolean,
    stops: List<StopWithPhotos>,
    isPosted: Boolean,
    isPosting: Boolean,
    isImportingPhotos: Boolean,
    note: String?,
    onOpen: () -> Unit,
    onPost: () -> Unit,
    onAddPhotos: () -> Unit
) {
    val photos = stops.flatMap { it.photoUrls }
    HairlineCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open Day $day", onClick = onOpen)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // The title takes what it needs and only the leftover goes to the gap,
                // so the date isn't cut short to make room for empty space.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        listOfNotNull("Day $day", dateLabel).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isToday) Tag("Today")
                }
                DayStatusChip(isPosted)
            }

            PhotoStrip(photos, emptyText = if (stops.isEmpty()) "Nothing here yet" else "No photos yet")

            if (stops.isNotEmpty()) {
                Text(
                    listOf(
                        countText(stops.size, Noun.STOP),
                        countText(photos.size, Noun.PHOTO)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StopPreviewRow(stopPreview(stops.map { it.stop.name }, stops.size))
            }

            note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when {
                    isPosted -> TextButton(onClick = onOpen) { Text("Edit day") }
                    else -> {
                        if (stops.isNotEmpty()) {
                            PrimaryButton(onClick = onPost, enabled = !isPosting && !isImportingPhotos) {
                                if (isPosting) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text("Post Day $day")
                                }
                            }
                        }
                        if (isImportingPhotos) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Reading photos…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TextButton(onClick = onAddPhotos, enabled = !isPosting) { Text("Add photos") }
                        }
                    }
                }
            }
        }
    }
}

/** "Posted" in the primary tint, or a muted "Draft · only you". */
@Composable
private fun DayStatusChip(isPosted: Boolean) {
    val (container, content) = if (isPosted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        if (isPosted) "Posted" else "Draft · only you",
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .background(container)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    )
}

/** Up to three of the day's photos, or a quiet empty slot saying [emptyText] when it has none. */
@Composable
private fun PhotoStrip(photos: List<String>, emptyText: String) {
    val shape = RoundedCornerShape(CuratedCornerRadius)
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        photos.take(3).forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/** Stops whose day row is gone. Nobody else can see them until they're moved onto a day. */
@Composable
private fun UnassignedCard(stops: List<StopWithPhotos>, onEdit: (StopWithPhotos) -> Unit) {
    HairlineCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Text(
                "Not on a day",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
            Text(
                "Only you can see these. Tap one to move it onto a day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
            stops.forEachIndexed { index, item ->
                if (index > 0) HairlineDivider(modifier = Modifier.padding(start = Spacing.md))
                StopRow(
                    number = index + 1,
                    item = item,
                    canMoveUp = false,
                    canMoveDown = false,
                    onClick = { onEdit(item) },
                    onMoveUp = {},
                    onMoveDown = {}
                )
            }
        }
    }
}

/**
 * Confirms End trip. Names what it will do to unposted days, and offers the way
 * back for a day that was skipped on purpose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndTripSheet(
    unpostedDays: List<Int>,
    isEnding: Boolean,
    canEnd: Boolean,
    onEnd: () -> Unit,
    onReviewDays: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        EndTripSheetContent(unpostedDays, isEnding, canEnd, onEnd, onReviewDays)
    }
}

@Composable
private fun EndTripSheetContent(
    unpostedDays: List<Int>,
    isEnding: Boolean,
    canEnd: Boolean,
    onEnd: () -> Unit,
    onReviewDays: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text("End this trip?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            when {
                !canEnd -> "Add at least one place before ending the trip."
                unpostedDays.isEmpty() -> "Every day is already posted. The trip moves to your profile and to Explore."
                unpostedDays.size == 1 ->
                    "1 unposted day will be published with this trip (Day ${unpostedDays.single()})."
                else ->
                    "${unpostedDays.size} unposted days will be published with this trip " +
                        "(Days ${unpostedDays.joinToString(", ")})."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (unpostedDays.isNotEmpty()) {
            Text(
                "They won't show up in anyone's feed on their own. Empty days are left out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PrimaryButton(onClick = onEnd, enabled = canEnd && !isEnding, modifier = Modifier.fillMaxWidth()) {
            if (isEnding) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text("End trip")
            }
        }
        if (unpostedDays.isNotEmpty()) {
            TextButton(onClick = onReviewDays, enabled = !isEnding, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Review days first")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EndTripSheetPreview() {
    CuratedTheme { EndTripSheetContent(unpostedDays = listOf(3, 4), isEnding = false, canEnd = true, onEnd = {}, onReviewDays = {}) }
}

private fun previewStop(id: String, name: String, photos: Int) = StopWithPhotos(
    stop = Stop(
        id = id, dayId = "day", tripId = "trip", name = name, category = StopCategory.SIGHT,
        latitude = 0.0, longitude = 0.0, orderInDay = 0, createdAt = Instant.parse("2026-10-05T09:00:00Z")
    ),
    photoUrls = List(photos) { "" }
)

@Preview(showBackground = true, name = "Draft day")
@Composable
private fun LiveDayCardDraftPreview() {
    CuratedTheme {
        LiveDayCard(
            day = 3, dateLabel = "Mon, Oct 5", isToday = true,
            stops = listOf(
                previewStop("1", "Belém Tower", 3),
                previewStop("2", "Pastéis de Belém", 1),
                previewStop("3", "LX Factory", 1),
                previewStop("4", "Time Out Market", 0)
            ),
            isPosted = false, isPosting = false, isImportingPhotos = false, note = null,
            onOpen = {}, onPost = {}, onAddPhotos = {}
        )
    }
}

@Preview(showBackground = true, name = "Posted day")
@Composable
private fun LiveDayCardPostedPreview() {
    CuratedTheme {
        LiveDayCard(
            day = 2, dateLabel = "Sun, Oct 4", isToday = false,
            stops = listOf(previewStop("1", "Miradouro de Santa Luzia", 2), previewStop("2", "Alfama", 2)),
            isPosted = true, isPosting = false, isImportingPhotos = false, note = null,
            onOpen = {}, onPost = {}, onAddPhotos = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty day")
@Composable
private fun LiveDayCardEmptyPreview() {
    CuratedTheme {
        LiveDayCard(
            day = 1, dateLabel = "Sat, Oct 3", isToday = false, stops = emptyList(),
            isPosted = false, isPosting = false, isImportingPhotos = false, note = null,
            onOpen = {}, onPost = {}, onAddPhotos = {}
        )
    }
}
