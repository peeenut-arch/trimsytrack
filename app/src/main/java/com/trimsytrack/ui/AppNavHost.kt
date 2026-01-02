package com.trimsytrack.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trimsytrack.AppGraph
import com.trimsytrack.ui.screens.AuthScreen
import com.trimsytrack.ui.screens.HomeScreen
import com.trimsytrack.ui.screens.JournalScreen
import com.trimsytrack.ui.screens.ManualTripScreen
import com.trimsytrack.ui.screens.OnboardingScreen
import com.trimsytrack.ui.screens.ReviewPlacesScreen
import com.trimsytrack.ui.screens.SettingsScreen
import com.trimsytrack.ui.screens.CameraScreen
import com.trimsytrack.ui.screens.EvidenceGalleryScreen
import com.trimsytrack.ui.screens.TripConfirmScreen
import com.trimsytrack.ui.screens.TripDetailScreen
import com.trimsytrack.ui.screens.TripMediaReviewScreen

object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Manual = "manual"
    const val Review = "review"
    const val Journal = "journal"
    const val Settings = "settings"
    const val Camera = "camera"
    const val Evidence = "evidence"
    const val Auth = "auth"
    const val Confirm = "confirm"
    const val Trip = "trip"
    const val MediaReview = "mediaReview"
}

@Composable
fun AppNavHost(intent: Intent) {
    val navController = rememberNavController()

    val onboardingCompleted by AppGraph.settings.onboardingCompleted.collectAsState(initial = false)
    val startDestination = if (!onboardingCompleted) Routes.Onboarding else Routes.Home

    val initialPromptId = remember(intent) {
        intent.getLongExtra("promptId", -1L).takeIf { it > 0 }
    }

    val initialTripId = remember(intent) {
        intent.getLongExtra("tripId", -1L).takeIf { it > 0 }
    }

    LaunchedEffect(initialPromptId, initialTripId) {
        when {
            initialPromptId != null -> navController.navigate("${Routes.Confirm}/$initialPromptId")
            initialTripId != null -> navController.navigate("${Routes.Trip}/$initialTripId")
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.Home) {
            HomeScreen(
                onAddTrip = { withMedia ->
                    if (withMedia) navController.navigate("${Routes.Manual}?addMedia=1")
                    else navController.navigate(Routes.Manual)
                },
                onReviewPlaces = { navController.navigate(Routes.Review) },
                onJournal = { navController.navigate(Routes.Journal) },
                onCamera = { navController.navigate(Routes.Camera) },
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }
        composable(
            route = "${Routes.Manual}?addMedia={addMedia}",
            arguments = listOf(navArgument("addMedia") { type = NavType.IntType; defaultValue = 0 }),
        ) {
            ManualTripScreen(
                onBack = { navController.popBackStack() },
                onOpenTrip = { tripId, addMedia ->
                    navController.navigate("${Routes.Trip}/$tripId?addMedia=${if (addMedia) 1 else 0}")
                },
            )
        }
        composable(Routes.Review) {
            ReviewPlacesScreen(
                onBack = { navController.popBackStack() },
                onOpenPrompt = { navController.navigate("${Routes.Confirm}/$it") },
                onOpenTrip = { navController.navigate("${Routes.Trip}/$it") },
            )
        }
        composable(Routes.Journal) {
            JournalScreen(
                onBack = { navController.popBackStack() },
                onOpenTrip = { navController.navigate("${Routes.Trip}/$it") },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenOnboarding = { navController.navigate(Routes.Onboarding) },
                onOpenAuth = { navController.navigate(Routes.Auth) },
                onOpenEvidence = { navController.navigate(Routes.Evidence) },
            )
        }

        composable(
            route = "${Routes.Camera}?tripId={tripId}&return={return}",
            arguments = listOf(
                navArgument("tripId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("return") { type = NavType.IntType; defaultValue = 0 },
            )
        ) {
            val tripId = it.arguments?.getLong("tripId") ?: -1L
            val returnCapture = (it.arguments?.getInt("return") ?: 0) == 1
            CameraScreen(
                tripId = tripId.takeIf { id -> id > 0L },
                returnCaptureToCaller = returnCapture,
                onCaptureConfirmed = { uri, capturedAt ->
                    val prev = navController.previousBackStackEntry
                    prev?.savedStateHandle?.set("cameraCaptureUri", uri)
                    prev?.savedStateHandle?.set("cameraCaptureAt", capturedAt)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.MediaReview}/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L
            TripMediaReviewScreen(
                tripId = tripId,
                savedStateHandle = backStackEntry.savedStateHandle,
                onTakePhoto = {
                    navController.navigate("${Routes.Camera}?tripId=-1&return=1")
                },
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Evidence) {
            EvidenceGalleryScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Auth) {
            AuthScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.Confirm}/{promptId}",
            arguments = listOf(navArgument("promptId") { type = NavType.LongType })
        ) {
            val promptId = it.arguments?.getLong("promptId") ?: 0L
            TripConfirmScreen(
                promptId = promptId,
                onAddTrip = { tripId ->
                    navController.navigate("${Routes.Trip}/$tripId") {
                        popUpTo("${Routes.Confirm}/$promptId") { inclusive = true }
                    }
                },
                onAddTripWithMedia = { tripId ->
                    navController.navigate("${Routes.Trip}/$tripId?addMedia=1") {
                        popUpTo("${Routes.Confirm}/$promptId") { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "${Routes.Trip}/{tripId}?addMedia={addMedia}",
            arguments = listOf(
                navArgument("tripId") { type = NavType.LongType },
                navArgument("addMedia") { type = NavType.IntType; defaultValue = 0 },
            )
        ) {
            val tripId = it.arguments?.getLong("tripId") ?: 0L
            val addMedia = (it.arguments?.getInt("addMedia") ?: 0) == 1
            TripDetailScreen(
                tripId = tripId,
                showAddMediaImmediately = addMedia,
                onOpenMediaReviewForTrip = { id ->
                    navController.navigate("${Routes.MediaReview}/$id")
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
