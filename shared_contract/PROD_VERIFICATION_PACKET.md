# Prod Verification Packet — TrimsyTrack stuck uploads / apiV1 route mismatch

This packet is intended for backend/oncall. It is safe to copy/paste into a ticket.

If you only run one thing, run **handshakeGet + drivingTripCreate** and compare deployment identity.

## Situation

TrimsyTrack uploads appear “stuck” when canonical writes cannot drain. In prod we observed:
- `POST /apiV1/drivingTripCreate` ⇒ HTTP `404` “Unknown route”
- `POST /apiV1/handshakeGet` ⇒ HTTP `200`
- `POST /apiV1/driverdataGet` ⇒ HTTP `200`

That combination strongly suggests a deployed revision/service mismatch (wrong base URL, older revision, or different service target).

## Contract requirements that must be live

- `driverdataGet`: first-time user with no snapshot ⇒ HTTP `200` with **empty DriverData v3** (raw object; no `{ok,result}` wrapper).
- `uidEnsure`: exists and returns `200` with stable `{ ok: true, result: { ensured, created, uid, serverTime*, deployment*, identity* } }`.
- Unknown route: `404` with `details.machineCode = "ROUTE_NOT_FOUND"`.
- `drivingTripCreate`: route must exist (proof = **NOT 404**; it may return `200` or `400` depending on body).

## Base URL

Production:
- `https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1`

## Verify with headers (PowerShell)

```powershell
$base = "https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1"
$idToken = "<PASTE_FIREBASE_ID_TOKEN>"
```

Handshake (captures headers + JSON):
```powershell
$r = Invoke-WebRequest -Method Post -Uri "$base/handshakeGet" -Headers @{ Authorization = "Bearer $idToken" } -ContentType "application/json" -Body "{}"
$r.StatusCode
$r.Headers | Format-List
($r.Content | ConvertFrom-Json) | ConvertTo-Json -Depth 30
```

Driving trip create (success = NOT 404):
```powershell
try {
  $r = Invoke-WebRequest -Method Post -Uri "$base/drivingTripCreate" -Headers @{ Authorization = "Bearer $idToken" } -ContentType "application/json" -Body "{}"
  $r.StatusCode
  $r.Headers | Format-List
  $r.Content
} catch {
  $_.Exception.Response.StatusCode.value__
  $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
  $sr.ReadToEnd()
}
```

## Verify with curl (always shows headers)

```powershell
curl.exe -i -X POST "$base/handshakeGet" -H "Authorization: Bearer $idToken" -H "Content-Type: application/json" --data "{}"
curl.exe -i -X POST "$base/drivingTripCreate" -H "Authorization: Bearer $idToken" -H "Content-Type: application/json" --data "{}"
```

## What to paste back

- `handshakeGet` JSON: `result.deployment.*`
- For `handshakeGet` and `drivingTripCreate`: response headers `X-Trimsy-Service`, `X-Trimsy-Revision`, `X-Trimsy-Function-Target`
- For `drivingTripCreate`: HTTP status + body

If `handshakeGet` and `drivingTripCreate` disagree on service/revision, TrimsyTrack is not hitting the same backend build.
