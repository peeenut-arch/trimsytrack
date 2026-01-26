# Trimsy coder handoff packet

Docs extracted from commit: 1424fa3

Tomorrow restart keyword: checkpoint

## Send this first (TL;DR)

- Single source of truth: `docs/TRIMSY_SYSTEM_CONTRACT.md` (system contract) + `docs/BACKEND_EXPORTED_ENDPOINTS.md` (real deployed surface)
- Startup gate (all clients): call `handshakeGet` on startup, then include `clientProtocolVersion` on every request body
- Write gate: include `app_id` on all truth-creating writes (`trimsyapp` / `trimsytrack` / `trimsypc`)
- Account deletion: call `uidDelete` to tombstone UID + purge UID-keyed data (irreversible)
- Hard app restrictions:
  - `drivingTripCreate` = TrimsyTrack-only (`app_id=trimsytrack`)
  - `receiptCreate` = TrimsyApp-only (`app_id=trimsyapp`) and must include `drivingTripId`
  - TrimsyPC must not create purchase receipts

## Backend snapshot verification (MANDATORY)

This handoff packet is only meaningful if everyone is talking about the **same backend folder** and the **same git commit**.

Before discussing “which endpoints exist”, each person MUST paste:

- The on-disk backend folder path they are using (example: `C:\...\BACKENDTRIMSY\functions` or some other repo root)
- The output of these commands (run **from that backend folder**):

```bash
git rev-parse HEAD
git status -sb
git rev-parse --show-toplevel

# Locate the HTTP gateway router
git grep -n "export const apiV1" -- functions/src/index.ts

# Prove route presence/absence (add/remove greps as needed)
git grep -n "route === 'driverdata" -- functions/src/index.ts
git grep -n "route === 'drivingTripCreate'" -- functions/src/index.ts
git grep -n "route === 'saleCreate'" -- functions/src/index.ts
git grep -n "route === 'ledgerEntryCreate'" -- functions/src/index.ts
git grep -n "route === 'accountingVerificationCreate'" -- functions/src/index.ts
```

Why: there are known layout variants in the Trimsy ecosystem:

- **Monorepo layout**: `functions/` is a folder inside the main repo (same `git rev-parse HEAD` as repo root).
- **Split-backend layout**: `functions/` is its own git repository (its `git rev-parse HEAD` is different from the surrounding repo).

If two people report different route lists, they are on different backend snapshots. Do not argue about “the backend” until the folder path + commit hash are aligned.

## TrimsyTrack backend sync intake (REQUIRED INPUTS)

To set up the correct backend sync contract for TrimsyTrack (and to port any missing endpoints into this canonical backend), TrimsyTrack must provide the following in one message.

### A) Track repo snapshot (so we can reproduce)

- Track repo: on-disk path + `git rev-parse HEAD`
- Which app flavor/build is used for backend sync (debug/release), and where base URL is configured (e.g. `BuildConfig.BACKEND_API_BASE`).

### B) How Track calls the backend (plumbing)

- Base URL used (exact string) + environment switching rules (emulator vs prod).
- Auth mechanism:
  - HTTP: confirm `Authorization: Bearer <Firebase ID token>` header, and where token is retrieved/refreshed.
  - Or Callable SDK: callable function names used + how app selects region/project.

### C) Exact backend call inventory (no hand-waving)

For every backend call Track performs (or expects to perform), provide:

- Route/callable name (string literal)
- Code pointer: file path + function/method name where it is called
- Request JSON schema (example payload)
- Response JSON schema (example payload)
- Error handling expectations (retriable vs fatal)
- Idempotency behavior (which key, how generated, how retries behave)

### D) “100% complete sync” definition (what must be possible)

TrimsyTrack must state what “complete” means in terms of reads/writes:

- Writes required: create trip, add/edit stops, rename locations, attach metadata, etc.
- Reads required for other clients:
  - What TrimsyApp needs (trip list? stop list? location names?)
  - What TrimsyPC needs (same) and desired query shape (range by time, paging, deltas, etc.)

### E) Minimal test plan

Provide a single manual/automated test sequence that proves sync end-to-end:

- Preconditions (signed in, law accepted, handshake done)
- Steps (trip created -> visible in reads -> consumed by receipt flow, etc.)
- Expected outputs (IDs, counts, specific fields)

Once A–E are provided, we will:
- Implement/port missing endpoints into the canonical backend router (`apiV1`)
- Update the exported endpoint docs to match the router surface
- Keep this document accurate and drift-proof

## Coverage note (implemented vs planned)

This section describes the endpoint surface for the **specific backend snapshot** you are running.
If your router (`functions/src/index.ts`) shows a different route list (e.g. `driverdata*`/`drivingTripCreate` vs `sale*`/`ledgerEntryCreate`), you are on a different backend commit/repo — run the commands in “Backend snapshot verification (MANDATORY)” above and update docs accordingly.

These endpoints are **implemented in this backend workspace** (Functions export surface):
- Product + acquisition: `productCreate`, `productSetCategoryNumber`, `productSetWeight`, `productSetStorageLocation`, `receiptCreate`, `receiptRowCreate`, `productCostAllocate`
- Sales + bookkeeping: `saleCreate`, `saleReceiptLink`, `ledgerEntryCreate`, `accountingVerificationCreate`
- Storage + ops/diagnostics: `storage*`, `invariantsOpen`, `opsGetSafetyMode`, `opsSetSafetyMode`
- TrimsyTrack sync surfaces: `driverdataGet`, `driverdataPut`, `drivingTripCreate`
- Identity + deletion: `handshakeGet`, `lawGet`, `lawAccept`, `lawContractGet`, `uidDelete`

These are **mentioned in docs but not implemented here yet** (do not build clients against them unless you add them first):
- Driving journal: `drivingTripCreate` (receipt linking is derived in TrimsyApp; no backend link endpoint)
- Finalize snapshots + receipt media/snapshot endpoints

(Note: `drivingTripCreate` is implemented/exported in this workspace. Trip↔receipt linking is derived by TrimsyApp and stored on `receiptCreate` via `drivingTripId`.)

---

## Contract mode (read this as a spec)

This handoff is intended to be a **working contract** between:
- TrimsyApp (Android)
- TrimsyTrack (Android)
- TrimsyPC (desktop)
- Backend (Firebase Functions + Firestore)

### Normative keywords

- **MUST** / **MUST NOT**: required for correctness.
- **SHOULD** / **SHOULD NOT**: strongly recommended; deviations require explicit agreement.
- **MAY**: optional.

### Status markers (to prevent false certainty)

Every rule below is labeled as:
- **ENFORCED**: backend rejects violations today.
- **REQUIRED (NOT ENFORCED YET)**: contract requirement; clients MUST follow now, backend enforcement will be added later.
- **PLANNED (NOT AVAILABLE)**: mentioned in docs but endpoint/behavior not implemented in this workspace.

### Canonical request envelope (all clients)

Except `handshakeGet`, every backend request body MUST include:
- `clientProtocolVersion` (**ENFORCED**) — obtained from `handshakeGet`.
- `idempotencyKey` (**ENFORCED** for truth-creating endpoints) — stable across retries.
- `app_id` (**REQUIRED (NOT ENFORCED YET)**; **ENFORCED** for TrimsyTrack sync routes) — one of: `trimsyapp`, `trimsytrack`, `trimsypc`.

HTTP gateway additionally requires:
- `Authorization: Bearer <Firebase ID token>` (**ENFORCED**)

### Canonical IDs clients MUST persist locally

If a client creates or receives one of these IDs, it MUST persist it durably (so retries/reinstalls do not fork truth):
- `receiptId` (backend receipt id)
- `receiptRowId` (backend receipt row id)
- `productId` (backend product id)
- `categoryNumberId` (SKU lock id; deterministic)
- `placementId` (product placement event id)
- `weightId` (weight event id)
- `allocationId` (cost allocation event id)
- `saleId` (backend sale id)
- `saleReceiptId` (backend sale-receipt link id)
- `ledgerEntryId` (backend ledger entry id)
- `verificationId` (backend accounting verification lock id)

Recommended stable scope key:
- `scopeId` SHOULD be the `saleId` for sale bookkeeping scopes.

---

## TrimsyApp compliance checklist (onboarding)

Note: The TrimsyApp Android source is **not** present in this backend workspace, so we cannot automatically verify its implementation here.
Use this checklist as a go/no-go before shipping.

### A) Startup gating (must pass)

- Call `handshakeGet`/`handshakeGetCallable` once on startup.
- Persist `protocolVersion` and include `clientProtocolVersion` on **every** subsequent request body.
- Implement law gating UX: call `lawGet` then `lawAccept` and block truth writes until accepted.
- Respect safety mode: if handshake returns `writesEnabled=false` or `safetyModeEnabled=true`, block truth writes.

### B) Envelope rules for truth writes

- Every truth-creating call MUST include `idempotencyKey` (stable across retries/timeouts).
- Every truth-creating call SHOULD include `app_id: "trimsyapp"` (required by contract; backend enforcement may be added later).

### C) Receipt creation payload (must send / must persist)

On `receiptCreate`:
- MUST send `vendorName`, `currency`, `idempotencyKey`.
- SHOULD send trip linkage: `drivingTripId` (accepted as metadata today; required by contract).
- SHOULD send Android anchors so nothing is lost:
  - `clientReceiptCanonicalId` (e.g. `kvitto_<uuid>`)
  - `clientReceiptDisplayId` (`P#` / `E#` / `SW#` / `R#`)
  - `clientReceiptFilename`
  - `receiptKind`, `sequenceNumber`, `storeId`, `receiptEpochMs`, `matchedTripId`
  - `kCount`, `totalCents`, `vmbApplied`, `vatRatePercent`, `vatAmountCents`
  - Expense metadata: `expenseAccountNumber`, `expenseAccountTitle`, `expenseAccountGroup`

Locally persist (durable): returned `receiptId` and all downstream canonical IDs created by the flow.

### Enforcement summary (today)

- Handshake + protocol gate: **ENFORCED**
- Law acceptance gate: **ENFORCED**
- Idempotency replay for truth-creating calls: **ENFORCED**
- Cross-UID access returns 404: **ENFORCED**

- `app_id` required + per-app restrictions: **REQUIRED (NOT ENFORCED YET)**
- `receiptCreate` MUST include trip linkage (`drivingTripId`): **REQUIRED (NOT ENFORCED YET)**
- Driving trip endpoints beyond `drivingTripCreate`: **PLANNED (NOT AVAILABLE)**
- Finalize snapshot endpoints: **PLANNED (NOT AVAILABLE)**

---

## [BACKEND_CONTRACT.md](BACKEND_CONTRACT.md)

(Inlined from git show 1424fa3:BACKEND_CONTRACT.md)

