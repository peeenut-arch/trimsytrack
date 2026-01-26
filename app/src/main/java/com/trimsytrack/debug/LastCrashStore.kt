package com.trimsytrack.debug

import android.content.Context
import com.trimsytrack.AppGraph
import java.time.Instant

/**
 * Stores a single "last crash" record on disk so it survives process death.
 *
 * This is intentionally tiny and local-only; it's meant to help us get a stacktrace
 * when the app closes abruptly (e.g. during login) and the in-memory log buffer is lost.
 */
object LastCrashStore {

    private const val PREFS_NAME: String = "trimsy_last_crash"

    private const val KEY_AT_MILLIS: String = "atMillis"
    private const val KEY_THREAD: String = "thread"
    private const val KEY_SUMMARY: String = "summary"
    private const val KEY_STACK: String = "stack"

    data class Crash(
        val atMillis: Long,
        val threadName: String,
        val summary: String,
        val stack: String,
    ) {
        val atIso: String
            get() = runCatching { Instant.ofEpochMilli(atMillis).toString() }.getOrDefault(atMillis.toString())
    }

    fun installDefaultHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(): Crash? {
        val prefs = prefs()
        val atMillis = prefs.getLong(KEY_AT_MILLIS, 0L)
        if (atMillis <= 0L) return null

        return Crash(
            atMillis = atMillis,
            threadName = prefs.getString(KEY_THREAD, "") ?: "",
            summary = prefs.getString(KEY_SUMMARY, "") ?: "",
            stack = prefs.getString(KEY_STACK, "") ?: "",
        )
    }

    fun clear() {
        prefs().edit().clear().apply()
    }

    private fun record(thread: Thread, throwable: Throwable) {
        val (summary, stack) = buildCrashStrings(throwable)
        prefs().edit()
            .putLong(KEY_AT_MILLIS, System.currentTimeMillis())
            .putString(KEY_THREAD, thread.name)
            .putString(KEY_SUMMARY, summary)
            .putString(KEY_STACK, stack)
            .apply()
    }

    private fun buildCrashStrings(throwable: Throwable): Pair<String, String> {
        val summary = throwable::class.java.name + (throwable.message?.let { ": $it" } ?: "")

        val stackText = runCatching {
            val chain = ArrayList<String>(4)
            var current: Throwable? = throwable
            var guard = 0
            while (current != null && guard < 4) {
                val header = current::class.java.name + (current.message?.let { ": $it" } ?: "")
                val frames = current.stackTrace
                    .take(25)
                    .joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName ?: "?"}:${it.lineNumber})" }

                chain += if (guard == 0) {
                    "$header\n$frames"
                } else {
                    "Caused by: $header\n$frames"
                }

                current = current.cause
                guard += 1
            }

            chain.joinToString("\n")
        }.getOrDefault(throwable.toString())

        // Keep it small; SharedPreferences isn't for giant payloads.
        val stackTrimmed = stackText
            .lineSequence()
            .take(120)
            .joinToString("\n")
            .take(8_000)

        return summary.take(400) to stackTrimmed
    }

    private fun prefs(context: Context = AppGraph.appContext) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
