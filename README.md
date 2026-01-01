# TrimsyTRACK (Business Trip Tracking)

Android app (Kotlin) that stays idle until Android geofencing triggers a DWELL event at configured second-hand store locations.

Core behavior:
- Geofence DWELL (default 5 min) triggers a notification.
- User confirms → distance computed (Google Maps Routes API) → cached permanently → trip stored locally.
- All prompts (triggered/dismissed/left/ignored) are kept in **Today’s Travels** for correction.

## Build notes
- Set `MAPS_API_KEY` in `local.properties` (not committed):
  - `MAPS_API_KEY=YOUR_KEY`
- APIs used (serverless, direct HTTPS): Google Maps **Routes API** (distance) and optionally **Geocoding API** (manual address → lat/lng cached once).

## Play Store / privacy principles
- No foreground service.
- No GPS polling.
- Tracking is user-controlled via Settings toggle.
- Clear permission rationale before requesting background location.
