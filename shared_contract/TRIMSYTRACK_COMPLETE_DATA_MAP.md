# TrimsyTRACK — Complete Data Map (What We Store, Where, and How To Build a Driving Journal/Analytics)

Status: **as-implemented** (Android app + BACKENDTRIMSY in this workspace)

This document is for Trimsy (TrimsyApp / analytics tooling) to understand **exactly what TrimsyTRACK stores**, **where it stores it**, **what is authoritative**, and **how to turn it into a complete driving journal + analytics**.

## 0) Core principles (do not violate)

### Identity / scope
- Everything is scoped to the authenticated **Firebase UID**.
- Local databases store the UID explicitly on most rows.
- Backend ownership is derived from the Bearer token (the client never chooses ownership).

### Authoritative truth vs checkpoints
- **Canonical driving-trip truth** is written via backend route `apiV1/drivingTripCreate`.
- **DriverData snapshots** (`driverdataGet/driverdataPut`) are **checkpoint snapshots** for restore/reinstall and cross-device seeding. They are not the canonical “truth stream”; they’re “current known state” snapshots.

### Evidence bytes never go to backend
- Evidence/media bytes are **local-only** and exported via a ContentProvider to TrimsyApp.
- Backend snapshots can carry **evidence metadata** (IDs, hashes, linkage) but never the media bytes.

---

## 1) Storage surfaces overview

### 1.1 Android local storage (TrimsyTRACK app)
TrimsyTRACK persists data in four places:

1) **Room DB (main):** `trimsytrack.db`
2) **Room DB (sync/outbox):** `trimsytrack.sync.db`
3) **Preferences DataStore:** name `settings` (file is typically `settings.preferences_pb`)
4) **App-private files:** `filesDir/regions/*`, `filesDir/evidence/*` (+ some exports in cache)

### 1.2 Backend storage (BACKENDTRIMSY)
TrimsyTRACK interacts with these backend surfaces:

- **Startup gating:** `handshakeGet`, then law gating (`lawGet`, `lawAccept`, etc.)
- **DriverData snapshot store:** `driverdataGet`, `driverdataPut`
- **Canonical driving-trip truth:** `drivingTripCreate`

Optional / capability-gated (may not exist on every backend revision):
- **TrackEvents (telemetry-like):** `trackEventsBatchPut`, `trackEventsSinceGet` (advertised via `handshakeGet.capabilities.trackEvents` when supported)

---

## 2) Android local: Room DB `trimsytrack.db`

Room database file name: **`trimsytrack.db`** (see `Room.databaseBuilder(..., "trimsytrack.db")`).

Room schema version: `23` (destructive migration enabled).

Tables:
- `stores`
- `trips`
- `prompt_events`
- `ping_events`
- `visited_stores`
- `runs`
- `attachments`
- `distance_cache`

### 2.1 Table `trips` (`TripEntity`)
Primary key: `id` (auto-increment Long). This is **local-only** and not stable across devices.

Columns:
- `id: Long` (local trip number)
- `uid: String` (Firebase UID)

Backend sync fields:
- `clientRef: String?` — **stable UUID TripID** (preferred join key across systems)
- `backendId: String?` — backend drivingTripId when canonically accepted
- `syncStatus: SyncStatus` — `LOCAL_ONLY | PENDING | SYNCED | REJECTED`
- `syncErrorMachineCode: String?`
- `syncErrorMessage: String?`

Time:
- `createdAt: Instant` — when the trip row was created locally
- `day: LocalDate` — day bucket **derived using the trip’s `timeZoneId`**
- `startedAt: Instant`
- `endedAt: Instant`
- `timeZoneId: String` — IANA tz (e.g. `Europe/Stockholm`)

