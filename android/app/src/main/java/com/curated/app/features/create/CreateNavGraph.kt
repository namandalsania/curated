package com.curated.app.features.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.curated.app.navigation.AppRoutes

/**
 * Two ways in, chosen on [NewTripScreen]:
 *
 * - Traveling now: where are you, then straight into posting Day 1. The live
 *   screen sits underneath, with one Post Day screen per day, until End trip.
 * - Already took it: the photo picker, a review of what the photos said, then
 *   the builder and publish. Adding a place or more photos hangs off the builder.
 */
fun NavGraphBuilder.createNavGraph(navController: NavController) {
    navigation(startDestination = CreateRoutes.NEW_TRIP, route = CreateRoutes.GRAPH) {
        composable(CreateRoutes.NEW_TRIP) { entry ->
            val context = LocalContext.current
            val viewModel = entry.createGraphViewModel(navController)
            NewTripScreen(
                onTravelingNow = { navController.navigate(CreateRoutes.LIVE_START) },
                onPhotosPicked = { uris ->
                    viewModel.analyzePhotos(context, uris)
                    navController.navigate(CreateRoutes.IMPORT_REVIEW)
                }
            )
        }
        composable(CreateRoutes.LIVE_START) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            LiveStartScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onStarted = { tripId ->
                    // The trip exists now, so its question isn't somewhere to go back to:
                    // back from Day 1 is the live trip, and back from that is the start.
                    navController.navigate(CreateRoutes.live(tripId)) {
                        popUpTo(CreateRoutes.LIVE_START) { inclusive = true }
                    }
                    navController.navigate(CreateRoutes.postDay(1))
                }
            )
        }
        composable(CreateRoutes.IMPORT_REVIEW) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            ImportReviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCreated = {
                    navController.navigate(CreateRoutes.BUILDER) {
                        popUpTo(CreateRoutes.IMPORT_REVIEW) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "${CreateRoutes.LIVE}?tripId={tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            LaunchedEffect(tripId) { if (tripId.isNotEmpty()) viewModel.openLiveTrip(tripId) }
            LiveTripScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenDay = { day -> navController.navigate(CreateRoutes.postDay(day)) },
                onEnded = { endedId ->
                    navController.navigate(AppRoutes.tripDetail(endedId)) {
                        popUpTo(CreateRoutes.GRAPH) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = CreateRoutes.POST_DAY_PATTERN,
            arguments = listOf(navArgument("dayIndex") { type = NavType.IntType })
        ) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            PostDayScreen(
                viewModel = viewModel,
                dayIndex = entry.arguments?.getInt("dayIndex") ?: 1,
                onBack = { navController.popBackStack() },
                onAddPlace = { day -> navController.navigate(CreateRoutes.addPlace(day)) }
            )
        }
        composable(CreateRoutes.IMPORT_PHOTOS) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            ImportPhotosScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(CreateRoutes.BUILDER) {
                        // The photo screen is a step on the way, not somewhere to go back to.
                        popUpTo(CreateRoutes.IMPORT_PHOTOS) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "${CreateRoutes.BUILDER}?tripId={tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            // Set when opened from Profile - Drafts.
            LaunchedEffect(tripId) { if (tripId.isNotEmpty()) viewModel.resumeDraft(tripId) }
            ItineraryBuilderScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddPlace = { dayIndex -> navController.navigate(CreateRoutes.addPlace(dayIndex)) },
                onImportPhotos = { navController.navigate(CreateRoutes.IMPORT_PHOTOS) },
                onReview = { navController.navigate(CreateRoutes.PUBLISH) }
            )
        }
        composable(
            route = CreateRoutes.ADD_PLACE_PATTERN,
            arguments = listOf(navArgument("dayIndex") { type = NavType.IntType })
        ) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            AddPlaceScreen(
                viewModel = viewModel,
                dayIndex = entry.arguments?.getInt("dayIndex") ?: 1,
                onDone = { navController.popBackStack() }
            )
        }
        composable(CreateRoutes.PUBLISH) { entry ->
            val viewModel = entry.createGraphViewModel(navController)
            PublishScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPublished = { tripId ->
                    navController.navigate(AppRoutes.tripDetail(tripId)) {
                        popUpTo(CreateRoutes.GRAPH) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun NavBackStackEntry.createGraphViewModel(navController: NavController): CreateTripViewModel {
    val context = LocalContext.current
    val parentEntry = remember(this) { navController.getBackStackEntry(CreateRoutes.GRAPH) }
    return viewModel(viewModelStoreOwner = parentEntry, factory = CreateTripViewModel.factory(context))
}
