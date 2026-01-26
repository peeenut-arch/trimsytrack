# Prod Verification Packet — TrimsyTrack stuck uploads / apiV1 route mismatch

This is the single, copy/pasteable packet to:
- Verify production is running the expected backend revision and route set.
- Confirm contract behaviors needed by TrimsyTrack are live.
- Diagnose “wrong service / old revision” quickly using deployment identity.

## Situation

Symptom:
- TrimsyTrack uploads get stuck because `POST /apiV1/drivingTripCreate` returns HTTP `404` “Unknown route” in production.
- At the same time, `POST /apiV1/handshakeGet` and `POST /apiV1/driverdataGet` return HTTP `200`.

Interpretation:
- This strongly indicates TrimsyTrack is hitting a different deployed revision/service than expected (or a different base URL), because the canonical router in this workspace exports `drivingTripCreate`.

## Source-of-truth (workspace)

The canonical HTTP gateway is `apiV1` (Cloud Functions Gen2 `onRequest`).

Router implementation:
- `BACKENDTRIMSY/functions/src/index.ts`

Handshake payload includes best-effort deployment identity:
- `BACKENDTRIMSY/functions/src/handshake.ts`

## Contract requirements to be live in prod

### A) `POST /apiV1/driverdataGet`

Requirement:
- If no snapshot exists yet for a UID, return HTTP `200` with an **empty DriverData v3 object**.
- Must not return `404` for first-time users.

Response shape:
- **Raw** DriverData JSON object (no `{ ok: true, result: ... }` wrapper).
- Example (empty v3):
  - `{ "schemaVersion": 3, "stops": [], "trips": [] }`

### B) `POST /apiV1/uidEnsure`

Requirement:
- Idempotently ensures `uid_state/{uid}` exists.
- Returns HTTP `200` with a stable envelope.

Expected response (example shape):
```json
{
  "ok": true,
  "result": {
    "ensured": true,
    "created": false,
    "uid": "<uid>",
    "serverTimeIso": "2026-01-22T12:34:56.789Z",
    "serverTimeMs": 1760000000000,
    "deployment": {
      "service": "<k_service|null>",
      "revision": "<k_revision|null>",
      "functionTarget": "<function_target|null>"
    },
    "identity": { "uid": "<uid>", "email": "<email|null>" }
  }
}
```

### C) Unknown routes must be machine-classifiable

Requirement:
- Unknown routes must return HTTP `404` with:
  - `details.machineCode = "ROUTE_NOT_FOUND"`

Expected response:
```json
{
  "ok": false,
  "error": {
    "code": "not-found",
    "message": "Unknown route.",
    "details": {
      "machineCode": "ROUTE_NOT_FOUND",
      "route": "<route>"
    }
  }
}
```

## Deployment identity (how to correlate client logs to backend revision)

### Response headers (apiV1)

Every `apiV1` response should include these headers (best-effort):
- `X-Trimsy-Server-Time`
- `X-Trimsy-Service` (from `K_SERVICE`)
- `X-Trimsy-Revision` (from `K_REVISION`)
- `X-Trimsy-Function-Target` (from `FUNCTION_TARGET`)

### Handshake JSON

`handshakeGet` returns `result.deployment` with the same identity fields.

If the headers / deployment fields differ between endpoints or between retries, you are likely hitting:
- different revisions during rollout, or
- the wrong base URL, or
- a different deployed service.

## Base URLs (what TrimsyTrack should use)

Production (Cloud Functions):
- `https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1`

Emulator:
- `http://127.0.0.1:5001/trimsy-d12de/europe-north1/apiV1`

Hard rule:
- The base URL must end with `/apiV1`.
- The route is appended after that, e.g. `/apiV1/handshakeGet`.

## One-line verification (PowerShell)

Pre-req:
- You must have a valid Firebase ID token for the same project as the backend.

Set variables:
```powershell
$base = "https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1"
$idToken = "<PASTE_FIREBASE_ID_TOKEN>"
```

