package com.trimsytrack.geofence

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Task<Void>.awaitVoid() {
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(Unit) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}