```md
# Backend Contract  Canonical Truth Architecture

North Star (core goal / non-negotiables):
- [docs/CORE_SYSTEM_GOAL.md](docs/CORE_SYSTEM_GOAL.md)

System-wide contract (backend + 2 apps + PC):
- [docs/TRIMSY_SYSTEM_CONTRACT.md](docs/TRIMSY_SYSTEM_CONTRACT.md)

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

Authoritative deploy surface (real exported endpoints):
- [docs/BACKEND_EXPORTED_ENDPOINTS.md](docs/BACKEND_EXPORTED_ENDPOINTS.md)

## Multi-app note (trimsyapp + trimsytrack)

This backend is shared across multiple clients (mobile apps + PC).

Every client MUST identify itself on requests using an app identifier.

Why: shared UID across apps is intentional, but backend enforcement ("this endpoint is Track-only", etc.) requires a reliable client identity signal.

Allowed values:
- `trimsyapp`
- `trimsytrack`
- `trimsypc`

Rules:
- `app_id` MUST be present on all truth-creating requests.
- `app_id` SHOULD be present on every request (including reads) for observability.
- Each app build MUST hardcode exactly one `app_id` value (no runtime switching).

Optional (recommended): per-install identifier
- Clients SHOULD send `app_instance_id` (stable per install, e.g. UUID) to distinguish multiple installs of the same app on the same UID.
- This is metadata only (not an account scope key).

## Identity & Account Scope (UID-only)

- Authentication is Firebase Auth (UID-anchored).
- The Firebase Auth `uid` is the only account scope key.
- There are no profiles. Clients must not implement profile onboarding, profile selection, or profile switching.
- **No auto-create/repair rule:** backend must not create UID-keyed data just because a user signed in.
- **Data existence gate (ENFORCED):** if `uid_state/{uid}` does not exist, the backend rejects requests with `failed-precondition` and `machineCode: UID_DATA_MISSING`.
- **Deletion tombstone (ENFORCED):** if `deleted_uids/{uid}` exists, the backend rejects requests forever with `permission-denied` and `machineCode: UID_DELETED`.
- **Hard rule**: if an authenticated account does not own a record  return **404**, never 403.

Provisioning note (required by contract):
- A brand-new Firebase Auth UID will not work until an explicit provisioning step creates `uid_state/{uid}`.
- Provisioning must be an explicit user action or an operator/support action (never implicit on auth).

Clients must follow [docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md](docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md).

## Account Media (Logos, Picture)

- Branding/media is presentation data, not canonical truth.
- If/when supported, it must never block truth writes.
- Current UID-only backend does not require any media/account document to exist for canonical operations.

## Product Photos (Local-only) + Photo Metadata (Syncable)

- Product photos must **not** be uploaded to the backend (no bytes, no base64).
- The backend may store **only metadata** needed for:
	- referencing photos in UI
	- linking to truth (productId, tripId)
	- verifying integrity (hash)
	- describing the asset (dimensions/contentType)

Recommended metadata shape (example):

- `productId`
- `photoId` (client-generated stable id)
- `sha256` (of original bytes)
- `contentType` (e.g. `image/jpeg`)
- `width`, `height`
- `capturedAt` (ISO)
- `source` (e.g. `CAMERA`, `IMPORT`)
- `labels`/`notes` (optional)

Hard rule: the backend must reject attempts to include product photo bytes (e.g. `base64`, `bytes`, `image`) in product write payloads.

## Canonical Truth Rules

- Canonical IDs are globally unique, backend-assigned, immutable, never reused.
- No UPDATE or DELETE on canonical truth.
- Corrections happen only via compensating events.
- Derived values are never stored.
- Media is evidence only, never truth.

## Receipts + Driving Trips (Purchase Flow)

Truth ordering (non-negotiable):

- A **driving trip is linked to the receipt at receipt creation time**.
- The receipt is the canonical owner of the trip link (`canonicalDrivingTripId`).
- Downstream derived artifacts (e.g. Finalize Log snapshots) may *copy/snapshot* trip/store facts for analytics, but must never be able to change the receipts trip association.

Authority split:

- `drivingTripCreate` is **TrimsyTrack-only** (`app_id=trimsytrack`). (**IMPLEMENTED (EXPORTED)** in this workspace; `app_id` enforcement is partial: if `app_id` is sent and is not `trimsytrack`, the backend rejects.)
- `receiptCreate` (purchase receipts) is **TrimsyApp-only** (`app_id=trimsyapp`). TrimsyPC must not create purchase receipts. (**REQUIRED (NOT ENFORCED YET)**)

Required inputs:

- All write calls SHOULD include `clientProtocolVersion` + `app_id`.
  - `clientProtocolVersion` is **ENFORCED** on canonical endpoints.
  - TrimsyTrack sync endpoints **ENFORCE** `clientProtocolVersion`.
  - `app_id` is a contract requirement; enforcement is being rolled out incrementally.
- `receiptCreate` requires (**ENFORCED** unless noted):
  - `idempotencyKey`
  - `vendorName`
  - `currency`
  - `drivingTripId` (**REQUIRED (NOT ENFORCED YET)**; accepted as metadata today)

Optional receipt metadata (from TrimsyApp) that SHOULD be sent and is stored for audit/linking:
  - `clientReceiptCanonicalId` (Android canonical id: `kvitto_<uuid>`)
  - `clientReceiptDisplayId` (Android: `P#` / `E#` / `SW#` / `R#`)
  - `clientReceiptFilename` (Android prefs key / vault filename)
  - `receiptKind`, `sequenceNumber`, `storeId`, `receiptEpochMs`, `matchedTripId`, `kCount`, `totalCents`
  - `vmbApplied`, `vatRatePercent`, `vatAmountCents`
  - Expense-only metadata: `expenseAccountNumber`, `expenseAccountTitle`, `expenseAccountGroup`

Linking invariants:

- One receipt  one trip.
- Attempts to link a second different trip to the same receipt must be rejected.

## Idempotency

Truth-creating endpoints are idempotent via a required `idempotencyKey`.

- Retries with the same idempotency key must replay the same canonical IDs.
- The backend must not create duplicate truth on retry/timeouts.

## SKU / Category+Number Identity Lock

Trimsys implemented SKU-like identity lock is **Category+Number**.

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

## Local PC Sync v1 (Phone  PC)  Contract of Truth

PC Sync v1 is a **local Wi-Fi subsystem** (not a Firebase/Cloud backend feature).

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
```

---

## [docs/TRIMSY_SYSTEM_CONTRACT.md](docs/TRIMSY_SYSTEM_CONTRACT.md)

(Inlined from git show 1424fa3:docs/TRIMSY_SYSTEM_CONTRACT.md)

```md
# Trimsy System Contract (Backend Truth + 2 Apps + 1 PC)

This document is the **single contract** for the Trimsy system:

- **TrimsyApp** (photos + receipt scan + Finalize Log)
- **TrimsyTrack** (driving journal / trips)
- **TrimsyPC** (desktop UI + local PC Sync v1 receiver)
- **Backend** (Firebase Functions + Firestore) as the enforcing authority

If any client behavior or spec contradicts this document (or the linked authoritative docs/tests), it is **drift** and must be resolved.

## 1) Single Source of Truth

- The backend is the enforcing authority: it **validates, records, rejects**. It does not infer or auto-correct.
- The authoritative deployed surface is: docs/BACKEND_EXPORTED_ENDPOINTS.md
- The executable enforcement is: functions/src/__tests__/canonical_invariants.test.ts

## 2) Identity + Safety Gates (Required for all clients)

All clients must follow the startup gates:

1. Call handshake (`handshakeGet` / `handshakeGetCallable`) on startup.
2. Include `clientProtocolVersion` on **every** subsequent request body.
3. Include `app_id` on **every** truth-creating / write-like request body.
4. Block all sync/writes until law acceptance is complete.

Authoritative gate contract:
- docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md
- docs/UNIVERSAL_UID_IDENTITY_AND_STARTUP_CONTRACT.md

Allowed `app_id` values:
- `trimsyapp`
- `trimsytrack`
- `trimsypc`

## 3) Glossary: map kvalues to backend truth

Your domain terms map to backend objects like this:

- **Receipt**: a purchase receipt container.
  - Backend write: `receiptCreate` (requires `drivingTripId`).
- **kvalue**: one **receipt line item** (a receipt row).
  - Backend write: `receiptRowCreate`
  - `description` = your kcategory label (e.g. SKOR / Shoes line 1)
  - `lineAmountCents` = your kPrice (in cents)
- **Product (pair of shoes)**: the physical item being tracked.
  - A product gets linked to a specific receipt row amount via cost allocation.
- **Finalize Log**: a per-product write-once snapshot that ties the system together.
  - Backend write: `finalizeLogSnapshotCreate` (write-once truth per product)
  - This snapshot can reference: receipt, trip, allocations (kvalues), weight, storage, and SKU.
- **SKU**: Category+Number identity like `SKOR1`, `SKOR2`, 
  - Backend write: `productSetCategoryNumber`
  - Stored as `categoryNumberId`.

Important truth anchor:
- The Finalize Log snapshots `costAllocations[]` is the canonical anchor for this product owns this kvalue/receipt row selection.

## 4) Truth ordering (non-negotiable)

TrimsyTrack backend sync is **standalone** and does not depend on TrimsyApp. Trips and driverdata are useful by themselves; other clients may later *consume* them.

Purchase + tracking truth ordering:

1. **TrimsyTrack** creates a trip (`drivingTripCreate`).
2. (Later consumption) **TrimsyApp** may create receipts and store a `drivingTripId`, derived from trips it reads from the backend.
3. **TrimsyApp** creates receipt rows (`receiptRowCreate`) for each kvalue/line item.
4. **TrimsyApp** links product  receipt rows via `productCostAllocate`.
5. **TrimsyApp** writes the Finalize Log snapshot (`finalizeLogSnapshotCreate`) for each product.

Hard rule:
- The receipt is the canonical owner of the trip link. Downstream snapshots may copy facts for UI/audit, but must not change receipttrip association.

Reference reminder:
- INTEGRATION_REMINDERS.md

## 5) Per-client responsibilities (what each app should write)

### TrimsyTrack (driving journal)

Writes (Track-only):
- `drivingTripCreate`
- `driverdataPut` (opaque snapshot)

Reads (Track-only):
- `driverdataGet`

Required request envelope (Track sync = correct-first contract):
- `clientProtocolVersion` is required on all Track sync calls.
- `app_id` is required and must be `trimsytrack`.
- All writes require `idempotencyKey`.

Trip↔receipt linking:
- No Track→backend link call is needed. TrimsyApp derives the association from trip data it reads and stores it on `receiptCreate` (`drivingTripId`).

### TrimsyApp (photos + receipts + Finalize Log)

Writes (App-only / truth creating):
- `receiptCreate` (`app_id=trimsyapp`)  must include `drivingTripId`
- `receiptRowCreate`  one per kvalue/receipt line
- `receiptMediaSet`  receipt photos (bytes accepted here)
- `productCostAllocate`  link product  receiptRowId + amount
- `productSetCategoryNumber`  assigns SKU-like Category+Number
- `weightCreate` (if used in Finalize Log)
- `placementSet` / storage placement writes (if used in Finalize Log)
- `finalizeLogSnapshotCreate`  write-once per product

### TrimsyPC (desktop UI + local receiver)

Backend side:
- Primarily reads/audits truth (Finalize Log snapshots, receipt snapshots, trace, etc.).
- Must not create purchase receipts.

Local device-to-PC sync:
- PC Sync v1 is a separate local Wi-Fi subsystem.
- Single source of truth spec: docs/TRIMSYAPP_TRIMSYPC_CONNECTIVITY.md

## 6) Definition of Done (system-level)

- All three clients send `clientProtocolVersion` consistently.
- All write-like calls send `app_id` (contract requirement; backend enforcement is partial today and being expanded).
- TrimsyTrack can create trips and the trip IDs are used by TrimsyApp receipt creation.
- TrimsyApp can:
  - create a receipt with `drivingTripId`
  - add rows (kvalues) with correct amounts
  - allocate rows to products
  - assign Category+Number (SKU)
  - write Finalize Log snapshots per product
- TrimsyPC can:
  - receive PC Sync v1 payloads
  - display/audit backend truth without inventing it

## End-to-end verification order (numbered)

Use this exact order when wiring clients and when verifying correctness:

1) `handshakeGet` (store returned `protocolVersion`)
2) `lawGet` then `lawAccept` (block all writes until accepted)
3) `productCreate` (repeat per product)
4) `receiptCreate` (create the purchase container)
5) `receiptRowCreate` (one per kvalue/line)
6) `productCostAllocate` (link product -> receiptRowId with cents; must not exceed row amount)
7) `productSetCategoryNumber` (Category+Number SKU lock)
8) (Optional) `productSetWeight` and/or `productSetStorageLocation`
9) `saleCreate` (one sale per product)
10) `saleReceiptLink` (attach sale to a receipt; one-to-one)
11) `ledgerEntryCreate` (book deterministic signed cents per account; repeat until balanced)
12) `accountingVerificationCreate` (the "verification snapshot" lock; succeeds only when ledger has >=2 entries AND sums to 0)

