package com.curated.app.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.pressScale

/**
 * The one filled/primary button in the app: one per screen, solid terracotta,
 * shared corner radius, subtle press-scale feedback. [elevated] is for the
 * handful of CTAs that should visibly float — e.g. Publish — everywhere else
 * stays flat like the rest of the flat/hairline chrome.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevated: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(CuratedCornerRadius),
        elevation = if (elevated) {
            ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
        } else {
            ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        },
        modifier = modifier.pressScale(interactionSource),
        content = content
    )
}

/** The one outlined/secondary button in the app: hairline border, shared shape, press-scale. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(CuratedCornerRadius),
        modifier = modifier.pressScale(interactionSource),
        content = content
    )
}
