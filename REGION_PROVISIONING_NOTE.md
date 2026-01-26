# Region provisioning note (Trimsy)

The canonical startup contract in `UNIVERSAL_UID_IDENTITY_STARTUP_CONTRACT.md` declares production Functions region as `europe-north1`.

As of 2026-01-11, deploying Firebase Functions to `europe-north1` fails from this workspace with:

- `HTTP Error: 403, Permission denied on 'locations/europe-north1' (or it may not exist)`

Current live deployment (verified via `firebase functions:list`) is in `europe-west1`.

## What this means

- Clients can only successfully call the backend in regions where functions are actually deployed.
- Until `europe-north1` is provisioned/allowed for this project, local dev and production traffic must target the currently deployed region (`europe-west1`).

## To move to europe-north1

One of these must be true (outside this repo):

- The project/org allows Cloud Functions in `europe-north1`.
- Required Google Cloud APIs and permissions are enabled for that region.
- App Engine app exists (Firebase CLI sometimes requires it for new function deploy locations).

Once that is fixed:

1) Change `setGlobalOptions({ region: ... })` in `BACKENDTRIMSY/functions/src/index.ts` to `europe-north1`.
2) Update local config (`local.properties`) to point to `europe-north1`.
3) Deploy: `firebase deploy --only functions`.
4) Verify: `firebase functions:list` shows `europe-north1`.
