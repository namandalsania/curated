package com.curated.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.curated.app.core.data.BlockedAccounts
import com.curated.app.core.data.ModerationRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.features.activity.ActivityScreen
import com.curated.app.features.activity.ActivityTab
import com.curated.app.features.create.CreateRoutes
import com.curated.app.features.create.createNavGraph
import com.curated.app.features.explore.ExploreScreen
import com.curated.app.features.explore.TripDetailScreen
import com.curated.app.features.home.HomeFeedScreen
import com.curated.app.features.home.HomeReselect
import com.curated.app.features.moderation.BlockedAccountsScreen
import com.curated.app.features.plans.PlanEditorScreen
import com.curated.app.features.plans.PlansScreen
import com.curated.app.features.plans.SavedPlacesScreen
import com.curated.app.features.profile.EditProfileScreen
import com.curated.app.features.profile.FollowListKind
import com.curated.app.features.profile.FollowListScreen
import com.curated.app.features.profile.ProfileScreen
import com.curated.app.features.settings.DeleteAccountScreen
import com.curated.app.features.settings.SettingsScreen

@Composable
fun AppNavHost(currentUserId: String) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Who you've blocked, for screens to filter what they've already loaded.
    // Reset per account, so a sign-out doesn't leak the last user's list.
    LaunchedEffect(currentUserId) {
        BlockedAccounts.clear()
        runCatching { ModerationRepository(SupabaseProvider.client(context.applicationContext)).refreshBlockedIds() }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevelRoutes = setOf(AppRoutes.HOME, AppRoutes.EXPLORE, CreateRoutes.GRAPH, AppRoutes.PROFILE_PATTERN)
    val showBottomBar = currentDestination?.hierarchy?.any { it.route in topLevelRoutes } == true

    // No window insets here: each screen's own Scaffold/TopAppBar already pads for
    // the status bar, so applying them again left a blank band above every title.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentDestination.isInHierarchy(AppRoutes.HOME),
                        onClick = {
                            // Already on Home: scroll it to the top instead.
                            if (currentDestination?.route == AppRoutes.HOME) HomeReselect.events.tryEmit(Unit)
                            else navController.navigateToTab(AppRoutes.HOME)
                        },
                        icon = { NavIconSlot { Icon(Icons.Outlined.Home, contentDescription = "Home") } },
                        label = { Text("Home") },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = currentDestination.isInHierarchy(AppRoutes.EXPLORE),
                        onClick = { navController.navigateToTab(AppRoutes.EXPLORE) },
                        icon = { NavIconSlot { Icon(Icons.Outlined.Explore, contentDescription = "Explore") } },
                        label = { Text("Explore") },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = currentDestination.isInHierarchy(CreateRoutes.GRAPH),
                        // A tab like the others: pushed onto whichever tab was open, it
                        // got saved with that tab and came back in place of its root.
                        onClick = { navController.navigateToTab(CreateRoutes.GRAPH) },
                        icon = {
                            // The one FAB-style element in the bottom bar: raised
                            // above the flat chrome to signal "this is actionable."
                            NavIconSlot {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Add,
                                        contentDescription = "Create",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            }
                        },
                        label = { Text("Create") },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = currentDestination.isInHierarchy(AppRoutes.PROFILE_PATTERN),
                        onClick = { navController.navigateToTab(AppRoutes.profile(currentUserId)) },
                        icon = { NavIconSlot { Icon(Icons.Outlined.AccountCircle, contentDescription = "Profile") } },
                        label = { Text("Profile") },
                        colors = itemColors
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppRoutes.HOME) {
                HomeFeedScreen(
                    onTripClick = { tripId -> navController.navigate(AppRoutes.tripDetail(tripId)) },
                    onAuthorClick = { authorId -> navController.navigate(AppRoutes.profile(authorId)) },
                    onOpenPlans = { navController.navigate(AppRoutes.PLANS) { launchSingleTop = true } },
                    onOpenActivity = { tab -> navController.navigate(AppRoutes.activity(tab)) { launchSingleTop = true } },
                    onOpenSavedPlaces = { navController.navigate(AppRoutes.SAVED_PLACES) { launchSingleTop = true } },
                    onLiveDayClick = { tripId, dayIndex -> navController.navigate(AppRoutes.tripDetailAtDay(tripId, dayIndex)) },
                    onFindPeople = { navController.navigateToTab(AppRoutes.EXPLORE) }
                )
            }
            composable(AppRoutes.EXPLORE) {
                ExploreScreen(
                    onTripClick = { tripId -> navController.navigate(AppRoutes.tripDetail(tripId)) },
                    onAuthorClick = { authorId -> navController.navigate(AppRoutes.profile(authorId)) }
                )
            }
            createNavGraph(navController)
            composable(
                route = AppRoutes.TRIP_DETAIL_PATTERN,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.StringType },
                    navArgument("day") { type = NavType.IntType; defaultValue = -1 }
                )
            ) { entry ->
                val tripId = entry.arguments?.getString("tripId").orEmpty()
                val initialDay = entry.arguments?.getInt("day")?.takeIf { it > 0 }
                TripDetailScreen(
                    tripId = tripId,
                    onBack = { navController.popBackStack() },
                    onAuthorClick = { authorId -> navController.navigate(AppRoutes.profile(authorId)) },
                    onOpenSavedPlaces = { navController.navigate(AppRoutes.SAVED_PLACES) { launchSingleTop = true } },
                    initialDay = initialDay
                )
            }
            composable(
                route = AppRoutes.PROFILE_PATTERN,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                val userId = entry.arguments?.getString("userId").orEmpty()
                val reloadKey by entry.savedStateHandle
                    .getStateFlow(AppRoutes.PROFILE_RELOAD_KEY, 0L)
                    .collectAsState()
                ProfileScreen(
                    userId = userId,
                    reloadKey = reloadKey,
                    onTripClick = { tripId -> navController.navigate(AppRoutes.tripDetail(tripId)) },
                    onFollowersClick = { navController.navigate(AppRoutes.followList(userId, FollowListKind.FOLLOWERS)) },
                    onFollowingClick = { navController.navigate(AppRoutes.followList(userId, FollowListKind.FOLLOWING)) },
                    onEditProfile = { navController.navigate(AppRoutes.EDIT_PROFILE) },
                    onOpenSavedPlaces = { navController.navigate(AppRoutes.SAVED_PLACES) { launchSingleTop = true } },
                    onResumeDraft = { tripId -> navController.navigate(CreateRoutes.resumeDraft(tripId)) },
                    onOpenLiveTrip = { tripId -> navController.navigate(CreateRoutes.live(tripId)) },
                    onOpenPlans = { navController.navigate(AppRoutes.PLANS) { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.BLOCKED_ACCOUNTS) {
                BlockedAccountsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBlockedAccounts = { navController.navigate(AppRoutes.BLOCKED_ACCOUNTS) { launchSingleTop = true } },
                    onDeleteAccount = { navController.navigate(AppRoutes.DELETE_ACCOUNT) { launchSingleTop = true } }
                )
            }
            composable(AppRoutes.DELETE_ACCOUNT) {
                DeleteAccountScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = AppRoutes.ACTIVITY_PATTERN,
                arguments = listOf(navArgument("tab") { type = NavType.StringType })
            ) { entry ->
                val tab = entry.arguments?.getString("tab")
                    ?.let { name -> ActivityTab.entries.firstOrNull { it.name == name } }
                    ?: ActivityTab.ACTIVITY
                ActivityScreen(
                    initialTab = tab,
                    onBack = { navController.popBackStack() },
                    // The day is carried for opening a trip at a day (Home, step 4); the trip page opens at the top for now.
                    onOpenTrip = { tripId, _ -> navController.navigate(AppRoutes.tripDetail(tripId)) },
                    onOpenProfile = { userId -> navController.navigate(AppRoutes.profile(userId)) },
                    onOpenPlans = { navController.navigate(AppRoutes.PLANS) { launchSingleTop = true } }
                )
            }
            composable(AppRoutes.SAVED_PLACES) {
                SavedPlacesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrip = { tripId -> navController.navigate(AppRoutes.tripDetail(tripId)) },
                    onOpenPlans = { navController.navigate(AppRoutes.PLANS) { launchSingleTop = true } }
                )
            }
            composable(AppRoutes.PLANS) {
                PlansScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlan = { planId -> navController.navigate(AppRoutes.planEditor(planId)) }
                )
            }
            composable(
                route = AppRoutes.PLAN_EDITOR_PATTERN,
                arguments = listOf(navArgument("planId") { type = NavType.StringType })
            ) { entry ->
                PlanEditorScreen(
                    planId = entry.arguments?.getString("planId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenSavedPlaces = { navController.navigate(AppRoutes.SAVED_PLACES) { launchSingleTop = true } }
                )
            }
            composable(AppRoutes.EDIT_PROFILE) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(AppRoutes.PROFILE_RELOAD_KEY, System.currentTimeMillis())
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = AppRoutes.FOLLOW_LIST_PATTERN,
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType },
                    navArgument("kind") { type = NavType.StringType }
                )
            ) { entry ->
                val userId = entry.arguments?.getString("userId").orEmpty()
                val kind = entry.arguments?.getString("kind")
                    ?.let { runCatching { FollowListKind.valueOf(it) }.getOrNull() }
                    ?: FollowListKind.FOLLOWERS
                FollowListScreen(
                    userId = userId,
                    kind = kind,
                    onBack = { navController.popBackStack() },
                    onUserClick = { id -> navController.navigate(AppRoutes.profile(id)) }
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun androidx.navigation.NavDestination?.isInHierarchy(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

/**
 * A fixed-height slot for a bottom-bar icon.
 *
 * Create's raised 40dp circle is taller than the other tabs' 24dp glyphs, which
 * pushed its label down and left the four labels on different baselines. Giving
 * every tab the same slot height lines them up again.
 */
@Composable
private fun NavIconSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.height(NavIconSlotHeight),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Tall enough for the raised Create circle, which is the biggest of the four. */
private val NavIconSlotHeight = 40.dp
