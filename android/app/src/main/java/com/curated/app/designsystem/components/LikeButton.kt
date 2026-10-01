package com.curated.app.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A brief scale-up bounce on tap, not an instant icon swap.
 *
 * The tints are overridable because the feed sits this on a photo, where the
 * surface colors it defaults to have nothing like enough contrast.
 */
@Composable
fun LikeButton(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    likedTint: Color = MaterialTheme.colorScheme.primary,
    unlikedTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    var bounce by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (bounce) 1.35f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        finishedListener = { if (bounce) bounce = false },
        label = "likeBounce"
    )
    IconButton(
        onClick = {
            bounce = true
            onToggle()
        },
        modifier = modifier.size(36.dp)
    ) {
        Icon(
            if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Like",
            tint = if (liked) likedTint else unlikedTint,
            modifier = Modifier.scale(scale)
        )
    }
}
