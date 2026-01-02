# TrimsyTRACK – Build Handover (Dec 29, 2025)

## Goal
Android (Kotlin) app that stays idle and relies on Android system geofencing to wake it only when the user **dwells** at configured second-hand store locations (default **5 minutes**) → then prompts the user to confirm a business trip. Distance is computed **only after confirmation** (Google Maps **Routes API**) and cached permanently to avoid repeated billing.

## Non‑negotiables (battery + compliance)
- No continuous services; no foreground service.
- No GPS polling / no frequent location updates.
- Use Android geofencing with DWELL + loitering delay.
- Transparent, user-controlled, Play Store compliant.

## Multi-app isolation (shared auth + shared backend)
- This app's fixed `app_id` is **trimsytrack**.
- The other app's fixed `app_id` is **trimsyapp**.
- Every backend request must include `Authorization` (Firebase ID token), `X-App-Id`, and `X-Profile-Id`.
- In this repo, `X-App-Id` is compiled into the APK via `BuildConfig.APP_ID`.
  - Default: `trimsytrack`
  - Override at build time: `./gradlew assembleDebug -PAPP_ID=trimsytrack`

## Current implementation status (in repo)
- Project scaffolding + Compose UI theme (black minimal).
- Room database schema for stores, prompts, trips, attachments (foundation).
- DataStore settings with user-configurable dwell/radius/limits (foundation).
- Geofence rotation via WorkManager; receiver creates prompt events + notifications.
- Today’s Travels screen lists today’s prompts + today’s trips.
- Trip confirmation screen supports:
  - "Continue from last store" (same day & within 10 km)
  - "Use current" (last known location)
  - Computes distance via Routes API only on confirmation

## Background flow
1. User enables Tracking (Settings toggle) → app requests location/background location + notifications (as applicable).
2. Geofence sync worker loads active region stores and registers up to `maxActiveGeofences`.
3. Android triggers geofence DWELL after `dwellMinutes`.
4. App writes a PromptEvent and shows a notification.
5. User opens the prompt → confirms trip.
6. App computes distance (Routes API) and caches it, then writes the Trip.
7. Prompt status becomes CONFIRMED.

## Prompt quality rules
Enforced at geofence DWELL handling:
- Per store per day (default true) via latest prompt lookup.
- Daily limit via `dailyPromptLimit`.
- Suppression after dismissal via `suppressionMinutes`.
- Auto-dismiss on EXIT (prompt becomes LEFT_AREA).

## Geofence scalability by region
- Stores are loaded from `app/src/main/assets/regions/{region}.json`.
- Active geofences are rotated daily with a deterministic offset to stay within platform limits.

## Distance calculation & caching
- Provider: Google Maps **Routes API** (`directions/v2:computeRoutes`).
- Cached in Room (`distance_cache`) by quantized start/dest ($1e-5$ degrees) + travel mode.
- Computed only after user confirmation.

## Verification checklist (build phase)
- Doze: leave device idle; confirm app does not run until DWELL.
- Reboot: boot receiver schedules geofence sync.
- Missed prompt recovery: prompt events remain in Today’s Travels.
- Spam prevention: per-store-per-day and suppression respected.
- Billing: repeated same start/dest uses cached distance (no repeat API calls).

## Remaining build items (next)
- Attachments (photos/PDFs) on trip detail; persistable URIs.
- Manual address start option (geocode once & cache).
- Saved locations + Home start location.
- Merge/linking UI for missed prompts into a "run" (manual, suggested).
- Settings UI: sliders/inputs for timers & limits, not just read-only display.
