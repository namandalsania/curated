package com.curated.app.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

private const val PressedScale = 0.97f

/**
 * The one press feedback in the app: a subtle scale-down on press that
 * springs back on release. Applied to TripCard, CuratedFilterChip, and
 * every primary/secondary button.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return this.scale(scale)
}
