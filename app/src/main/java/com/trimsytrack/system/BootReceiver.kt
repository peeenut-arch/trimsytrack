package com.trimsytrack.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.trimsytrack.AppGraph

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TrimsyTrack"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "BootReceiver.onReceive action=${intent?.action}")
        AppGraph.init(context)
        AppGraph.geofenceSyncManager.scheduleSync("boot")
    }
}
