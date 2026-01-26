# Client Backend Startup Handshake (All Apps)

This document is the single source of truth for how every client (mobile/web/PC/Electron) connects to the Trimsy backend safely.

## Startup contract

The backend enforces a single, strict startup contract:

1) **Handshake** (machine-only)
2) **Law gating** (acceptance)
3) **Safety mode** (write blocking)

Clients should treat these as backend-enforced gates and react only to backend machine-codes.

## Non‑Negotiables

- Backend is the enforcer. Clients must not write canonical truth unless the backend allows it.
- Clients must not start sync/writes until handshake + law gating pass.
- Do not guess, do not auto-fix, do not retry non-retryable failures.

## 1) Handshake (machine-only)

On startup, the client must call:

- `POST ${API_BASE}/handshakeGet` (Authorization: `Bearer <Firebase ID token>`)

The response includes:
- `protocolVersion` (number)
- `writesEnabled` and safety mode state
- `identity.uid` (Firebase UID; stable identity)
- `identity.email` (normalized email; optional metadata)
- `hasData` (boolean; always `true` if handshake succeeds)

UID-only model note:
- There are no profiles. Handshake does not return `profile.*`.
- Handshake is rejected if this UID is not provisioned (`UID_DATA_MISSING`) or has been deleted (`UID_DELETED`).

The client must store `protocolVersion` and include it on **every subsequent backend request body** (HTTP routes and callable payloads) as:

- `clientProtocolVersion: <protocolVersion>`

**Rule:** every backend request body must include `clientProtocolVersion` except `handshakeGet`.

### Identity rule (must implement)

Trimsy backend identity is **UID-anchored**.

- `identity.uid` is the stable identity across devices.
- `identity.email` is optional metadata and may be missing/null.

### Protocol gating behavior

If any endpoint returns `412 failed-precondition` with:
- `error.details.machineCode == "HANDSHAKE_REQUIRED"`

Client behavior:
- Stop all sync/writes.
- Call `handshakeGet` once.
- Retry the original request once with the returned `clientProtocolVersion`.

If any endpoint returns `412 failed-precondition` with:
- `error.details.machineCode == "PROTOCOL_MISMATCH"`

Client behavior:
- Stop all sync/writes immediately.
- Call `handshakeGet` once.
- If still mismatched, require an app update (do not retry writes).

## 2) Law gating (acceptance)

Before any canonical truth endpoints, the user must:
- Fetch law pack: `lawGet`
- Accept law: `lawAccept`

If any canonical endpoint returns `412 failed-precondition` with:
- `error.details.machineCode == "LAW_ACCEPTANCE_REQUIRED"` → show docs + acceptance UI and block syncing

## 3) Safety mode (write blocking)

Handshake returns `writesEnabled` and safety mode details.

- If safety mode is enabled, the client must treat the system as **read-only**.
- Canonical truth creation endpoints must not be attempted until safety mode is disabled.
- Idempotency replays may still succeed (backend-controlled).

## Required error handling (must implement)

- `401 unauthenticated` → show “Sign in again”, refresh token, retry once.
- `412 failed-precondition` → do not loop; go to the blocking gate UI based on `machineCode`.
- `400 invalid-argument` / `422 validation` → mark the specific pending operation blocked (no auto retry).
- `429 resource-exhausted` → obey `Retry-After`/`retryAfterSeconds`, show countdown, stop retry loops.

## Idempotency (must use for creates)

To guarantee **0 duplicates** during retries/timeouts, clients must send stable idempotency keys for truth-creating endpoints.

Required:
- `productCreateCallable`: `idempotencyKey`
- `receiptCreateCallable`: `idempotencyKey`
- `receiptRowCreateCallable`: `idempotencyKey`

Behavior:
- Retries with the same `idempotencyKey` replay the same IDs; no duplicates are created.

## Client UX rule (blocking)

Whenever the backend indicates user attention is required, show a blocking notification:

Title: **Action required to protect your data**

Primary actions by machine code:
- `HANDSHAKE_REQUIRED` → Reconnect
- `PROTOCOL_MISMATCH` → Update app
- `ACCOUNT_CONFLICT` → Sign out and sign in again
- `LAW_ACCEPTANCE_REQUIRED` → Review & accept
- `SAFETY_MODE_WRITE_BLOCKED` → Read-only mode
- `UNAUTHENTICATED` → Sign in again

UID-only gates:
- `UID_DATA_MISSING` → account is not provisioned; stop sync/writes and show a provisioning-required UI.
- `UID_DELETED` → account is permanently deleted; stop all requests forever.

Legacy/deprecated:
- `EMAIL_REQUIRED` → older backends only; modern clients should not assume email is required

## Branding/media (not part of startup gating)

The UID-only backend model does not require any account media document to exist.

- Clients must not block startup on account media.
- If branding/media is implemented later, it must remain optional presentation data and must never block truth writes.
