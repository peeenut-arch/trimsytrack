# TrimsyTRACK Backend “No‑Touch” / Protected Surfaces Contract

This document defines what the backend **must not break** for TrimsyTRACK.

Important reality check:
- The backend **cannot** directly touch Android local storage, WorkManager workers, Room DB files, or on‑device save locations.
- What we *can* and *must* guarantee is: the backend will not break TrimsyTRACK by changing or removing the **network surfaces** and **server-side schemas/semantics** that TrimsyTRACK depends on.

## 0) Paste‑ready message for backend coders

**TrimsyTRACK No‑Touch Rule (Backend):**

TrimsyTRACK is a shipped client with durable outbox + driverdata snapshot sync. You must treat the following as **protected surfaces**:

- Routes: `handshakeGet`, `driverdataGet`, `driverdataPut`, `drivingTripCreate`.
- Their request/response semantics (including required field types).
- Their deterministic failure behavior (machine codes / 412 gates).
- TrimsyTRACK-owned Firestore namespaces used by those routes.

Rules:
- No removals, no repurposing, no silent schema changes.
- Only additive changes, or versioned changes (new route or protocol bump + explicit gate + deprecation window).
- No cross-app mutation: TrimsyApp/TrimsyPC must not be able to write TrimsyTRACK truth; enforce via `app_id`.

Enforcement:
- Any PR touching a protected surface must include updated contract docs + tests.
- Unit gate: `BACKENDTRIMSY/functions: npm run test:unit` must stay green.

## 1) Definition: “Touch” (for backend changes)
A backend change is considered a forbidden “touch” if it does any of the following without a versioned migration plan:
- Removes a TrimsyTRACK-used API route.
- Changes required request fields or field types for a TrimsyTRACK-used route.
- Changes response envelope meaning (`ok/result/error`) or changes success semantics.
- Changes error codes/machine codes in a way that alters client behavior.
- Moves/rewires Firestore storage locations for TrimsyTRACK-owned data.
- Adds server-side writes that mutate TrimsyTRACK-owned data outside contract-defined flows.

Non-breaking clarifications:
- Backend can add internal logic, indexes, or additional documents **as long as** it does not change the meaning/contract.
- Backend must not assume anything about Android implementation details (Room schema, WorkManager timing, file paths).

Allowed by default:
- Additive changes (new optional fields, new routes, new collections under new namespaces).

## 2) Protected TrimsyTRACK surfaces (must remain stable)
These are the **minimum** backend surfaces TrimsyTRACK depends on today:

### 2.1 API routes (protected)

| Surface | Type | Purpose | Backward compatibility requirement |
|---|---|---|---|
| `handshakeGet` | read-ish bootstrap | Auth/UID/protocol/safety gating metadata | Must stay callable; additive only |
| `driverdataGet` | read | Restore driverdata snapshot | Must accept current schemaVersion(s) |
| `driverdataPut` | write | Upload driverdata snapshot | Must preserve idempotency + schema rules |
| `drivingTripCreate` | write | Canonical truth creation for trips | Must preserve validation + idempotency |

(TrimsyTRACK also uses `uidEnsure` as an identity bootstrap in some flows; treat it as protected if used by clients.)

### 2.2 Stability rules for these routes
- Must not be removed.
- Must not be repurposed.
- Request/response meaning must not silently change.
- Breaking changes require **versioning** (e.g. new route `v2` or `clientProtocolVersion` bump + explicit gate).
- Avoid opaque 5xx: protected routes must return a parseable envelope and deterministic machine code; if a 500 occurs it must include `error.details.machine=INTERNAL_ERROR` and a `clientRequestId` when available.

### 2.3 Required request metadata
For truth-creating / write-like operations:
- `clientProtocolVersion` must be accepted for the supported range.
- `app_id` must be present and enforced.

## 3) App isolation (“no cross-app collateral damage”)
- Each write-like route must validate `app_id` and reject calls from the wrong app.
- TrimsyTRACK routes must not allow TrimsyApp/TrimsyPC to write TrimsyTRACK-owned truth.

This is already partially enforced for `drivingTripCreate` and should be enforced consistently on all write-like routes.

## 4) Enforcement (how we make this real)
A markdown contract is not enough; we enforce with gates:

### 4.1 Automated tests (must pass before deploy)
Backend tests must assert:
- Protected routes do not regress to `ROUTE_NOT_FOUND`.
- Cross-app restrictions exist (wrong `app_id` is rejected deterministically).
- Responses are still parseable by TrimsyTRACK (envelope stability).

Implemented in this repo:
- Contract gate test: `BACKENDTRIMSY/functions/src/__tests__/apiV1_contract.unit.test.ts`
- CI-friendly command: `BACKENDTRIMSY/functions: npm run test:unit`

### 4.2 Protocol + preflight gates
- Breaking changes require a `clientProtocolVersion` bump and a deterministic failure (`PROTOCOL_MISMATCH` / preflight gate) for old clients.
- Safety mode/write gating must remain deterministic.

### 4.3 Change control
Any PR that modifies a protected route must:
- Update the shared contract docs.
- Include or update tests proving compatibility.
- If breaking: include a versioned replacement + deprecation window.

## 5) References
- Multi-app compatibility law: [shared_contract/MULTI_APP_HARMONY_CONTRACT.md](shared_contract/MULTI_APP_HARMONY_CONTRACT.md)
- Preflight / law gate behavior: [BACKEND_PREFLIGHT_GATE_SPEC.md](BACKEND_PREFLIGHT_GATE_SPEC.md)
- Shared backend contract: [shared_contract/BACKEND_CONTRACT.md](shared_contract/BACKEND_CONTRACT.md)
