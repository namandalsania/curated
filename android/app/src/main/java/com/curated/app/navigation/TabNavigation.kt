package com.curated.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/** Switches to a bottom-bar tab, saving the one being left and bringing back the one chosen. */
internal fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Opens a Create screen (a live trip, a draft) from another tab, as the Create tab.
 *
 * Pushed onto the tab it was opened from, it got saved with that tab: tapping that
 * tab brought the Create screen straight back, so it looked like the tap was ignored.
 * Not restored, so it always opens what was asked for rather than an older Create stack.
 */
internal fun NavController.openInCreateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}