## TrimsyApp full chain (backend sync contract view)

This is the full end-to-end chain of how TrimsyApp produces canonical truth, expressed purely as a backend contract.
Use it as a checklist when implementing client sync and when reviewing “coverage” for missing endpoints.

### 0) Startup gates (always, before any writes)

1) `handshakeGet` → store `protocolVersion` (must be echoed as `clientProtocolVersion` on all subsequent write-like calls)
2) `lawGet` → `lawAccept` → block canonical writes until accepted
3) (Optional, for operators/PC) `opsGetSafetyMode` → if enabled, clients must not attempt non-idempotent truth writes

### 1) Trip context (TrimsyTrack → TrimsyApp)

4) TrimsyTrack creates a driving trip: `drivingTripCreate` → yields `drivingTripId`
5) TrimsyApp must attach `drivingTripId` when creating purchase receipts (hard backend restriction)

### 2) Purchase receipt truth (TrimsyApp-only)

6) `receiptCreate` → yields `receiptId`
   - Required: `drivingTripId`, currency, vendor, occurredAt
   - Idempotency: required on truth-creating calls
7) (Optional but common) receipt media upload:
   - `receiptMediaSet` (upload / attach media)
   - `receiptMediaListByReceipt` (list)
8) `receiptRowCreate` (repeat per kvalue/line item) → yields `receiptRowId`
   - This is the k-index / row identity used for later allocation and sale linkage.
9) (Optional) `receiptSnapshotGet` (read-only) for audit/debug

### 3) Inventory item truth (product) and explicit links to purchase

10) `productCreate` (repeat per item/SKU) → yields `productId`
11) `productCostAllocate` (repeat; links product → receiptRowId with explicit cents)
  - Invariant: allocations for a receipt row must not exceed the row amount
12) `productSetCategoryNumber` (SKU lock, Category+Number) → yields `categoryNumberId` (deterministic)
  - Invariant: Category+Number cannot be reused across products
13) (Optional) `productSetWeight`

### 4) Storage truth (optional but canonical when used)

14) (If using structured storage) `storageSlotCreate` (repeat per slot) → yields `storageSlotId`
15) `storagePlaceProduct` / `storageRemoveProduct` / `storageMoveProduct` (repeat as user moves items)
16) (Optional) `storageSlotArchive` (decommission slot)
17) (Optional) `storageSnapshotGet` (read-only) for audit/debug

### 5) Sale truth (TrimsyApp trigger points: “mark sold”)

18) `saleCreate` (exactly one sale per product)
  - Client sends the sold fact + timestamp.
  - Client may also include the Android accounting envelope as metadata (selling/purchase price, VAT/VMB flags, shipping/fee fields).
  - Identity can be either `productId` OR `categoryNumberId` (backend resolves to product).
19) `saleReceiptLink` (attach sale to purchase receipt context)
  - Required: `saleId` + `receiptId`
  - Optional: `receiptKIndex` (k-index / receipt row index) and `salesReceiptRef` (outgoing receipt artifact ref)

### 6) Bookkeeping truth + verification snapshot lock

20) `ledgerEntryCreate` (repeat per posting line)
  - Uses `scopeId` (deterministic accounting scope; commonly the `saleId`)
  - Signed integer `amountCents`, `account`, `currency`
21) `accountingVerificationCreate`
  - Locks the snapshot for a `scopeId`
  - Hard invariants: >= 2 ledger entries exist for the scope AND sum(amountCents) == 0

### 7) Finalize snapshot truth (per-product audit trail)

22) `finalizeLogSnapshotCreate` (repeat; per product) → yields `finalizeSnapshotId`
23) Read-only audit/listing:
  - `finalizeLogSnapshotGet`
  - `finalizeLogSnapshotsListByProduct`

### 8) Diagnostics and operational read paths

24) `invariantsOpen` (read-only; reports gaps and invariant violations)

### 9) Non-negotiable cross-cutting rules

- Every client must send `app_id` and `clientProtocolVersion`.
- Truth-creating endpoints require `idempotencyKey` so retries do not create duplicate truth.
- Backend enforces: do not infer or “fill missing”; always link explicitly (product→receiptRow allocation, sale→receipt context, ledger scope→verification).

## Reconciliation: Android “Complete ID chain” → backend contract

This section is derived from the TrimsyApp (skuphoto) document “Complete ID + metadata chain (receipt → item → sale → verification)”.
Goal: ensure every identifier that exists today in Android has a defined place in the backend contract (either as a canonical field, or as stored metadata).

### Receipt identifiers + metadata

Android has BOTH a display ID (P/E/SW/R) and a canonical ID (`kvitto_<uuid>`). Backend receipt ids are separate.

- Backend endpoint: `receiptCreate`
  - Required: `vendorName`, `currency`, `idempotencyKey`
  - Recommended idempotency: derive from Android canonical receipt id, e.g. `android:receiptCreate:<kvitto_uuid>`
  - Optional metadata fields accepted and stored for audit/linking:
    - `clientReceiptCanonicalId` (Android: `canonical_id`)
    - `clientReceiptDisplayId` (Android: `receipt_id` like `P12`)
    - `clientReceiptFilename` (Android prefs key / vault filename)
    - `receiptKind`, `sequenceNumber`, `storeId`, `receiptEpochMs`, `matchedTripId`
    - `kCount`, `totalCents`, `vmbApplied`, `vatRatePercent`, `vatAmountCents`
    - `expenseAccountNumber`, `expenseAccountTitle`, `expenseAccountGroup`
    - `drivingTripId` (if you want the explicit trip link on receipts)

### K-index / receipt line indexing (must be explicit)

Android uses K-lines and has multiple representations:
- In UI strings: `K3` means “K index number 3”.
- In some arrays: k-values are stored as 0-based list positions.

Backend contract rule:
- When sending a “K-index” to the backend, treat it as the human K number (1-based, `K1`..`Kn`).
- For `saleReceiptLink.receiptKIndex`, send the 1-based K number (so `K3` → `3`).

### Storage + inventory events

Android has local-only inventory events (STORED/SOLD/etc.) with IDs and slot IDs.
Backend contract today does NOT ingest those events directly; instead it ingests canonical storage + sale truth:
- `storageSlotCreate` + `storagePlaceProduct`/`storageRemoveProduct`/`storageMoveProduct`
- `saleCreate` (sold fact) + `saleReceiptLink` (purchase receipt linkage)

### Sale + bookkeeping linkage

Android has:
- `AccountingEntry.id` (nowMillis)
- Optional sales verification id: `APPS<n>`

Backend mapping:
- `saleCreate`
  - Recommended idempotency: `sale:<bookingEntryId>`
  - Optional metadata: `bookingEntryId`, plus the accounting envelope fields (VAT/VMB/shipping/fee)
- `accountingVerificationCreate`
  - Optional metadata: `verificationRef` (store Android `APPS<n>` here)
  - Canonical lock remains deterministic by `scopeId` (commonly `saleId`) and balance invariants

### TrimsyApp → sales + bookkeeping mapping (uses existing app facts)

This is the intended wiring for Android without inventing new business behavior — only moving the same facts across the boundary.

1) `saleCreate` = the “mark sold” fact + inventory removal trigger
- Trigger point in app today: the moment the app clears the storage slot and appends a SOLD inventory event.
- What to send:
  - Identity: send either `productId` (canonical) OR `categoryNumberId` (SKU like `CAT#123` that resolves to product) and optionally `itemId` (Android SKU id) as metadata.
  - Timestamp: `soldAt` = sale date you already use.
  - Accounting envelope (optional metadata; stored as-is): `sellingPrice`, `purchasePrice`, `vatRate`, `vmbApplied`, `shippingCost`, `shippingVatRate`, `fee`, `feeVatRate`, and optional `currency`.
  - `idempotencyKey`: derived from a stable local key (e.g. `sale:<bookingEntryId>` or `sale:<inventoryEventId>`).

2) `saleReceiptLink` = attach the sale to the purchase receipt + K-index (and optionally to an outgoing written sales receipt artifact)
- Source of truth in app today: your accounting entry already stores `receiptId` + `receiptKIndex` linkage.
- What to send:
  - `saleId` returned from `saleCreate`
  - `receiptId` (purchase receipt container)
  - `receiptKIndex` (K-index / receipt row index)
  - Optional: `salesReceiptRef` (file name / local id / uri for the outgoing written sales receipt artifact)
  - `idempotencyKey`: stable per link operation

3) `ledgerEntryCreate` + `accountingVerificationCreate` = create balanced postings + lock the verification snapshot
- If the backend is doing the voucher expansion (future): client calls `saleCreate` (+ `saleReceiptLink`) only; backend derives voucher lines from stored booking settings.
- If voucher expansion stays on-device (current-compatible): client calls `ledgerEntryCreate` once per expanded voucher line, then calls `accountingVerificationCreate` for the same `scopeId` when balanced.
- Hard invariant: verification only succeeds when there are at least 2 ledger entries for the scope AND the sum of `amountCents` is 0.

## 7) Authoritative references (send these to coders)

- docs/BACKEND_EXPORTED_ENDPOINTS.md
- docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md
- docs/BACKEND_STORAGE_AND_SYNC_PAYLOAD.md
- docs/TRIMSYAPP_TRIMSYPC_CONNECTIVITY.md
- BACKEND_CONTRACT.md
- docs/CORE_SYSTEM_GOAL.md
- functions/src/__tests__/canonical_invariants.test.ts
```

---

## [docs/BACKEND_EXPORTED_ENDPOINTS.md](docs/BACKEND_EXPORTED_ENDPOINTS.md)

(Inlined from git show 1424fa3:docs/BACKEND_EXPORTED_ENDPOINTS.md)

```md
# Backend Exported Endpoints (Deployed Surface)

This document is the **authoritative list of backend endpoints that are actually exported** by Cloud Functions.

Source of truth:
- Functions entrypoint: [functions/src/index.ts](../functions/src/index.ts)

Hard rule:
- If an endpoint is not listed under **Implemented (exported)** below, it is not part of the deployed backend surface.

---

## Identity + scope (non-negotiable)

- Authentication: Firebase Auth.
- Account scope key: Firebase Auth `uid`.
- No profiles exist.
- If `uid_state/{uid}` is missing, backend rejects with `UID_DATA_MISSING`.
- If `deleted_uids/{uid}` exists, backend rejects forever with `UID_DELETED`.

---

## Client styles

### A) Raw HTTP gateway (recommended for PC/non-Firebase-SDK clients)

Base URL:
- Production: `https://europe-north1-<project>.cloudfunctions.net/apiV1`
- Emulator: `http://127.0.0.1:5001/<project>/europe-north1/apiV1`

Call format:
- Method: `POST`
- Header: `Authorization: Bearer <Firebase ID token>`
- Body: JSON

Startup gating is mandatory for all clients:
- Handshake  Law acceptance  Safety mode
- See [docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md](CLIENT_BACKEND_STARTUP_HANDSHAKE.md)

### B) Firebase Functions callables (recommended for Firebase SDK clients)

Callable names are exported as `*Callable` functions (see list below).

---

## HTTP routes (`apiV1/<route>`)

All routes below are `POST`.

### Startup + law gating

- `health` (note: still requires Bearer token; implemented inside `apiV1`)
- `handshakeGet`
- `lawGet`
- `lawAccept`
- `lawContractGet`

- `uidDelete`

### TrimsyTrack sync surfaces

