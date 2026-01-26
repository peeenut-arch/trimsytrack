# 2‑day real‑use validation checklist

This repo includes an interactive end‑to‑end smoke test script that captures log evidence for:
- backend write after a user action (`driverdataPut 200`)
- backend restore after restart (`driverdataGet 200`)
- reinstall safety (no unauthenticated retry loop)
- re-login after reinstall restores again (`handshakeGet 200` + `driverdataGet 200`)

## During the next ~2 days

Use the app normally.

Try to include these real‑world conditions at least once:
1. **Bad network**: turn on airplane mode for 5–10 minutes, then turn it off.
2. **Phone restart**: reboot the phone once.

(These help validate WorkManager + auth + retries behave under real conditions.)

## After ~2 days: run the saved smoke test

From VS Code:
- Run Task: **Sync smoke test (restart + reinstall + relogin)**

Or from a PowerShell terminal at repo root:
- `powershell -ExecutionPolicy Bypass -File .\tools\smoke_sync_check.ps1 -DoRestart -DoReinstall`

## When you come back (exact commands we’ve been using)

Run the full reinstall + relogin flow:
- `powershell -ExecutionPolicy Bypass -File .\tools\smoke_sync_check.ps1 -DoRestart -DoReinstall`

If you want a quicker check first (restart only):
- `powershell -ExecutionPolicy Bypass -File .\tools\smoke_sync_check.ps1 -DoRestart`

Optional sanity checks:
- Build/install/launch on device: `powershell -ExecutionPolicy Bypass -File .\tools\build_install_launch.ps1`
- Verify installed APK is TrimsyTRACK: `powershell -ExecutionPolicy Bypass -File .\tools\verify_installed_apk.ps1`

Where to grab evidence:
- Logs are written under `tmp\sync_check\<timestamp>\`

Backend safety net (already saved locally):
- Backend repo has branch `backup/2026-01-23` and tag `backup-2026-01-23`.
- Offline restore file: `tmp\git_bundles\BACKENDTRIMSY_backup-2026-01-23.bundle`

### What it will prompt you to do

1. **ACTION STEP**: create/save something in the app, then press Enter.
2. **RESTART STEP**: automatic (force-stop + relaunch).
3. **REINSTALL STEP**: type `YES` to uninstall + reinstall.
4. **RE-LOGIN STEP**: sign in again, then press Enter.

### What “success” looks like

In the printed output folder under `tmp/sync_check/YYYYMMDD_HHMMSS/`:
- `02_after_action_filtered.txt` contains `driverdataPut` with HTTP `200`.
- `03_after_restart_filtered.txt` contains `driverdataGet` with HTTP `200`.
- `04_after_reinstall_filtered.txt` contains **no** `401/403/UNAUTHENTICATED` spam (it may be empty and that’s OK).
- `05_after_relogin_filtered.txt` contains `handshakeGet 200` and `driverdataGet 200`.

Trip truth creation expectations:
- `drivingTripCreate` should normally be HTTP `200`.
- A transient `5xx` can happen in the real world; it is acceptable only if it is clearly retriable (e.g. `kept=true retriable=true`) and you later see `drivingTripCreate http=200` in the same run (meaning the outbox drained).
- A `4xx` on `drivingTripCreate` is a regression (non-retriable / likely contract mismatch) unless explicitly version-gated.

If any file shows `401`, `403`, or `UNAUTHENTICATED`, that’s a regression: share the folder and we’ll trace which worker did it.
