# START HERE — Shared Contract Bundle

This folder is a **copyable contract bundle**.

If you received this folder from another repo, your job is to:
1) Read it (start here)
2) Ensure your code matches it
3) Treat it as **authoritative** (don’t “adapt” it silently)

---

## What this bundle means (non-negotiable)

### Identity model

- **UID-only identity.** Firebase Auth `uid` is the only identity.
- **No profiles.** Do not implement profile onboarding, profile selection, profile switching, or any `profile*` endpoints.
- **Email is optional metadata**. Never use email as an account key.

### Provisioning gate

- Backend state must exist at `uid_state/{uid}`.
- Provisioning is **idempotent**: on first contact, the backend ensures `uid_state/{uid}` exists (via `handshakeGet` or `uidEnsure`).
- Clients must not treat missing provisioning state as a “UID missing” issue.

### Irreversible deletion

- Deleted UIDs are tombstoned at `deleted_uids/{uid}`.
- After tombstoning, all future requests must fail with `UID_DELETED`.
- Deletion endpoints: `uidDelete` / `uidDeleteCallable`.

### Startup handshake + protocol version

- Client calls `handshakeGet` at startup.
- Store returned `protocolVersion`.
- Include `clientProtocolVersion` in **every** post-handshake request body.

First-time (no DriverData yet) is a valid empty state:
- `driverdataGet` returns an empty DriverData v3 object when no snapshot exists yet.
- Clients should display this as “No data yet (first-time user).”, not as an error.

### App identity (required)

- `app_id` is required by contract for truth-creating writes.
- `app_instance_id` is optional (recommended).
- TrimsyTrack sync endpoints require `app_id=trimsytrack`.

**System identity lock:** this bundle is for **TrimsyTRACK**.
- Must never be confused with **TrimsyApp**.
- Must never silently change which routes/meaning are used.

Authoritative identity boundary: [TRIMSYTRACK_SYSTEM_IDENTITY.md](TRIMSYTRACK_SYSTEM_IDENTITY.md)

---

## What you must do in your repo

1) Copy the entire folder `shared_contract/` into your repo (keep filenames unchanged).

2) Run a quick drift scan:
- Search for any usage of: `profileId`, `profile.`, `profileCreate`, `profileGet`, `profileMediaSet`, `PROFILE_REQUIRED`, `profile_auth/`.
- If you find any, it’s drift. Remove it or raise it.

3) Confirm your client behavior:
- Calls `handshakeGet` on startup.
- Sends `clientProtocolVersion` after handshake.
- Sends `app_id` on writes.
- Handles hard failures: `UID_DATA_MISSING`, `UID_DELETED`.

4) Run your normal build/tests.

---

## Which document answers what

- **Authoritative truth**: `CODER_HANDOFF_PACKET_NO_REPO_LINK.md`
- Practical implementation handoff: `CODER_HANDOFF_PACKET.md`
- Startup sequence + required fields: `CLIENT_BACKEND_STARTUP_HANDSHAKE.md`
- Exported HTTP/callable surface: `BACKEND_EXPORTED_ENDPOINTS.md`
- Storage + payload rules: `BACKEND_STORAGE_AND_SYNC_PAYLOAD.md`
- Architectural contract summary: `BACKEND_CONTRACT.md`

---

## If you think something conflicts

Do **not** “patch around” conflicts. Escalate:
- Quote the exact line(s) that conflict
- Quote the exact endpoint / code path you think differs
- Decide which side is wrong and update the canonical source (not local forks)
