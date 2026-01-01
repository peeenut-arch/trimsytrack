package com.trimsytrack.export

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.TripRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object KorjournalExporter {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private suspend fun buildYearCsv(
        settings: SettingsStore,
        trips: TripRepository,
        year: Int,
    ): BuiltCsv {
        val startDay = LocalDate.of(year, 1, 1)
        val endDay = LocalDate.of(year, 12, 31)

        val tripList = trips.listTripsBetweenDays(startDay, endDay)

        val vehicleRegNumber = settings.vehicleRegNumber.first()
        val driverName = settings.driverName.first()
        val businessHomeAddress = settings.businessHomeAddress.first()
        val odometerYearStartKm = settings.odometerYearStartKm.first()
        val odometerYearEndKm = settings.odometerYearEndKm.first()

        val csv = buildString {
            // Semicolon-separated (often plays nicer with Swedish Excel locales)
            appendLine(
                listOf(
                    "year",
                    "vehicleRegNumber",
                    "odometerYearStartKm",
                    "odometerYearEndKm",
                    "businessHomeAddress",
                    "date",
                    "tripOdometerStartKm",
                    "tripOdometerEndKm",
                    "tripDistanceKm",
                    "startAddress",
                    "endAddress",
                    "purpose",
                    "visitedPlace",
                    "driver",
                    "notes",
                ).joinToString(";") { it.csvCell() }
            )

            for (t in tripList) {
                val distanceKm = (t.distanceMeters / 1000.0)
                appendLine(
                    listOf(
                        year.toString(),
                        vehicleRegNumber,
                        odometerYearStartKm,
                        odometerYearEndKm,
                        businessHomeAddress,
                        t.day.format(dateFormatter),
                        "", // tripOdometerStartKm (not captured yet)
                        "", // tripOdometerEndKm (not captured yet)
                        String.format("%.1f", distanceKm),
                        t.startLabelSnapshot,
                        t.storeNameSnapshot,
                        t.notes, // purpose (mapped to notes for now)
                        t.storeNameSnapshot,
                        driverName,
                        t.notes,
                    ).joinToString(";") { it.csvCell() }
                )
            }
        }

        return BuiltCsv(
            csv = csv,
            tripCount = tripList.size,
        )
    }

    suspend fun buildYearCsvUtf8(
        settings: SettingsStore,
        trips: TripRepository,
        year: Int,
    ): ByteArray {
        return buildYearCsv(settings = settings, trips = trips, year = year)
            .csv
            .toByteArray(Charsets.UTF_8)
    }

    suspend fun exportYearCsv(
        context: Context,
        settings: SettingsStore,
        trips: TripRepository,
        year: Int,
    ): ExportResult {
        val built = buildYearCsv(settings = settings, trips = trips, year = year)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "korjournal_${year}.csv")

        file.writeText(built.csv, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        return ExportResult(
            uri = uri,
            displayName = file.name,
            tripCount = built.tripCount,
        )
    }

    suspend fun exportYearCsvToUri(
        context: Context,
        settings: SettingsStore,
        trips: TripRepository,
        year: Int,
        destinationUri: Uri,
    ): ExportToUriResult {
        val built = buildYearCsv(settings = settings, trips = trips, year = year)
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            out.write(built.csv.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open output stream")

        return ExportToUriResult(
            uri = destinationUri,
            tripCount = built.tripCount,
        )
    }

    /**
     * Saves to shared storage under Downloads/TrimsyTRACK (visible in most file managers).
     * Uses MediaStore (API 29+). For older Android versions, prefer exportYearCsvToUri via SAF.
     */
    suspend fun exportYearCsvToDownloads(
        context: Context,
        settings: SettingsStore,
        trips: TripRepository,
        year: Int,
        displayName: String,
    ): ExportToUriResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Downloads export requires Android 10+ (API 29+)")
        }

        val built = buildYearCsv(settings = settings, trips = trips, year = year)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TrimsyTRACK/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create download item")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(built.csv.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open output stream")

            val doneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, doneValues, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        return ExportToUriResult(
            uri = uri,
            tripCount = built.tripCount,
        )
    }
}

data class ExportResult(
    val uri: android.net.Uri,
    val displayName: String,
    val tripCount: Int,
)

data class ExportToUriResult(
    val uri: Uri,
    val tripCount: Int,
)

private data class BuiltCsv(
    val csv: String,
    val tripCount: Int,
)

private fun String.csvCell(): String {
    // Minimal CSV escaping for semicolon-separated values.
    // Always quote to keep it predictable across locales.
    val escaped = this.replace("\"", "\"\"")
    return "\"$escaped\""
}
