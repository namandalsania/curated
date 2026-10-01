package com.curated.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing

/** How loud a [Tag] is: [Accent] for state worth noticing (LIVE), [Quiet] for plain facts (Private). */
enum class TagStyle { Accent, Quiet }

/**
 * A small static label. Not interactive - [CuratedFilterChip] is the tappable one.
 * Opaque in both styles, so it stays legible laid over a cover photo.
 */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, style: TagStyle = TagStyle.Quiet) {
    val (container, content) = when (style) {
        TagStyle.Accent -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        TagStyle.Quiet -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(CuratedCornerRadius))
            .background(container)
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    )
}

@Preview
@Composable
private fun TagPreview() {
    CuratedTheme {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.background(Color.DarkGray).padding(Spacing.md),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)
        ) {
            Tag("LIVE · Day 3", style = TagStyle.Accent)
            Tag("Unlisted")
            Tag("Private")
        }
    }
}
