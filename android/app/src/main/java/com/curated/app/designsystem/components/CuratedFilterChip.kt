package com.curated.app.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.curated.app.designsystem.pressScale

/**
 * The one chip style in the app: quiet hairline border when unselected
 * (inherited from the theme's outline tokens), solid terracotta fill when
 * selected — never the default Material secondaryContainer tint. Subtle
 * press-scale feedback on tap.
 */
@Composable
fun CuratedFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        interactionSource = interactionSource,
        modifier = modifier.pressScale(interactionSource),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
