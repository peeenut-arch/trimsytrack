# TrimsyTRACK – Data Contract (System Truths)

This is the short checklist for adding features safely (IDs, scoping, background work, notifications). If a change violates any item below, treat it as a bug unless explicitly justified.

## 1) Identity + scope
- **`profileId` is the scope boundary** for almost all user data.
  - Every row that is “user-owned” must include `profileId: String`.
  - Every DAO query that targets a single row must filter by both `profileId` and the row’s `id`.
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
- **Destructive restore is explicit.** “Download & restore” is the only operation allowed to replace local DB/settings from the backend.

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
  - Evidence never reaches the backend (neither bytes nor metadata).
  - Implication: backend snapshots are not an Evidence backup.
  - Restore behavior: when restoring from backend snapshot, local Evidence must be preserved.

### 11.4 What is NOT a synced entity
- “Visited stores” is not synced as its own list; it is derived from local trips/stores and filtered by `visitedHiddenStoreIds` (which is included in snapshot settings).
- Stores are not currently pushed incrementally via outbox (only trips are).

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
    - Snapshot: included (e.g., `visitedHiddenStoreIds`, store images, cached Places details).
  - Some caches are intentionally not included (e.g., driving distance cache).

### 12.3 Derived views
- **Visited stores list** is **persistent and monotonic** (once visited, always visited).
  - Source of truth: local DB table `visited_stores`, updated whenever a trip is inserted.
  - Filtered by `visitedHiddenStoreIds`.
  - Cross-device consistency requires syncing the underlying trips (or the derived visited table) plus the filter setting.
