package com.trimsytrack.distance

import android.content.Context
import android.content.pm.PackageManager

object MapsKeyProvider {
    fun getKeyOrNull(context: Context): String? {
        val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        return ai.metaData?.getString("com.google.android.geo.API_KEY")?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Kept for backward compatibility with existing call sites.
     * Returns an empty string when no key is configured.
     */
    fun getKey(context: Context): String = getKeyOrNull(context).orEmpty()
}
