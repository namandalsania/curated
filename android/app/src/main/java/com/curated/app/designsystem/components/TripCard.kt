package com.curated.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.pressScale

/**
 * The one trip card: a large edge-to-edge photo (no thumbnail padding),
 * flat — no elevation or shadow — separated from the page only by a
 * hairline border, with a caller-supplied content slot below the image.
 * Subtle press-scale feedback on tap.
 */
@Composable
fun TripCard(
    imageUrl: String?,
    imageContentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 3:2 in the feed, so two cards fit on screen; 4:3 elsewhere. */
    imageAspectRatio: Float = 4f / 3f,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CuratedCornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imageAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = imageContentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        content()
    }
}