Destination (end place):
- `storeId: String` — canonical store id
- `storeLocationId: String?` — stable backend-friendly location id
- `postOmbudId: String?` — stable backend-friendly postombud id
- `storeNameSnapshot: String`
- `citySnapshot: String`
- `storeLatSnapshot: Double`
- `storeLngSnapshot: Double`
- `endPlaceType: PlaceType` — `HOME | WAREHOUSE | STORE | SUPPLIER | OTHER`
- `endAddressSnapshot: String?`

Start place:
- `startLabelSnapshot: String`
- `startLat: Double`
- `startLng: Double`
- `startPlaceType: PlaceType`
- `startAddressSnapshot: String?`

Distance/time:
- `distanceMeters: Int`
- `distanceMethod: DistanceMethod` — `MAPS | GPS_STRAIGHT_LINE | MANUAL | UNKNOWN`
- `durationMinutes: Int`

Business/journal metadata:
- `notes: String`
- `businessPurpose: String` (Syfte)
- `supplierOrArea: String?`
- `isBusiness: Boolean`

Grouping:
- `runId: Long?` — groups multiple trips into one “run”

Future foundation fields:
- `currencyCode: String?`
- `mileageRateMicros: Long?`

Fees:
- `parkingTrafficFeeMinor: Int?`
- `parkingTicketId: String?` — UUID; metadata is eligible for cloud snapshot

### 2.2 Table `attachments` (`AttachmentEntity`)
Primary key: `id` (auto-increment Long).

Columns:
- `id: Long` (local evidence id)
- `uid: String` (Firebase UID)
- `tripId: Long` (FK-ish to `trips.id`, local)

Evidence identity:
- `clientRef: String?` — **stable UUID EvidenceID** (preferred join key)

Media reference:
- `uri: String` — usually a FileProvider content URI pointing into `filesDir/evidence/...`
- `mimeType: String`
- `displayName: String`

Timestamps:
- `capturedAt: Instant` — when photo/doc was captured/imported
- `addedAt: Instant` — when inserted into DB

Integrity:
- `sha256: String?` — SHA-256 hex of file bytes (when stored under evidence folder)
- `sizeBytes: Long?`

Link provenance:
- `linkedAt: Instant?`
- `linkedByDeviceId: String?` — per-install UUID from DataStore (`installId`)

### 2.3 Table `stores` (`StoreEntity`)
Primary key: composite `(uid, id)`.

Columns:
- `uid: String`
- `id: String` (storeId)
- `name: String`
- `lat: Double`, `lng: Double`
- `radiusMeters: Int`
- `regionCode: String`
- `city: String`
- `isActive: Boolean`
- `isFavorite: Boolean`

### 2.4 Table `prompt_events` (`PromptEventEntity`)
Primary key: `id` (auto-increment).

Columns:
- `id: Long`
- `uid: String`
- `storeId: String`
- `storeNameSnapshot: String`
- `storeLatSnapshot: Double`, `storeLngSnapshot: Double`
- `day: LocalDate`
- `triggeredAt: Instant`
- `status: PromptStatus` — `TRIGGERED | DISMISSED | LEFT_AREA | CONFIRMED | DELETED`
- `notificationId: Int`
- `lastUpdatedAt: Instant`
- `linkedTripId: Long?` (local trip id)

### 2.5 Table `ping_events` (`PingEventEntity`) (UX telemetry)
Primary key: `id` (auto-increment).

Columns:
- `id: Long`
- `uid: String`
- `storeId: String`
- `storeNameSnapshot: String`
- `storeLatSnapshot: Double`, `storeLngSnapshot: Double`
- `day: LocalDate`
- `occurredAt: Instant`
- `transition: PingTransition` — `ENTER | DWELL | EXIT`
- `source: PingSource` — `GEOFENCE`

Route snapshot from previous confirmed trip anchor:
- `routeDistanceFromPrevMeters: Int?`
- `routeDurationFromPrevMinutes: Int?`
- `routeSource: String?`
- `routeComputedAt: Instant?`
- `routeAnchorTripId: Long?` (local)
- `createdTripId: Long?` (local; prevents duplicates)

