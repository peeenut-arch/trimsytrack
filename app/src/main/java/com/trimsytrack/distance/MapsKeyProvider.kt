package com.trimsytrack.distance

import android.content.Context
import android.content.pm.PackageManager

object MapsKeyProvider {
    fun getKey(context: Context): String {
        val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val key = ai.metaData?.getString("com.google.android.geo.API_KEY")
        require(!key.isNullOrBlank()) { "Missing MAPS_API_KEY (set MAPS_API_KEY in local.properties)" }
        return key
    }
}
