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
- **EvidenceID** → `AttachmentEntity.id: Long` (images/PDFs are stored as attachments)

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