### 2.6 Table `visited_stores` (`VisitedStoreEntity`)
Primary key: composite `(uid, storeId)`.

Columns:
- `uid: String`
- `storeId: String`
- `firstVisitedAt: Instant`
- `lastVisitedAt: Instant`
- `visitCount: Int`
- `lastStoreNameSnapshot: String`
- `lastCitySnapshot: String`
- `lastLatSnapshot: Double`
- `lastLngSnapshot: Double`

### 2.7 Table `runs` (`RunEntity`)
Primary key: `id` (auto-increment).

Columns:
- `id: Long`
- `uid: String`
- `clientRef: String?` (future)
- `backendId: String?` (future)
- `syncStatus: SyncStatus`
- `day: LocalDate`
- `createdAt: Instant`
- `label: String`

### 2.8 Table `distance_cache` (`DistanceCacheEntity`)
Primary key: `id` (auto-increment). Has a uniqueness index on `(uid, startLatE5, startLngE5, destLatE5, destLngE5, travelMode)`.

Columns:
- `id: Long`
- `uid: String`
- `startLocationId: String?` (preferred stable id)
- `endLocationId: String?`
- `startLatE5: Int`, `startLngE5: Int`
- `destLatE5: Int`, `destLngE5: Int`
- `travelMode: String`
- `distanceMeters: Int`
- `durationMinutes: Int`
- `routePolyline: String?`
- `source: String` (e.g. `INTERNAL | GOOGLE`)
- `createdAt: Instant`

---

## 3) Android local: Room sync/outbox DB `trimsytrack.sync.db`

Room database file name: **`trimsytrack.sync.db`**.

Tables:
- `canonical_write_outbox`
- `track_event_outbox`

### 3.1 Table `canonical_write_outbox` (`CanonicalWriteOutboxEntity`)
Used to persist and retry canonical backend writes.

Columns:
- `id: Long` (auto)
- `route: String` (e.g. `drivingTripCreate`)
- `idempotencyKey: String` (stable across retries)
- `bodyJson: String` (JSON request body)
- `localTripId: Long?` (links to local trip)
- `state: Int` (`0 = pending`, `1 = uploaded`)
- `attempts: Int`
- `lastAttemptAtMillis: Long?`

### 3.2 Table `track_event_outbox` (`TrackEventOutboxEntity`)
Used for incremental TrackEvents upload (telemetry-like).

Columns:
- `eventId: String` (primary key)
- `type: String`
- `createdAtMillis: Long`
- `payloadJson: String?`
- `state: Int` (`0 = pending`, `1 = uploaded`)
- `attempts: Int`
- `lastAttemptAtMillis: Long?`

---

## 4) Android local: Preferences DataStore (`settings`)

TrimsyTRACK uses **Preferences DataStore**: `preferencesDataStore(name = "settings")`.

Key facts:
- Many preferences are **scoped per-account** using a `(baseKey + uid)` pattern.
- Some keys are global/per-install (e.g. `installId`, some diagnostics).

### 4.1 DataStore keys (complete list of named keys)
These are defined under `SettingsStore.Keys`.

Onboarding / account:
- `onboardingCompleted`
- `accountPictureUri`
- `installId` (stable per-install UUID)

Prompt/tracking gating:
- `activeStartMinutes`, `activeEndMinutes`, `activeDaysCsv`
- `trackingEnabled`, `regionCode`
- `dwellMinutes`, `radiusMeters`, `responsivenessSeconds`
- `dailyPromptLimit`, `perStorePerDay`, `suppressionMinutes`
- `maxActiveGeofences`
- `suggestLinkingWindowMinutes`

Tracking diagnostics:
- `geofenceLastSyncAtMillis`, `geofenceLastSyncReason`, `geofenceLastSyncTotalStores`, `geofenceLastSyncRegisteredStores`, `geofenceLastSyncResult`
- `geofenceLimitWarningAtMillis`
- `geofenceLastEventAtMillis`, `geofenceLastEventStoreId`, `geofenceLastEventTransition`
- `lastPingAtMillis`
- `batteryOptimizationPromptShown`

