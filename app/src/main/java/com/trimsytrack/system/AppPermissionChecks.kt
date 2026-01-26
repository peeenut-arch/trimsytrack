package com.trimsytrack.system

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object AppPermissionChecks {
    fun hasFineLocation(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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

    fun missingCritical(context: Context): List<CriticalMissing> {
        val missing = mutableListOf<CriticalMissing>()

        if (!hasFineLocation(context)) {
            missing += CriticalMissing(
                key = "location_permission",
                title = "Location permission",
                description = "Required for GPS/geofence features.",
            )
        } else if (!isLocationServicesEnabled(context)) {
            missing += CriticalMissing(
                key = "location_services",
                title = "Location services",
                description = "Turn on Location/GPS in system settings.",
            )
        }

        if (!hasNotifications(context)) {
            missing += CriticalMissing(
                key = "notifications_permission",
                title = "Notifications permission",
                description = "Required so the app can warn you and show reminders.",
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
