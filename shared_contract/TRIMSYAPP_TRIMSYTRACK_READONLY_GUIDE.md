# TrimsyAPP ← TrimsyTRACK (Backend) Read-Only Retrieval Guide

Status: **as-implemented** (BACKENDTRIMSY)

See also:
- `shared_contract/TRIMSYAPP_BACKEND_DATA_ACCESS_GUIDE.md` (master list of all TrimsyAPP backend read surfaces)

This document tells TrimsyAPP exactly:
- how to fetch **TrimsyTRACK-owned** driving/trip data from the backend,
- without using the primary TrimsyTRACK sync route names,
- and without being allowed to write/alter TrimsyTRACK truth.

Authoritative implementation:
- `BACKENDTRIMSY/functions/src/index.ts`

---

## 1) Goal: strict app isolation

TrimsyAPP must be able to **read** TrimsyTRACK data, while:
- TrimsyAPP must NOT be able to write to TrimsyTRACK-owned truth stores.
- TrimsyAPP must NOT call TrimsyTRACK sync route names.
- TrimsyTRACK sync must remain unchanged.

This is accomplished by exposing **TrimsyAPP-only bridge routes** with dedicated names.

---

## 2) Auth + base URL

All routes are `POST` to:
- `https://<region>-<project>.cloudfunctions.net/apiV1/<route>`

All requests require:
- Header: `Authorization: Bearer <Firebase ID token>`
- JSON body including:
  - `clientProtocolVersion` (number; currently `1`)
  - `app_id: "trimsyapp"`

Optional but recommended:
- `clientRequestId` (string) to correlate logs; echoed back as response headers.

---

## 3) Allowed routes for TrimsyAPP

Allowed routes for `app_id=trimsyapp`:
- `trimsytrackDriverdataGet` (read-only snapshot of DriverData v3)
- `trimsytrackTripEvidenceListByTrip` (list evidence metadata by trip)
- `trimsytrackTripEvidenceDownload` (signed download URL for evidence bytes)

Not allowed:
- Any TrimsyTRACK write routes (e.g. `driverdataPut`, `drivingTripCreate`, `tripEvidenceUploadInit`)
- TrackEvents sync routes (`trackEventsBatchPut`, `trackEventsSinceGet`)

---

## 4) Route contracts

### 4.1 `trimsytrackDriverdataGet`

Request body:
```json
{
  "clientProtocolVersion": 1,
  "app_id": "trimsyapp",
  "clientRequestId": "optional-request-id"
}
```

Response:
- Enveloped, returns DriverData v3 under `result`.
```json
{
  "ok": true,
  "result": {
    "schemaVersion": 3,
    "driverId": "<uid>",
    "settings": {},
    "trips": [],
    "stops": []
  }
}
```

Notes:
- If no snapshot exists yet, the backend returns an empty v3 shape (not an error).
- The payload is canonicalized server-side.

### 4.2 `trimsytrackTripEvidenceListByTrip`

Request body:
```json
{
  "clientProtocolVersion": 1,
  "app_id": "trimsyapp",
  "tripClientRef": "<TRIP_CLIENT_REF>",
  "limit": 50
}
```

Response (enveloped):
```json
{
  "ok": true,
  "result": {
    "tripClientRef": "<TRIP_CLIENT_REF>",
    "items": [
      {
        "clientEvidenceId": "...",
        "tripClientRef": "...",
        "backendTripId": null,
        "parkingTicketId": null,
        "contentType": "image/jpeg",
        "displayName": "...",
        "sha256": "...",
        "sizeBytes": 123,
        "capturedAt": "...",
        "linkedAt": "...",
        "linkedByDeviceId": "...",
        "storagePath": "...",
        "uploadedAtIso": "..."
      }
    ]
  }
}
```

### 4.3 `trimsytrackTripEvidenceDownload`

Request body:
```json
{
  "clientProtocolVersion": 1,
  "app_id": "trimsyapp",
  "clientEvidenceId": "<CLIENT_EVIDENCE_ID>"
}
```

Response (enveloped):
```json
{
  "ok": true,
  "result": {
    "clientEvidenceId": "...",
    "tripClientRef": "...",
    "contentType": "...",
    "displayName": "...",
    "sha256": "...",
    "sizeBytes": 123,
    "parkingTicketId": null,
    "downloadUrl": "https://...",
    "expiresAtIso": "..."
  }
}
```

---

## 5) Recommended TrimsyAPP algorithm

1) Call `trimsytrackDriverdataGet`.
2) Read `result.trips[]`.
3) For each trip, use its stable trip id (Trip `clientRef`) as `tripClientRef`.
4) Call `trimsytrackTripEvidenceListByTrip(tripClientRef)`.
5) For each evidence item, call `trimsytrackTripEvidenceDownload(clientEvidenceId)` and download bytes from the signed URL.

---

## 6) PowerShell examples

Prereqs:
- `$base = "https://europe-north1-<project>.cloudfunctions.net/apiV1"`
- `$idToken = "<FIREBASE_ID_TOKEN>"`

### trimsytrackDriverdataGet
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsyapp'; clientRequestId = 'trimsyapp-test-1' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/trimsytrackDriverdataGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

### trimsytrackTripEvidenceListByTrip
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsyapp'; tripClientRef = '<TRIP_CLIENT_REF>'; limit = 50 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/trimsytrackTripEvidenceListByTrip" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

### trimsytrackTripEvidenceDownload
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsyapp'; clientEvidenceId = '<CLIENT_EVIDENCE_ID>' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/trimsytrackTripEvidenceDownload" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

Then download the returned `downloadUrl`:
```powershell
Invoke-WebRequest -Uri '<SIGNED_DOWNLOAD_URL>' -OutFile '.\evidence.bin'
```
