package com.trimsytrack.debug

import java.time.Instant
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small in-memory ring buffer for "sync-relevant" logs.
 *
 * Intentionally does NOT persist to disk (so it won't leak across accounts).
 */
object DebuggLogStore {

    data class Entry(
        val atMillis: Long,
        val tag: String,
        val message: String,
    ) {
        fun toLine(): String {
            val iso = runCatching { Instant.ofEpochMilli(atMillis).toString() }.getOrDefault(atMillis.toString())
            val safeMsg = message.replace("\n", " ").trim()
            return "$iso [$tag] $safeMsg"
        }
    }

    private const val MAX_ENTRIES: Int = 250

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>(MAX_ENTRIES)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun add(tag: String, message: String) {
        val entry = Entry(
            atMillis = System.currentTimeMillis(),
            tag = tag.trim().ifBlank { "Debugg" },
            message = message,
        )

        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
            _entries.value = buffer.toList()
        }
    }

    fun snapshotLines(limit: Int = 200): List<String> {
        val safeLimit = limit.coerceAtLeast(1)
        synchronized(lock) {
            return buffer
                .takeLast(safeLimit)
                .map { it.toLine() }
        }
    }

    private fun <T> ArrayDeque<T>.takeLast(n: Int): List<T> {
        if (n <= 0) return emptyList()
        val list = this.toList()
        return if (list.size <= n) list else list.takeLast(n)
    }
}
