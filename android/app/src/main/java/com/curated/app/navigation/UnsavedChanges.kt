package com.curated.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the screen on show holds input that leaving it would lose, so the
 * bottom bar can ask first. Screens that save as you go never report anything.
 */
class UnsavedChanges {
    private var owner by mutableStateOf<Any?>(null)

    val any: Boolean get() = owner != null

    fun report(key: Any, unsaved: Boolean) {
        if (unsaved) owner = key else clear(key)
    }

    /** Only the screen that reported can clear it, so a screen leaving can't wipe the next one's. */
    fun clear(key: Any) {
        if (owner === key) owner = null
    }
}

val LocalUnsavedChanges = staticCompositionLocalOf { UnsavedChanges() }

/** Tells the bottom bar whether this screen has [unsaved] input; cleared when the screen goes. */
@Composable
fun ReportUnsavedChanges(unsaved: Boolean) {
    val changes = LocalUnsavedChanges.current
    val key = remember { Any() }
    SideEffect { changes.report(key, unsaved) }
    DisposableEffect(changes) { onDispose { changes.clear(key) } }
}
