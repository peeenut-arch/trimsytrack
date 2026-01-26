package com.trimsytrack.debug

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Lightweight network logger for the in-app Debugg report.
 *
 * Avoids reading request/response bodies (may contain user data / be large).
 */
class DebuggHttpInterceptor : Interceptor {

    private val maxPeekBytes: Long = 1024

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val method = req.method
        val url = req.url
        val path = url.encodedPath
        val query = url.encodedQuery

        val requestBytes = runCatching { req.body?.contentLength() }.getOrNull()
        val requestBytesText = when {
            requestBytes == null -> "?"
            requestBytes < 0 -> "?"
            else -> requestBytes.toString()
        }

        val startNs = System.nanoTime()
        return try {
            val res = chain.proceed(req)
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

            val code = res.code
            val responseBytes = runCatching { res.body?.contentLength() }.getOrNull()
            val responseBytesText = when {
                responseBytes == null -> "?"
                responseBytes < 0 -> "?"
                else -> responseBytes.toString()
            }

            val errorBodyText = if (res.code >= 400) {
                runCatching {
                    res.peekBody(maxPeekBytes)
                        .string()
                        .replace("\n", " ")
                        .trim()
                        .take(220)
                }.getOrNull()
            } else {
                null
            }

            val q = if (query.isNullOrBlank()) "" else "?$query"
            DebuggLogStore.add(
                tag = "HTTP",
                message = buildString {
                    append("$method $path$q -> $code (${tookMs}ms) req=${requestBytesText}B res=${responseBytesText}B")
                    if (!errorBodyText.isNullOrBlank()) {
                        append(" body=")
                        append(errorBodyText)
                    }
                },
            )
            res
        } catch (t: Throwable) {
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            val q = if (query.isNullOrBlank()) "" else "?$query"
            DebuggLogStore.add(
                tag = "HTTP",
                message = "$method $path$q -> EX (${tookMs}ms) req=${requestBytesText}B err=${t.javaClass.simpleName}:${t.message}",
            )
            throw t
        }
    }
}
