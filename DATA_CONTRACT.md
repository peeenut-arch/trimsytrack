# TrimsyTRACK – Data Contract v1.0

## Source of Truth (Backend Law)

The canonical backend contract and validation rules live in the cloned repository:

- `BACKENDTRIMSY/` (cloned from `https://github.com/peeenut-arch/BACKENDTRIMSY.git`)

Treat that repo as the absolute truth. Before connecting to the backend, all contract assumptions in this app must be checked against it.

**Profile Contract: One Account → One Profile → Backend-Authoritative Truth**

This is the canonical contract for identity, scoping, data ownership, and cross-device consistency. All features must comply with this hierarchy.

---

## 🔒 SYSTEM LAW (LOCKED IN)

**There is exactly one real profile per account.**
- It defines identity and presentation.
- All canonical truth is scoped to it.
- Visual assets (logos, signatures) are local and per-profile.
- The backend never guesses, never invents, and never sees what is not real.

If a user wants separation: new email, new account, new universe of truth. That's intentional and explicit.

---

## SYSTEM LAW – CALLABLES CONTRACT (CLIENT-FACING)

TrimsyTRACK uses Firebase Callable Functions for the System Law / Preflight flow (no base URL, no bearer header plumbing).

### Callable vs HTTP response shape

Important: **Callable Functions return the raw result object**.

The `{ ok, result, error }` envelope exists on the **HTTP** endpoints (`apiV1/*`) and is used by desktop/non-Firebase-SDK clients.

### HTTP Envelope (apiV1/*)

All `apiV1/*` responses use the same envelope:

```json
{
  "ok": true,
  "result": { "...": "..." },
  "error": null
}
```

On failure:

```json
{
  "ok": false,
  "result": null,
  "error": { "message": "..." }
}
```

### lawGetCallable (Callable) → LawGetResult

This is the canonical model the Android app decodes from `lawGetCallable`.

`LawGetResult`:

```json
{
  "packSha256": "<sha256>",
  "digestMarkdown": "# ...",
  "index": {
    "received": [
      {
        "userDocNumber": "1",
        "title": "...",
        "filename": "...",
        "sha256": "<sha256>"
      }
    ]
  }
}
```

Notes:
- `packSha256` is the immutable identifier for the current law pack.
- `digestMarkdown` is user-facing and must be displayed as part of the preflight.
- `index.received[]` lists the documents that make up the pack.

## IDENTITY HIERARCHY (FOUNDATIONAL)

```
Firebase Auth UID (authentication boundary)
        ↓
Account (one person, one email)
        ↓
Profile (one business/identity, one universe of canonical truth)
        ↓
Canonical Truth (products, receipts, trips, transactions)
```

### Key Rules

- **Firebase UID** = authentication only. Not scoped, global.
- **Profile** = identity + presentation + ownership scope for all canonical data.
- **Canonical truth** = backend-authoritative reality (trips, receipts, ledger).
- **Apps** = renderers + intent senders (not writers of truth).
- **Backend** = only writer of truth; always injects `profileId` into all writes.

---

## TRIPS + EVIDENCE: LOCAL IDS vs CLOUD METADATA

This app currently has **local-only media evidence** (synced phone → PC via the Evidence content provider), while **trip + evidence metadata** can be uploaded to cloud snapshots.

### Trip logging (what we store)

Trips are stored in Room as `TripEntity` and created through `TripRepository.createTrip(...)`.

**Trip identifiers**

- `TripEntity.id` (Long, auto-increment) = **Trip# (local DB primary key)**
  - Used for UI display ("Trip #123") and for evidence folder names.
  - Safe to use inside this device/DB and for phone → PC evidence transfer.
  - Not globally stable across multiple devices.

- `TripEntity.clientRef` (String UUID) = **TripID (stable client reference)**
  - Generated automatically when creating a trip.
  - Intended for future backend sync / idempotency.

**Location identifiers (explicit, backend-friendly)**

Trips also carry explicit IDs that tell the backend what kind of location this trip ended at:

- `TripEntity.storeLocationId` = **storelocationID** (e.g. `storelocation:<storeId>`)
- `TripEntity.postOmbudId` = **postombudID** (e.g. `postombud:<storeId>`) when the place is a postombud

