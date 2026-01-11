package com.trimsytrack.export

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.TripRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object KorjournalExporter {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("HH:mm")
        .toFormatter()

    private val utf8Bom: ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    private suspend fun buildYearCsv(
        settings: SettingsStore,
        trips: TripRepository,
        year: Int,
    ): BuiltCsv {
        val startDay = LocalDate.of(year, 1, 1)
        val endDay = LocalDate.of(year, 12, 31)

        val tripList = trips.listTripsBetweenDays(startDay, endDay)

        val profileId = settings.profileId.first().ifBlank { "default" }

        val vehicleRegNumber = settings.vehicleRegNumber.first()
        val driverName = settings.driverName.first()
        val businessHomeAddress = settings.businessHomeAddress.first()
        val businessHomeLat = settings.businessHomeLat.first()
        val businessHomeLng = settings.businessHomeLng.first()
        val odometerYearStartKm = settings.odometerYearStartKm.first()
        val odometerYearEndKm = settings.odometerYearEndKm.first()

        fun exportPlaceLabel(raw: String): String {
            val label = raw.trim()
            return if (label == "Business home") {
                businessHomeAddress.ifBlank { "Verksamhetsadress" }
            } else {
                label
            }
        }

        fun exportDistanceMethodLabel(method: String): String {
            return when (method) {
                "MAPS" -> "Karta"
                "GPS_STRAIGHT_LINE" -> "Rak linje"
                "MANUAL" -> "Manuell"
                "UNKNOWN" -> "Okänd"
                else -> method
            }
        }

        fun exportSyfte(raw: String): String {
            val v = raw.trim()
            return v.ifBlank { SettingsStore.DEFAULT_BUSINESS_PURPOSE }
        }

        val csv = buildString {
            // Semicolon-separated (often plays nicer with Swedish Excel locales)
            appendLine(
                listOf(
                    "Rese-ID",
                    "År",
                    "Registreringsnummer",
                    "Mätarställning 1 jan (km)",
                    "Mätarställning 31 dec (km)",
                    "Verksamhetsadress",
                    "Datum",
                    "Starttid",
                    "Sluttid",
                    "Tidszon",
                    "Mätarställning start (km)",
                    "Mätarställning slut (km)",
                    "Sträcka (km)",
                    "Beräkningsmetod",
                    "Start",
                    "Mål",
                    "Syfte",
                    "Tjänsteresa",
                    "Underlag (antal)",
                    "Besökt plats",
                    "Förare",
                    "Anteckningar",
                ).joinToString(";") { it.csvCell() }
            )

            val byDay = tripList
                .groupBy { it.day }
                .toSortedMap()

            for ((day, dayTrips) in byDay) {
                val ordered = dayTrips.sortedBy { it.createdAt }

                for (t in ordered) {
                    val distanceKm = (t.distanceMeters / 1000.0)

                    val tz = runCatching { ZoneId.of(t.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
                    val startTime = runCatching { t.startedAt.atZone(tz).toLocalTime().format(timeFormatter) }.getOrDefault("")
                    val endTime = runCatching { t.endedAt.atZone(tz).toLocalTime().format(timeFormatter) }.getOrDefault("")
                    val evidenceCount = runCatching { AppGraph.db.attachmentDao().countByTrip(profileId, t.id) }.getOrDefault(0)

                    val startAddress = exportPlaceLabel(t.startLabelSnapshot)
                    val endAddress = exportPlaceLabel(t.storeNameSnapshot)
                    val visitedPlace = endAddress
                    val syfte = exportSyfte(t.businessPurpose)
                    val distanceMethodLabel = exportDistanceMethodLabel(t.distanceMethod.name)
                    appendLine(
                        listOf(
                            t.id.toString(),
                            year.toString(),
                            vehicleRegNumber,
                            odometerYearStartKm,
                            odometerYearEndKm,
                            businessHomeAddress,
                            day.format(dateFormatter),
                            startTime,
                            endTime,
                            t.timeZoneId,
                            "", // tripOdometerStartKm (not captured yet)
                            "", // tripOdometerEndKm (not captured yet)
                            String.format("%.1f", distanceKm),
                            distanceMethodLabel,
                            startAddress,
                            endAddress,
                            syfte,
                            if (t.isBusiness) "true" else "false",
                            evidenceCount.toString(),
                            visitedPlace,
                            driverName,
                            t.notes,
                        ).joinToString(";") { it.csvCell() }
                    )
                }

                // Auto append: last stop -> Business home (to ensure day both starts and ends at home).
                val homeLat = businessHomeLat
                val homeLng = businessHomeLng
                val last = ordered.lastOrNull()
                if (homeLat != null && homeLng != null && last != null && distanceKm(last.storeLatSnapshot, last.storeLngSnapshot, homeLat, homeLng) > 0.2) {
                    val returnRouteResult = runCatching {
                        AppGraph.distanceRepository.getOrComputeDrivingRoute(
                            startLat = last.storeLatSnapshot,
                            startLng = last.storeLngSnapshot,
                            destLat = homeLat,
                            destLng = homeLng,
                            startLocationId = last.storeId,
                            endLocationId = BUSINESS_HOME_LOCATION_ID,
                        )
                    }
                    val returnRoute = returnRouteResult.getOrElse {
                        AppGraph.distanceRepository.estimateStraightLineRoute(
                            startLat = last.storeLatSnapshot,
                            startLng = last.storeLngSnapshot,
                            destLat = homeLat,
                            destLng = homeLng,
                        )
                    }

                    val endAddress = businessHomeAddress.ifBlank { "Verksamhetsadress" }
                    val distanceKm = (returnRoute.distanceMeters / 1000.0)

                    val tz = runCatching { ZoneId.of(last.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
                    val startTime = runCatching { last.endedAt.atZone(tz).toLocalTime().format(timeFormatter) }.getOrDefault("")
                    val distanceMethod = if (returnRouteResult.isSuccess) "MAPS" else "GPS_STRAIGHT_LINE"
                    val distanceMethodLabel = exportDistanceMethodLabel(distanceMethod)
                    appendLine(
                        listOf(
                            "return-home:${last.id}",
                            year.toString(),
                            vehicleRegNumber,
                            odometerYearStartKm,
                            odometerYearEndKm,
                            businessHomeAddress,
                            day.format(dateFormatter),
                            startTime,
                            "", // endTime (synthetic row)
                            last.timeZoneId,
                            "", // tripOdometerStartKm (not captured yet)
                            "", // tripOdometerEndKm (not captured yet)
                            String.format("%.1f", distanceKm),
                            distanceMethodLabel,
                            exportPlaceLabel(last.storeNameSnapshot),
                            endAddress,
                            "Återresa till verksamhetsadress",
                            "true",
                            "0",
                            endAddress,
                            driverName,
                            "",
                        ).joinToString(";") { it.csvCell() }
                    )
                }
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
        val bytes = buildYearCsv(settings = settings, trips = trips, year = year)
            .csv
            .toByteArray(Charsets.UTF_8)
        // Add BOM so Swedish Excel reliably detects UTF-8 and shows å/ä/ö correctly.
        return utf8Bom + bytes
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

private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