### Option 1: Use `Invoke-WebRequest` (recommended: includes headers)

Handshake (`POST /handshakeGet`):
```powershell
$r = Invoke-WebRequest -Method Post -Uri "$base/handshakeGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType "application/json" -Body "{}"
$r.StatusCode
$r.Headers | Format-List
($r.Content | ConvertFrom-Json) | ConvertTo-Json -Depth 30
```

UID ensure (`POST /uidEnsure`):
```powershell
$r = Invoke-WebRequest -Method Post -Uri "$base/uidEnsure" -Headers @{ Authorization = "Bearer $idToken" } -ContentType "application/json" -Body "{}"
$r.StatusCode
$r.Headers | Format-List
($r.Content | ConvertFrom-Json) | ConvertTo-Json -Depth 30
```

DriverData get (`POST /driverdataGet`):
```powershell
$r = Invoke-WebRequest -Method Post -Uri "$base/driverdataGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType "application/json" -Body "{}"
$r.StatusCode
$r.Headers | Format-List
($r.Content | ConvertFrom-Json) | ConvertTo-Json -Depth 60
```

Driving trip create (`POST /drivingTripCreate`):

Expected:
- If the deployed revision includes the route: **NOT 404**.
  - It may be `200` (success) or `400` (validation) depending on body.
- If you get `404` with `ROUTE_NOT_FOUND`, you are on the wrong service or an older revision.

Command:
```powershell
try {
  $r = Invoke-WebRequest -Method Post -Uri "$base/drivingTripCreate" -Headers @{ Authorization = "Bearer $idToken" } -ContentType "application/json" -Body "{}"
  $r.StatusCode
  $r.Headers | Format-List
  ($r.Content | ConvertFrom-Json) | ConvertTo-Json -Depth 30
} catch {
  # Invoke-WebRequest throws on non-2xx; capture status + body anyway.
  $_.Exception.Response.StatusCode.value__
  $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
  $body = $sr.ReadToEnd()
  $body
}
```

### Option 2: Use `curl.exe -i` (shows headers + body)

Handshake:
```powershell
curl.exe -i -X POST "$base/handshakeGet" -H "Authorization: Bearer $idToken" -H "Content-Type: application/json" --data "{}"
```

UID ensure:
```powershell
curl.exe -i -X POST "$base/uidEnsure" -H "Authorization: Bearer $idToken" -H "Content-Type: application/json" --data "{}"
```

DriverData get:
```powershell
curl.exe -i -X POST "$base/driverdataGet" -H "Authorization: Bearer $idToken" -H "Content-Type: application/json" --data "{}"
```

Driving trip create (success = NOT 404):
```powershell
curl.exe -i -X POST "$base/drivingTripCreate" -H "Authorization: Bearer $idToken" -H "Content-Type: application/json" --data "{}"
```

## How to diagnose “wrong backend” in 60 seconds

1) Run `handshakeGet` and record:
- `result.deployment.service`
- `result.deployment.revision`
- `result.deployment.functionTarget`

2) Run the failing call (`drivingTripCreate`) and record headers:
- `X-Trimsy-Service`
- `X-Trimsy-Revision`
- `X-Trimsy-Function-Target`

3) If `handshakeGet` and `drivingTripCreate` show different service/revision values:
- You are not hitting the same backend build.

## Client-side rules (TrimsyTrack)

- Do not invent “UID missing” from transport errors.
- Treat timeouts and `5xx` as retryable (backoff + jitter).
- Fail-fast only on deterministic, machine-classifiable errors (e.g. `ROUTE_NOT_FOUND`, protocol mismatch, safety mode).

## Operational next step

If prod fails any checks above:
- Deploy the current backend revision that includes `drivingTripCreate`, `uidEnsure`, and the DriverData empty-snapshot behavior.
- Re-run the verification commands and capture deployment identity fields for the release note.