Körjournal / export:
- `vehicleRegNumber`, `driverName`
- `businessHomeAddress`, `businessHomeLat`, `businessHomeLng`
- `journalYear`, `odometerYearStartKm`, `odometerYearEndKm`

Store/UI caches/customization:
- `storeImagesJson`, `storeBusinessHoursJson`, `storeDisplayOverridesJson`, `storeFetchedDetailsJson`
- `homeTileIconImagesJson`
- `preferredCategoriesJson`
- `storeSyncRadiusKm`
- `ignoredStoreIdsJson`
- `visitedHiddenStoreIdsJson`
- `hiddenTripPlacesJson`
- `expandedStoreCitiesJson`
- `manualTripStoreSortMode`
- `manualTripSelectedStoreIdsJson`, `manualTripHiddenStoreIdsJson`
- `manualTripShowStores`, `manualTripShowPostOffice`, `manualTripShowOnlineResults`
- `manualTripSearchRadiusKm`, `manualTripCategoriesInitialized`, `manualTripCategoryConfigsJson`, `manualTripEnabledCategoryLabels`

UI:
- `darkModeEnabled`
- `useNewUi`
- `useLegacySettingsLayout`

Backend configuration + handshake state:
- `backendBaseUrl`
- `backendDriverId`
- `backendProtocolVersion`
- `backendIdentityUid`
- `backendIdentityEmail`
- `backendWritesEnabled`
- `backendSafetyModeEnabled`
- `backendSafetyModeReason`

Legacy cached “profile” payloads (compat only):
- `backendProfileJson`
- `backendProfileMediaJson`

Document rendering defaults:
- `useLogosInDocuments`
- `documentLogoOptOutJson`

DriverData snapshot bookkeeping:
- `driverDataLastUploadAtMillis`
- `driverDataLastUploadResult`
- `driverDataLastUploadFingerprint`

DriverData region-file integrity bookkeeping:
- `driverDataRegionsLastVerifyAtMillis`
- `driverDataRegionsLastVerifyResult`
- `driverDataRegionsLastVerifyFingerprint`

TrackEvents incremental sync cursor/diagnostics:
- `trackEventsAppliedSeq`
- `trackEventsLastSyncAtMillis`
- `trackEventsLastSyncResult`
- `trackEventsBackendSupported` — local capability latch (default `true`). If the backend returns HTTP 404 for TrackEvents routes, the client sets this to `false` and cancels TrackEvents scheduling to prevent churn.

TrackEvents self-heal mechanism:
- If TrackEvents is disabled and the backend does not advertise `capabilities.trackEvents`, the client runs a low-frequency probe (WorkManager) to re-enable TrackEvents if the routes appear later.

Receipt reminder:
- `receiptReminderMinutes`
- `receiptReminderMessage`

### 4.2 Which DataStore settings are exported to backend snapshots?
DriverData snapshots export a **subset** of settings (see `DriverSettings` in DriverData v3).

Exported:
- identity-ish: `profileId` (uid), `profileName` (email)
- tracking config: active window/days, dwell/radius/responsiveness, prompt limits
- journal profile: vehicle/driver name, business home address/coords, journal year, odometer strings
- UI + autosync place behavior (survives reinstall/restore):
  - `preferredCategoriesJson`
  - `storeSyncRadiusKm`
  - `ignoredStoreIdsJson`, `visitedHiddenStoreIdsJson`
  - `hiddenTripPlacesJson` (metadata for hidden/removed places)
  - `storeDisplayOverridesJson` (user-set name/city/category per place)
  - `manualTripCategoryConfigsJson`, `manualTripEnabledCategoryLabels`
  - `storeBusinessHoursJson`, `storeFetchedDetailsJson` (Google metadata cache)
  - `manualTripStoreSortMode`
