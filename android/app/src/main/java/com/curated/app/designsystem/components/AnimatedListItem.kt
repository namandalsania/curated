package com.curated.app.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fade + slight upward slide on first composition, staggered by [index].
 * Used for Home feed and Explore list items so the feed doesn't just pop in.
 *
 * Draw-phase only (graphicsLayer): the item occupies its full height from the
 * very first frame. An AnimatedVisibility here would measure every item at 0px
 * until its animation started, which lets a LazyColumn compose the whole list
 * at once and stalls scrolling on each newly-visible item.
 */
@Composable
fun AnimatedListItem(index: Int, content: @Composable () -> Unit) {
    // Saveable so an item scrolled off and back on doesn't replay the entrance.
    var hasAppeared by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (hasAppeared) 1f else 0f) }
    val offsetPx = with(LocalDensity.current) { 24.dp.toPx() }

    LaunchedEffect(Unit) {
        if (hasAppeared) return@LaunchedEffect
        delay(index.coerceAtMost(10) * 30L)
        launch { progress.animateTo(1f, tween(250)) }
        hasAppeared = true
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * offsetPx
        }
    ) {
        content()
    }
}
