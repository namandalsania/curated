package com.curated.app.navigation

import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The app's tab layout in miniature: Home first, Profile, and Create as a nested graph. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabNavigationTest {

    private lateinit var nav: NavHostController

    private val route get() = nav.currentDestination?.route

    @Before
    fun setUp() {
        nav = NavHostController(RuntimeEnvironment.getApplication())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.setViewModelStore(ViewModelStore())
        nav.graph = nav.createGraph(startDestination = "home") {
            composable("home") {}
            composable("explore") {}
            composable("profile/{userId}") {}
            navigation(startDestination = "create/new_trip", route = "create_graph") {
                composable("create/new_trip") {}
                composable("create/live?tripId={tripId}") {}
                composable("create/post_day/{dayIndex}") {}
            }
        }
    }

    @Test
    fun `a live trip opened from Profile lets the Profile tab take you back to Profile`() {
        nav.navigateToTab("profile/me")
        nav.openInCreateTab("create/live?tripId=t1")
        nav.navigate("create/post_day/3")

        nav.navigateToTab("profile/me")

        assertEquals("profile/{userId}", route)
        assertFalse(nav.currentBackStack.value.any { it.destination.route == "create_graph" })
    }

    @Test
    fun `the Create tab brings the live trip back where it was`() {
        nav.navigateToTab("profile/me")
        nav.openInCreateTab("create/live?tripId=t1")
        nav.navigate("create/post_day/3")
        nav.navigateToTab("explore")

        nav.navigateToTab("create_graph")

        assertEquals("create/post_day/{dayIndex}", route)
    }

    @Test
    fun `pushing Create onto Profile is what hid the Profile tab`() {
        // The old way in: a plain navigate from Profile.
        nav.navigateToTab("profile/me")
        nav.navigate("create/live?tripId=t1")

        nav.navigateToTab("profile/me")

        assertEquals("create/live?tripId={tripId}", route)
    }

    @Test
    fun `opening a Create screen never brings back an older Create stack`() {
        nav.navigateToTab("create_graph")
        nav.navigateToTab("profile/me")

        nav.openInCreateTab("create/live?tripId=t1")

        assertEquals("create/live?tripId={tripId}", route)
        assertEquals("t1", nav.currentBackStackEntry?.arguments?.getString("tripId"))
    }

    @Test
    fun `unsaved input is cleared only by the screen that reported it`() {
        val changes = UnsavedChanges()
        val leaving = Any()
        val arriving = Any()
        changes.report(leaving, true)
        changes.report(arriving, true)
        changes.clear(leaving)
        assertTrue(changes.any)
        changes.report(arriving, false)
        assertFalse(changes.any)
    }
}
