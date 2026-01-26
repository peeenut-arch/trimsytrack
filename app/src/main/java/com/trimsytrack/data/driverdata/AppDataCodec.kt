package com.trimsytrack.data.driverdata

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object AppDataCodec {
    const val CONTENT_ENCODING_GZIP_BASE64_JSON = "gzip_base64_json"

    fun gzipBase64EncodeUtf8(inputUtf8: String): String {
        val inputBytes = inputUtf8.toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(inputBytes) }
        val gz = bos.toByteArray()
        return Base64.getEncoder().encodeToString(gz)
    }

    fun gzipBase64DecodeToUtf8(base64: String): String {
        val gz = Base64.getDecoder().decode(base64)
        val bis = ByteArrayInputStream(gz)
        val out = ByteArrayOutputStream()
        GZIPInputStream(bis).use { it.copyTo(out) }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    fun sha256HexUtf8(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    /**
     * Chunk an ASCII payload string (e.g. base64) by byte size.
     *
     * Because base64 is ASCII, bytes == chars for UTF-8, so substring boundaries are safe.
     */
    fun chunkAsciiPayload(payload: String, maxChunkBytes: Int): List<String> {
        require(maxChunkBytes > 0)
        if (payload.isEmpty()) return listOf("")

        val chunks = ArrayList<String>((payload.length / maxChunkBytes) + 1)
        var i = 0
        while (i < payload.length) {
            val end = (i + maxChunkBytes).coerceAtMost(payload.length)
            chunks.add(payload.substring(i, end))
            i = end
        }
        return chunks
    }
}
