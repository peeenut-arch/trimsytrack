# Backend Contract — Canonical Truth Architecture

## Backend Posture

The backend is an enforcing authority, not a decision-maker.

It validates, records, and rejects.

It does not infer, guess, recommend, auto-complete, or correct user intent.

Any behavior not explicitly permitted by canonical documents is forbidden by default.

## Startup Gating (Handshake)

All clients must follow the startup handshake contract:

- Call `handshakeGet` on startup.
- Include `clientProtocolVersion` on every subsequent request body.

The backend enforces protocol compatibility and blocks syncing/writes with machine-readable `failed-precondition` errors.

See [docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md](docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md).

## App Identity (Required)

Clients must identify themselves in requests:

- `app_id` is required by contract (especially for truth-creating writes).
- `app_instance_id` is optional (recommended for diagnostics).

Some subsystems enforce stricter values:

- TrimsyTrack sync endpoints require `app_id=trimsytrack`.

System identity boundary (TrimsyTRACK vs TrimsyApp) is non-negotiable:
- See [TRIMSYTRACK_SYSTEM_IDENTITY.md](TRIMSYTRACK_SYSTEM_IDENTITY.md)

## Identity (UID-only)

- Firebase Auth `uid` is the **only identity**.
- There are no profiles, no `profileId`, and no profile onboarding endpoints.
- All user-owned backend data is scoped to `uid`.
- **Hard rule**: if an authenticated UID does not own a record → return **404**, never 403.

### Provisioning gate

The backend does **not** auto-create or repair user state.

- If `uid_state/{uid}` does not exist, requests fail with `UID_DATA_MISSING`.

### Irreversible deletion contract

Deletion is irreversible and must be enforced forever.

- Deletion writes a tombstone at `deleted_uids/{uid}`.
- After tombstoning, **all future requests** from that UID must fail with `UID_DELETED`.

Deletion endpoints:

- HTTP: `uidDelete`
- Callable: `uidDeleteCallable`

## Canonical Truth Rules

- Canonical IDs are globally unique, backend-assigned, immutable, never reused.
- No UPDATE or DELETE on canonical truth.
- Corrections happen only via compensating events.
- Derived values are never stored.
- Media is evidence only, never truth.

## Idempotency

Truth-creating endpoints are idempotent via a required `idempotencyKey`.

- Retries with the same idempotency key must replay the same canonical IDs.
- The backend must not create duplicate truth on retry/timeouts.

## SKU / Category+Number Identity Lock

Trimsy’s implemented SKU-like identity lock is **Category+Number**.

- Uniqueness is enforced within a UID.
- A product may only be assigned Category+Number once.
- Category+Number is an explicit user-provided anchor (not inferred).

## Lifecycle Preconditions Are Enforced, Not Stored

- The backend does **not** store lifecycle state, enums, or flags.
- Every canonical event declares (in code) its required prior facts.
- If preconditions are not satisfied, the event is rejected.

## No Silent Acceptance

Any request that omits required canonical references must fail explicitly.

The backend must never fill in missing canonical IDs, even if logically deducible.

## Read-Time Derivation Boundary

Derived values may only be computed at read-time via deterministic functions that do not persist results.

## Correction Visibility Guarantee

All corrections must preserve visibility of original facts.

Suppression, overwriting, or hiding of canonical truth is forbidden.

## Backend Self-Test Hook

The backend is allowed (and expected) to run invariant self-checks against stored truth and report violations **without mutating data**.

---

## Local PC Sync v1 (Phone → PC) — Contract of Truth

PC Sync v1 is a **local Wi‑Fi subsystem** (not a Firebase/Cloud backend feature).

Authoritative connectivity + correctness contract:
- `docs/TRIMSYAPP_TRIMSYPC_CONNECTIVITY.md`

Non-negotiables (for on-call clarity):
- Transport: TCP only.
- Port: fixed `43821`.
- Discovery: mDNS (`_trimsy-upload._tcp`).
- Correctness: temp + durable DB + atomic finalize; no overwrite; idempotent retries.

Repo enforcement:
- `tools/check_connectivity_rules.ps1`

## Forbidden Concepts

The backend must reject attempts to store:

- current stock level
- availability flags
- receipt totals
- product cost as a field
- margin
- is-sold booleans
- UI state
- OCR intermediates
- inferred locations
- guesses

If needed, compute at read-time (without persisting) or refuse.
