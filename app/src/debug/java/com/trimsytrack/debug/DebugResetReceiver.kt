package com.trimsytrack.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DebugResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        if (intent?.action != ACTION_RESET) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init(context.applicationContext)

                runCatching { WorkManager.getInstance(context).cancelAllWork() }
                runCatching { FirebaseAuth.getInstance().signOut() }

                runCatching { AppGraph.settings.clearAll() }
                runCatching { AppGraph.db.clearAllTables() }
                runCatching { AppGraph.syncDb.clearAllTables() }

                runCatching { deleteRecursively(context.filesDir) }
                runCatching { deleteRecursively(context.cacheDir) }
                runCatching { deleteRecursively(context.noBackupFilesDir) }

                Log.i(TAG, "Debug reset complete")
            } catch (t: Throwable) {
                Log.e(TAG, "Debug reset failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun deleteRecursively(file: java.io.File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        runCatching { file.delete() }
    }

    companion object {
        private const val TAG = "DebugReset"
        const val ACTION_RESET = "com.trimsytrack.DEBUG_RESET_APP"
    }
}
