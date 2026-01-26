# Universal UID Identity + Startup Contract (Trimsy)

This is the **single source of truth** for how any client (Android / iOS / Web / PC / Electron) must connect to the Trimsy backend.

If you are implementing a client, follow this document exactly.

---

## Production defaults (Trimsy)

- Firebase project: `trimsy-d12de`
- Functions region (client-facing): `europe-north1` (Stockholm)
- Production HTTP base (apiV1):
  - `https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1`

> Note: Firebase Auth is a global service. Only the **functions** you call or trigger are regional.

---

## What changed (core truth)

- **Stable identity is Firebase Auth `uid`.**
- **Email is optional metadata** (may be `null`, missing, or blank).
- Clients must **never** assume an email exists.
- Clients must **never** use email as the “account key”.

### Account key rule (non‑negotiable)

- Your local “account namespace” key must be: `identity.uid`
- Examples of what must be keyed by `uid` (not email):
  - local profile store partitions / folders
  - cached documents
  - sync queues / outbox
  - background worker scopes
  - analytics “accountId” fields

Email is allowed only for:
- display in UI (if present)
- best-effort metadata cache
- optional business contact fields (if you actually need a contact email)

### Backend mapping model

Backend stores identity mapping:
- `profile_auth/{uid} -> { profileId }` (**authoritative**)


---

## Startup gates (strict order)

Every client must treat startup as backend-enforced gates:

1) **Handshake** (machine-only)
2) **Profile gating** (exists vs prompt-create)
3) **Law gating** (acceptance)
4) **Safety mode** (write blocking)

Do not write canonical truth unless all gates allow it.

---

## 1) Handshake (machine-only)

On startup, the client must call handshake exactly once:

- HTTP: `POST ${API_BASE}/handshakeGet`
  - Header: `Authorization: Bearer <Firebase ID token>`
- Callable: `handshakeGetCallable` (Firebase Functions SDK)

Where `API_BASE` is typically:
- Production: `https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1`
- Emulator: `http://127.0.0.1:5001/<project>/<region>/apiV1` (depending on your emulator setup)

Handshake returns:
- `protocolVersion` (number)
- `writesEnabled` (boolean)
- `safetyMode.enabled` + `safetyMode.reason` (safety mode state)
- `identity.uid` (string, required)
- `identity.email` (string|null, optional)
- `profile.exists` (boolean)
- `profile.profileId` (string|null)

### Required client behavior

- Store `protocolVersion` and include it on **every subsequent backend request body** as:

```json
{ "clientProtocolVersion": 1 }
```

Rule: every backend request body must include `clientProtocolVersion` **except** handshake.

### Identity requirements

- Treat `identity.uid` as the stable account identity.
- Treat `identity.email` as optional metadata only:
  - OK to show in UI if present.
  - OK to store as a best-effort cache.
  - Do **not** use as a key for profile storage, data folders, or account namespace.
- Your client must work correctly when:
  - `identity.email` is `null`
  - `identity.email` is missing
  - `identity.email` is an empty string

---

## 2) Profile gating

After handshake, branch on:

- `profile.exists == true` AND `profile.profileId` present → continue
- otherwise → show Create Profile onboarding UI (blocking)

Profile endpoints (all require `clientProtocolVersion`):
- `profileStatusGet` (does NOT create new profile)
- `profileCreate` (creates profile if missing; idempotent)
- `profileGet`
- `profileMediaGet`
- `profileMediaSet`

### Required universal flow

On every login (mobile, web, PC, Electron):

1) Call `handshakeGet`
2) If `profile.exists == true`:
   - Call `profileGet` and `profileMediaGet`
   - Cache them locally under **uid + profile scope**
   - Continue to law gating / sync
3) If `profile.exists == false`:
   - Show Create Profile onboarding (mandatory)
   - On completion, call `profileCreate`
   - Upload media using `profileMediaSet` (or pass media inside `profileCreate` if supported)
   - Then call `handshakeGet` again (or call `profileStatusGet`) to confirm `profile.exists == true`
   - Immediately call `profileGet` + `profileMediaGet` and proceed

---

## 3) Law gating

Before any snapshot/sync/canonical endpoints, the user must:
- Fetch law pack: `lawGet`
- Accept law: `lawAccept`

If any endpoint returns `LAW_ACCEPTANCE_REQUIRED`, show docs + acceptance UI and block writes.

---

## 4) Safety mode (write blocking)

Handshake returns `writesEnabled` and safety mode details.

- If safety mode is enabled, treat system as **read-only**.
- Truth-creating endpoints must not be attempted until safety mode is disabled.
- Idempotency replays may still succeed (backend-controlled).

---

## Required error handling

General rules:
- Don’t loop non-retryable failures.
- Only retry when the backend says it’s safe.

Common behaviors:
- `401 unauthenticated` → refresh token / sign in again, retry once
- `412 failed-precondition` → route to blocking gate UI based on `machineCode`
- `429 resource-exhausted` → obey `Retry-After` / `retryAfterSeconds`

### Machine codes (canonical list)

- `HANDSHAKE_REQUIRED` → call handshake, then retry once
- `PROTOCOL_MISMATCH` → require app update
- `PROFILE_REQUIRED` → prompt create profile
- `ACCOUNT_CONFLICT` → sign out and sign in again
- `LAW_ACCEPTANCE_REQUIRED` → show law UI, accept
- `SAFETY_MODE_WRITE_BLOCKED` → read-only mode
- `VALIDATION_FAILED` → mark the specific item blocked (no auto retry)

Legacy/deprecated:
- `EMAIL_REQUIRED` → older backends only; modern clients must not assume email is required

---

## Idempotency (mandatory for creates)

To guarantee zero duplicates on retry/timeouts, clients must send a stable idempotency key for truth-creating endpoints.

Required:
- `productCreateCallable`: `idempotencyKey`
- `receiptCreateCallable`: `idempotencyKey`
- `receiptRowCreateCallable`: `idempotencyKey`

Behavior:
- Retries with the same key replay the same IDs.

---

## Reference: handshake response shape

HTTP envelope:

```json
{
  "ok": true,
  "result": {
    "protocolVersion": 1,
    "writesEnabled": true,
    "safetyMode": {
      "enabled": false,
      "reason": null
    },
    "identity": {
      "uid": "firebaseUidHere",
      "email": "user@example.com"
    },
    "profile": {
      "exists": true,
      "profileId": "profile_abc123"
    }
  }
}
```

Notes:
- `identity.email` may be `null` or an empty string depending on client JSON parsing.
- Clients must be resilient to missing fields they don’t need.
- Clients must treat `identity.uid` as the only stable account key.

---

## Copy/paste prompt (for any implementer)

> Implement Trimsy startup using UID-authoritative identity.
> - Call handshake on startup and persist `protocolVersion`.
> - Treat `identity.uid` as stable identity and as the account key.
> - Treat `identity.email` as optional metadata; never block on missing email.
> - Gate app features behind profile creation, law acceptance, and safety mode.
> - Include `clientProtocolVersion` in every request body after handshake.
> - Handle machine codes as specified in this doc.
