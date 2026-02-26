# TrimsyPC ← TrimsyTRACK (Backend) Sync/Retrieval Guide

Status: **as-implemented** (BACKENDTRIMSY + TrimsyTRACK Android)

This document tells TrimsyPC exactly:
- what TrimsyTRACK syncs to the backend,
- what TrimsyPC can fetch (read-only),
- how to retrieve **parking fee** + **trip media** (evidence bytes).

TL;DR (trip photos / media):
- Get trips via `driverdataGet`.
- For each trip, list media via `tripEvidenceListByTrip(tripClientRef = Trip.clientRef)`.
- For each returned item, download bytes via `tripEvidenceDownload(clientEvidenceId)`.

Authoritative route implementation:
- `BACKENDTRIMSY/functions/src/index.ts`
- Evidence routes: `BACKENDTRIMSY/functions/src/tripEvidence.ts`

Field-level DriverData schema reference:
- `shared_contract/TRIMSYTRACK_COMPLETE_DATA_MAP.md`

---

## 1) Hard rules (app isolation)

TrimsyPC must be **read-only** for TrimsyTRACK-owned truth.

Allowed routes for `app_id=trimsypc`:
- `driverdataGet`
- `tripEvidenceListByTrip`
- `tripEvidenceDownload`

Not allowed (must be rejected for TrimsyPC):
- Any TrimsyTRACK write routes (e.g. `driverdataPut`, `drivingTripCreate`, `tripEvidenceUploadInit`)

---

## 2) Auth + base URL

All routes are `POST` to:
- `https://<region>-<project>.cloudfunctions.net/apiV1/<route>`

All requests require:
- Header: `Authorization: Bearer <Firebase ID token>`
- JSON body including:
  - `clientProtocolVersion` (number)
  - `app_id: "trimsypc"`

### Response shape gotcha (important)

- Most routes respond with an envelope `{ ok, result, error }`.
- `driverdataGet` returns the **DriverData object directly** at the top-level (legacy compatibility).

---

## 3) What TrimsyTRACK syncs (what TrimsyPC can fetch)

### 3.1 DriverData snapshot (`driverdataGet`)

`driverdataGet` returns **DriverData v3** (checkpoint snapshot). It contains (at least):
- `settings` (driver name, vehicle reg, etc.)
- `trips[]` (the driving journal rows + fees)
- `stops[]` (derived ordered visit list)

And commonly also:
- `attachments[]` (evidence metadata, not bytes)
- `parkingTickets[]` (parking fee metadata)
- `stores[]`, `promptEvents[]`, `pingEvents[]`, `visitedStores[]`, `runs[]`, `distanceCache[]`

TrimsyPC should treat this as:
- “current known state” checkpoint for restore + analytics seeding
- not a canonical immutable truth log

### 3.2 Trip evidence bytes (media)

Evidence/media bytes (photos / receipts / other attachments) are stored in backend storage and retrieved via:
- `tripEvidenceListByTrip` (metadata list)
- `tripEvidenceDownload` (signed download URL)

DriverData snapshots do **not** inline evidence bytes.

Important clarification:
- `driverdataGet.attachments[]` (when present) is **metadata only** and must not be treated as “the media bytes”.
- To fetch **all photos attached to a trip**, TrimsyPC must use `tripEvidenceListByTrip` for that trip, then download each item via `tripEvidenceDownload`.

---

## 4) Stable join keys (do not use local auto-increment IDs)

Stable identifiers:
- Trip stable ID: `Trip.clientRef` (UUID string) == `tripClientRef`
- Evidence stable ID: `Attachment.clientRef` (UUID string) == `clientEvidenceId`

Never treat local Room primary keys as stable:
- `Trip.id` (Long) is device-local only
- `Attachment.id` (Long) is device-local only

---

## 5) How TrimsyPC fetches trips + media (recommended algorithm)

### Step A: Fetch the snapshot
1) Call `driverdataGet` with `{ clientProtocolVersion, app_id: "trimsypc" }`.
2) Read `trips[]`.
3) For each trip, keep:
   - `trip.clientRef` (TripID)
   - `trip.parkingTrafficFeeMinor` and `trip.parkingTicketId` (if present)

### Step B: Fetch evidence list per trip
For each trip with a non-empty `trip.clientRef`:
1) Call `tripEvidenceListByTrip` with:
   - `tripClientRef = trip.clientRef`
2) This returns metadata items including:
   - `clientEvidenceId`, `contentType`, `displayName`, `sha256`, `sizeBytes`, `parkingTicketId`

TrimsyPC should treat these returned items as the authoritative list of “trip media attachments” for that trip.

### Step C: Download each evidence item
For each evidence item:
1) Call `tripEvidenceDownload` with:
   - `clientEvidenceId`
2) Read `downloadUrl` and download bytes via normal HTTPS GET.
3) Optional but recommended:
   - validate byte length vs `sizeBytes` when present
   - validate SHA-256 vs `sha256` when present

Note: signed URLs are short-lived (TTL is currently ~15 minutes).

---

## 6) Parking fee + parking receipt media (the exact linkage)

Parking/traffic fee fields on a trip:
- `Trip.parkingTrafficFeeMinor` (minor units)
- `Trip.parkingTicketId` (UUID string)

Receipt evidence linkage rule:
- `Trip.parkingTicketId` == receipt evidence `clientEvidenceId`

Recommended retrieval:
1) For a trip with `parkingTicketId`:
   - list evidence via `tripEvidenceListByTrip(tripClientRef)`
2) Pick the item where:
   - `item.clientEvidenceId == trip.parkingTicketId`
   - (and/or) `item.parkingTicketId == trip.parkingTicketId`
3) Download via `tripEvidenceDownload(clientEvidenceId)`.

---

## 7) Minimal PowerShell examples (manual verification)

Prereqs:
- `$base = "https://europe-north1-<project>.cloudfunctions.net/apiV1"`
- `$idToken = "<FIREBASE_ID_TOKEN>"`

### driverdataGet (raw DriverData object)
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsypc' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/driverdataGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

### tripEvidenceListByTrip (enveloped)
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsypc'; tripClientRef = '<TRIP_CLIENT_REF>' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/tripEvidenceListByTrip" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

### tripEvidenceDownload (enveloped; returns signed URL)
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsypc'; clientEvidenceId = '<CLIENT_EVIDENCE_ID>' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/tripEvidenceDownload" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

Then download the returned `downloadUrl`:
```powershell
Invoke-WebRequest -Uri '<SIGNED_DOWNLOAD_URL>' -OutFile '.\evidence.bin'
```
