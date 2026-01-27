package com.trimsytrack.system

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AppPermissionChecks {
    fun hasFineLocation(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun isLocationServicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        }
    }

    /**
     * True when Android battery optimization is disabled ("Unrestricted") for this app.
     * On pre-M devices there is no Doze/App Standby battery optimization.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun missingCritical(
        context: Context,
        includeBatteryOptimization: Boolean = false,
    ): List<CriticalMissing> {
        val missing = mutableListOf<CriticalMissing>()

        // Location / GPS requirements
        if (!hasFineLocation(context)) {
            missing += CriticalMissing(
                key = "location_permission_foreground",
                title = "Location permission",
                description = "Required for GPS/geofence features.",
            )
        }

        // For geofencing to be reliable, we require "Allow all the time" on Android 10+.
        if (hasFineLocation(context) && !hasBackgroundLocation(context)) {
            missing += CriticalMissing(
                key = "location_permission_background",
                title = "Location set to 'Always allow'",
                description = "Set Location to 'Allow all the time' so GPS/geofences work reliably.",
            )
        }

        if (!isLocationServicesEnabled(context)) {
            missing += CriticalMissing(
                key = "location_services",
                title = "Location services",
                description = "Turn on Location/GPS in system settings.",
            )
        }

        // Notifications requirements
        if (!areNotificationsEnabled(context)) {
            missing += CriticalMissing(
                key = "notifications_disabled",
                title = "Notifications turned off",
                description = "Enable notifications in system settings so TrimsyTRACK can warn you.",
            )
        }

        if (!hasNotifications(context)) {
            missing += CriticalMissing(
                key = "notifications_permission",
                title = "Notifications permission",
                description = "Required on Android 13+ so the app can warn you and show reminders.",
            )
        }

        // Battery optimization is important for reliability, but on OEM ROMs the user-facing
        // "unrestricted" setting may not map cleanly to Android's Doze whitelist APIs.
        // Keep it as a recommended warning by default; screens can opt-in to blocking.
        if (includeBatteryOptimization && !isBatteryOptimizationDisabled(context)) {
            missing += CriticalMissing(
                key = "battery_optimization",
                title = "Battery optimization",
                description = "Set Battery to 'Unrestricted' so background pings work reliably.",
            )
        }

        return missing
    }
}

data class CriticalMissing(
    val key: String,
    val title: String,
    val description: String,
)
