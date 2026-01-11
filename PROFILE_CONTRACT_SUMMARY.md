# ✅ PROFILE CONTRACT v1.0 - LOCKED IN

**Timestamp:** January 10, 2026  
**Status:** CANONICAL & IRREVOCABLE  
**Location:** [DATA_CONTRACT.md](DATA_CONTRACT.md)

---

## THE DECISION

**One account → One profile → Backend-authoritative truth**

There is **exactly one real profile per account**. It is the universe of truth for that account.

### What Changed

The old multi-profile system with test modes and profile-switching complexity has been replaced with a single, clean architecture:

```
Firebase UID (auth)
    ↓
Account (one email)
    ↓
Profile (one business/identity)
    ↓
Canonical Truth (backend-authoritative)
```

---

## WHAT'S LOCKED IN (SECTIONS ADDED TO DATA_CONTRACT.md)

### 1. **Identity Hierarchy (Foundational)**
   - Firebase UID = authentication only
   - Profile = identity + presentation + ownership scope
   - Canonical truth = backend-authoritative reality
   - Apps = renderers + intent senders
   - Backend = only writer of truth

### 2. **Profile Definition (IS / IS NOT)**
   - **Profile IS:** business identity, personal identity, branding, preferences, ownership scope
   - **Profile IS NOT:** accounting logic, storage logic, validation logic, invariants
   - Profile never decides *how* things are booked—only *who owns* them and *how they look*

### 3. **Profile Data Model (Conceptual)**
   ```json
   {
     "profileId": "uuid",
     "person": { "displayName", "avatarUri" },
     "business": { "name", "organisationNumber", "vatRegistrationNumber", "address", "country" },
     "preferences": { "language", "theme", "documentDefaults" },
     "version": 1  // Increment on every backend change
   }
   ```

### 4. **ProfileScope Pattern (Safety Mechanism)**
   - Filesystem namespace: `/profiles/<accountId>/<profileId>/`
   - Applied to: logos, signatures, profile pictures, document preferences, caches, templates
   - Guarantees: no bleed, no collision, no accidental reuse, deterministic cleanup

### 5. **Visual Assets Model (Local, Per-Profile, Presentation-Only)**
   - All stored locally under `profileScope`
   - Referenced by stable logical IDs (not paths)
   - Never part of canonical truth
   - Backend never sees them
   - Resolution at render time (safe fallbacks)

### 6. **Backend Responsibilities (Authoritative, Profile-Aware)**
   - ✅ Authenticate Firebase UID
   - ✅ Resolve exactly one profile
   - ✅ Inject `profileId` into all writes
   - ✅ Scope all truth by `profileId`
   - ✅ Reject cross-profile access
   - ✅ Return canonical objects
   - ❌ Never store logos/signatures
   - ❌ Never branch logic on profile fields
   - ❌ Never guess or invent

### 7. **App Responsibilities (Render + Intent Sender)**
   - ✅ Authenticate and fetch profile
   - ✅ Render identity + branding
   - ✅ Store visuals locally
   - ✅ Generate PDFs with logos
   - ✅ Send intents/events to backend
   - ❌ Never invent canonical data
   - ❌ Never bypass backend rules
   - ❌ Never write truth locally

### 8. **PC (Companion App) Responsibilities (Read-Only)**
   - ✅ Pull evidence files
   - ✅ Pull visited stores
   - ✅ Pull trip data from backend
   - ✅ Render and export documents
   - ❌ Never write to backend
   - ❌ Never modify trip data

### 9. **FINAL SYSTEM LAW (Canonical)**
   ```
   There is exactly one real profile per account.
   It defines identity and presentation.
   All canonical truth is scoped to it.
   Visual assets are local and per-profile.
   The backend never guesses, never invents, never sees what is not real.

   If a user wants separation: new email → new account → new universe of truth.
   Intentional and explicit.
   ```

---

## WHY THIS IS WORTH HAVING

- ✅ **One mental model** — no confusion, no edge cases
- ✅ **One business identity** — consistent branding everywhere
- ✅ **Shared experience** — all apps and devices show the same truth
- ✅ **No duplication** — single source of truth
- ✅ **No drift** — backend canonicalization
- ✅ **No legal ambiguity** — clear ownership and identity
- ✅ **Zero test-mode confusion** — one real profile, one real data universe
- ✅ **Deterministic cleanup** — profile deletion is predictable

---

## WHAT'S NEXT

For all future features:

1. **Profile is presentation + ownership scope**
   - It controls how things look
   - It controls who owns them
   - It does NOT control how they're booked

2. **Backend is the only writer of canonical truth**
   - Apps propose transactions
   - Backend canonicalizes
   - Apps render results

3. **Apps are renderers + intent senders**
   - Never inventors
   - Never validators
   - Never truth-writers

**This is not negotiable.**

---

## FILES UPDATED

- **[DATA_CONTRACT.md](DATA_CONTRACT.md)** — Complete Profile Contract v1.0 with all locked-in sections

## RELATED DOCUMENTS

- **[BACKEND_MIGRATION.md](BACKEND_MIGRATION.md)** — Old backend system cleanup (completed)
- **[CLEANUP_SUMMARY.md](CLEANUP_SUMMARY.md)** — App data wipe documentation

---

## VALIDATION

✅ No compilation errors  
✅ All sections cross-referenced  
✅ Profile model is self-consistent  
✅ Backend + App + PC responsibilities are aligned  
✅ Visual asset model is deterministic  
✅ ProfileScope pattern is applied consistently  
✅ FINAL SYSTEM LAW is canonical  

---

**STATUS: READY FOR IMPLEMENTATION**

The profile architecture is now the canonical source of truth for all new features.
