package com.curated.app.features.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.curated.app.core.model.TripVisibility
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing

fun TripVisibility.label(): String = when (this) {
    TripVisibility.PUBLIC -> "Public"
    TripVisibility.UNLISTED -> "Unlisted"
    TripVisibility.PRIVATE -> "Private"
}

/**
 * What each setting means, in the terms the app enforces: list queries only
 * return public trips, an unlisted trip still opens from its link, and the
 * database hides a private one from everyone but its author.
 */
fun TripVisibility.explanation(): String = when (this) {
    TripVisibility.PUBLIC -> "Shown in Explore and search."
    TripVisibility.UNLISTED -> "Only people with the link."
    TripVisibility.PRIVATE -> "Only you."
}

/** Who can see a trip: one row per setting, each with what it means. */
@Composable
fun TripVisibilityPicker(
    selected: TripVisibility,
    onSelect: (TripVisibility) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier = modifier.selectableGroup()) {
        Text(
            "Who can see it",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Spacing.xs)
        )
        TripVisibility.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) }
                    )
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // The row handles the click, so the button itself doesn't take one.
                RadioButton(selected = option == selected, onClick = null, enabled = enabled)
                Column {
                    Text(
                        option.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        option.explanation(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun TripVisibilityPickerPreview() {
    CuratedTheme {
        TripVisibilityPicker(
            selected = TripVisibility.UNLISTED,
            onSelect = {},
            modifier = Modifier.padding(Spacing.md)
        )
    }
}