- `driverdataGet`
- `driverdataPut`
- `drivingTripCreate`

### Canonical inventory / purchase flow

- `productCreate`
- `productSetCategoryNumber`
- `productSetWeight`
- `productSetStorageLocation`

- `receiptCreate`
- `receiptRowCreate`
- `productCostAllocate`

### Storage system

- `storageSlotCreate`
- `storageSlotArchive`
- `storagePlaceProduct`
- `storageRemoveProduct`
- `storageMoveProduct`
- `storageSnapshotGet`

### Sales + bookkeeping

- `saleCreate`
- `saleReceiptLink`
- `ledgerEntryCreate`
- `accountingVerificationCreate`

### Diagnostics

- `invariantsOpen`

### Operator-only (admin)

- `opsGetSafetyMode`
- `opsSetSafetyMode`

---

## Callable exports (Firebase Functions SDK)

### Global

- `health`

### Canonical + gating

- `handshakeGetCallable`

- `lawGetCallable`
- `lawAcceptCallable`
- `lawContractGetCallable`

- `uidDeleteCallable`

- `productCreateCallable`
- `productSetCategoryNumberCallable`
- `productSetWeightCallable`
- `productSetStorageLocationCallable`

- `saleCreateCallable`
- `saleReceiptLinkCallable`
- `ledgerEntryCreateCallable`
- `accountingVerificationCreateCallable`

- `receiptCreateCallable`
- `receiptRowCreateCallable`
- `productCostAllocateCallable`

- `storageSlotCreateCallable`
- `storageSlotArchiveCallable`
- `storagePlaceProductCallable`
- `storageRemoveProductCallable`
- `storageMoveProductCallable`
- `storageSnapshotGetCallable`

- `invariantsOpenCallable`

### Internal-only

- `selfTestCallable` (requires `INTERNAL_SELF_TEST_EMAIL` configuration)

---

## Planned (NOT available in this workspace)

These are mentioned elsewhere in the contract docs, but they are not exported by this backend workspace today:

- Driving journal: additional trip/stop mutation endpoints (beyond `drivingTripCreate`) if/when Track expands its sync surface
- Receipt media + receipt snapshot endpoints (e.g. `receiptMediaSet`, `receiptMediaListByReceipt`, `receiptSnapshotGet`)
- Finalize snapshot endpoints (e.g. `finalizeLogSnapshotCreate`, `finalizeLogSnapshotGet`, `finalizeLogSnapshotsListByProduct`)

---

## App restrictions (must stay true)

These are hard backend constraints (and are also documented in [BACKEND_CONTRACT.md](../BACKEND_CONTRACT.md)):

- `drivingTripCreate` is **TrimsyTrack-only** (`app_id=trimsytrack`).
- `receiptCreate` (purchase receipts) is **TrimsyApp-only** (`app_id=trimsyapp`) and requires `drivingTripId`.
- TrimsyPC must **not** create purchase receipts.

---

## Non-endpoints

Not part of the public client API surface:

- `authUserCreated` (Gen1 Auth trigger) exists but is a no-op; it is not a client-callable endpoint.
```

---

## [docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md](docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md)

(Inlined from git show 1424fa3:docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md)

```md
# Client Backend Startup Handshake (All Apps)

This document is the single source of truth for how every client (mobile/web/PC/Electron) connects to the Trimsy backend safely.

## Startup contract

The backend enforces a single, strict startup contract:

1) **Handshake** (machine-only)
2) **Law gating** (acceptance)
3) **Safety mode** (write blocking)

Clients should treat these as backend-enforced gates and react only to backend machine-codes.

## Non-Negotiables

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

### Multi-app identifier (`app_id`)

Clients MUST identify which app is calling the backend.

Enforcement note:
- `app_id` is a contract requirement across all clients; backend enforcement is being rolled out incrementally.
- TrimsyTrack sync routes currently enforce `app_id=trimsytrack`.

```json
{ "app_id": "trimsytrack" }
```

Allowed values (currently):
- `trimsyapp`
- `trimsytrack`
- `trimsypc`

Rules:
- `app_id` MUST be present on all truth-creating requests.
- `app_id` SHOULD be present on all requests for observability.

Optional (recommended): per-install identifier

```json
{ "app_instance_id": "<stable-per-install-uuid>" }
```

- `app_instance_id` SHOULD be present on all requests.
- `app_instance_id` must be stable across app restarts (until reinstall).

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

### UID existence + deletion gating behavior

If any endpoint returns `412 failed-precondition` with:
- `error.details.machineCode == "UID_DATA_MISSING"`

Client behavior:
- Stop all sync/writes.
- Show a blocking message that this account is not provisioned yet.
- Do not retry-loop; resolution requires an explicit provisioning step.

If any endpoint returns `403 permission-denied` with:
- `error.details.machineCode == "UID_DELETED"`

Client behavior:
- Stop all sync/writes.
- Sign out locally.
- Show "Account deleted" and do not attempt recovery.

## 2) Law gating (acceptance)

Before any canonical truth endpoints, the user must:
- Fetch law pack: `lawGet`
- Accept law: `lawAccept`

If any canonical endpoint returns `412 failed-precondition` with:
- `error.details.machineCode == "LAW_ACCEPTANCE_REQUIRED"`  show docs + acceptance UI and block syncing

## 3) Safety mode (write blocking)

Handshake returns `writesEnabled` and safety mode details.

- If safety mode is enabled, the client must treat the system as **read-only**.
- Canonical truth creation endpoints must not be attempted until safety mode is disabled.
- Idempotency replays may still succeed (backend-controlled).

## Required error handling (must implement)

- `401 unauthenticated`  show Sign in again, refresh token, retry once.
- `412 failed-precondition`  do not loop; go to the blocking gate UI based on `machineCode`.
- `400 invalid-argument` / `422 validation`  mark the specific pending operation blocked (no auto retry).
- `429 resource-exhausted`  obey `Retry-After`/`retryAfterSeconds`, show countdown, stop retry loops.

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
- `HANDSHAKE_REQUIRED`  Reconnect
- `PROTOCOL_MISMATCH`  Update app
- `ACCOUNT_CONFLICT`  Sign out and sign in again
- `LAW_ACCEPTANCE_REQUIRED`  Review & accept
- `SAFETY_MODE_WRITE_BLOCKED`  Read-only mode
- `UNAUTHENTICATED`  Sign in again

Legacy/deprecated:
- `EMAIL_REQUIRED`  older backends only; modern clients should not assume email is required

## Legacy profile fields (removed)

No profile fields exist in the current backend model. Clients must not send or expect `profileId` / `profile.*`.

## Branding/media (not part of startup gating)

The UID-only backend model does not require any account media document to exist.

- Clients must not block startup on account media.
- If branding/media is implemented later, it must remain optional presentation data and must never block truth writes.
```

---

## [docs/BACKEND_STORAGE_AND_SYNC_PAYLOAD.md](docs/BACKEND_STORAGE_AND_SYNC_PAYLOAD.md)

(Inlined from git show 1424fa3:docs/BACKEND_STORAGE_AND_SYNC_PAYLOAD.md)

```md
# Backend Storage & Sync Payload (Canonical Writes)

This doc describes the canonical writes that clients (Mobile/PC) may send, and the IDs they must persist for idempotency/retry correctness.

## Startup gate (required)

- Clients must call `handshakeGet` at startup and send `clientProtocolVersion` on every request.
- Canonical writes are blocked until law acceptance is satisfied.

See [docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md](docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md).

## Identity system (what IDs mean)

### Canonical IDs

- Canonical IDs are backend-assigned, immutable, never reused.
- Clients may *reference* canonical IDs but must never invent them.

### UID scope

- Account scope key is Firebase Auth `uid`.
- Clients never choose or send ownership.
- If a user tries to access another user's record, backend returns **404**.

### SKU / Category+Number

Trimsys current implemented SKU-like identity lock is **Category+Number**:

- Set via `productSetCategoryNumberCallable`.
- Uniqueness is enforced **within a UID**: a Category+Number cannot be used by two products.
- Single-assignment is enforced: a product cannot be assigned Category+Number twice.

The canonical identifier used by the backend is:

- `categoryNumberId = "${CATEGORY}#${NUMBER}"`

This is a deterministic anchor id derived from explicit user inputs (not a guess).

## Canonical writes added for logging

## Product Photos: bytes are local-only

- Product photos (image bytes/base64) must **never** be sent to Firebase/Cloud Functions.
- Only **photo metadata** is allowed to sync (hash, contentType, dimensions, capturedAt, and a stable client photo id).
- If cross-device photo viewing is needed later, handle it as a separate opt-in storage system; do not mix with canonical truth writes.

## Finalize Log snapshot (recommended link point)

When TrimsyApp runs **Finalize Log**, it typically creates multiple canonical facts (product, receipt rows, weight, storage placement, Category+Number). To make syncing and UI linking simple, the client should also record a snapshot that ties these together.

This snapshot is **write-once truth per product**:
- exactly one snapshot per `productId` (within a UID)
- immutable (if the client attempts to write a different snapshot later, backend rejects)
- safe to retry (exact replays are allowed)

Endpoint:
- `finalizeLogSnapshotCreateCallable`

Request body (photo bytes forbidden):
- `clientProtocolVersion` (required)
- `app_id` (required for writes)
- `idempotencyKey` (required)
- `productId` (required)
- Optional references created in the same Finalize Log flow:
  - `receiptId`, `storageSlotId`, `weightId`, `placementId`, `categoryNumberId`
- Optional explicit values:
  - `category`, `number`
- Optional photo metadata batch:
  - `photoBatch.batchId`
  - `photoBatch.photos[]` entries with: `photoId`, `sha256`, `contentType`, optional `width`, `height`, `capturedAt`, `isPrimary`, `note`

Response:
- `snapshotId`

### 1) Weight truth

When the user enters weight in the Finalize Log flow, the client sends a canonical write.
If weight is empty, the client must send nothing.

Callable:
- `productSetWeightCallable`

Request body:
- `clientProtocolVersion` (required)
- `idempotencyKey` (required)
- `productId` (required)
- `weightGrams` (required, integer > 0)
- `occurredAt` (optional ISO timestamp)

Response:
- `weightId`

Client rule:
- Persist `weightId` in the local queue/log record.

### 2) Location truth (log placement)

When Finalize Log uses a real `storageSlotId` (user kept the suggested tier location), the client sends a canonical placement write.

Callable:
- `productSetStorageLocationCallable`

Request body:
- `clientProtocolVersion` (required)
- `idempotencyKey` (required)
- `productId` (required)
- `storageSlotId` (required)
- Optional informational fields the client may send (backend resolves canonical values from the slot):
  - `locationId`, `unitCode`, `componentCode`
  - `tier1Id`, `tier1Code`, `tier2Kind`, `tier2Id`, `tier2Index`, `tier3Index`
- `occurredAt` (optional ISO timestamp)

Response:
- `placementId`

Client rule:
- Persist `placementId` in the local queue/log record.

Backend behavior:
- Enforces one-product-per-slot.
- Updates derived current location (`storage_product_locations`) and occupancy (`storage_slot_occupancy`).
- Records an append-only canonical placement record in `product_storage_placements`.
```

---

## [docs/CONNECTING_CLIENTS.md](docs/CONNECTING_CLIENTS.md)

(Inlined from git show 1424fa3:docs/CONNECTING_CLIENTS.md)

```md
# Connecting apps to the Trimsy backend

North Star (core goal / non-negotiables):
- [docs/CORE_SYSTEM_GOAL.md](docs/CORE_SYSTEM_GOAL.md)

System-wide contract (backend + 2 apps + PC):
- [docs/TRIMSY_SYSTEM_CONTRACT.md](docs/TRIMSY_SYSTEM_CONTRACT.md)

