# TrimsyAPP (Backend) Data Access Guide

Status: **as-implemented** (BACKENDTRIMSY)

This document lists **all current ways** TrimsyAPP can obtain backend data.

It is intentionally TrimsyAPP-focused and answers:
- which routes exist,
- which are safe/allowed for `app_id=trimsyapp`,
- what auth + envelope fields are required,
- request/response JSON shapes,
- what NOT to use (to avoid intertwining with TrimsyTRACK sync).

Authoritative backend implementation:
- `BACKENDTRIMSY/functions/src/index.ts`

Related (TrimsyTRACK read-only bridge details):
- `shared_contract/TRIMSYAPP_TRIMSYTRACK_READONLY_GUIDE.md`

---

## 0) Non-negotiable isolation rules

TrimsyAPP must not be intertwined with TrimsyTRACK sync.

Therefore:
- TrimsyAPP must NOT call TrimsyTRACK write/sync routes.
- TrimsyAPP must NOT share TrimsyTRACK/TrimsyPC route names for TrimsyTRACK data retrieval.
- If TrimsyAPP needs TrimsyTRACK-owned data, it uses **TrimsyAPP-only bridge routes**.

---

## 1) Auth + Base URL (HTTP)

All HTTP routes are `POST` to:

- `https://<region>-<project>.cloudfunctions.net/apiV1/<route>`

All HTTP requests require:
- Header: `Authorization: Bearer <Firebase ID token>`
- Body: JSON

Optional:
- `clientRequestId` (string, recommended) — echoed back as response headers:
  - `X-Client-Request-Id`
  - `X-Trimsy-Client-Request-Id`

### 1.1 Required envelope fields

Most routes require:
- `clientProtocolVersion`: integer (currently `1`)
- `app_id`: one of `trimsyapp | trimsytrack | trimsypc`

Exceptions:
- `handshakeGet` does **not** require `clientProtocolVersion`.
- `uidEnsure` does **not** require `clientProtocolVersion`.

### 1.2 Response shape conventions

- Most routes respond with an envelope:
  - `{ ok: true, result: ... }`
  - or `{ ok: false, error: { code, message, details? } }`

- Legacy exception:
  - `driverdataGet` returns the DriverData object **directly** (no envelope).
  - TrimsyAPP is not allowed to call `driverdataGet`.

---

## 2) Method A — System / startup data (TrimsyAPP allowed)

### 2.1 `health` (server time)

Route:
- `apiV1/health`

Request body:
- `{}` (no required fields)

Response:
```json
{ "ok": true, "serverTime": "2026-02-12T12:34:56.789Z" }
```

### 2.2 `handshakeGet` (startup handshake)

Purpose:
- Returns protocol support range + identity + backend mode.

Route:
- `apiV1/handshakeGet`

Request body (minimum):
```json
{ "app_id": "trimsyapp" }
```

Response (enveloped):
```json
{ "ok": true, "result": { "protocolVersion": 1, "identity": { "uid": "..." }, "serverTime": "..." } }
```

### 2.3 `uidEnsure` (ensure uid_state exists)

Purpose:
- Ensures lightweight provisioning state exists for this UID.

Route:
- `apiV1/uidEnsure`

Request body:
- `{}`

Response (enveloped):
- Includes `ensured`, `uid`, `serverTime`, and deployment info.

---

## 3) Method B — Law / compliance data (TrimsyAPP allowed)

These routes are explicitly allowed for any `app_id`.

Routes:
- `apiV1/lawGet`
- `apiV1/lawQuizGet`
- `apiV1/lawQuizSubmit`
- `apiV1/lawAccept`
- `apiV1/lawContractGet`

Common request fields:
```json
{ "clientProtocolVersion": 1, "app_id": "trimsyapp" }
```

Response:
- All are enveloped: `{ ok: true, result: ... }`

Note:
- Exact law payload schemas are owned by the law subsystem; treat `result` as the contract.

---

## 4) Method C — TrimsyAPP-owned data via the AppData chunk store (recommended for TrimsyAPP sync)

This is a generic, chunked, per-user snapshot store.

Key concepts:
- `source_app_id`: logical namespace for the data stream (recommended: `trimsyapp` or `trimsyapp.<domain>`)
- `commitId`: sha256 hex of the whole payload (client-defined)
- `revision`: monotonically increasing head revision per `(uid, source_app_id)`

