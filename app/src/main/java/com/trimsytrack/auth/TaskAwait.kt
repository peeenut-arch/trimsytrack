package com.trimsytrack.auth

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun Task<*>.awaitUnit() {
    suspendCancellableCoroutine<Unit> { cont ->
        addOnSuccessListener { cont.resume(Unit) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}

internal suspend fun <T> Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