This backend supports two client styles:

1) **Firebase SDK (recommended)**
- Use Firebase Auth to sign in.
- Use Firebase Functions **callables** for backend operations (e.g. `handshakeGetCallable`).
- Use Firestore directly if your client is designed to read/write collections (many flows in this repo are via Functions).

2) **Raw HTTP (desktop / non-Firebase-SDK clients)**
- Sign in with Firebase Auth to obtain a Firebase **ID token**.
- Call the backend HTTP gateway (`apiV1`) with `Authorization: Bearer <idToken>`.

---

## Production (deployed)

### Firebase project
- Project id: `trimsy-d12de`
- Region: `europe-north1`

Note: the only non-Stockholm function is `authUserCreated` (a Gen 1 Auth trigger) which must run in `europe-west1` due to Gen 1 region limitations; it is currently a no-op.

### HTTP gateway base URL
Stable base URL (recommended):
- `https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1`

Cloud Run base URL (also works, but changes when you redeploy):
- (printed at deploy time)

Routes are appended as path segments and are **POST** only:
- `POST /handshakeGet`
- `POST /lawGet`
- `POST /lawAccept`
- `POST /lawContractGet`
- `POST /uidDelete`
- `POST /receiptCreate`
- `POST /productCreate`
- `POST /storageSnapshotGet`
- `POST /driverdataGet`
- `POST /driverdataPut`
- `POST /drivingTripCreate`
- etc (see `docs/BACKEND_EXPORTED_ENDPOINTS.md`)

Important:
- Follow the universal startup gate contract in `docs/CLIENT_BACKEND_STARTUP_HANDSHAKE.md` (handshake  law  safety mode).
- Most canonical endpoints require `clientProtocolVersion` in the request body.
- Truth-creating endpoints require `app_id` and are backend-restricted by client:
  - `receiptCreate` (purchase receipts) is **TrimsyApp-only** and requires `drivingTripId`.
  - `drivingTripCreate` is **TrimsyTrack-only**.
  - TrimsyPC must not create purchase receipts.

UID-only note:
- There is no profile onboarding gate and no profile endpoints.
- Requests fail if this UID is not provisioned (`UID_DATA_MISSING`).
- Requests fail forever after deletion (`UID_DELETED`).

All `apiV1/*` routes require:
- Header: `Authorization: Bearer <Firebase ID token>`
- JSON body: request payload (most canonical routes require `clientProtocolVersion`)

### Callable functions
If you use the Firebase Functions client SDK, callables are deployed under these names:
- `handshakeGetCallable`
- `receiptCreateCallable`
- `lawGetCallable`
- `lawAcceptCallable`
- `lawContractGetCallable`
- `uidDeleteCallable`
- `selfTestCallable`
- and the rest listed by `firebase functions:list`

Authoritative deploy surface (real exported endpoints):
- `docs/BACKEND_EXPORTED_ENDPOINTS.md`

---

## Local development (emulators)

If you run emulators locally, the default ports from `firebase.json` are:
- Emulator UI: `http://127.0.0.1:4000/`
- Auth: `127.0.0.1:9099`
- Firestore: `127.0.0.1:8080`
- Functions: `127.0.0.1:5001`

HTTP function base URL (local):
- `http://127.0.0.1:5001/trimsy-d12de/europe-north1/apiV1`

---

## Minimal client-side env knobs (suggested)

Recommended environment variables for your apps:

- `TRIMSY_FIREBASE_PROJECT_ID=trimsy-d12de`
- `TRIMSY_FUNCTIONS_REGION=europe-north1`
- `TRIMSY_USE_EMULATORS=0` (set to `1` for local)

If using raw HTTP (desktop gateway):
- `TRIMSY_BACKEND_BASE_URL=https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1`

If using local emulators:
- `TRIMSY_BACKEND_BASE_URL=http://127.0.0.1:5001/trimsy-d12de/europe-north1/apiV1`

---

## Example: raw HTTP call (PowerShell)

```powershell
$base = "https://europe-north1-trimsy-d12de.cloudfunctions.net/apiV1"
$token = "<FIREBASE_ID_TOKEN>"

Invoke-RestMethod -Method Post `
  -Uri "$base/handshakeGet" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body "{}"
```

---

## Notes

- If you switch between emulators and production, make sure you **dont** accidentally call `connectAuthEmulator/connectFirestoreEmulator/connectFunctionsEmulator` in production.
- Your `apiV1` gateway verifies ID tokens server-side; clients must sign in to Firebase Auth and refresh tokens when they expire.
```

---

## [docs/TRIMSYAPP_TRIMSYPC_CONNECTIVITY.md](docs/TRIMSYAPP_TRIMSYPC_CONNECTIVITY.md)

(Inlined from git show 1424fa3:docs/TRIMSYAPP_TRIMSYPC_CONNECTIVITY.md)

