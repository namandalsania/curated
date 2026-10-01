package com.curated.app.features.create

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.curated.app.core.format.displayText
import kotlinx.datetime.LocalTime

/** A chip showing the stop's arrival time ("Add arrival time" when unset) that opens a time picker. */
@Composable
fun ArrivalTimeField(time: LocalTime?, onTimeChange: (LocalTime?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = { showPicker = true },
            label = { Text(time?.let { "Arrived ${it.displayText()}" } ?: "Add arrival time") },
            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) }
        )
        if (time != null) {
            IconButton(onClick = { onTimeChange(null) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear arrival time")
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = time?.hour ?: 12,
            initialMinute = time?.minute ?: 0,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current)
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Arrival time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        )
    }
}
