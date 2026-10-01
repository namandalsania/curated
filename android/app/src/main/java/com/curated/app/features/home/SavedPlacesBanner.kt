package com.curated.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.pressScale

private val BannerCorner = 16.dp

/**
 * "3 saved places - ready to plan", above the feed. Opens the saved-places list,
 * which is where a trip actually gets started from.
 *
 * Dismissal is deliberately session-only: the prompt is worth showing again next
 * time the app opens, and a permanent dismissal would need somewhere to store it.
 */
@Composable
fun SavedPlacesBanner(
    savedPlaceCount: Int,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (savedPlaceCount <= 0) return

    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(BannerCorner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .pressScale(interactionSource)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "$savedPlaceCount saved ${if (savedPlaceCount == 1) "place" else "places"} - ready to plan",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