- backendBaseUrl + backendDriverId

Intentionally NOT exported (kept local-only to avoid bloat):
- `storeImagesJson` (can contain large base64 blobs)
- `homeTileIconImagesJson`

---

## 5) Android local: app-private files

### 5.1 Region files
Directory: `filesDir/regions/`
- Each region file is `<regionCode>.json`.
- These contents are included in DriverData snapshots as `regions: Map<String, String>`.

### 5.2 Evidence files (media bytes)
Directory: `filesDir/evidence/<tripId>/<file>`

Rules:
- Evidence file bytes never go to backend.
- Filenames are canonicalized to:
  - `trip-<tripId>__ev-<evidenceId>__ts-<capturedAtEpochMillis>.<ext>`

Evidence is stored as FileProvider content URIs pointing under `files/evidence/...`.

### 5.3 Evidence export API for TrimsyApp (ContentProvider)
Provider: `content://<applicationId>.evidence/*`

Routes:
- `.../list` — DB-backed evidence list with trip context columns
- `.../files` — filesystem walk of evidence files (even if DB linkage missing)
- `.../ev/<evidenceId>` — open actual bytes for the evidence row
- `.../file?path=<tripId>/<fileName>` — open by relative path

Both `list` and `files` expose:
- `relativePath` (under evidence root)
- inferred `tripId` and trip context (day, store name, city)
- linkage to DB evidenceId when available

### 5.4 Journal export artifact (CSV)
Körjournal exporter writes a CSV to cache:
- `cacheDir/exports/korjournal_<year>.csv`

The exported CSV columns are the authoritative mapping target for a “complete driving journal” view.

---

## 6) Backend storage and APIs (as implemented)

### 6.1 DriverData snapshot store (`driverdataGet/driverdataPut`)
HTTP route names (all POST under `apiV1/<route>`):
- `driverdataGet`
- `driverdataPut`

Firestore storage:
- Collection: `driverdata_snapshots`
- Document id: **`<uid>`**
- Document shape invariant: exactly one field:
  - `{ snapshot: <DriverDataV3 object> }`

Idempotency:
- `driverdataPut` requires `idempotencyKey`.
- Stored under collection `idempotency_keys` with id:
  - `${uid}::DRIVERDATA_PUT::${idempotencyKey}`

Response contract:
- both `driverdataGet` and `driverdataPut` return the **DriverData object directly** (not an `{ok,result}` envelope).

First-time / empty state:
- If no snapshot exists yet for a UID, `driverdataGet` returns an **empty DriverData v3 object** (HTTP 200), not an error.
- Clients should treat this as normal (“No data yet / first-time user”).

Server-side canonicalization/repair:
- Backend normalizes common corrupt shapes (JSON string, double-nested `{snapshot:{...}}`).
- Backend enforces `schemaVersion == 3` and ensures `trips[]` and `stops[]` exist.

### 6.2 Canonical driving-trip truth (`drivingTripCreate`)
Route:
- `apiV1/drivingTripCreate`

Firestore collections involved:
- `drivingTrips` — canonical trip documents
- `drivingTripClientRefs` — mapping `(uid, clientTripId)` → `canonicalDrivingTripId`
- `events` — canonical event log (`DRIVING_TRIP_CREATE`)

Idempotency:
- Backend is idempotent by `(uid, clientTripId)` using `drivingTripClientRefs`.
- Client also uses a stable outbox idempotencyKey:
  - `drivingTripCreate:<clientTripId>`

Canonical driving-trip fields written include:
- uid, clientTripId, localTripNumber, runId, syfte
- driverName, vehicleRegNumber
- startedAt, endedAt, timeZoneId
- start/end lat/lng, place name/address, endPlaceType
- storeId/storeLocationId/postOmbudId, city
- distanceMeters, durationMinutes, distanceMethod
- lastSpotTripClientId, distanceFromLastSpot/durationFromLastSpot
- notes
- evidence[] metadata (IDs/hashes only)

