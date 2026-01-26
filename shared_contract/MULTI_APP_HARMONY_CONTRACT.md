# Multi-App Harmony Contract — One Backend, Zero Collateral Damage

This contract exists to ensure **TrimsyApp**, **TrimsyTrack**, and **TrimsyPC** can share one backend without one app’s solution becoming another app’s problem.

This is a *compatibility law*: changes must be safe across apps, safe across redeploys, and safe across time.

## Gold Standard

- **One backend, shared identity, explicit namespaces.**
- **Additive evolution by default.** Removing or rewiring shared surfaces is forbidden without a deprecation plan.
- **Deterministic failures.** When something is wrong (misdeploy/protocol mismatch/safety mode), the backend must say so clearly and consistently.

## Definitions

- **App**: A client with its own release cadence (TrimsyApp / TrimsyTrack / TrimsyPC).
- **Surface**: A route, callable, Storage path, or other integration point.
- **Shared surface**: Anything two or more apps depend on.
- **Owned data**: Data primarily authored by one app.

## Laws (Backend)

### 1) Canonical Identity Is Always Firebase Auth UID

- The only canonical identity is `request.auth.uid` (or the UID verified from the ID token).
- Clients must never choose or mint their own UID.
- Provisioning must be **idempotent** and safe on login (`uid_state/{uid}` must exist).

### 2) Namespacing Prevents Collisions

Every persisted thing must have a clear namespace boundary:

- Firestore collections must be app-owned or explicitly shared.
- Cloud Storage object paths must be namespaced (example patterns):
  - `receipt_media/v1/<uid>/...` (TrimsyApp-owned)
  - `evidence/v1/<uid>/...` (TrimsyTrack/TrimsyApp-owned)

No new feature is allowed to store data in an ambiguous, shared “misc” location.

### 3) Shared Surfaces Are Stable

If a route/callable/path is published as part of the shared contract:

- It must not be removed.
- It must not be repurposed.
- It must not silently change request/response meaning.

Changes must be either:

- **Additive** (new optional fields, new routes, new versions), or
- **Versioned** (e.g. `v2` path or protocol bump), or
- **Deprecated** with a defined sunset schedule.

### 4) Deprecation Process (Required)

To remove or rewire any shared surface:

1. Add a replacement surface.
2. Support both for a minimum compatibility window.
3. Ship updated clients.
4. Only then deprecate/disable the old surface.

The backend must return a deterministic error for deprecated calls (no silent behavior changes).

### 5) Errors Must Be Machine-Classifiable

- Backend errors must use a stable `details.machineCode` when relevant.
- Unknown routes must return `ROUTE_NOT_FOUND`.
- Protocol mismatch must return `PROTOCOL_MISMATCH`.

Clients must not invent “UID missing” from ambiguous failures.

### 6) Redeploys Must Be Surviveable

During deploys/cold starts, clients may see transient errors. The system must be designed so this does not cause data loss or confusing state.

Backend requirements:

- Provide deployment metadata for diagnosis (revision/service/time) either via headers and/or handshake payload.
- Avoid returning “null means missing identity” for first-time states.

Client requirements:

- Retry transient failures (`5xx`, timeouts) with backoff + jitter.
- Fail fast only on deterministic states (protocol mismatch, safety mode, explicit permission errors).

### 7) No Cross-App Data Destruction

- An app must not delete or mutate another app’s owned data except via explicit shared, contract-defined flows.
- Account deletion is the only global “delete everything” operation and must be explicit and irreversible.

## What “Separation” Means Here

We do **not** split into separate backends.

We separate by:

- Namespaces (collections, storage paths)
- Protocol/version gates
- Clear ownership rules
- Stable shared surfaces

## Minimum Required Shared Surfaces

- Identity + handshake (startup contract)
- DriverData read surface (cross-app read is allowed)

The precise list of exported endpoints lives in the shared contract docs.

## Enforcement

- Any backend change must update the shared contract docs if it changes a shared surface.
- Any change that removes/rewires a shared surface must include a deprecation section in the PR description.
