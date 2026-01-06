package com.trimsytrack.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.trimsytrack.AppGraph
import com.trimsytrack.data.IdKeys
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
import com.trimsytrack.ui.screens.ProfileSelectScreen
import com.trimsytrack.ui.screens.ProfileLocationScreen
import com.trimsytrack.ui.screens.SavedStoresScreen
import com.trimsytrack.ui.screens.TestPingActionsScreen
import com.trimsytrack.notifications.ReceiptReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
    const val Profiles = "profiles"
    const val Confirm = "confirm"
    const val Trip = "trip"
    const val MediaReview = "mediaReview"
    const val ProfileLocation = "profileLocation"
    const val SavedStores = "savedStores"
    const val TestPing = "testPing"
}

@Composable
fun AppNavHost(intent: Intent) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")
    val profiles by AppGraph.settings.profiles.collectAsState(initial = emptyList())
    val currentUser = rememberFirebaseUser()

    // Deep links from notifications / intents.
    val initialPromptId = remember(intent) {
        intent.getLongExtra("promptId", -1L).takeIf { it > 0 }
    }

    val initialTripId = remember(intent) {
        val a = intent.getLongExtra(IdKeys.TRIP_ID, -1L).takeIf { it > 0 }
        val b = intent.getLongExtra(IdKeys.TRIP_ID_ALT, -1L).takeIf { it > 0 }
        (a ?: b)
    }

    val openTestPing = remember(intent) {
        intent.getBooleanExtra("openTestPing", false)
    }
    val testPingAddress = remember(intent) {
        intent.getStringExtra("testPingAddress").orEmpty()
    }
    val testPingLat = remember(intent) {
        intent.getDoubleExtra("testPingLat", Double.NaN)
    }
    val testPingLng = remember(intent) {
        intent.getDoubleExtra("testPingLng", Double.NaN)
    }

    val testPingRoute = remember(openTestPing, testPingAddress, testPingLat, testPingLng) {
        if (!openTestPing) return@remember null

        val latText = if (testPingLat.isFinite()) testPingLat.toString() else ""
        val lngText = if (testPingLng.isFinite()) testPingLng.toString() else ""
        val addressEncoded = Uri.encode(testPingAddress)
        "${Routes.TestPing}?address=$addressEncoded&lat=${Uri.encode(latText)}&lng=${Uri.encode(lngText)}"
    }

    val pendingInitialRoute = remember(testPingRoute, initialPromptId, initialTripId) {
        when {
            testPingRoute != null -> testPingRoute
            initialPromptId != null -> "${Routes.Confirm}/$initialPromptId"
            initialTripId != null -> "${Routes.Trip}/$initialTripId"
            else -> null
        }
    }

    // Spec: app always launches to Login Screen (Auth).
    val startDestination = remember { Routes.Auth }

    fun scheduleReceiptReminder(tripId: Long) {
        if (tripId <= 0L) return
        scope.launch {
            runCatching {
                val trip = withContext(Dispatchers.IO) { AppGraph.tripRepository.get(tripId) } ?: return@launch
                val minutes = AppGraph.settings.receiptReminderMinutes.first()
                val message = AppGraph.settings.receiptReminderMessage.first()
                ReceiptReminderWorker.scheduleForTrip(
                    context = context.applicationContext,
                    profileId = trip.profileId,
                    tripId = trip.id,
                    triggerMinutes = minutes,
                    message = message,
                )
            }
        }
    }

    fun activateProfileAndNavigate(selectedId: String) {
        if (selectedId.isBlank()) return
        scope.launch {
            AppGraph.settings.activateProfile(selectedId)

            // First-run migration for users upgrading from single-profile builds.
            withContext(Dispatchers.IO) {
                AppGraph.db.storeDao().claimUnscoped(selectedId)
                AppGraph.db.tripDao().claimUnscoped(selectedId)
                AppGraph.db.promptDao().claimUnscoped(selectedId)
                AppGraph.db.runDao().claimUnscoped(selectedId)
                AppGraph.db.attachmentDao().claimUnscoped(selectedId)
                AppGraph.db.distanceCacheDao().claimUnscoped(selectedId)
                AppGraph.db.syncOutboxDao().claimUnscoped(selectedId)
            }

            val onboarded = AppGraph.settings.onboardingCompleted.first()
            val target = pendingInitialRoute ?: if (!onboarded) Routes.Onboarding else Routes.Home
            navController.navigate(target) {
                popUpTo(Routes.Auth) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(currentUser, profiles, activeProfileId, pendingInitialRoute) {
        // Soft-migrate legacy single-profile into the list.
        AppGraph.settings.ensureActiveProfileListed()

        val currentRoute = navController.currentBackStackEntry?.destination?.route

        // If signed out: ensure we're on Auth.
        if (currentUser == null) {
            if (currentRoute != Routes.Auth) {
                navController.navigate(Routes.Auth) {
                    popUpTo(Routes.Auth) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        // Signed in: auto-select profile if possible.
        val activeIsValid = activeProfileId.isNotBlank() && profiles.any { it.id == activeProfileId }
        when {
            activeIsValid -> {
                // If we're on Auth or Profiles, skip picker.
                if (currentRoute == Routes.Auth || currentRoute == Routes.Profiles) {
                    val onboarded = AppGraph.settings.onboardingCompleted.first()
                    val target = pendingInitialRoute ?: if (!onboarded) Routes.Onboarding else Routes.Home
                    navController.navigate(target) {
                        popUpTo(Routes.Auth) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            profiles.size == 1 -> {
                if (currentRoute == Routes.Auth || currentRoute == Routes.Profiles) {
                    activateProfileAndNavigate(profiles.first().id)
                }
            }

            else -> {
                // Multiple profiles and none active: show picker.
                if (currentRoute == Routes.Auth) {
                    navController.navigate(Routes.Profiles) {
                        popUpTo(Routes.Auth) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    LaunchedEffect(currentUser, activeProfileId) {
        // Definition-of-done: app cannot enter main UI without an active profile.
        if (currentUser == null) return@LaunchedEffect

        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val allowedWithoutProfile = setOf(Routes.Auth, Routes.Profiles)
        if (activeProfileId.isBlank() && currentRoute !in allowedWithoutProfile) {
            navController.navigate(Routes.Profiles) {
                popUpTo(Routes.Auth) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // If the app is already running and a notification intent arrives, navigate once we're ready.
    LaunchedEffect(pendingInitialRoute, currentUser, activeProfileId) {
        val route = pendingInitialRoute ?: return@LaunchedEffect
        if (currentUser == null) return@LaunchedEffect
        if (activeProfileId.isBlank()) return@LaunchedEffect
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.Profiles) {
            ProfileSelectScreen(
                onSelectProfile = { selectedId ->
                    activateProfileAndNavigate(selectedId)
                },
            )
        }
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onDone = {
                    // Ensure the active profile is listed and has onboarding marked complete.
                    // (OnboardingScreen already sets onboardingCompleted in SettingsStore.)
                    // Keep profiles metadata in sync.
                    scope.launch {
                        val id = activeProfileId
                        if (id.isNotBlank()) {
                            AppGraph.settings.ensureActiveProfileListed()
                            AppGraph.settings.setProfileOnboardingCompleted(id, true)
                        }
                    }
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
                onAddTripQuickLogWithPhoto = {
                    navController.navigate("${Routes.Camera}?tripId=-1&return=0&autoSave=1&quickLog=1")
                },
                onReviewPlaces = { navController.navigate(Routes.Review) },
                onJournal = { navController.navigate(Routes.Journal) },
                onCamera = { navController.navigate(Routes.Camera) },
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenProfiles = { navController.navigate(Routes.Profiles) },
                onOpenOnboarding = { navController.navigate(Routes.Onboarding) },
                onOpenProfileLocation = { navController.navigate(Routes.ProfileLocation) },
                onOpenSavedStores = { navController.navigate(Routes.SavedStores) },
            )
        }
        composable(
            route = "${Routes.Manual}?addMedia={addMedia}",
            arguments = listOf(navArgument("addMedia") { type = NavType.IntType; defaultValue = 0 }),
        ) {
            ManualTripScreen(
                onBack = { navController.popBackStack() },
                onOpenTrip = { tripId, addMedia ->
                    if (addMedia) {
                        scheduleReceiptReminder(tripId)
                        navController.navigate("${Routes.Camera}?tripId=$tripId&return=0&autoSave=1")
                    } else {
                        navController.navigate("${Routes.Trip}/$tripId?addMedia=0")
                    }
                },
            )
        }
        composable(Routes.Review) {
            ReviewPlacesScreen(
                onOpenPrompt = { navController.navigate("${Routes.Confirm}/$it") },
                onOpenTrip = { navController.navigate("${Routes.Trip}/$it") },
                onOpenSavedStores = { navController.navigate(Routes.SavedStores) },
            )
        }
        composable(Routes.Journal) {
            JournalScreen(
                onBack = { navController.popBackStack() },
                onOpenTrip = { navController.navigate("${Routes.Trip}/$it") },
                onAddMediaToTrip = { tripId ->
                    navController.navigate("${Routes.Camera}?tripId=$tripId&return=0")
                },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenOnboarding = { navController.navigate(Routes.Onboarding) },
                onOpenAuth = { navController.navigate(Routes.Auth) },
                onOpenEvidence = { navController.navigate(Routes.Evidence) },
                onOpenProfileLocation = { navController.navigate(Routes.ProfileLocation) },
                onOpenSavedStores = { navController.navigate(Routes.SavedStores) },
            )
        }

        composable(Routes.ProfileLocation) {
            ProfileLocationScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.TestPing}?address={address}&lat={lat}&lng={lng}",
            arguments = listOf(
                navArgument("address") { type = NavType.StringType; defaultValue = "" },
                navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                navArgument("lng") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val address = entry.arguments?.getString("address").orEmpty()
            val lat = entry.arguments?.getString("lat").orEmpty()
            val lng = entry.arguments?.getString("lng").orEmpty()

            TestPingActionsScreen(
                address = address,
                lat = lat,
                lng = lng,
                onOpenTrip = { tripId, addReceipt ->
                    navController.navigate("${Routes.Trip}/$tripId?addMedia=${if (addReceipt) 1 else 0}") {
                        popUpTo(Routes.TestPing) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SavedStores) {
            SavedStoresScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.Camera}?tripId={tripId}&return={return}&autoSave={autoSave}&quickLog={quickLog}",
            arguments = listOf(
                navArgument("tripId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("return") { type = NavType.IntType; defaultValue = 0 },
                navArgument("autoSave") { type = NavType.IntType; defaultValue = 0 },
                navArgument("quickLog") { type = NavType.IntType; defaultValue = 0 },
            )
        ) {
            val tripId = it.arguments?.getLong("tripId") ?: -1L
            val returnCapture = (it.arguments?.getInt("return") ?: 0) == 1
            val autoSave = (it.arguments?.getInt("autoSave") ?: 0) == 1
            val quickLog = (it.arguments?.getInt("quickLog") ?: 0) == 1
            CameraScreen(
                tripId = tripId.takeIf { id -> id > 0L },
                returnCaptureToCaller = returnCapture,
                autoSaveToTrip = autoSave,
                quickLogTripOnAutoSave = quickLog,
                onCaptureConfirmed = { uri, mimeType, isTempLocalFileProviderUri, capturedAt ->
                    val prev = navController.previousBackStackEntry
                    prev?.savedStateHandle?.set("cameraCaptureUri", uri)
                    prev?.savedStateHandle?.set("cameraCaptureAt", capturedAt)
                    prev?.savedStateHandle?.set("cameraCaptureMimeType", mimeType)
                    prev?.savedStateHandle?.set("cameraCaptureIsTemp", isTempLocalFileProviderUri)
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
                onOpenCameraForTrip = { id, scheduleReceiptReminder ->
                    if (scheduleReceiptReminder) {
                        scheduleReceiptReminder(id)
                        navController.navigate("${Routes.Camera}?tripId=$id&return=0&autoSave=1")
                    } else {
                        navController.navigate("${Routes.Camera}?tripId=$id&return=0")
                    }
                },
                onOpenMediaReviewForTrip = { id ->
                    navController.navigate("${Routes.MediaReview}/$id")
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun rememberFirebaseUser(): FirebaseUser? {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { a ->
            user = a.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    return user
}