These are generated automatically on trip creation so the backend can answer queries like:

"June 10, 22:00 → give me the storelocationID/postombudID for the trip".

**Trip metadata fields (the important ones)**

- `businessPurpose` = **Syfte**
- `day` + `startedAt`/`endedAt` + `timeZoneId` = **Date/Time**
- `storeNameSnapshot` + `citySnapshot` + coordinates = **Location/Store name (incl postombud formatting)**

**Date/Time rule (important):** whenever a `day` field is stored alongside a timestamp, `day` must be derived from that timestamp using the same `timeZoneId` (not from "now"). This prevents day-bucket drift when GPS/location timestamps are delivered late or batched.

### Driver + vehicle fields (synced)

Driver identity fields required for a complete driving journal are synced in snapshot settings (`DriverSettings`):

- `driverName` = driver’s name
- `vehicleRegNumber` = car registration number

### Evidence / media (local-only)

Evidence (photos/scans/PDFs) is stored as `AttachmentEntity` and added via `TripRepository.addAttachment(...)`.

- `AttachmentEntity.id` (Long, auto-increment) = **EvidenceId (local)**
- `AttachmentEntity.tripId` (Long) = link to the trip's **local Trip#**

Evidence files are stored under:

`files/evidence/<tripId>/<file>`

and the app enforces a canonical filename containing the local trip id and evidence id.

**Rule:** Evidence bytes/media are never uploaded to backend. They are shared to the companion/PC using `EvidenceProvider`.

### Parking fee receipts (parkingticketID)

Parking/traffic fee receipts are modeled as:

- `TripEntity.parkingTrafficFeeMinor` = cost (minor units)
- `TripEntity.parkingTicketId` = **parkingticketID** (String UUID)

When a parking fee is added in the Trip UI, we ensure `parkingTicketId` exists and attach the receipt photo as local-only evidence (PC-synced).

**Parking ticket metadata uploaded to cloud snapshots** (media excluded):