```md
# TRIMSYAPP / TRIMSYPC CONNECTIVITY (Local Wi-Fi Phone  PC)

Status: **Contract + implementation guide**

This document defines how **TRIMSYAPP (phone)** and **TRIMSYPC (PC receiver)** achieve:
- **Automatic connectivity** (discover  connect  reconcile  upload)
- **Operational safety** (no data loss, duplication, corruption)
- **Reliability and clarity** (no blocked in the dark; explicit prompts when needed)

This is not a LAN attacker security document. Here, secure means **safe, accurate, available, instant, smooth**.

---

## PC heartbeat + wake phone (Backend-assisted)

This section previously described backend-assisted presence (`heartbeatV2` + `pc_presence_v2`).

That Presence V2 system has been removed from the repository.

## DOCUMENT OF TRUTH (Authoritative) + Chain of Events

If there is any disagreement between old docs/scripts and this system:
- This document is the **single source of truth** for PC Sync v1.
- Code must follow this doc; any change to behavior must be made here first.
- Repo invariants enforce the key non-negotiables.

Authoritative spec:
- `docs/TRIMSYAPP_TRIMSYPC_CONNECTIVITY.md` (this file)

Enforcement:
- `tools/check_connectivity_rules.ps1` (invoked by `tools/run_invariants.ps1`)

Reference implementations:
- PC receiver: `apps/trimsy-main/pc/trimsy_pc_sync_v1/server.py`
- Wire format: `apps/trimsy-main/pc/trimsy_pc_sync_v1/protocol.py`
- Storage/path/conflicts: `apps/trimsy-main/pc/trimsy_pc_sync_v1/storage.py`
- Receiver DB: `apps/trimsy-main/pc/trimsy_pc_sync_v1/db.py`
- Android client: `apps/trimsy-main/app/src/main/java/com/trimsy/camera/pcsyncv1/*`

Shared troubleshooting playbook:
- `docs/POTENTIAL_PROBLEMS_AND_HOW_TO_SOLVE_THEM.md`

### Canonical chain of events (what happens on the wire)
1) Phone discovers PC via mDNS (`_trimsy-upload._tcp`) and connects TCP to port `43821`.
2) Handshake:
  - Phone sends `HELLO` (optionally with `trustedPcId`).
  - PC returns `HELLO_ACK` or closes with `ERROR`.
3) Reconciliation:
  - Phone sends `RECONCILE_QUERY` pages.
  - PC returns `RECONCILE_PAGE` ground truth: `missing` / `partial(resumeOffset)` / `complete`.
4) Upload:
  - Phone sends `START`  PC responds `START_ACK(resumeOffset)`.
  - For each chunk: `CHUNK_HEADER` + `CHUNK_BYTES`  `CHUNK_ACK(ackedOffsetEnd)`.
  - Phone sends `COMMIT`  PC returns `COMMIT_ACK` only after durable finalize.
5) Recovery:
  - On crash/Wi-Fi drops, phone reconnects and repeats reconcile+resume.

---

## Checklist: start using PC Sync v1 (production)

### PC (TRIMSYPC)
- Install/run the receiver so it listens on TCP `43821`.
- Ensure Windows Firewall allows inbound TCP `43821` (prompt or pre-created rule).
- Ensure mDNS is enabled on the PC network and the receiver is advertising `_trimsy-upload._tcp`.
- Ensure the uploads directory is writable and has sufficient free space.

Recommended one-time install (admin):
- `powershell -NoProfile -ExecutionPolicy Bypass -File apps/trimsy-main/pc/install-trimsy-pc-sync-v1.ps1`

### Phone (TRIMSYAPP)
- Phone must be on local Wi-Fi (same LAN as the PC).
- Enable PC Sync v1 in app settings (if gated).
- First successful connect pins `trustedPcId` (subsequent connects must match).

### What "ready" means
- PC is listening on `43821` and advertising via mDNS.
- Phone can discover the service and complete HELLO.
- Reconciliation converges (no stuck items).

### Quick sanity validation
- Run PC smoke suite:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File apps/trimsy-main/pc/trimsy_pc_sync_v1/run_smoke_suite.ps1`

---

## 0) Scope / Guarantees

### In scope (required)
- Local Wi-Fi / local LAN only.
- Phone uploads to exactly **one** PC at a time.
- Upload-only direction: **phone  PC**.
- Supports images + important data (files defined by app contract).
- Works through crashes, reboots, Wi-Fi drops, sleep/wake.
- Never duplicates output files.
- Never corrupts output files.
- Never loses queued uploads.
- Never requires manual recovery; self-healing via reconciliation.

### Out of scope (explicit)
- Hard security against malicious actors on the same network.
- Cloud relays / third-party tunnels / middleman services.

---

## 0.1) Max Smoothness + No-Loss Defaults (All Key Decisions)

This section is the set it this way for flawless behavior list: every decision that makes connectivity
easy/smooth/constant **without** ever losing/corrupting uploads.

### Connectivity decisions (smooth + constant)
- Single PC target: TRIMSYAPP uploads to exactly one trusted PC (`trustedPcId`).
- Fixed transport: TCP only.
- Fixed port: `43821` and treat as a protocol constant (no port negotiation, no multi-port).
- Bind/listen: TRIMSYPC listens on `0.0.0.0:43821` (all LAN interfaces).
- Discovery: mDNS/Bonjour only (no manual IP entry).
- Advertise only when reachable: TRIMSYPC does not advertise the mDNS service until the listener is up and reachable.
- Waiting for PC is normal: discovery failure is not treated as an error.
- Burst mode: when the user initiates upload, TRIMSYAPP enters 1030s of aggressive discovery+connect.
- Battery-friendly steady-state: outside burst mode, discovery uses bounded backoff.
- Timeouts (prevent hangs):
  - TCP connect timeout: 13s
  - Read timeout: 25s
- Heartbeat (fast detection): interval 25s; reconnect after 23 missed intervals.
- Monotonic time for retries/backoff (no wall-clock dependencies).

### Repo-enforced rules
To keep the implementation aligned with this contract, the repo enforces:
- Forbidden legacy connectivity: the old UDP discovery + HTTP receiver must not appear in active UI/scripts/docs.
- Required fixed port: `43821` must remain the only PC Sync v1 port.

Enforcement entrypoint:
- `tools/check_connectivity_rules.ps1` (also invoked by `tools/run_invariants.ps1`)

### Windows Firewall decisions (no blocked in the dark)
Goal: no silent blocking. Either Windows prompts once, or we pre-create a rule.

Recommended for **maximum smoothness**:
- Create an inbound allow rule for TCP `43821` on **Private + Public** profiles.
- Remote address scope: **Any**.
- Prefer program-scoped if possible (rule attached to the TRIMSYPC executable), to avoid other programs using the same port.

If you want a slightly tighter home-only stance while staying smooth:
- Profile: Private only (but note: Windows sometimes misclassifies networks as Public).
- Remote address: LocalSubnet (or your LAN CIDR).

### Identity / re-trust decisions (smooth reconnect, minimal prompts)
- TRIMSYPC has a persistent `pcId` (UUIDv4) stored durably (survives updates).
- TRIMSYAPP pins `trustedPcId` after first trust.
- Only prompt when identity changes:
  - New PC detected. Trust this PC? when the discovered `pcId` differs.

### No-loss / no-corruption decisions (correctness core)
- Durable queue on phone (SQLite): the upload list survives app kills/reboots.
- Stable per-item identity: `uploadUuid` generated once per item; never changes.
- Resume semantics: receiver returns `resumeOffset` on START; sender resumes exactly from that offset.
- Two-layer integrity:
  - Full-file `sha256Full` verified on COMMIT.
  - Per-chunk checksum (e.g. SHA-256) on each CHUNK.
- No delete before certainty: phone deletes source bytes only after **COMMIT ACK**.
- Receiver never writes final files directly:
  - Write to temp location.
  - Persist progress in transactional DB.
  - Atomically rename/move into final destination only after verification.
- Idempotency everywhere:
  - Duplicate START/CHUNK/COMMIT must never duplicate or corrupt output.
  - Duplicate COMMIT returns already complete success.
- Single-writer safety:
  - Exactly one active writer per `uploadUuid` enforced via DB constraint/lock.
- Backpressure without breakage:
  - PC can return `SLOW`/`PAUSE`.
  - Phone adapts rate/chunk sizing but correctness rules stay identical.
- Reconciliation is mandatory (self-healing):
  - Run on phone startup, PC startup, on connect, and periodically.
  - Phone asks PC missing/partial(offset)/complete and corrects its own queue state.

---

## 1) Roles

- **TRIMSYPC**: LAN server, discovery advertiser, upload receiver, durable state owner for uploads.
- **TRIMSYAPP**: discovery client, connection manager, durable upload queue owner, upload sender.

---

## 2) Fixed Port + Transport

- Transport: **TCP only**.
- PC listens on a **fixed port** (required).

Recommended default:
- `43821`

Rule:
- Treat the port as a protocol constant.
- This port is fixed for this system. If it ever changes, that is a protocol change and must require an explicit protocolVersion bump.
- Do not use multiple ports.
- Do not negotiate ports.

Rationale:
- Fixed port avoids configuration drift and makes firewall rules stable.

### Address families (IPv4 + IPv6) (v1 decision)
v1 supports **IPv4 and IPv6**.

Rules:
- TRIMSYPC should listen in a dual-stack mode when possible (accept IPv4 + IPv6 on the fixed port).
- Discovery may yield IPv6 addresses first on dual-stack networks; TRIMSYAPP must not treat that as an error.
- TRIMSYAPP should connect using the resolved address directly (do not assume IPv4).

Rationale:
- Reduces works on some Wi-Fi incidents caused by IPv6-first networks.
- Avoids future migration risk; address family support is best decided in v1.

---

## 3) Discovery (mDNS / Bonjour)

### PC advertising rules (required)
- PC advertises an mDNS service only when:
  - The TCP listener is actively listening, AND
  - The port is reachable from the LAN (best-effort check), AND
  - The PC is ready to accept connections.

### Phone discovery rules (required)
- Phone performs discovery continuously or periodically.
- Discovery failure is **not an error**. The correct UX state is: **Waiting for PC**.
- No manual IP entry.
- No pairing flow UI beyond:
  - first trust of a discovered PC, and
  - Trust this new PC? when the PC identity changes.

### Discovery burst mode (required)
Because TRIMSYPC may be launched only when it is time to upload, TRIMSYAPP must support a short-lived
high-intensity discovery/connect mode to minimize time to first connect.

Rules:
- When the user initiates upload (or when the app enters an upload now flow), TRIMSYAPP enters **burst mode**.
- Burst mode duration: 1030 seconds (recommended).
- During burst mode:
  - Restart discovery as needed if the platform requires it.
  - Attempt to connect immediately when the trusted PC is discovered.
- After burst mode:
  - Fall back to normal discovery with backoff to protect battery.

PC behavior in support of burst mode (recommended):
- On TRIMSYPC startup, send an mDNS announcement burst (e.g., several announcements over ~25 seconds)
  so the phone learns about the service quickly.

### Service identity (recommended shape)
- Service type (example): `trimsy-upload._tcp`.
- TXT fields (recommended):
  - `pcId=<uuid>` (required)
  - `displayName=<string>` (recommended)
  - `protocolVersion=<int>` (recommended)
  - `supportsResume=true|false` (recommended)
  - `busy=true|false` (optional)
  - `capacityHint=<int>` (optional)

Notes:
- TXT fields are optimization only. Correctness must not rely on them.

---

## 4) Identity = Smooth Reconnect (not security)

### PC identity (`pcId`) (required)
- PC generates a persistent unique ID:
  - `pcId = UUIDv4`
- PC stores it durably so that app updates do not change it.
  - Windows recommendation: store under a stable, machine-wide location (e.g. `%ProgramData%\Trimsy\pc_id.txt`) or an equivalent durable store.

### Phone trust model (required)
- Phone stores `trustedPcId` after first successful trust/connection.
- When `trustedPcId` exists:
  - Phone auto-connects only to the PC advertising the same `pcId`.
  - All other discovered PCs are ignored.

### Reset / new PC behavior (required)
If the PC is reinstalled/reset and advertises a different `pcId`:
- Phone must show an explicit prompt (one-time):
  - **New PC detected. Trust this PC?**
- If user accepts:
  - Replace `trustedPcId` with the new one.
  - Immediately run reconciliation and resume uploads.
- If user rejects:
  - Keep waiting; do not upload.

Rationale:
- Avoid silent wrong target while staying fully automatic in normal use.

---

## 5) Windows Firewall: One-Time Setup (PC-only)

Goal:
- Firewall never blocks in the dark. Either:
  - The user sees a one-time allow prompt, or
  - The app explicitly tells them what is needed.

### Hard UX rules
- Phone has **zero firewall logic**.
- If the PC cannot accept connections (firewall blocked / not listening), the PC must:
  - Not advertise, OR
  - Advertise but mark itself unreachable (not recommended), OR
  - Advertise and still show clear UI that it is not reachable.

Recommended: **do not advertise until reachable**.

### Implementation options
Option A (acceptable; simplest): rely on Windows Defender Firewall prompt
- Start listening on the fixed port.
- Windows prompts Allow access on first run.
- User clicks Allow.

Option B (more controlled): create an inbound firewall rule once
- On first run, request elevation (UAC) and create a persistent inbound allow rule:
  - Allow inbound TCP on the fixed port.
  - Remote address scope:
    - For maximum smoothness: **Any**.
    - For home-only tightness: **LocalSubnet** (or your LAN CIDR).
  - Profile scope:
    - For maximum smoothness: **Private + Public** (avoids Windows marked my Wi-Fi as Public breakage).
    - For home-only tightness: **Private**.
  - Prefer a program-scoped rule (attached to the TRIMSYPC executable) if feasible.

Important stability note:
- If the receiver executable path changes each update, Windows may treat it as a new app.
- Prefer a stable installation path for the PC receiver so firewall behavior stays stable.

---

## 6) Connection Model (State Machine)

### Principles (required)
- No UI thread blocking.
- Short socket timeouts.
- Connection loss never blocks the upload queue.
- Heartbeat exists; missed heartbeat triggers reconnect.

### Timeout guidance (recommended defaults)
- TCP connect timeout: 13 seconds.
- Read timeout during upload/control: 25 seconds (tuned alongside heartbeat).

### Recommended states
- `DISCOVERING`  scanning for trusted PC.
- `CONNECTING`  attempting TCP connect.
- `CONNECTED`  connected + handshake done.
- `UPLOADING`  actively sending chunks.
- `WAITING_FOR_PC`  trusted PC not found / not reachable.

### Heartbeat
- Interval: 25 seconds.
- Missed heartbeat threshold: 23 intervals, then reconnect.

### Backoff
- Use exponential backoff for reconnect attempts.
- Bound maximum retry rate to avoid storms.

### Aggressive connect rule (required)
- In burst mode, TRIMSYAPP may attempt reconnects more aggressively than normal, but it must still obey:
  - short connect timeouts, and
  - a hard upper bound on attempts per second.
- Outside burst mode, TRIMSYAPP must use battery-friendly backoff.

### Time base rule (required)
- All retry/backoff scheduling must use a monotonic clock.
- No logic may depend on wall-clock time for correctness (only for UI timestamps/logging).

---

## 7) Upload Queue (Phone)  Durable + Restart-safe

### Required properties
- Queue is durable (SQLite recommended).
- Each queued item includes:
  - `uploadUuid` (generated once and stable)
  - `relativePath` (or canonical file reference)
  - `sizeBytes`
  - `sha256Full`
  - `lastConfirmedOffset`
  - `state` (e.g. pending / uploading / awaiting_commit_ack / complete)

### Deletion rule
- Phone must not delete source bytes until it receives **COMMIT ACK**.

### Restart safety
- After app kill / reboot:
  - Queue is reloaded.
  - Reconciliation runs.
  - Upload resumes from the PC-provided offset.

---

## 8) Receiver Storage (PC)  Temp + Atomic Commit

### Required properties
- Never write directly to final location.
- Use temp files for partial uploads.
- Persist upload progress in a transactional DB (SQLite recommended).
- Completion uses an atomic rename/move into the final destination.

### Final file naming & path safety (required)
Receiver computes:
- `finalPath = uploadsRootDir / sanitize(relativePath)`

Sanitize rules (Windows-safe):
- Reject absolute paths, traversal (`..`), empty segments.
- Reject Windows-invalid characters (`<>:"/\\|?*` and control chars).
- Reject Windows trailing dot/space in any path segment.
- Reject reserved device names (case-insensitive) for the base name, even with extensions:
  - `CON`, `PRN`, `AUX`, `NUL`, `COM1..COM9`, `LPT1..LPT9`.
- Reject paths that exceed a conservative Windows path budget (fail early).

### Conflict policy (required)
Silent overwrite is forbidden.

If the computed final path already exists:
- If the receiver considers the uploadUuid already complete, it must return `already_complete` (idempotent).
- Otherwise, receiver must choose a deterministic conflict name derived from `uploadUuid` (e.g. a short hash suffix), and must guarantee the chosen conflict path does not already exist (add a bounded counter if needed).

Receiver must report the resolved final relative path back to TRIMSYAPP:
- `COMMIT_ACK.relativePathResolved`
- `COMMIT_ACK.conflictRenamed`

### Crash-safe commit rule (required)
To prevent duplicates across crashes/power loss, the receiver must ensure:
- If a final file exists for an uploadUuid, the DB converges to `complete` for that uploadUuid on startup.
- Commit must be idempotent: repeated COMMIT must never create a second final file.

Implementation shape:
- Write bytes to temp and fsync.
- Update DB progress.
- On COMMIT: verify full checksum, then transition DB to a "committing" state that records the chosen final path, then perform an atomic no-overwrite move, then mark DB `complete`.
- On startup: scan any `committing` rows; if the recorded final path exists and matches metadata, mark `complete`.

### Durability / ACK semantics (required)
Definitions:
- CHUNK_ACK is safe means the receiver will never later accept a different byte range at that offset without detecting it.
- COMMIT_ACK is safe means the final file exists at the resolved path and the receiver DB reflects `complete`.

Rules:
- Receiver may send `CHUNK_ACK` only after:
  - bytes are written to disk at the correct offset,
  - file buffers are flushed (`fsync` or platform equivalent),
  - and progress is committed durably in the receiver DB.
- Receiver may send `COMMIT_ACK` only after:
  - full checksum verified,
  - atomic move into final destination succeeded (no overwrite),
  - and receiver DB is durably updated to `complete` (with resolved final path recorded).

### Receiver availability (required)
TRIMSYPC is allowed to be **on-demand**.

Rules:
- TRIMSYPC may be opened only when it is time to upload.
- When TRIMSYPC is open, it must:
  - start listening immediately, and
  - advertise immediately (once reachable).
- When TRIMSYPC is closed, TRIMSYAPP must safely remain in Waiting for PC and never lose its queue.

Optional upgrade (recommended): always-on mode
- TRIMSYPC runs as an always-on background service or tray app and auto-starts on boot/login.
- This improves always available behavior, but is not required for correctness.

### Crash-only behavior
- Assume crash at any line.
- On startup:
  - Reconcile temp files vs DB state.
  - Ensure the system is always recoverable.

---

## 9) Upload Protocol (Correctness Core)

This section defines the minimal semantics needed to achieve practical exactly-once.

### Identifiers (required)
- `uploadUuid`: stable per file/item, created once by the phone.
- PC treats `uploadUuid` as the primary identity.

### Steps (required)
1) **START**
   - Phone: I want to upload `uploadUuid` with expected `sizeBytes` and `sha256Full`.
   - PC responds with:
     - `resumeOffset` (0..size)
     - server-side upload state (missing/partial/complete)

2) **CHUNK** (repeated)
   - Phone sends bytes for `[offset, offset+len)`.
   - Each chunk includes:
     - `offset`
     - `chunkSha256` (or equivalent per-chunk checksum)
  - PC validates and acks.
  - Phone must not advance `lastConfirmedOffset` until the chunk ACK is received.

3) **COMMIT**
   - Phone requests commit after last chunk.
   - PC verifies full-file checksum.
   - PC performs atomic finalize.

4) **COMMIT ACK**
   - PC returns success only after durable finalize is complete.
   - Phone marks item complete and may delete local source bytes.

  ### Handshake failure rules (required)
  HELLO is the only handshake message.

  HELLO fields:
  - `protocolVersion` (required)
  - `trustedPcId` (optional; if present, must match the receiver's `pcId`)

  Receiver behavior:
  - Unsupported protocolVersion  send `ERROR.UNSUPPORTED_VERSION` and close.
  - If `trustedPcId` is present and mismatches receiver `pcId`  send `ERROR.UNTRUSTED_CLIENT` and close.

  Phone behavior:
  - `UNSUPPORTED_VERSION` is non-retryable (requires app update).
  - `UNTRUSTED_CLIENT` is non-retryable automatically; requires user trust reset / re-trust action.

  ### Concurrency policy (v1) (required)
  v1 is serialized by default for stability:
  - TRIMSYAPP runs one active upload session per phone.
  - TRIMSYPC should advertise `recommendedParallelism=1` (and may enforce effective serialization via THROTTLE).

  Rationale:
  - Disk contention + fsync-heavy correctness is the first thing that fails under concurrency.

### Idempotency (required)
- Repeating any request must not create duplicates.
- Duplicate START must return the same `resumeOffset` and state.
- Duplicate CHUNK must not corrupt data; must ack safely.
- Duplicate COMMIT must not duplicate output; must return already complete success.

---

## 9.1) Backpressure & Flow Control

Goal:
- Prevent the phone from overwhelming PC CPU/disk.
- Slowdown/pause must never break correctness.

### Required semantics
- PC can respond to START/CHUNK with a throttle signal:
  - `SLOW` (continue, but reduce rate)
  - `PAUSE` (stop sending chunks temporarily)
- Phone must adapt by:
  - reducing chunk size and/or upload rate
  - backing off and retrying later

### Capacity advertisement (recommended)
- PC may expose best-effort capacity hints via discovery TXT:
  - `busy=true|false`
  - `capacityHint=<int>`

Notes:
- Capacity hints are advisory only; correctness must never rely on them.

### THROTTLE frame shape (required)
The receiver may send one or more THROTTLE frames at any safe message boundary.

Fields:
- `mode`: `PAUSE` (v1)
- `retryAfterMs`: how long to pause before continuing
- `reason`: optional string

Phone semantics:
- THROTTLE is retryable and should be handled without user-visible error.
- Phone should sleep for `retryAfterMs` (bounded) and then continue.

---

## 9.2) Error taxonomy & retry semantics (required)
Errors must include `code` and `message`.

Classification rules:
- Connection-fatal errors (receiver closes TCP after ERROR):
  - `UNSUPPORTED_VERSION`, `UNTRUSTED_CLIENT`, `PROTOCOL_VIOLATION`.
- Upload-fatal errors (uploadUuid cannot proceed without user action):
  - `PATH_INVALID`, `PATH_TOO_LONG`, `PERMISSION_DENIED`, `INSUFFICIENT_SPACE`.
- Retryable correctness errors:
  - `BAD_CHECKSUM` (retry chunk), `BAD_OFFSET` (re-START and resume).
- Retryable transient errors:
  - `INTERNAL` (retry with backoff; run reconciliation before resuming).

Idempotency rule:
- If receiver has already completed an uploadUuid, COMMIT must return `already_complete` and must include `relativePathResolved`.

---

## 10) Reconciliation (Most Important Self-healing Loop)

Reconciliation is what prevents stuck forever and makes the system feel instant.

### When to run (required)
- On phone startup.
- On PC startup.
- On connect.
- Periodically while connected.

### Ground truth query (required)
Phone asks PC for per-UUID truth:
- `complete` (final output exists and is durable)
- `partial(offset)` (bytes accepted up to offset)
- `missing`

### Phone correction rules (required)
- If phone thinks uploading but PC reports `missing`:
  - restart from offset 0 (same UUID)
- If phone thinks pending but PC reports `partial(offset)`:
  - resume from that offset
- If phone thinks pending/uploading but PC reports `complete`:
  - mark complete immediately and delete local source only if COMMIT ACK semantics are satisfied by complete

### Bounded behavior (required)
- Reconciliation must be efficient for large queues (paging or changed since recommended).

---

## 10.1) Single-writer & Concurrency Safety

### Required rules
- Exactly one active writer per `uploadUuid`.
- PC must reject concurrent uploads for the same `uploadUuid` (or serialize them safely).
- PC must enforce this at the storage layer (DB constraint or lock), not just in-memory.

Recommended DB constraint:
- Unique row keyed by `uploadUuid` with a state machine (`missing`/`partial`/`complete`).

---

## 11) No Dark Failures UX Rules

### Phone UI (required)
- Only show:
  - Connected
  - Uploading
  - Waiting for PC
- Avoid scary errors for recoverable conditions.

### PC UI / logs (required)
- If not reachable (not listening / firewall blocked):
  - Show a clear status: Receiver not reachable. Click to allow firewall.
- Logs must include:
  - Connection attempts
  - Listener status
  - Firewall setup status
  - Per-upload state transitions by `uploadUuid`

### Explain itself when idle (required)
- When idle, both sides must explain why:
  - Waiting for PC (not found)
  - Waiting for PC (found, not reachable)
  - Paused (PC asked to pause)
  - Idle (no files queued)

---

## 12) Minimal First Run Experience

Target behavior:
1) User starts TRIMSYPC.
2) If Windows prompts firewall allow:
   - user clicks Allow.
3) PC starts listening and advertising.
4) Phone discovers and connects automatically.
5) If `trustedPcId` not set yet:
   - phone does a one-time Trust this PC? prompt.
6) Reconciliation runs.
7) Upload proceeds automatically.

---

## 12.1) Long-run Stability Targets

Required behaviors:
- Handles sleep/wake.
- Handles Wi-Fi roaming (SSID/AP changes) without manual recovery.
- Handles long idle periods (minutes/hours) and resumes cleanly.
- Handles large backlogs (many files) without UI jank or retry storms.

Recommended validation:
- Multi-hour soak test with deliberate Wi-Fi drops.
- Power-cycle tests during active uploads.

---

## 13) Implementation Checklist (YES/NO)

- Scope: local LAN only.
- Scope: phone uploads to exactly one PC.
- Scope: upload-only (phone  PC).
- Correctness: never duplicates/corrupts/loses queued files.
- Network: TCP only.
- Network: fixed listening port on PC.
- Discovery: mDNS/Bonjour.
- Discovery: PC advertises only when reachable.
- Discovery: phone auto-discovers; no manual IP entry.
- UX: discovery failure is not an error (Waiting for PC).
- UX: phone supports burst mode for fast connect when PC is opened.
- Firewall: handled once on first launch.
- Firewall: rule/prompt is visible (no dark blocking).
- Identity: PC has persistent `pcId`.
- Identity: phone pins `pcId` after first trust.
- Identity: unknown PCs are ignored.
- Identity: reinstall/update preserves identity (or re-pairs explicitly).
- Connection model: explicit state machine.
- Connection model: short TCP timeouts (no blocking).
- Connection model: heartbeat (25s) and reconnect on miss.
- Connection model: connection loss never blocks queue.
- Phone queue: durable storage (SQLite).
- Phone queue: per-file UUID + path + size + full SHA-256 + offset + state.
- Phone queue: never delete before COMMIT ACK.
- PC receiver: uses temp files; never writes directly to final.
- PC receiver: atomic rename on completion.
- PC receiver: transactional DB for metadata/progress.
- PC receiver: startup reconciliation exists.
- PC receiver: on-demand supported (open only when uploading).
- Protocol: START returns correct resume offset.
- Protocol: chunked uploads.
- Protocol: every chunk has checksum.
- Protocol: chunk ACK required before advancing.
- Protocol: full-file checksum verified.
- Protocol: explicit COMMIT + COMMIT ACK.
- Protocol: fully idempotent across START/CHUNK/COMMIT.
- Exactly-once (practical): repeats cause no duplication/corruption.
- Backpressure: PC can signal SLOW/PAUSE; phone adapts without breaking correctness.
- Crash-only design: no reliance on graceful shutdown; restart always resumes.
- Reconciliation: runs on phone startup, PC startup, periodic while connected.
- Reconciliation: phone asks ground truth; drift is self-healing.
- Concurrency: single-writer per UUID; PC enforces via lock/DB constraint.
- Time/retry: monotonic clocks for retries; bounded backoff (no storms).
- Observability: truthful states + logs; system explains itself when idle.
- Long-run stability: handles roaming/idle/large backlogs; soak/power tests recommended.

---

## 14) Build Plan (TRIMSYPC + TRIMSYAPP)

This section is the actionable plan to build the system.

### 14.1) TRIMSYPC (PC receiver)  what we need to implement

Core components
- **Receiver core**: TCP server on `0.0.0.0:43821` with per-connection session handling.
- **Discovery advertiser**: mDNS service `trimsy-upload._tcp` with TXT fields (at least `pcId`, `protocolVersion`).
- **Reachability gate for advertising**: advertise only when the listener is active and we believe we are reachable.
- **Durable receiver DB** (SQLite): tables keyed by `uploadUuid` holding state (`missing/partial/complete`), offsets, checksums, temp file path, final path, timestamps.
- **Temp file writer**: random-access writes at offsets, fsync strategy, safe close.
- **Atomic finalize**: rename/move temp  final only after checksum verify.
- **Single-writer lock**: DB constraint/transaction that prevents two writers for same `uploadUuid`.
- **Throttle/backpressure**: ability to respond `SLOW/PAUSE` based on CPU/disk pressure.
- **UI / tray status** (minimal but mandatory): shows Listening, Not reachable / firewall, Receiving, Paused, Idle.
- **Logging**: structured logs for connection lifecycle + upload state transitions.

First-run + stability components
- **Persistent `pcId` store**: generated once and stored durably (survives updates).
- **Firewall bootstrap** (choose one):
  - Windows prompt-based allow (simplest)
  - Programmatic rule creation (recommended for no dark failures)
    - inbound allow TCP 43821, RemoteAddress=Any, Profiles=Private+Public (max smoothness)
- **Network/sleep resilience**: listener restarts on interface changes; re-announce mDNS on startup.

PC receiver protocol endpoints (must exist)
- **Handshake**: client sends `protocolVersion`; server replies with accepted version + `pcId`.
- **Ground truth query** for reconciliation: returns status for many `uploadUuid` (paged).
- **START**: idempotent; returns `resumeOffset` + state.
- **CHUNK**: idempotent; validates checksum; acks only after bytes are durable.
- **COMMIT**: idempotent; verifies `sha256Full`; finalizes atomically; returns COMMIT ACK.
- **Throttle response**: server may return `SLOW/PAUSE` at any time.

### 14.2) TRIMSYAPP (Phone)  what we need to implement

Core components
- **Discovery client**: mDNS browse for `trimsy-upload._tcp`.
- **Trust + targeting**:
  - first connection prompts Trust this PC? and stores `trustedPcId`
  - after trust: connect only to matching `pcId`
- **Connection manager**: state machine + heartbeat + short timeouts + reconnect/backoff.
- **Burst mode**: 1030s aggressive discovery/connect when user initiates upload.
- **Durable upload queue** (SQLite): per-item `uploadUuid`, file reference, size, `sha256Full`, offset, state.
- **Uploader**: START  CHUNK loop  COMMIT; advances offset only on ACK.
- **Backpressure handler**: reacts to `SLOW/PAUSE` by reducing rate / pausing correctly.
- **Reconciliation runner**: runs on startup, on connect, periodically; corrects local queue state.
- **UI**: only these user-facing states are required: Connected / Uploading / Waiting for PC.

Phone file IO + hashing rules
- Hashes are computed from the exact bytes uploaded.
- Chunk checksum must match the same chunk bytes that are sent.
- Deletion rule is strict: delete local source only after COMMIT ACK.

### 14.3) What we might be missing (common failure points)

- **Destination mapping + path safety** (must be explicit):
  - Define how `relativePath` is mapped into a final folder.
  - Reject path traversal (`..`), absolute paths, and invalid Windows filename characters.
  - Define overwrite policy (recommended: never overwrite silently; use deterministic conflict naming).
- **Disk space + permissions**:
  - PC must check available disk space before/while receiving and fail clearly (`ERROR: INSUFFICIENT_SPACE`) or throttle.
  - PC must ensure output directory is writable (or show a clear UI error).
- **Temp cleanup / GC**:
  - Define when abandoned temp uploads are deleted (e.g., after N days with no progress).
  - Cleanup must never delete a temp file that the DB still considers active.
- **Protocol limits (to prevent OOM / abuse-by-bug)**:
  - Define maximum JSON payload size.
  - Define maximum chunk size.
  - Define maximum concurrent uploads.
- **Paging strategy** for reconciliation: thousands of queued items must be fast.
- **Large file strategy**: chunk sizing, fsync cadence, and memory limits.
- **Multiple interfaces**: PC may have Wi-Fi + Ethernet; ensure we advertise correct addresses.
- **IPv6**: decide whether to support it; mDNS often returns IPv6 first.
- **Clock/time drift**: ensure no correctness depends on wall-clock; only monotonic for retries.
- **Duplicate file paths**: define deterministic mapping from `uploadUuid`  final destination; prevent overwrites.
- **Crash/power loss** at every point: verify DB+temp files recover cleanly.

### 14.4) Milestones (build order)

1) TRIMSYPC: listener + minimal START/CHUNK/COMMIT with temp+atomic finalize (no discovery yet)
2) TRIMSYAPP: queue + connect to manual IP (dev-only) + full upload correctness
3) Add reconciliation endpoints + phone reconciliation loop
4) Add mDNS discovery + trust (`pcId`) + burst mode
5) Add firewall bootstrap + no dark failures UI
6) Add backpressure + soak testing + power-cut testing

---

## 15) Wire Format (Framing + Schemas)

This is the exact on-the-wire format so both sides implement the same thing.

### 15.1) Transport framing (required)

Single TCP connection carries a sequence of frames.

Frame header (fixed 16 bytes, network byte order / big-endian):
- `magic` (4 bytes): ASCII `TRMS`
- `version` (1 byte): `1`
- `type` (1 byte): see message types below
- `flags` (2 bytes): reserved (0)
- `streamId` (4 bytes): used to correlate request/response (uint32)
- `length` (4 bytes): payload length in bytes (uint32)

Payload:
- For JSON messages: UTF-8 JSON bytes
- For CHUNK bytes: raw bytes

Correlation + ordering rules (required)
- `streamId` is chosen by the requester.
- Any response to a request must reuse the same `streamId`.
- For simplicity and correctness, v1 requires in-order processing:
  - Phone must not send a new `CHUNK_HEADER` for an `uploadUuid` until it has received the corresponding `CHUNK_ACK`.
  - PC may reject out-of-order chunks with `ERROR: BAD_OFFSET`.

Size limits + defaults (required)
- Max JSON payload length: 1 MiB.
- Max chunk length: 4 MiB.
- Recommended default chunk length: 1 MiB.

Rules:
- Receiver must support partial reads and buffer until full frame is available.
- Any unknown `version` or invalid `magic` => close connection.

### 15.2) Message types (required)

Control messages (JSON payload):
- `0x01` HELLO
- `0x02` HELLO_ACK
- `0x03` PING
- `0x04` PONG
- `0x10` RECONCILE_QUERY
- `0x11` RECONCILE_PAGE
- `0x20` START
- `0x21` START_ACK
- `0x22` CHUNK_HEADER
- `0x23` CHUNK_ACK
- `0x24` COMMIT
- `0x25` COMMIT_ACK
- `0x30` THROTTLE (SLOW/PAUSE)
- `0x7F` ERROR

Data message:
- `0x2A` CHUNK_BYTES (raw bytes payload)

### 15.3) Required JSON schemas (v1)

All JSON messages include:
- `type` (string)
- `protocolVersion` (int)

`HELLO` (phone  pc)
- `type`: `"HELLO"`
- `protocolVersion`: `1`
- `appInstanceId`: string (stable per phone install)
- `trustedPcId`: string | null

`HELLO_ACK` (pc  phone)
- `type`: `"HELLO_ACK"`
- `protocolVersion`: `1`
- `pcId`: string
- `serverTimeUnixMs`: number (for logs only)

`RECONCILE_QUERY` (phone  pc)
- `type`: `"RECONCILE_QUERY"`
- `protocolVersion`: `1`
- `pageSize`: number (recommended 200)
- `cursor`: string | null

`RECONCILE_PAGE` (pc  phone)
- `type`: `"RECONCILE_PAGE"`
- `protocolVersion`: `1`
- `nextCursor`: string | null
- `items`: array of:
  - `uploadUuid`: string
  - `state`: `"missing" | "partial" | "complete"`
  - `resumeOffset`: number (0..size)
  - `sha256Full`: string | null (present when complete)

`START` (phone  pc)
- `type`: `"START"`
- `protocolVersion`: `1`
- `uploadUuid`: string
- `relativePath`: string
- `sizeBytes`: number
- `sha256Full`: string

`START_ACK` (pc  phone)
- `type`: `"START_ACK"`
- `protocolVersion`: `1`
- `uploadUuid`: string
- `state`: `"missing" | "partial" | "complete"`
- `resumeOffset`: number

`CHUNK_HEADER` (phone  pc)
- `type`: `"CHUNK_HEADER"`
- `protocolVersion`: `1`
- `uploadUuid`: string
- `offset`: number
- `length`: number
- `chunkSha256`: string

Immediately after `CHUNK_HEADER`, phone sends one `CHUNK_BYTES` frame with exactly `length` bytes.

`CHUNK_ACK` (pc  phone)
- `type`: `"CHUNK_ACK"`
- `protocolVersion`: `1`
- `uploadUuid`: string
- `ackedOffsetEnd`: number

`COMMIT` (phone  pc)
- `type`: `"COMMIT"`
- `protocolVersion`: `1`
- `uploadUuid`: string
- `sizeBytes`: number
- `sha256Full`: string

`COMMIT_ACK` (pc  phone)
- `type`: `"COMMIT_ACK"`
- `protocolVersion`: `1`
- `uploadUuid`: string
- `status`: `"ok" | "already_complete"`

`THROTTLE` (pc  phone)
- `type`: `"THROTTLE"`
- `protocolVersion`: `1`
- `mode`: `"SLOW" | "PAUSE"`
- `retryAfterMs`: number

`ERROR` (either direction)
- `type`: `"ERROR"`
- `protocolVersion`: `1`
- `code`: string (e.g. `BAD_CHECKSUM`, `BAD_OFFSET`, `BUSY`, `INTERNAL`)
- `message`: string
- `uploadUuid`: string | null

### 15.4) Correctness rules tied to framing (required)

- PC must not send `CHUNK_ACK` until bytes are durable (written + metadata committed).
- Phone must not advance `lastConfirmedOffset` until `CHUNK_ACK`.
- `COMMIT_ACK` is sent only after atomic finalize is complete.
- Any disconnect at any point must be recoverable via reconciliation + START resume.

---

## 16) Test Matrix (Must Pass)

These are the tests that prove smooth + constant + no corruption + no loss.

### Connectivity
- PC not running: phone shows Waiting for PC (no errors).
- PC starts while phone is waiting: connect within burst window.
- Firewall blocked: PC shows explicit status; phone remains waiting.
- Wi-Fi drop mid-upload: resumes from correct offset, no duplicates.
- Sleep/wake (both devices): resumes cleanly.

### Correctness
- Kill phone app mid-chunk: no corruption; resumes.
- Kill PC mid-chunk: temp file + DB recover; resumes.
- Power cut PC during finalize: either complete or resumable; never partial final file.
- Duplicate START/CHUNK/COMMIT replay: no duplication; no corruption.
- Bad chunk checksum: rejected; phone retries.
- Wrong offset: rejected; phone re-runs START and resumes.

### Scale
- 10k queued items: reconciliation paging works; UI remains responsive.
- Large files (e.g. 210GB): chunking does not OOM; throughput stable.

### Backpressure
- PC sends SLOW: phone reduces rate; correctness unchanged.
- PC sends PAUSE: phone stops sending; resumes after retryAfterMs.
```

---

## [docs/CHECKPOINT_2026-01-17.md](docs/CHECKPOINT_2026-01-17.md)

(Inlined from git show 1424fa3:docs/CHECKPOINT_2026-01-17.md)

```md
# Checkpoint  2026-01-17

## Why this checkpoint exists

Were enforcing the core goal: **one backend truth** shared by **TrimsyApp + TrimsyTrack + TrimsyPC**, with anti-drift automation so clients/specs cant silently diverge.

## Status (as of this checkpoint)

- Functions pipeline is green: build + test + lint.
- Anti-drift regression is in place:
  - docs/BACKEND_EXPORTED_ENDPOINTS.md is enforced by functions/src/__tests__/export_surface_doc.test.ts
- Write gating is enforced by tests:
  - app_id required for write-like operations (covered in functions/src/__tests__/canonical_invariants.test.ts)
- New team-facing system contract doc created:
  - docs/TRIMSY_SYSTEM_CONTRACT.md
  - Linked from BACKEND_CONTRACT.md and docs/CONNECTING_CLIENTS.md

## Key contract mapping (kvalues)

- kvalue == receipt line item
  - Backend: receiptRowCreate (description + lineAmountCents)
- Product owns selected receipt row(s) via cost allocations
  - Backend: productCostAllocate + finalizeLogSnapshotCreate.costAllocations
- SKU is Category+Number
  - Backend: productSetCategoryNumber (categoryNumberId)

## Before sending coders links

- Ensure todays changes are pushed to GitHub (otherwise they will not see docs/TRIMSY_SYSTEM_CONTRACT.md).

## Tomorrows first action

- Use docs/TRIMSY_SYSTEM_CONTRACT.md as the single handoff to coders.
```

---