---

## 7) DriverData v3 snapshot schema (the restore/export object)

### 7.1 Top-level `DriverData`
TrimsyTRACK uses:
- `schemaVersion = 3`

Fields:
- `schemaVersion: number`
- `exportedAt: string` (ISO; ignore for change detection)
- `driverId: string` (in TrimsyTRACK: same as uid)
- `appId: string` (defaults to `com.trimsytrack`)

- `settings: DriverSettings`
- `regions: Map<regionCode, jsonString>`

- `stores: StoreDto[]`
- `trips: TripDto[]`
- `stops: StopDto[]` (derived from trips; ordered)

- `promptEvents: PromptEventDto[]`
- `pingEvents: PingEventDto[]` (UX telemetry; not truth)
- `visitedStores: VisitedStoreDto[]`
- `runs: RunDto[]`
- `distanceCache: DistanceCacheDto[]`
- `attachments: AttachmentDto[]` (metadata; uri blank in cloud snapshot)
- `parkingTickets: ParkingTicketDto[]` (metadata only)

### 7.2 `TripDto` (driving journal truth)
The trip is the core truth record.

Important identity fields:
- `id: number` — local Trip# (not stable cross-device)
- `clientRef: string` — stable TripID (preferred)
- `backendId: string|null`
- `syncStatus: string`

Time:
- `createdAt: string` (ISO)
- `day: string` (YYYY-MM-DD)
- `startedAt: string`
- `endedAt: string`
- `timeZoneId: string`

Start/end places + location identity:
- `storeId: string`
- `storeLocationId: string|null`
- `postOmbudId: string|null`
- `storeNameSnapshot: string`
- `citySnapshot: string`
- `storeLatSnapshot: number`
- `storeLngSnapshot: number`
- `endPlaceType: string`
- `endAddressSnapshot: string|null`
- `startLabelSnapshot: string`
- `startLat: number`, `startLng: number`
- `startPlaceType: string`
- `startAddressSnapshot: string|null`

Distance/time:
- `distanceMeters: number`
- `durationMinutes: number`
- `distanceMethod: string`

Journal fields:
- `notes: string`
- `businessPurpose: string`
- `supplierOrArea: string|null`
- `isBusiness: boolean`

Grouping:
- `runId: number|null`

Optional:
- `currencyCode: string|null`
- `mileageRateMicros: number|null`
- `parkingTrafficFeeMinor: number|null`
- `parkingTicketId: string|null`

### 7.3 `StopDto` (ordered visit list)
Stops are derived from trips as:
- one Stop per trip
- ordered by `(endedAt, id)`
- `stopId == trip.clientRef`
- `tripNumber == trip.id`
- `prevStopId` is the previous trip’s clientRef

### 7.4 `AttachmentDto` (evidence metadata)
- `clientRef` is EvidenceID.
- `tripClientRef` is the stable link to the owning trip.
- In cloud snapshots, `uri` is intentionally `""`.

To fetch bytes, Trimsy must use the EvidenceProvider routes.

---

## 8) How to build a complete driving journal from this data

### 8.1 Authoritative “journal rows”
Use **trips** as the journal truth. You can present them in these common projections:

- **By day:** group by `Trip.day`.
- **Within day:** order by `Trip.endedAt` (or `createdAt` as fallback).
- **Home-to-home run:** group by `runId` (when present) and/or detect `endPlaceType == HOME` as run completion.

### 8.2 Joining evidence
Preferred join keys:
- TripID: `Trip.clientRef`
- EvidenceID: `Attachment.clientRef`

Link rule:
- Local DB: `Attachment.tripId` → `Trip.id`
- Cross-system: `AttachmentDto.tripClientRef` → `TripDto.clientRef`

Evidence count per trip:
- locally: count `attachments` where `tripId == trips.id`
- from snapshot: count `attachments` where `tripClientRef == trip.clientRef`

