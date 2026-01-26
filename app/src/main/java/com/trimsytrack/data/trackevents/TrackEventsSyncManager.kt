package com.trimsytrack.data.trackevents

import android.content.Context
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class TrackEventsSyncManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val changes = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    init {
        scope.launch {
            changes
                .debounce(DEFAULT_DEBOUNCE_MS)
                .onEach { reason -> enqueueImmediate(reason = "debounced:$reason") }
                .collect()
        }
    }

    fun enqueueImmediate(reason: String) {
        TrackEventsOutboxWorker.enqueue(context, reason)
    }

    /**
     * Best-effort "idle" batching:
     * - emits are persisted immediately to the local outbox
     * - uploads are scheduled after a short quiet period
     */
    fun enqueueDebounced(reason: String) {
        val accepted = changes.tryEmit(reason)
        if (!accepted) {
            enqueueImmediate(reason = "overflow:$reason")
        }
    }

    fun flushNow(reason: String = "flush") {
        enqueueImmediate(reason = reason)
    }

    companion object {
        private const val DEFAULT_DEBOUNCE_MS = 1500L
    }
}
