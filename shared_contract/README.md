# Shared Contract Bundle

Start with `START_HERE.md`.

This folder is a **vendorable contract bundle** intended to be shared across:
- TrimsyApp
- TrimsyTrack
- TrimsyPC

It contains **copies** of the canonical backend contract docs from this repo’s `docs/` folder.

## Files

- `START_HERE.md`
- `CODER_HANDOFF_PACKET.md`
- `CODER_HANDOFF_PACKET_NO_REPO_LINK.md`
- `BACKEND_CONTRACT.md`
- `BACKEND_EXPORTED_ENDPOINTS.md`
- `CLIENT_BACKEND_STARTUP_HANDSHAKE.md`
- `BACKEND_STORAGE_AND_SYNC_PAYLOAD.md`

## How to consume (pick one)

### 1) Git subtree (recommended)

In the app/PC repo, vendor this folder as a subtree (so the app repo has its own copy).

### 2) Copy + update script

Copy this folder into the app/PC repo (e.g. `contracts/`) and periodically refresh it.

### 3) Submodule

If you prefer a submodule, you can add the backend repo as a submodule and point readers at this folder.

## Updating this bundle

Run from repo root:

- `powershell -NoProfile -File tools/update_shared_contract.ps1`

This overwrites the files in `shared_contract/` from `docs/`.

## Ground rules (contract)

- Identity is **UID-only** (no profiles).
- Clients MUST call `handshakeGet` on startup and send `clientProtocolVersion` afterward.
- Clients MUST identify app via `app_id` (required for truth-creating writes; recommended for all requests).
