package com.curated.app.designsystem.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Thin marker wrapper around [Icon]: every call site should be passing an
 * `Icons.Outlined.*` (or `Icons.AutoMirrored.Outlined.*`) vector, never
 * `Icons.Filled.*`, so the whole app reads as line icons, not solid glyphs.
 */
@Composable
fun OutlineIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