### 8.3 Parking/traffic fee receipts
Parking/traffic fee media is local-only evidence, but the metadata is in:
- `Trip.parkingTrafficFeeMinor`
- `Trip.parkingTicketId`
- and emitted to `parkingTickets[]` in DriverData snapshots.

### 8.4 Swedish Körjournal CSV mapping (as implemented)
The app’s Körjournal exporter maps data like this:
- Date: `Trip.day`
- Start time: `Trip.startedAt` converted using `Trip.timeZoneId`
- End time: `Trip.endedAt` converted using `Trip.timeZoneId`
- Distance km: `Trip.distanceMeters / 1000`
- Method: `Trip.distanceMethod`
- Start: `Trip.startLabelSnapshot` (with “Business home” replaced by businessHomeAddress)
- End: `Trip.storeNameSnapshot` (or business home label)
- Syfte: normalized `Trip.businessPurpose`
- Tjänsteresa: `Trip.isBusiness`
- Underlag count: evidence count
- Förare: `settings.driverName`
- Registreringsnummer: `settings.vehicleRegNumber`

The exporter also optionally appends a synthetic “return home” row if business home coordinates are set and the last stop is not near home.

---

## 9) Analytics: recommended derived metrics

From `trips[]` you can compute:
- distance totals per day/week/month
- distance totals per storeId/city
- time totals (durationMinutes) per day/store
- frequency: visitCount per store (cross-check with `visitedStores[]`)
- business-purpose distribution (`businessPurpose`)
- data quality:
  - missing/blank timezone
  - `endedAt < startedAt` (should not happen)
  - `distanceMethod == UNKNOWN` share

From `runs[]` / `runId` you can compute:
- runs per day
- avg non-home stops per run (can also be derived)

From evidence:
- evidence coverage rate: % trips with ≥1 attachment

---

## 10) Practical implementation guidance for Trimsy

### 10.1 Preferred ingestion pipeline
1) Pull **DriverData v3** from backend (`driverdataGet`) for restore/cross-device.
2) Pull **evidence bytes** from device via EvidenceProvider (`.../list`, `.../ev/<id>`, `.../file?...`).
3) Pull **canonical trips** from backend truth store (future/if TrimsyApp becomes canonical reader). Today, TrimsyTRACK itself writes canonical truth via `drivingTripCreate` and stores the resulting `backendId` locally.

### 10.2 Treat local Trip# as display-only
- Do not use `Trip.id` as a long-term stable identifier.
- Use `Trip.clientRef` for cross-device, cross-system, and deduplication.

### 10.3 Evidence bytes are optional in cloud restore
- After reinstall, a device may restore trips and attachment metadata from cloud but will not have the evidence bytes unless they are still present locally or re-synced phone→PC.

---

## Appendix A: Where to look in code

Android (TrimsyTRACK):
- DriverData schema: `app/src/main/java/com/trimsytrack/data/driverdata/DriverDataModels.kt`
- Snapshot export/restore: `app/src/main/java/com/trimsytrack/data/driverdata/DriverDataRepository.kt`
- Room entities: `app/src/main/java/com/trimsytrack/data/entities/*.kt`
- Outbox DB: `app/src/main/java/com/trimsytrack/data/sync/SyncDatabase.kt`
- Canonical trip outbox + writer: `app/src/main/java/com/trimsytrack/data/canonical/*`
- Evidence ContentProvider: `app/src/main/java/com/trimsytrack/export/EvidenceProvider.kt`
- Körjournal CSV export mapping: `app/src/main/java/com/trimsytrack/export/KorjournalExporter.kt`
- DataStore keys: `app/src/main/java/com/trimsytrack/data/SettingsStore.kt`

Backend (BACKENDTRIMSY):
- driverdataGet/Put and storage: `BACKENDTRIMSY/functions/src/index.ts`
- canonical drivingTripCreate storage: `BACKENDTRIMSY/functions/src/events.ts`
