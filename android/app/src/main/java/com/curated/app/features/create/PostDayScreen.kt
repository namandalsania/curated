package com.curated.app.features.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.format.shortDayText
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton
import com.curated.app.designsystem.components.Tag
import com.curated.app.designsystem.components.TagStyle

/**
 * One day of a live trip: pull in that day's photos, tidy the stops, post it.
 *
 * A posted day opens here too, for editing. Edits are saved as they're made and
 * never touch published_at, so an edited day keeps its place in the feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDayScreen(
    viewModel: CreateTripViewModel,
    dayIndex: Int,
    onBack: () -> Unit,
    onAddPlace: (Int) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<StopWithPhotos?>(null) }

    val isPosted = state.isPosted(dayIndex)
    val stops = state.stopsOn(dayIndex)
    val isPosting = state.postingDay == dayIndex

    // A stale refusal or import note from another day shouldn't greet this one.
    DisposableEffect(dayIndex) {
        viewModel.clearPostProblem()
        onDispose { viewModel.clearPostProblem() }
    }

    LaunchedEffect(state.justPostedDay) {
        if (state.justPostedDay == dayIndex) {
            viewModel.consumeJustPosted()
            onBack()
        }
    }

    val pickDayPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) viewModel.importPhotosForDay(context, uris, dayIndex)
    }
    val addStopPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        val stopId = editing?.stop?.id
        if (stopId != null && uris.isNotEmpty()) viewModel.addPhotosToStop(stopId, context, uris)
    }

    editing?.let { item ->
        EditStopSheet(
            item = item,
            dayCount = state.dayCount,
            currentDay = dayIndex,
            onSave = { name, category, caption, tips, time ->
                viewModel.updateStop(item.stop.id, name, category, caption, tips, time)
                editing = null
            },
            onAddPhotos = { addStopPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onMoveToDay = { day ->
                viewModel.moveStopToDay(item.stop.id, dayIndex, day)
                editing = null
            },
            onDelete = {
                viewModel.deleteStop(item.stop.id)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(listOfNotNull("Day $dayIndex", state.dateOf(dayIndex)?.shortDayText()).joinToString(" · "))
                        Text(
                            state.title,
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
                    if (isPosted) Tag("Posted", style = TagStyle.Accent, modifier = Modifier.padding(end = Spacing.md))
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                postProblemText(state.postProblem)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                }
                state.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                }
                if (isPosted) {
                    Text(
                        "Changes save as you make them and show up for everyone straight away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    SecondaryButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                } else {
                    PrimaryButton(
                        onClick = { viewModel.postDay(dayIndex) },
                        enabled = !isPosting && !state.isSaving && !state.isImportingPhotos,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Post Day $dayIndex")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item(key = "photos") {
                    SecondaryButton(
                        onClick = {
                            pickDayPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !state.isImportingPhotos,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add photos from this day", modifier = Modifier.padding(start = Spacing.xs))
                    }
                }
                item(key = "photos-hint") {
                    Text(
                        state.importSummary
                            ?: "Photos are grouped into places by where and when they were taken. Only photos from this day are used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.importSummary != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "stops") {
                    HairlineCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                            if (stops.isEmpty()) {
                                Text(
                                    "No places on this day yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                                )
                            }
                            stops.forEachIndexed { index, item ->
                                if (index > 0) HairlineDivider(modifier = Modifier.padding(start = Spacing.md))
                                StopRow(
                                    number = index + 1,
                                    item = item,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < stops.lastIndex,
                                    onClick = { editing = item },
                                    onMoveUp = { viewModel.moveStop(dayIndex, item.stop.id, -1) },
                                    onMoveDown = { viewModel.moveStop(dayIndex, item.stop.id, +1) }
                                )
                            }
                            TextButton(onClick = { onAddPlace(dayIndex) }, modifier = Modifier.padding(start = Spacing.xs)) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Add a place", modifier = Modifier.padding(start = Spacing.xs))
                            }
                        }
                    }
                }
            }

            if (state.isImportingPhotos) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Reading your photos…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = Spacing.sm)
                        )
                    }
                }
            }
        }
    }
}

/** The inline reason Post Day was refused, naming the stops at fault. */
private fun postProblemText(problem: LiveTripRules.PostProblem?): String? = when (problem) {
    null -> null
    LiveTripRules.PostProblem.NoStops -> "Add at least one place before posting this day."
    is LiveTripRules.PostProblem.Unassigned ->
        "These places aren't attached to a day yet: ${problem.stops.joinToString { it.name }}. " +
            "Open each one and choose its day."
}
