package com.curated.app.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The one corner radius in the app. Every rounded surface reads from here. */
val CuratedCornerRadius = 12.dp

/**
 * All five Material3 shape tiers map to the same radius, so regardless of
 * which tier a given component (Card, Chip, TextField, Dialog, BottomSheet…)
 * reads from internally, it resolves to one consistent corner everywhere.
 */
val CuratedShapes = Shapes(
    extraSmall = RoundedCornerShape(CuratedCornerRadius),
    small = RoundedCornerShape(CuratedCornerRadius),
    medium = RoundedCornerShape(CuratedCornerRadius),
    large = RoundedCornerShape(CuratedCornerRadius),
    extraLarge = RoundedCornerShape(CuratedCornerRadius)
)
