package com.curated.app.features.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.format.shortDayText
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton
import com.curated.app.designsystem.components.Tag
import com.curated.app.designsystem.components.TagStyle

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

    LaunchedEffect(state.endedTrip) {
        state.endedTrip?.let { trip ->
            viewModel.reset()
            onEnded(trip.id)
        }
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

    LiveTripContent(
        state = state,
        onBack = onBack,
        onOpenDay = onOpenDay,
        onEditUnassigned = { editing = it },
        onEndTrip = { showEndSheet = true }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTripContent(
    state: CreateWizardState,
    onBack: () -> Unit,
    onOpenDay: (Int) -> Unit,
    onEditUnassigned: (StopWithPhotos) -> Unit,
    onEndTrip: () -> Unit
) {
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
                actions = { Tag("LIVE", style = TagStyle.Accent, modifier = Modifier.padding(end = Spacing.md)) }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                state.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                }
                SecondaryButton(onClick = onEndTrip, enabled = !state.isEnding, modifier = Modifier.fillMaxWidth()) {
                    Text("End trip")
                }
            }
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
                    item(key = "intro") {
                        Text(
                            "Post each day when it's done. Until you do, only you can see it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                    if (state.unassignedStops.isNotEmpty()) {
                        item(key = "unassigned") {
                            UnassignedCard(stops = state.unassignedStops, onEdit = onEditUnassigned)
                        }
                    }
                    // Newest day first: while travelling, today is what you're working on.
                    items((state.dayCount downTo 1).toList(), key = { "day-$it" }) { day ->
                        LiveDayCard(
                            day = day,
                            dateLabel = state.dateOf(day)?.shortDayText(),
                            stops = state.stopsOn(day),
                            isPosted = state.isPosted(day),
                            onOpen = { onOpenDay(day) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDayCard(
    day: Int,
    dateLabel: String?,
    stops: List<StopWithPhotos>,
    isPosted: Boolean,
    onOpen: () -> Unit
) {
    HairlineCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull("Day $day", dateLabel).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isPosted) Tag("Posted", style = TagStyle.Accent) else Tag("Not posted")
            }
            Text(
                when {
                    stops.isEmpty() -> "Nothing here yet."
                    else -> stops.joinToString(" → ") { it.stop.name }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isPosted) {
                SecondaryButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Edit Day $day") }
            } else {
                PrimaryButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(if (stops.isEmpty()) "Add Day $day" else "Review & post Day $day")
                }
            }
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