### 4.1 Read: list latest heads (`appDataHeadsGet`)

Route:
- `apiV1/appDataHeadsGet`

Request body:
```json
{ "clientProtocolVersion": 1, "app_id": "trimsyapp", "source_app_id": "trimsyapp" }
```

Response:
```json
{ "ok": true, "result": { "heads": [ { "source_app_id": "trimsyapp", "commitId": "<sha256>", "totalChunks": 3, "schemaVersion": 12, "contentEncoding": "utf8", "revision": 7 } ] } }
```

Notes:
- If `source_app_id` is omitted, backend returns all heads for the user.

### 4.2 Read: download one chunk (`appDataChunkGet`)

Route:
- `apiV1/appDataChunkGet`

Request body:
```json
{ "clientProtocolVersion": 1, "app_id": "trimsyapp", "source_app_id": "trimsyapp", "commitId": "<sha256>", "chunkIndex": 0 }
```

Response:
```json
{ "ok": true, "result": { "commitId": "<sha256>", "chunkIndex": 0, "totalChunks": 3, "schemaVersion": 12, "contentEncoding": "utf8", "chunkData": "<utf8-string>" } }
```

### 4.3 Writes (for completeness — not “obtain data”)

TrimsyAPP can also write/advance its own AppData stream:
- `apiV1/appDataChunkPut` (upload chunks)
- `apiV1/appDataCommit` (publish head)

These are safe for TrimsyAPP because they are namespaced by `source_app_id`.

Isolation recommendation:
- Keep TrimsyAPP data under `source_app_id` beginning with `trimsyapp`.
- Do **not** reuse `source_app_id=trimsytrack`.

---

## 5) Method D — TrimsyTRACK-owned driving data via TrimsyAPP-only bridge routes (read-only, not intertwined)

If TrimsyAPP needs to *read* TrimsyTRACK driving journal + media evidence, it must use dedicated bridge route names.

Allowed routes for `app_id=trimsyapp`:
- `apiV1/trimsytrackDriverdataGet`
- `apiV1/trimsytrackTripEvidenceListByTrip`
- `apiV1/trimsytrackTripEvidenceDownload`

These routes are:
- **read-only**,
- **TrimsyAPP-only** (they reject non-trimsyapp callers),
- implemented as thin wrappers around the existing TrimsyTRACK/TrimsyPC read models.

See `shared_contract/TRIMSYAPP_TRIMSYTRACK_READONLY_GUIDE.md` for full payloads and examples.

---

## 6) Not usable by TrimsyAPP (by design)

### 6.1 TrimsyTRACK sync surfaces (blocked)

TrimsyAPP must not call these:
- `apiV1/driverdataGet` (restricted to `trimsytrack|trimsypc`)
- `apiV1/driverdataPut` (TrimsyTRACK-only)
- `apiV1/trackEventsBatchPut` and `apiV1/trackEventsSinceGet` (TrimsyTRACK-only)
- `apiV1/drivingTripCreate` (TrimsyTRACK-only)
- `apiV1/tripEvidenceUploadInit` (TrimsyTRACK-only)
- `apiV1/tripEvidenceListByTrip` and `apiV1/tripEvidenceDownload` (restricted to `trimsytrack|trimsypc`)

### 6.2 Operator-only routes

TrimsyAPP should never rely on:
- `apiV1/opsGetSafetyMode`
- `apiV1/opsSetSafetyMode`

These require operator privileges.

---

## 7) Minimal PowerShell examples (TrimsyAPP)

Prereqs:
- `$base = "https://europe-north1-<project>.cloudfunctions.net/apiV1"`
- `$idToken = "<FIREBASE_ID_TOKEN>"`

### handshakeGet
```powershell
$body = @{ app_id = 'trimsyapp'; clientRequestId = 'trimsyapp-start-1' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/handshakeGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

### appDataHeadsGet
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsyapp'; source_app_id = 'trimsyapp' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/appDataHeadsGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

### appDataChunkGet
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsyapp'; source_app_id = 'trimsyapp'; commitId = '<SHA256>'; chunkIndex = 0 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/appDataChunkGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```

### trimsytrackDriverdataGet (bridge)
```powershell
$body = @{ clientProtocolVersion = 1; app_id = 'trimsyapp'; clientRequestId = 'trimsyapp-track-1' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/trimsytrackDriverdataGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType 'application/json' -Body $body
```
