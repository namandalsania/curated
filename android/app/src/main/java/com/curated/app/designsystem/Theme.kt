package com.curated.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The one, final Curated palette — warm and terracotta-accented. There is
 * deliberately no dark variant yet: this is the single authoritative look.
 */
private val CuratedColorScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = TextOnAccent,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = TerracottaDeep,
    inversePrimary = TerracottaLight,

    secondary = TerracottaLight,
    onSecondary = WarmNearBlack,
    secondaryContainer = WarmSurfaceVariant,
    onSecondaryContainer = WarmNearBlack,

    tertiary = MutedText,
    onTertiary = TextOnAccent,
    tertiaryContainer = WarmSurfaceContainerHigh,
    onTertiaryContainer = WarmNearBlack,

    background = WarmBackground,
    onBackground = WarmNearBlack,

    surface = WarmBackground,
    onSurface = WarmNearBlack,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = MutedText,
    surfaceTint = Terracotta,

    surfaceBright = WarmBackground,
    surfaceDim = WarmSurfaceDim,
    surfaceContainerLowest = WarmBackground,
    surfaceContainerLow = WarmSurfaceContainerLow,
    surfaceContainer = WarmSurfaceContainer,
    surfaceContainerHigh = WarmSurfaceContainerHigh,
    surfaceContainerHighest = WarmSurfaceContainerHighest,

    outline = HairlineBorderStrong,
    outlineVariant = HairlineBorder,

    inverseSurface = WarmNearBlack,
    inverseOnSurface = WarmBackground,
    scrim = Color.Black,

    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

@Composable
fun CuratedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CuratedColorScheme,
        typography = CuratedTypography,
        shapes = CuratedShapes,
        content = content
    )
}
