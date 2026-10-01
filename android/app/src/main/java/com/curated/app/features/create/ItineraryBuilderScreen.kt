package com.curated.app.features.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.format.displayText
import com.curated.app.core.format.label
import com.curated.app.core.format.shortDayText
import com.curated.app.core.model.StopCategory
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.CuratedFilterChip
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton

/**
 * The one screen where a trip gets built: every day, every place, in order.
 * It replaced three passes over the same list (review, confirm, captions).
 * Changes are written to the draft as they're made.
 */
@Composable
fun ItineraryBuilderScreen(
    viewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onAddPlace: (Int) -> Unit,
    onImportPhotos: () -> Unit,
    onReview: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<StopWithPhotos?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val addPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        val stopId = editing?.stop?.id
        if (stopId != null && uris.isNotEmpty()) viewModel.addPhotosToStop(stopId, context, uris)
    }

    editing?.let { item ->
        EditStopSheet(
            item = item,
            dayCount = state.dayCount,
            currentDay = state.days.firstOrNull { day -> day.stops.any { it.stop.id == item.stop.id } }?.dayIndex ?: 1,
            onSave = { name, category, caption, tips, time ->
                viewModel.updateStop(item.stop.id, name, category, caption, tips, time)
                editing = null
            },
            onAddPhotos = {
                addPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onMoveToDay = { day ->
                val from = state.days.firstOrNull { d -> d.stops.any { it.stop.id == item.stop.id } }?.dayIndex ?: 1
                viewModel.moveStopToDay(item.stop.id, from, day)
                editing = null
            },
            onDelete = {
                viewModel.deleteStop(item.stop.id)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this trip?") },
            text = { Text("The draft and everything in it goes away. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discardDraft(onBack)
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.title.ifBlank { "Your trip" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Step 2 of 3 · Build your itinerary",
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Trip options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Import photos") },
                                onClick = { menuOpen = false; onImportPhotos() }
                            )
                            DropdownMenuItem(
                                text = { Text("Discard draft") },
                                onClick = { menuOpen = false; confirmDiscard = true }
                            )
                        }
                    }
                }
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
                PrimaryButton(
                    onClick = onReview,
                    enabled = state.canPublish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.stopCount == 0) "Add a place to continue" else "Review & publish")
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
                    if (state.stopCount == 0) {
                        item(key = "start") {
                            StartHint(
                                isImporting = state.isImportingPhotos,
                                onImportPhotos = onImportPhotos
                            )
                        }
                    }
                    state.importSummary?.let { summary ->
                        item(key = "import-summary") {
                            Text(
                                summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.xs)
                            )
                        }
                    }

                    items(count = state.dayCount, key = { "day-${it + 1}" }) { index ->
                        val day = index + 1
                        DayCard(
                            day = day,
                            dateLabel = state.dateOf(day)?.shortDayText(),
                            stops = state.stopsOn(day),
                            onAddPlace = { onAddPlace(day) },
                            onEdit = { editing = it },
                            onMoveUp = { viewModel.moveStop(day, it, -1) },
                            onMoveDown = { viewModel.moveStop(day, it, +1) }
                        )
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

@Composable
private fun StartHint(isImporting: Boolean, onImportPhotos: () -> Unit) {
    HairlineCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Build it your way",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Add places to each day yourself, or import your photos and we'll read their time and location to lay the days out for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SecondaryButton(onClick = onImportPhotos, enabled = !isImporting, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Import photos", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

@Composable
private fun DayCard(
    day: Int,
    dateLabel: String?,
    stops: List<StopWithPhotos>,
    onAddPlace: () -> Unit,
    onEdit: (StopWithPhotos) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    HairlineCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Text(
                listOfNotNull("Day $day", dateLabel).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )

            if (stops.isEmpty()) {
                Text(
                    "Nothing here yet.",
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
                    onClick = { onEdit(item) },
                    onMoveUp = { onMoveUp(item.stop.id) },
                    onMoveDown = { onMoveDown(item.stop.id) }
                )
            }

            TextButton(onClick = onAddPlace, modifier = Modifier.padding(start = Spacing.xs)) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add a place", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

@Composable
internal fun StopRow(
    number: Int,
    item: StopWithPhotos,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val stop = item.stop
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(CuratedCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val photo = item.photoUrls.firstOrNull()
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    "$number",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stop.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    stop.arrivalTime?.displayText(),
                    stop.category.label(),
                    item.photoUrls.size.takeIf { it > 0 }?.let { if (it == 1) "1 photo" else "$it photos" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move ${stop.name} earlier")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move ${stop.name} later")
            }
        }
    }
}

/** Everything about one place in one sheet: name, kind, time, caption, tip, photos. */
@Composable
internal fun EditStopSheet(
    item: StopWithPhotos,
    dayCount: Int,
    /** Null for a stop that isn't on any day yet. */
    currentDay: Int?,
    onSave: (String, StopCategory, String, String, kotlinx.datetime.LocalTime?) -> Unit,
    onAddPhotos: () -> Unit,
    onMoveToDay: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val stop = item.stop
    var name by remember(stop.id) { mutableStateOf(stop.name) }
    var category by remember(stop.id) { mutableStateOf(stop.category) }
    var caption by remember(stop.id) { mutableStateOf(stop.caption.orEmpty()) }
    var tips by remember(stop.id) { mutableStateOf(stop.tips.orEmpty()) }
    var time by remember(stop.id) { mutableStateOf(stop.arrivalTime) }
    var dayMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Place") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StopCategory.entries.forEach { option ->
                    CuratedFilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = option.label()
                    )
                }
            }

            ArrivalTimeField(time = time, onTimeChange = { time = it })

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption") },
                placeholder = { Text("What was it like?") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tips,
                onValueChange = { tips = it },
                label = { Text("Tip (optional)") },
                placeholder = { Text("e.g. Go before 9am to skip the line") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(onClick = onAddPhotos) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        if (item.photoUrls.isEmpty()) "Add photos" else "Add more",
                        modifier = Modifier.padding(start = Spacing.xs)
                    )
                }
                if (dayCount > 1 || currentDay == null) {
                    Box {
                        SecondaryButton(onClick = { dayMenu = true }) {
                            Text(if (currentDay == null) "Choose a day" else "Day $currentDay")
                        }
                        DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                            (1..dayCount).filter { it != currentDay }.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text("Move to Day $day") },
                                    onClick = { dayMenu = false; onMoveToDay(day) }
                                )
                            }
                        }
                    }
                }
            }

            PrimaryButton(
                onClick = { onSave(name.trim().ifEmpty { stop.name }, category, caption, tips, time) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            HorizontalDivider()
            TextButton(onClick = onDelete) {
                Text("Remove this place", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
