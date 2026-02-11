package com.trimsytrack.data.canonical

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.backend.BackendBlockedException
import com.trimsytrack.backend.BackendApiErrorEnvelope
import com.trimsytrack.data.trackevents.TrackEventsCapabilityProbeWorker
import com.trimsytrack.data.trackevents.TrackEventsOutboxWorker
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.system.SystemCallablesService
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class CanonicalWriteOutboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        runCatching { AppGraph.init(applicationContext) }

        // Avoid retry loops (and 401 noise) when there's no authenticated Firebase user.
        if (FirebaseAuth.getInstance().currentUser == null) {
            return Result.success()
        }

        // Refresh handshake state (best-effort)
        var handshakeSucceeded = false
        for (attempt in 0..1) {
            try {
                val hs = AppGraph.systemCallables.handshakeGet()
                AppGraph.settings.setBackendProtocolVersion(hs.protocolVersion)
                AppGraph.settings.setBackendProtocolSupportedRange(
                    minSupported = hs.protocol?.minSupported,
                    maxSupported = hs.protocol?.maxSupported,
                )
                AppGraph.settings.setBackendIdentityUid(hs.identityUid)
                AppGraph.settings.setBackendIdentityEmail(hs.identityEmail.orEmpty())
                // Handshake is the authoritative source for whether writes are enabled and
                // whether the backend is in safety mode. Do not let a locally-latched safety
                // flag permanently suppress canonical writes after the backend has been fixed.
                AppGraph.settings.setBackendWritesEnabled(hs.writesEnabled)
                AppGraph.settings.setBackendSafetyModeEnabled(hs.safetyModeEnabled)
                AppGraph.settings.setBackendSafetyModeReason(hs.safetyModeReason.orEmpty())
                AppGraph.settings.setBackendDeploymentMetadata(
                    service = hs.deployment?.service,
                    revision = hs.deployment?.revision,
                    functionTarget = hs.deployment?.functionTarget,
                    serverTimeIso = hs.deployment?.serverTimeIso,
                )

                // Backend-advertised capability: if present, treat as authoritative.
                hs.capabilities?.trackEvents?.let { supported ->
                    runCatching { AppGraph.settings.setTrackEventsBackendSupported(supported) }
                    if (supported) {
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(applicationContext) }
                        runCatching { TrackEventsOutboxWorker.schedulePeriodic(applicationContext) }
                    } else {
                        runCatching { TrackEventsOutboxWorker.cancelScheduled(applicationContext) }
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(applicationContext) }
                    }
                }

                // If TrackEvents is currently disabled (e.g. due to 404) and capability is absent,
                // schedule a low-frequency probe to self-heal.
                if (hs.capabilities?.trackEvents == null) {
                    val supported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
                    if (!supported) {
                        runCatching { TrackEventsCapabilityProbeWorker.schedulePeriodic(applicationContext, reason = "handshake") }
                    }
                }

                hs.protocol?.let { range ->
                    val v = com.trimsytrack.system.SystemCallablesService.CLIENT_PROTOCOL_VERSION
                    if (v < range.minSupported || v > range.maxSupported) {
                        runCatching {
                            AppGraph.settings.setBackendWritesEnabled(false)
                            AppGraph.settings.setBackendSafetyModeReason(
                                "App update required (protocol $v not in ${range.minSupported}..${range.maxSupported})",
                            )
                        }
                        return Result.success()
                    }
                }
                handshakeSucceeded = true
                break
            } catch (t: Throwable) {
                if (t is HttpException && (t.code() == 401 || t.code() == 403)) {
                    return Result.success()
                }
                if (t is BackendBlockedException) {
                    val code = t.machineCode?.trim()?.uppercase(Locale.ROOT)
                    if (code == "UID_DATA_MISSING") {
                        runCatching {
                            AppGraph.settings.setBackendWritesEnabled(false)
                            AppGraph.settings.setBackendSafetyModeReason(
                                t.message?.trim().orEmpty().ifBlank { "UID_DATA_MISSING (provisioning required)" },
                            )
                        }
                        return Result.success()
                    }
                    if (code == "PROTOCOL_REQUIRED" || code == "PROTOCOL_MISMATCH" || code == "CLIENT_UPDATE_REQUIRED") {
                        runCatching {
                            AppGraph.settings.setBackendWritesEnabled(false)
                            AppGraph.settings.setBackendSafetyModeReason(
                                t.message?.trim().orEmpty().ifBlank { "App update required" },
                            )
                        }
                        return Result.success()
                    }
                    if (code == "UNAUTHENTICATED" || code == "ACCOUNT_CONFLICT" || code == "UID_DELETED") {
                        return Result.success()
                    }
                    if (code == "HANDSHAKE_REQUIRED") {
                        return Result.success()
                    }
                }
                if (attempt == 0) continue
                return Result.retry()
            }
        }

        if (!handshakeSucceeded) return Result.retry()

        // Debug-only: allow forcing writes off via a local file flag, without touching backend.
        // Create on device with: adb shell run-as com.trimsytrack sh -c "echo 1 > files/debug_force_backend_writes_disabled.flag"
        // Remove with: adb shell run-as com.trimsytrack rm files/debug_force_backend_writes_disabled.flag
        if (BuildConfig.DEBUG) {
            val flag = File(applicationContext.filesDir, "debug_force_backend_writes_disabled.flag")
            if (flag.exists()) {
                runCatching {
                    AppGraph.settings.setBackendWritesEnabled(false)
                    AppGraph.settings.setBackendSafetyModeEnabled(true)
                    AppGraph.settings.setBackendSafetyModeReason("DEBUG override: debug_force_backend_writes_disabled.flag")
                }
            }
        }

        val writesEnabled = AppGraph.settings.backendWritesEnabled.first()
        if (!writesEnabled) {
            val safety = runCatching { AppGraph.settings.backendSafetyModeEnabled.first() }.getOrDefault(false)
            val reason = runCatching { AppGraph.settings.backendSafetyModeReason.first() }.getOrDefault("")
            Log.i(
                "TrimsyTrack",
                "CanonicalWriteOutboxWorker: writes disabled; skipping flush (safetyMode=$safety reason=${reason.take(200)})",
            )
            return Result.success()
        }

        val uid = AppGraph.settings.backendIdentityUid.first().trim()
        if (uid.isBlank()) return Result.success()

        val dao = AppGraph.syncDb.canonicalWriteOutboxDao()
        val pending = dao.listPending(limit = 25)
        if (pending.isEmpty()) {
            Log.i("TrimsyTrack", "CanonicalWriteOutboxWorker: pending=0 (nothing to flush)")
            return Result.success()
        }

        Log.i("TrimsyTrack", "CanonicalSync: start pending=${pending.size}")
        Log.i("TrimsyTrack", "CanonicalWriteOutboxWorker: pending=${pending.size}; flushing")

        val nowMillis = System.currentTimeMillis()
        dao.markAttempted(pending.map { it.id }, nowMillis)

        var okCount = 0
        var rejectedCount = 0

        for (item in pending) {
            // If another worker (or a previous attempt) has disabled writes, stop early.
            val stillEnabled = runCatching { AppGraph.settings.backendWritesEnabled.first() }.getOrDefault(true)
            if (!stillEnabled) {
                val safety = runCatching { AppGraph.settings.backendSafetyModeEnabled.first() }.getOrDefault(false)
                val reason = runCatching { AppGraph.settings.backendSafetyModeReason.first() }.getOrDefault("")
                Log.i(
                    "TrimsyTrack",
                    "CanonicalWriteOutboxWorker: writes disabled mid-flush; stopping (safetyMode=$safety reason=${reason.take(200)})",
                )
                return Result.success()
            }

            val route = item.route.trim()
            var body = item.bodyJson

            var drivingTripClientRequestId: String? = null

            val (httpStatus, ok, machineCode, message, retriable) = try {
                when (route) {
                    "drivingTripCreate" -> {
                        // Backward/forward compat: ensure `runId` is a JSON integer (some older queued rows used string/null).
                        val normalized = normalizeDrivingTripCreateBodyJson(body)
                        if (normalized !== body) {
                            body = normalized
                            runCatching { dao.updateBodyJson(item.id, body) }
                        }

                        val request = runCatching {
                            json.decodeFromString(DrivingTripCreateBody.serializer(), body)
                        }.getOrNull()
                        drivingTripClientRequestId = request?.clientRequestId?.trim()?.ifBlank { null }

                        val raw = AppGraph.canonicalApi.drivingTripCreate(body = body)
                        val parsed = runCatching { json.decodeFromString(DrivingTripCreateResponse.serializer(), raw) }.getOrNull()
                        val backendTripId = parsed?.result?.tripId
                            ?.trim()
                            ?.ifBlank { null }
                            ?: parsed?.result?.drivingTripId
                                ?.trim()
                                ?.ifBlank { null }

                        if (parsed?.ok == true && backendTripId != null) {
                            val endsAtHome = request?.endPlaceType
                                ?.trim()
                                ?.uppercase(Locale.ROOT) == "HOME"

                            // Mark local trip as synced.
                            val tripId = item.localTripId
                            if (tripId != null && tripId > 0) {
                                val local = AppGraph.db.tripDao().getById(uid, tripId)
                                if (local != null) {
                                    val updated = local.copy(
                                        backendId = backendTripId,
                                        syncStatus = SyncStatus.SYNCED,
                                        syncErrorMachineCode = null,
                                        syncErrorMessage = null,
                                    )
                                    runCatching { AppGraph.db.tripDao().update(updated) }
                                }
                            }

                            // Snapshot checkpoint only when a run is completed (HOME-ending trip).
                            if (endsAtHome) {
                                runCatching {
                                    AppGraph.driverDataSyncManager.enqueueImmediate(
                                        reason = "post_canonical_home_trip",
                                        trigger = "canonical",
                                    )
                                }
                            }

                            // If a previous attempt latched safety mode (e.g. misdeploy), clear it once we can write again.
                            runCatching {
                                AppGraph.settings.setBackendSafetyModeEnabled(false)
                                AppGraph.settings.setBackendSafetyModeReason("")
                            }

                            Quintuple(200, true, null, null, false)
                        } else {
                            val mc = parsed?.error?.details?.machineCode
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?: parsed?.error?.details?.machine
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            val msg = parsed?.error?.message
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?: run {
                                    val snippet = raw.trim().take(220)
                                    if (snippet.isNotBlank()) "drivingTripCreate non-ok; raw=$snippet" else "drivingTripCreate returned non-ok"
                                }
                            val retryAfter = parsed?.error?.details?.retryAfterSeconds
                            Quintuple(200, false, mc, msg, retryAfter != null && retryAfter > 0)
                        }
                    }

                    else -> {
                        Quintuple(0, false, null, "Unknown route: $route", false)
                    }
                }
            } catch (e: HttpException) {
                val errBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
                val parsed = runCatching { json.decodeFromString(BackendApiErrorEnvelope.serializer(), errBody) }.getOrNull()
                val mc = parsed?.error?.details?.machineCode
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: parsed?.error?.details?.machine
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                val msg = parsed?.error?.message?.trim()?.takeIf { it.isNotBlank() }
                    ?: errBody.trim().ifBlank { e.message() }
                val retryAfter = parsed?.error?.details?.retryAfterSeconds
                val retriable = e.code() >= 500 || e.code() == 429 || e.code() == 408 || (retryAfter != null && retryAfter > 0)
                Quintuple(e.code(), false, mc, msg, retriable)
            } catch (t: Throwable) {
                Quintuple(0, false, null, t.message ?: t.javaClass.simpleName, true)
            }

            Log.i(
                "TrimsyTrack",
                "CanonicalWriteOutbox: route=$route http=$httpStatus ok=$ok machine=${machineCode ?: "-"} req=${drivingTripClientRequestId ?: "-"} err=${message?.take(300)} kept=${!ok} retriable=$retriable"
            )

            if (!ok) {
                val machineUpper = machineCode?.trim()?.uppercase(Locale.ROOT)
                val msg = message?.trim().orEmpty()
                val isRouteMissing = httpStatus == 404 && (
                    machineUpper == "ROUTE_NOT_FOUND" ||
                        msg.contains("Unknown route", ignoreCase = true)
                )
                if (isRouteMissing) {
                    // Backend misdeploy / partial deploy: do NOT reject local data.
                    // Pause writes so we don't spin and we preserve the outbox for later.
                    runCatching {
                        AppGraph.settings.setBackendWritesEnabled(false)
                        AppGraph.settings.setBackendSafetyModeEnabled(true)
                        AppGraph.settings.setBackendSafetyModeReason(
                            msg.ifBlank { "Backend misdeploy: missing route $route" },
                        )
                    }
                    return Result.success()
                }

                val hard = AppGraph.systemCallables.hardBlockCodeOrNull(machineCode)
                if (hard != null || machineCode?.trim()?.uppercase(Locale.ROOT) == "UID_DATA_MISSING") {
                    runCatching {
                        AppGraph.settings.setBackendWritesEnabled(false)
                        AppGraph.settings.setBackendSafetyModeReason(
                            message?.trim().orEmpty().ifBlank { machineCode ?: "Backend blocked" }
                        )
                    }
                    return Result.success()
                }

                if (retriable) {
                    // Keep item for retry.
                    return Result.retry()
                }

                // Permanent failure: reject this item so the queue can progress.
                val tripId = item.localTripId
                if (tripId != null && tripId > 0) {
                    runCatching {
                        val local = AppGraph.db.tripDao().getById(uid, tripId)
                        if (local != null) {
                            AppGraph.db.tripDao().update(
                                local.copy(
                                    syncStatus = SyncStatus.REJECTED,
                                    syncErrorMachineCode = machineCode,
                                    syncErrorMessage = message,
                                )
                            )
                        }
                    }
                }
                dao.markUploaded(listOf(item.id))
                rejectedCount += 1
                continue
            }

            dao.markUploaded(listOf(item.id))
            okCount += 1
        }

        Log.i("TrimsyTrack", "CanonicalSync: flushed ok=$okCount rejected=$rejectedCount total=${pending.size}")

        return Result.success()
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
    )

    private fun jsonDoubleOrNull(p: JsonPrimitive?): Double? {
        val raw = p?.content?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.toDoubleOrNull()
    }

    private fun jsonBoolOrNull(p: JsonPrimitive?): Boolean? {
        if (p == null) return null
        // JsonPrimitive can represent booleans, numbers, or strings.
        // We intentionally avoid booleanOrNull for older kotlinx.serialization versions.
        val raw = p.content.trim().lowercase()
        if (raw.isBlank()) return null
        return when (raw) {
            "true" -> true
            "false" -> false
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun normalizeDrivingTripCreateBodyJson(bodyJson: String): String {
        val root = runCatching { json.parseToJsonElement(bodyJson).jsonObject }.getOrNull() ?: return bodyJson
        val updated = root.toMutableMap()
        var changed = false

        // Backward/forward compat: omit `runId` entirely.
        // We've observed backend deployments disagreeing on the expected JSON type (string vs integer),
        // and `runId` is optional for canonical truth creation. Omitting avoids hard failures.
        if (root.containsKey("runId")) {
            updated.remove("runId")
            changed = true
        }

        // Required canonical fields (older queued outbox rows may not include these).
        val existingAppId = (root["app_id"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (existingAppId.isBlank()) {
            updated["app_id"] = JsonPrimitive(BuildConfig.APP_ID)
            changed = true
        }

        val existingProto = (root["clientProtocolVersion"] as? JsonPrimitive)?.intOrNull
        if (existingProto == null || existingProto <= 0) {
            updated["clientProtocolVersion"] = JsonPrimitive(SystemCallablesService.CLIENT_PROTOCOL_VERSION)
            changed = true
        }

        val existingReqId = (root["clientRequestId"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (existingReqId.isBlank()) {
            updated["clientRequestId"] = JsonPrimitive(UUID.randomUUID().toString())
            changed = true
        }

        // Backward/forward compat: some backend revisions validate `businessPurpose` (string).
        // Older queued rows used `syfte` (string) and omitted/typed `businessPurpose`.
        val businessPurposePrim = root["businessPurpose"] as? JsonPrimitive
        val businessPurposeContent = businessPurposePrim?.content?.trim().orEmpty()
        val syfteContent = (root["syfte"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val needsBusinessPurpose = businessPurposeContent.isBlank() || businessPurposePrim?.isString == false
        if (needsBusinessPurpose) {
            val next = (if (syfteContent.isNotBlank()) syfteContent else businessPurposeContent).trim()
            if (next.isNotBlank()) {
                updated["businessPurpose"] = JsonPrimitive(next)
                changed = true
            }
        }

        // Backward/forward compat: backend expects isBusiness as boolean.
        val isBusinessPrim = root["isBusiness"] as? JsonPrimitive
        val isBusinessVal = jsonBoolOrNull(isBusinessPrim)
        if (isBusinessVal == null) {
            // Default to true to preserve existing semantics (trips are business by default).
            updated["isBusiness"] = JsonPrimitive(true)
            changed = true
        }

        // Backward/forward compat: ensure `day` exists as YYYY-MM-DD string.
        // Some backend revisions require it.
        val existingDay = (root["day"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (existingDay.isBlank()) {
            val startedAtRaw = (root["startedAt"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val endedAtRaw = (root["endedAt"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val timeZoneIdRaw = (root["timeZoneId"] as? JsonPrimitive)?.content?.trim().orEmpty()

            val day = runCatching {
                val zone = runCatching { ZoneId.of(timeZoneIdRaw) }.getOrNull() ?: ZoneId.of("UTC")
                val endedInstant = Instant.parse(endedAtRaw.ifBlank { startedAtRaw })
                endedInstant.atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrNull()

            if (!day.isNullOrBlank()) {
                updated["day"] = JsonPrimitive(day)
                changed = true
            }
        }

        // Backward/forward compat: some backend revisions expect nested `start` / `end` objects.
        // Preserve existing flat fields too (startLat/startLng/etc.) for older backend deployments.
        val existingStartObj = root["start"] as? JsonObject
        if (existingStartObj == null) {
            val startLatPrim = root["startLat"] as? JsonPrimitive
            val startLngPrim = root["startLng"] as? JsonPrimitive
            val startLatVal = jsonDoubleOrNull(startLatPrim)
            val startLngVal = jsonDoubleOrNull(startLngPrim)
            if (startLatVal != null && startLngVal != null) {
                val startMap = mutableMapOf<String, JsonElement>()
                val startedAtRaw2 = (root["startedAt"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (startedAtRaw2.isNotBlank()) startMap["at"] = JsonPrimitive(startedAtRaw2)
                startMap["lat"] = JsonPrimitive(startLatVal)
                startMap["lng"] = JsonPrimitive(startLngVal)

                val startPlaceTypeRaw = (root["startPlaceType"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (startPlaceTypeRaw.isNotBlank()) startMap["placeType"] = JsonPrimitive(startPlaceTypeRaw)

                val startNameRaw = (root["startPlaceName"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (startNameRaw.isNotBlank()) startMap["placeName"] = JsonPrimitive(startNameRaw)

                val startAddrRaw = (root["startAddress"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (startAddrRaw.isNotBlank()) startMap["address"] = JsonPrimitive(startAddrRaw)

                updated["start"] = JsonObject(startMap)
                changed = true
            }
        } else {
            val hasLabel = (existingStartObj["label"] as? JsonPrimitive)?.content?.trim().orEmpty().isNotBlank()
            if (!hasLabel) {
                val startNameRaw = (root["startPlaceName"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (startNameRaw.isNotBlank()) {
                    val startMap = existingStartObj.toMutableMap()
                    startMap["label"] = JsonPrimitive(startNameRaw)
                    updated["start"] = JsonObject(startMap)
                    changed = true
                }
            }

            val hasStartLabelSnapshot = (existingStartObj["startLabelSnapshot"] as? JsonPrimitive)?.content
                ?.trim()
                .orEmpty()
                .isNotBlank()
            if (!hasStartLabelSnapshot) {
                val startNameRaw = (root["startPlaceName"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (startNameRaw.isNotBlank()) {
                    val startMap = (updated["start"] as? JsonObject ?: existingStartObj).toMutableMap()
                    startMap["startLabelSnapshot"] = JsonPrimitive(startNameRaw)

                    val startPlaceTypeRaw = (root["startPlaceType"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    if (startPlaceTypeRaw.isNotBlank() && !startMap.containsKey("startPlaceType")) {
                        startMap["startPlaceType"] = JsonPrimitive(startPlaceTypeRaw)
                    }

                    updated["start"] = JsonObject(startMap)
                    changed = true
                }
            }
        }

        val existingEndObj = root["end"] as? JsonObject
        if (existingEndObj == null) {
            val endLatPrim = root["endLat"] as? JsonPrimitive
            val endLngPrim = root["endLng"] as? JsonPrimitive
            val endLatVal = jsonDoubleOrNull(endLatPrim)
            val endLngVal = jsonDoubleOrNull(endLngPrim)
            if (endLatVal != null && endLngVal != null) {
                val endMap = mutableMapOf<String, JsonElement>()
                val endedAtRaw2 = (root["endedAt"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (endedAtRaw2.isNotBlank()) endMap["at"] = JsonPrimitive(endedAtRaw2)
                endMap["lat"] = JsonPrimitive(endLatVal)
                endMap["lng"] = JsonPrimitive(endLngVal)

                // Backend validators expect this to be present and non-blank.
                val cityRaw = (root["city"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "Unknown" }
                endMap["citySnapshot"] = JsonPrimitive(cityRaw)

                val endPlaceTypeRaw = (root["endPlaceType"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (endPlaceTypeRaw.isNotBlank()) endMap["placeType"] = JsonPrimitive(endPlaceTypeRaw)

                val endNameRaw = (root["endPlaceName"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (endNameRaw.isNotBlank()) endMap["placeName"] = JsonPrimitive(endNameRaw)

                val endAddrRaw = (root["endAddress"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (endAddrRaw.isNotBlank()) endMap["address"] = JsonPrimitive(endAddrRaw)

                updated["end"] = JsonObject(endMap)
                changed = true
            }
        } else {
            val hasLabel = (existingEndObj["label"] as? JsonPrimitive)?.content?.trim().orEmpty().isNotBlank()
            if (!hasLabel) {
                val endNameRaw = (root["endPlaceName"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (endNameRaw.isNotBlank()) {
                    val endMap = existingEndObj.toMutableMap()
                    endMap["label"] = JsonPrimitive(endNameRaw)
                    updated["end"] = JsonObject(endMap)
                    changed = true
                }
            }

            val hasStoreNameSnapshot = (existingEndObj["storeNameSnapshot"] as? JsonPrimitive)?.content
                ?.trim()
                .orEmpty()
                .isNotBlank()
            if (!hasStoreNameSnapshot) {
                val endNameRaw = (root["endPlaceName"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (endNameRaw.isNotBlank()) {
                    val endMap = (updated["end"] as? JsonObject ?: existingEndObj).toMutableMap()
                    endMap["storeNameSnapshot"] = JsonPrimitive(endNameRaw)

                    val endPlaceTypeRaw = (root["endPlaceType"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    if (endPlaceTypeRaw.isNotBlank() && !endMap.containsKey("endPlaceType")) {
                        endMap["endPlaceType"] = JsonPrimitive(endPlaceTypeRaw)
                    }

                    updated["end"] = JsonObject(endMap)
                    changed = true
                }
            }

            val endObjCurrent = updated["end"] as? JsonObject ?: existingEndObj
            val endMap = endObjCurrent.toMutableMap()

            val citySnapshotExisting = (endObjCurrent["citySnapshot"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (citySnapshotExisting.isBlank()) {
                val cityRaw = (root["city"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "Unknown" }
                // Backend validation expects a string and some deployments treat blank as missing.
                endMap["citySnapshot"] = JsonPrimitive(cityRaw)
                changed = true
            }

            val storeIdExisting = (endObjCurrent["storeId"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (storeIdExisting.isBlank()) {
                val storeIdRaw = (root["storeId"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (storeIdRaw.isNotBlank()) {
                    endMap["storeId"] = JsonPrimitive(storeIdRaw)
                    changed = true
                }
            }

            val storeLocationIdExisting = (endObjCurrent["storeLocationId"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (storeLocationIdExisting.isBlank()) {
                val raw = (root["storeLocationId"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (raw.isNotBlank()) {
                    endMap["storeLocationId"] = JsonPrimitive(raw)
                    changed = true
                }
            }

            val postOmbudIdExisting = (endObjCurrent["postOmbudId"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (postOmbudIdExisting.isBlank()) {
                val raw = (root["postOmbudId"] as? JsonPrimitive)?.content?.trim().orEmpty()
                if (raw.isNotBlank()) {
                    endMap["postOmbudId"] = JsonPrimitive(raw)
                    changed = true
                }
            }

            val storeLatExisting = jsonDoubleOrNull(endObjCurrent["storeLatSnapshot"] as? JsonPrimitive)
            val storeLngExisting = jsonDoubleOrNull(endObjCurrent["storeLngSnapshot"] as? JsonPrimitive)
            if (storeLatExisting == null || storeLngExisting == null) {
                val endLat = jsonDoubleOrNull(root["endLat"] as? JsonPrimitive)
                val endLng = jsonDoubleOrNull(root["endLng"] as? JsonPrimitive)
                if (endLat != null && endLng != null) {
                    if (storeLatExisting == null) endMap["storeLatSnapshot"] = JsonPrimitive(endLat)
                    if (storeLngExisting == null) endMap["storeLngSnapshot"] = JsonPrimitive(endLng)
                    changed = true
                }
            }

            if (changed) {
                updated["end"] = JsonObject(endMap)
            }
        }

        // Guard against invalid local timestamps producing permanent 400s.
        // Backend requires endedAt >= startedAt.
        val startedAtRaw = (root["startedAt"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val endedAtRaw = (root["endedAt"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (startedAtRaw.isNotBlank() && endedAtRaw.isNotBlank()) {
            val started = runCatching { Instant.parse(startedAtRaw) }.getOrNull()
            val ended = runCatching { Instant.parse(endedAtRaw) }.getOrNull()
            if (started != null && ended != null && ended.isBefore(started)) {
                updated["endedAt"] = JsonPrimitive(startedAtRaw)
                // Keep occurredAt consistent when present.
                if (root.containsKey("occurredAt")) {
                    updated["occurredAt"] = JsonPrimitive(startedAtRaw)
                }
                changed = true
            }
        }

        if (!changed) return bodyJson
        return JsonObject(updated).toString()
    }
}
