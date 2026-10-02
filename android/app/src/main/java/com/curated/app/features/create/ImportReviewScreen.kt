package com.curated.app.features.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.curated.app.core.format.shortDayText
import com.curated.app.core.model.TripVisibility
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton
import com.curated.app.features.trip.TripVisibilityPicker
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * "I already took this trip", after the photo picker: everything the photos
 * could tell - where, when, and a title from both - filled in for checking
 * rather than typing. Creating the trip writes the stops and opens the builder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportReviewScreen(
    viewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val review = state.importReview

    var destination by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    // The title follows the destination and dates until it's typed into.
    var titleEdited by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf<DateField?>(null) }
    var visibility by remember { mutableStateOf(TripVisibility.PUBLIC) }

    LaunchedEffect(review) {
        if (review != null) {
            destination = review.destination
            startDate = review.startDate
            endDate = review.endDate
            titleEdited = false
        }
    }
    LaunchedEffect(destination, startDate, endDate, titleEdited) {
        if (!titleEdited) title = TripSuggestions.title(destination, startDate, endDate)
    }

    ImportReviewContent(
        review = review,
        isAnalyzing = state.isAnalyzingPhotos,
        isSaving = state.isSaving,
        error = state.error,
        destination = destination,
        title = title,
        startDate = startDate,
        endDate = endDate,
        visibility = visibility,
        onDestinationChange = { destination = it },
        onTitleChange = {
            title = it
            titleEdited = true
        },
        onPickStart = { picking = DateField.START },
        onPickEnd = { picking = DateField.END },
        onVisibilityChange = { visibility = it },
        onCreate = {
            val start = startDate
            val end = endDate
            if (start != null && end != null) {
                viewModel.createImportedTrip(title.trim(), destination.trim(), start, end, visibility, onCreated)
            }
        },
        onBack = onBack
    )

    picking?.let { field ->
        val initial = if (field == DateField.START) startDate else endDate ?: startDate
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial?.toEpochMillis())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = millis.toLocalDate()
                        if (field == DateField.START) startDate = date else endDate = date
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private enum class DateField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportReviewContent(
    review: ImportReview?,
    isAnalyzing: Boolean,
    isSaving: Boolean,
    error: String?,
    destination: String,
    title: String,
    startDate: LocalDate?,
    endDate: LocalDate?,
    visibility: TripVisibility,
    onDestinationChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onVisibilityChange: (TripVisibility) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit
) {
    val datesInOrder = startDate != null && endDate != null && endDate >= startDate
    val ready = review != null && destination.isNotBlank() && title.isNotBlank() && datesInOrder && !isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your trip") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (review != null) {
                PrimaryButton(
                    onClick = onCreate,
                    enabled = ready,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(Spacing.md)
                ) {
                    Text(if (isSaving) "Adding your photos…" else "Build the itinerary")
                }
            }
        }
    ) { padding ->
        if (review == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(Spacing.md)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Reading your photos…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                photoSummary(review),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                label = { Text("Destination") },
                placeholder = { Text("Lisbon, Portugal") },
                supportingText = if (review.destination.isBlank()) {
                    { Text("Couldn't tell where these were taken. Type where you went.") }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                DateButton(label = "Start", date = startDate, onClick = onPickStart, modifier = Modifier.weight(1f))
                DateButton(label = "End", date = endDate, onClick = onPickEnd, modifier = Modifier.weight(1f))
            }
            when {
                startDate == null || endDate == null -> Text(
                    "Your photos don't say when they were taken. Pick the dates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                !datesInOrder -> Text(
                    "The end date is before the start date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TripVisibilityPicker(
                selected = visibility,
                onSelect = onVisibilityChange,
                enabled = !isSaving,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md)
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SecondaryButton(onClick = onClick, modifier = modifier) {
        Text(date?.let { "$label: ${it.shortDayText()}" } ?: "$label date")
    }
}

/** "24 photos · 6 stops · 3 without a location" */
private fun photoSummary(review: ImportReview): String = buildList {
    add(if (review.photoCount == 1) "1 photo" else "${review.photoCount} photos")
    add(if (review.stopCount == 1) "1 stop" else "${review.stopCount} stops")
    if (review.withoutLocation > 0) add("${review.withoutLocation} without a location")
}.joinToString(" · ")

// The date picker works in UTC midnight millis.
private fun LocalDate.toEpochMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ImportReviewPreview() {
    CuratedTheme {
        ImportReviewContent(
            review = ImportReview(
                photoCount = 48,
                stopCount = 9,
                withoutLocation = 3,
                destination = "Lisbon, Portugal",
                startDate = LocalDate(2026, 9, 12),
                endDate = LocalDate(2026, 9, 16)
            ),
            isAnalyzing = false,
            isSaving = false,
            error = null,
            destination = "Lisbon, Portugal",
            title = "Lisbon · September 2026",
            startDate = LocalDate(2026, 9, 12),
            endDate = LocalDate(2026, 9, 16),
            visibility = TripVisibility.PUBLIC,
            onDestinationChange = {},
            onTitleChange = {},
            onPickStart = {},
            onPickEnd = {},
            onVisibilityChange = {},
            onCreate = {},
            onBack = {}
        )
    }
}
