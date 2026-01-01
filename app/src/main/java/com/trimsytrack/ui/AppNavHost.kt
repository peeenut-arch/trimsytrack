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
import com.trimsytrack.ui.screens.TripConfirmScreen
import com.trimsytrack.ui.screens.TripDetailScreen

object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Manual = "manual"
    const val Review = "review"
    const val Journal = "journal"
    const val Settings = "settings"
    const val Auth = "auth"
    const val Confirm = "confirm"
    const val Trip = "trip"
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
                onManualTrip = { navController.navigate(Routes.Manual) },
                onReviewPlaces = { navController.navigate(Routes.Review) },
                onJournal = { navController.navigate(Routes.Journal) },
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }
        composable(Routes.Manual) {
            ManualTripScreen(
                onBack = { navController.popBackStack() },
                onOpenTrip = { navController.navigate("${Routes.Trip}/$it") },
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
            TripConfirmScreen(
                promptId = it.arguments?.getLong("promptId") ?: 0L,
                onDone = { navController.popBackStack(Routes.Home, inclusive = false) }
            )
        }
        composable(
            route = "${Routes.Trip}/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.LongType })
        ) {
            TripDetailScreen(
                tripId = it.arguments?.getLong("tripId") ?: 0L,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