- `parkingTicketId` (parkingticketID)
- `tripId` (Trip# local)
- `costMinor`
- `syfte`
- `date`, `time`, `timeZoneId`
- `storeNameSnapshot`, `citySnapshot`, `storeLatSnapshot`, `storeLngSnapshot`, optional `endAddressSnapshot`

This is exported as `ParkingTicketDto` in DriverData snapshots.

---

## PROFILE: WHAT IT IS, WHAT IT IS NOT

### Profile IS:
- Business identity (name, org number, VAT registration)
- Personal identity (display name, avatar)
- Branding container (logos, signatures, document templates)
- Preferences (language, theme, formatting, defaults)
- Ownership scope for canonical truth

### Profile IS NOT:
- Accounting logic (VAT, ledger rules, chart of accounts)
- Storage logic (file management, caching)
- Validation logic (how transactions are booked)
- Anything that affects invariants or canonical data integrity

**Profile never decides *how* things are booked—only *who owns* them and *how they look*.**

---

## PROFILE DATA MODEL (CONCEPTUAL)

```json
{
  "profileId": "uuid",
  "createdAt": 1234567890,
  "version": 1,

  "profileType": "BUSINESS", 
  
  "person": {
    "displayName": "John Doe",
    "avatarUri": "content://..."
  },
  
  "business": {
    "name": "Doe Consulting",
    "organisationNumber": "12345678",
    "vatRegistrationNumber": "SE123456789012",
    "address": "123 Main St",
    "country": "SE",
    "contactInfo": {
      "email": "contact@example.com",
      "phone": "+46..."
    }
  },
  
  "preferences": {
    "language": "sv",
    "theme": "dark",
    "documentDefaults": {
      "logoId": "logo_main",
      "signatureId": "sig_default"
    }
  }
}
```

`profileType` values:
- `PRIVATE`
- `BUSINESS`

Backend implementation detail: the callable currently expects `profileKind` and also accepts `profileType` as an alias.

**Rule:** Version increments on every backend change. Clients always *replace*, never *merge*.

---

## PROFILESCOPE PATTERN (THE SAFETY MECHANISM)

ProfileScope is not "profile data". It is a **filesystem + storage namespace**.

This is the heart of safety. Any profile-specific data—whether files, caches, or preferences—lives under:

```
/profiles/<accountId>/<profileId>/
```

Applied consistently for:
- Logo files
- Signature images
- Profile picture
- Document preferences
- Local caches
- Document templates

**Guarantees:**
- No bleed between profiles
- No collision between accounts
- No accidental reuse
- Deterministic cleanup on profile deletion

---

## VISUAL ASSETS (LOGOS, SIGNATURES, DOCUMENT TEMPLATES)

### Storage Rule

All visual assets are:
- **Per profile** (scoped under `profileId`)
- **Stored locally** (app filesystem or cache)
- **Referenced by stable logical IDs** (not file paths)
- **Never part of canonical truth**

They are presentation assets, not data. The backend never sees them.

### Asset Types

- Profile picture
- Document signature preset (e.g., "John's legal signature")
- Document logo variants (e.g., "Doe Consulting logo for contracts")
- Document templates (preferred layout, color scheme, fonts)

### Linking Logic (Safe by Design)

```
Document (e.g., "contract_2024_01")
        ↓
References logical logo ID (e.g., "logo_main")
        ↓
Profile preferences map ID → file (at render time)
        ↓
App resolves file → renders with logo
```

**Key:**
- Documents reference logical IDs, not paths.
- Resolution happens at render time.
- Backend never cares about the image.
- Backend only cares that the document exists.
- If logo is missing: fallback rules apply (no error, no data loss).

---

## 1) Identity + Scope
- **`profileId` is the scope boundary** for all user-owned data.
  - Every row that is "user-owned" must include `profileId: String`.
  - Every DAO query targeting a single row must filter by both `profileId` and the row's `id`.
- **Never treat local Room `id` as globally unique** without `profileId`.
  - Example: `TripEntity.id` is only meaningful together with `TripEntity.profileId`.

### Canonical naming
- **TripID** → `TripEntity.id: Long`
- **DreciptID** → human-friendly receipt code string (currently formatted by `SettingsStore.formatDreciptID(...)`)
- **EvidenceID** → `AttachmentEntity.id: Long` (all media is “Evidence”: photos/screenshots/scans/PDFs)
  - Human-facing format: `evID` counting from 1 (this is the Room PK; always display as `evID=<id>`)

Evidence linkage truth:
- Evidence is always tied to a Trip.
- Canonical identity for a claimed/exported evidence item is:
  - `(profileId, evidenceId, tripId)`
- Evidence bytes are stored locally under the app evidence folder using a **deterministic path** that encodes the linkage:
  - `relativePath = <tripId>/<fileName>`
  - `fileName` must include both `tripId` and `evidenceId` so that PC sync can relink media later even though the backend never receives evidence.
- “Date/time/place” for evidence is derived from the linked trip (a trip is a snapshot):
  - date: `TripEntity.day`
  - time: `TripEntity.createdAt`
  - place: `TripEntity.storeNameSnapshot` (+ `citySnapshot` when available)

## 2) Primary keys vs. sync IDs
- **Room primary keys** (`id: Long`) are local-only and auto-generated.
- **Sync identity** (if applicable) must be separate fields:
  - `clientRef`: client-generated stable UUID for matching.
  - `backendId`: backend authoritative id.
- Do not overload/repurpose Room `id` for backend IDs.

## 3) Linkage rules
- Attachments:
  - `AttachmentEntity.tripId` links to `TripEntity.id`.
  - Attachment reads/writes must always be within the same `profileId` scope.
- Prompts:
  - `PromptEventEntity.linkedTripId` is the link from a prompt to the created trip.

## 4) “Default profile” fallback
- Repository reads that depend on active profile should use:
  - `settings.profileId` and fall back to `"default"` if blank.
- Don’t create new features that persist data with blank `profileId`.

## 5) Migration / legacy rows
- Legacy rows may exist with `profileId == ""`.
- When activating a profile, code should call the relevant `claimUnscoped(profileId)` migrations.
- If you add a new scoped table, add a `claimUnscoped` path if you expect legacy data.

## 6) Notifications
- **Notification IDs are not entity IDs.**
- Any notification that refers to a specific entity should be:
  - Stable per `(profileId, entityId)` so it’s replaceable.
  - Unique enough that multiple entities can notify without clobbering each other.
- If a notification should open an entity, deep-link via `Intent` extras (e.g. `tripId`) into `MainActivity` and let navigation route from there.

## 7) Background work (WorkManager)
- Unique work naming must be scoped.
  - Use a stable work name that includes `(profileId, entityId)` when work is entity-specific.
  - Use a global unique name only when the job is truly global (one-at-a-time).
- Work should be idempotent:
  - It’s always safe if it runs twice.
  - It’s safe if it runs late.
- Workers must not assume UI is present.

## 8) Settings (DataStore)
- Settings keys are global per app install.
- Keep defaults in one place and use safe bounds (`coerceIn`) for user-editable numeric settings.

## 9) “Definition of done” for new persisted features
- DAO methods exist for:
  - scoped get-by-id
  - scoped list/observe
  - scoped delete
- Repo enforces profile scoping.
- Any background work / notification that references the data includes:
  - stable identifiers
  - correct scoping
  - safe defaults

## 10) Caching & “pull once” behavior
- **Trips are snapshots.** Once a trip is created, we do not retroactively edit its start/end labels, coordinates, or distance if store metadata changes later.
- **Driving distance is cached.** Route computations are cached and reused; recomputation should only happen when the cache key changes.
- **Google Places details are cached after first fetch.** When we fetch formatted address/opening hours for a place, we persist it locally so future views can work offline and do not require refetch.
  - This cache is **local-only**: it is **not uploaded** to the backend and it is **not overwritten** by “Download & restore”.
  - Once cached, it must not be overwritten/cleared (to avoid staleness/override bugs).
  - Rationale: avoid backend storage bloat; keep UX stable if backend is wiped.
- **Destructive restore is explicit.** “Download & restore” is the only operation allowed to replace local DB/settings from the backend.
---

## BACKEND RESPONSIBILITIES (PROFILE-AWARE, NOT PROFILE-DRIVEN)

The backend is the **sole writer of canonical truth**. It must:

✅ **Must:**
- Authenticate Firebase UID (via `Authorization: Bearer <token>`)
- Resolve exactly one profile per account
- Inject `profileId` into all writes
- Scope all canonical truth by `profileId`
- Reject cross-profile access (even if technically possible)
- Canonicalize all timestamps and IDs before returning
- Return canonical objects (clients overwrite local data with backend response)
- Treat profile as read-only context only

❌ **Must never:**
- Store logos, signatures, or visual assets
- Store profile picture files
- Store document templates or layout preferences
- Branch business logic on profile fields
- Guess, invent, or assume profile data
- Accept profile modifications except for controlled fields
- Allow clients to write directly to canonical data

---

## APP RESPONSIBILITIES (RENDER + SEND INTENTS)

The app is a **renderer and intent sender**. It:

✅ **May:**
- Authenticate and fetch profile
- Render identity + branding from profile data
- Store visual assets locally under `profileScope`
- Generate PDFs with logos and signatures
- Render receipts, documents, contracts
- Send transactions/events to backend
- Use cached data for offline operation

❌ **May never:**
- Invent canonical data (truth must come from backend)
- Bypass backend validation or rules
- Write directly to canonical tables without backend coordination
- Store logos, signatures in backend
- Assume profile data hasn't changed between syncs
- Create new profiles or modify profile identity

---

## PC (COMPANION APP) RESPONSIBILITIES (SYNC + EXPORT)

The companion app pulls data from the phone:

✅ **May:**
- Pull evidence files from `EvidenceProvider`
- Pull visited stores from `VisitedStoresProvider`
- Pull canonical trip data from backend
- Render documents with logos
- Export receipts and reports
- Deduplicate files locally

❌ **May never:**
- Write to backend (read-only)
- Send intents to phone directly
- Modify canonical trip data
- Assume phone state without re-syncing

---
## 11) Backend sync (API + behavioral contract)

This section defines what the app expects from the backend and what the backend can expect from the app.

### 11.1 Common request requirements (all backend endpoints)
- Requests must include:
  - `Authorization: Bearer <Firebase ID token>`
  - `X-App-Id: <app-id>` (multi-app isolation; compiled as `BuildConfig.APP_ID`)
  - `X-Profile-Id: <profile-id>` (per-profile isolation; `settings.profileId` or `"default"`)
- All endpoints are versioned under `/api/v1/...`.

### 11.2 Outbox trip-create sync (small, incremental)
- Purpose: push newly created trips to backend without uploading the full database.
- Trigger: when a trip is created locally, the app enqueues an outbox item (`TYPE_TRIP_CREATE`).
- Data completeness requirement (Skatteverket körjournal; odometer excluded): backend must receive, per trip:
  - Date (`day`) and timestamps (`startedAt`, `endedAt`) + `timeZoneId`
  - Start location snapshot (label + lat/lng, and optional address + place type)
  - End location snapshot (storeNameSnapshot + citySnapshot + lat/lng, and optional address + place type)
  - Distance (`distanceMeters`) and duration (`durationMinutes`) + `distanceMethod`
  - Purpose (`businessPurpose`) and classification (`isBusiness`)
  - Optional fees: `parkingTrafficFeeMinor`
- Idempotency:
  - The app sends `Idempotency-Key: <uuid>`.
  - Backend must dedupe on `Idempotency-Key` and return the same canonical object for retries.
- Canonicalization:
  - Backend is authoritative for `backendId` and canonical timestamps.
  - Backend returns a canonical trip object; the app overwrites local trip fields with the canonical values and marks `syncStatus = SYNCED`.
- Failure semantics:
  - 4xx → treated as rejected; local trip becomes `REJECTED`.
  - 5xx/network → treated as retryable; outbox stays pending/failed-retry.
- Network expectations:
  - If the outbox is empty, the worker should perform no backend calls (so scheduled runs are effectively “free”).

### 11.3 Snapshot upload/download (bulk, destructive restore)
- Purpose: move a full “profile snapshot” across devices or recover from backend.
- Upload (`PUT /api/v1/driverdata/{driverId}`):
  - App uploads a full `DriverData` JSON payload.
  - Backend returns a canonical `DriverData` JSON payload.
  - App immediately restores local DB/settings from that canonical response.
- Download (`GET /api/v1/driverdata/{driverId}`):
  - Backend returns a `DriverData` JSON payload.
  - App restores local DB/settings from it.
- Destructive by design:
  - Snapshot download/restore replaces local DB and key settings.
  - This must remain an explicit user action (no silent background “daily restore”).
- Evidence policy:
  - Evidence bytes never reach the backend.
  - Evidence metadata (IDs, linkage, hashes, timestamps) may be included in snapshots for audit/reporting.
  - Implication: backend snapshots are not an evidence *file* backup.
  - Restore behavior: when restoring from backend snapshot, local evidence files (with device-local URIs) must be preserved.

- Store/place knowledge policy:
  - Store list + store details are **local-only** and must never reach the backend.
  - Restore behavior: snapshot restore must preserve local stores and must not restore/overwrite them from backend data.

### 11.4 What is NOT a synced entity
- “Visited stores” is not synced as its own list; it is derived from local trips/stores and filtered by `visitedHiddenStoreIds` (which is included in snapshot settings).
- Stores (and any cached place details such as opening hours) are never synced (neither via outbox nor snapshot).

### 11.5 Versioning expectations
- `DriverData.schemaVersion` must be bumped for breaking changes.
- Backend should accept older versions where feasible; app uses `ignoreUnknownKeys` to tolerate forward-compatible fields.

## 12) Sync rules (what moves when)

These rules define how **all information** in the app is expected to move between devices and the backend.

### 12.1 Two sync channels
- **Incremental outbox (small / frequent)**
  - Sends *only new trips* as discrete events.
  - Runs on immediate triggers and/or scheduled runs.
  - If the outbox is empty, it performs **no backend calls**.
- **Snapshot upload (bulk / daily)**
  - Sends a full `DriverData` snapshot (DB + key settings).
  - Scheduled **once per day**.
  - Uses a stable fingerprint to **skip uploading when nothing changed**.
  - Snapshot **download & restore** is destructive and must remain an explicit user action.

### 12.X Companion app (TrimsyApp): what comes from where

TrimsyApp consumes two distinct sources of truth:

- **From backend (DriverData / trips)**
  - What you should rely on:
    - Trips and their audit fields (time/place/distance/purpose/etc.) come from backend sync (outbox + snapshots).
    - Prompts / runs / settings that are included in `DriverData` come from backend snapshots.
  - What you must NOT expect:
    - Evidence bytes never exist in backend. Backend restore is not an evidence file backup.
    - Stores and place details never exist in backend.
  - Identity rules you must follow:
    - Treat `tripId` and `evidenceId` as scoped by `profileId`.
    - Never assume a bare `TripEntity.id` is globally unique across profiles.

- **From PC sync (evidence files + manifest)**
  - What you will receive:
    - Evidence files copied from the phone’s evidence folder.
    - The file layout itself encodes linkage for robust relinking:
      - `relativePath = <tripId>/<fileName>`
      - `fileName` includes both `tripId` and `evidenceId` (see “Evidence file layout”).
  - What you should store alongside the files (manifest):
    - `profileId`
    - `tripId`
    - `evidenceId`
    - `relativePath`
    - `sha256` + `sizeBytes` (preferred for integrity)
    - `capturedAt`, `linkedAt`, `linkedByDeviceId` (audit provenance)
  - How to relink evidence to a trip later:
    - Primary: match evidence to trip by `(profileId, tripId)` and evidence identity by `(profileId, evidenceId)`.
    - Secondary (file-based): parse `tripId` from the folder name and `evidenceId` from the canonical file name.
    - Integrity: if `sha256` exists in the manifest, verify file bytes match before presenting as “linked”.

Recommended TrimsyApp flow:
- Backend sync first (so trips exist locally).
- PC sync next:
  - Pull evidence list (`.../evidence/list`) to get DB linkage + integrity fields.
  - Copy bytes (either via `.../evidence/ev/<evidenceId>` or raw path for orphan recovery).
  - Store/update the manifest keyed by `(profileId, evidenceId)`.
- When showing a trip, display evidence by looking up manifest entries for that `(profileId, tripId)`.

### 12.2 Entity-by-entity rules
- **Trips**
  - Source of truth: local DB (created by the app), backend becomes authoritative after canonicalization.
  - Sync:
    - Incremental: outbox `TYPE_TRIP_CREATE`.
    - Snapshot: included.
  - Immutability: treated as snapshots (no retroactive edits based on later store metadata).

- **Stores (saved stores + custom GPS stores)**
  - Source of truth: local DB.
  - Sync:
    - Incremental: not currently.
    - Snapshot: included.

- **Prompt events**
  - Source of truth: local DB.
  - Sync:
    - Incremental: not currently.
    - Snapshot: included.

- **Runs**
  - Source of truth: local DB.
  - Sync:
    - Incremental: not currently.
    - Snapshot: included.

- **Attachments (evidence)**
- **Evidence (attachments)**
  - Source of truth: local device file + `AttachmentEntity` metadata.
  - Sync:
    - Backend: **never** (neither bytes nor metadata).
    - Computer: synced separately when connected (not via backend).

Evidence file layout (for computer sync / relinking):
- Files are stored under: `filesDir/evidence/<tripId>/...`
- Canonical on-disk file naming (current rule):
  - `trip-<tripId>__ev-<evidenceId>__ts-<capturedAtEpochMillis>.<ext>`
- The **database source of truth** for the linkage remains:
  - `AttachmentEntity.tripId` and `AttachmentEntity.id`
  - `AttachmentEntity.uri` (FileProvider URI pointing at the canonical file)
- The **file path** is an additional durable linkage signal for PC sync tools:
  - Parse `tripId` from the directory name and `evidenceId` from the filename.
  - Optionally verify integrity using `AttachmentEntity.sha256` and `sizeBytes`.

### 12.4 Evidence pull contract (TrimsyApp)
- Evidence can be pulled by the companion app via a **signature-protected** content provider:
  - List: `content://<applicationId>.evidence/list`
    - Includes `relativePath` extracted from the app FileProvider URI when possible.
  - List all files in the on-device evidence folder (including orphans): `content://<applicationId>.evidence/files`
  - Read bytes: `content://<applicationId>.evidence/ev/<evidenceId>`
- Read bytes for a raw file path (for orphan recovery): `content://<applicationId>.evidence/file?path=<tripId>/<fileName>`
- This provider uses the current active `profileId` (falls back to `default`).

Deduping rule for TrimsyApp:
- Maintain a local manifest of already-exported items keyed by:
  - preferred: `relativePath` + `sizeBytes` + `lastModified`, or
  - attachments: `(profileId, evidenceId)`
- Only copy files that are not present in the manifest; update the manifest after a successful copy.

### 12.5 Visited Stores pull contract (TrimsyApp)
- Visited stores can be pulled by the companion app via a **signature-protected** content provider:
  - Provider authority: `content://<applicationId>.visitedstores`
  - This provider uses the current active `profileId` (falls back to `default`).
- Endpoints:
  - Meta (lightweight change detector): `content://<applicationId>.visitedstores/meta`
    - Columns: `profileId`, `storeCount`, `maxLastVisitedAtMillis`
    - Purpose: TrimsyApp can determine if anything changed since last sync.
  - Full or incremental list: `content://<applicationId>.visitedstores/stores`
    - Optional filter: `?since=<epochMillis>` returns only rows with `last_visited_at_millis > since`.
    - Ordering: stable/deterministic (sorted by `store_id`).
- Store payload columns (per row):
  - Identity: `store_id` (stable, canonical)
  - Monotonic visit facts: `first_visited_at_millis`, `last_visited_at_millis`, `visit_count`
  - Store data for syncing a store list: `name`, `city`, `lat`, `lng`, `radius_meters`, `is_favorite`
  - Deterministic version: `version` (hash of the returned payload for that store)

Determinism & idempotency rules:
- Same request  same response (given unchanged DB state).
- Provider never pushes; TrimsyApp controls when reads happen.
- Reads have no side effects.

Recommended TrimsyApp sync flow:
- Query `.../meta` daily and compare `(storeCount, maxLastVisitedAtMillis)` to last stored values.
- If changed, query `.../stores?since=<lastMaxLastVisitedAtMillis>` and upsert by `store_id`.
- Optional: use `version` to skip writes when the payload is identical.

- **Settings / preferences / caches**
  - Source of truth: local DataStore.
  - Sync:
    - Snapshot: included for user preferences (e.g., `visitedHiddenStoreIds`).
    - Snapshot: intentionally **excludes** local-only caches (store images, cached Places details, cached business hours).
  - Some caches are intentionally not included (e.g., driving distance cache).

### 12.3 Derived views
- **Visited stores list** is **persistent and monotonic** (once visited, always visited).
  - Source of truth: local DB table `visited_stores`, updated whenever a trip is inserted.
  - Filtered by `visitedHiddenStoreIds`.
  - Cross-device consistency requires syncing the underlying trips (or the derived visited table) plus the filter setting.

---

##  FINAL SYSTEM LAW (CANONICAL + IRREVOCABLE)

**There is exactly one real profile per account.**

- It defines identity and presentation
- All canonical truth is scoped to it
- Visual assets (logos, signatures, documents) are local and per-profile
- The backend never guesses, never invents, and never sees what is not real

**If a user wants separation:**
- New email  new Firebase account
- New account  new profile
- New profile  new universe of truth
- This is intentional and explicit

**Why this system is worth having:**
-  One mental model (no confusion)
-  One business identity (consistent branding)
-  Shared experience across all apps and devices
-  No duplication or drift
-  No legal ambiguity
-  Zero test-mode confusion
-  Deterministic cleanup

**For all future features:**
- Profile is presentation + ownership scope
- Backend is the only writer of canonical truth
- Apps are renderers + intent senders
- This is not negotiable
