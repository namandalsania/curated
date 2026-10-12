package com.curated.app.features.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.curated.app.designsystem.CuratedTheme

/**
 * The trip owner's "More options" menu. Other people's trips get
 * ModerationMenu (report, block) in the same place instead.
 */
@Composable
fun TripOwnerMenu(
    onDeleteTrip: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Delete trip", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onDeleteTrip()
                }
            )
        }
    }
}

/** Asks before Delete trip; while it runs, the buttons wait instead of closing. */
@Composable
fun DeleteTripDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = { Text("Delete this trip?") },
        text = { Text("This permanently deletes the trip and its photos. It can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                Text(if (isDeleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Preview
@Composable
private fun DeleteTripDialogPreview() {
    CuratedTheme { DeleteTripDialog(isDeleting = false, onConfirm = {}, onDismiss = {}) }
}
