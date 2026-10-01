package com.curated.app.features.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sensors
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.curated.app.core.format.shortDayText
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineDivider
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.designsystem.components.SecondaryButton
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTripScreen(
    viewModel: CreateTripViewModel,
    onBack: () -> Unit,
    onStartFromPhotos: () -> Unit,
    onWriteItMyself: () -> Unit,
    onLiveStarted: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var title by remember { mutableStateOf(state.title) }
    var destination by remember { mutableStateOf(state.destination) }
    var startDate by remember { mutableStateOf(state.startDate) }
    var endDate by remember { mutableStateOf(state.endDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val start = startDate
    val end = endDate
    val ready = title.isNotBlank() && destination.isNotBlank() && start != null && end != null && end >= start
    // A live trip has no end yet, and starts today unless you say otherwise - but not in the future.
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val liveStart = start ?: today
    val liveReady = title.isNotBlank() && destination.isNotBlank() && liveStart <= today

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("New trip")
                        Text(
                            "Step 1 of 3 · The basics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Trip title") },
                placeholder = { Text("Three days in Lisbon") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Destination") },
                placeholder = { Text("Lisbon, Portugal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(start?.shortDayText() ?: "Start date")
                }
                SecondaryButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(end?.shortDayText() ?: "End date")
                }
            }
            if (start != null && end != null) {
                val days = (end.toEpochDays() - start.toEpochDays() + 1).toInt()
                Text(
                    if (days < 1) {
                        "The end date is before the start date."
                    } else {
                        "That's ${if (days == 1) "1 day" else "$days days"} to fill in."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (days < 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "How do you want to build it?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.sm)
            )

            PrimaryButton(
                onClick = { if (ready) viewModel.startDraft(title, destination, start!!, end!!, onStartFromPhotos) },
                enabled = ready && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Start from photos", modifier = Modifier.padding(start = Spacing.xs))
            }
            SecondaryButton(
                onClick = { if (ready) viewModel.startDraft(title, destination, start!!, end!!, onWriteItMyself) },
                enabled = ready && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Write it myself", modifier = Modifier.padding(start = Spacing.xs))
            }

            Text(
                if (ready) {
                    "Photos are read for time and location to lay out your days. You can mix both."
                } else {
                    "Add a title, a destination and your dates to carry on."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HairlineDivider(modifier = Modifier.padding(vertical = Spacing.xs))

            SecondaryButton(
                onClick = { if (liveReady) viewModel.startLiveTrip(title, destination, liveStart, onLiveStarted) },
                enabled = liveReady && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("I'm on this trip now", modifier = Modifier.padding(start = Spacing.xs))
            }
            Text(
                when {
                    liveStart > today -> "A live trip can't start in the future. Pick today or an earlier start date."
                    else -> "Post it a day at a time as you go, starting ${if (liveStart == today) "today" else liveStart.shortDayText()}. " +
                        "No end date needed - you end it when you're home."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (liveStart > today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showStartPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDate = it.toLocalDate() }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEndPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { endDate = it.toLocalDate() }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
