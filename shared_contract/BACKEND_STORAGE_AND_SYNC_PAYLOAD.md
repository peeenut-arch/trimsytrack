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

- `uid` is assigned by Firebase Auth.
- Clients never choose or send ownership; backend derives it from auth.
- If a user tries to access another UID’s record, backend returns **404**.

### SKU / Category+Number

Trimsy’s current implemented “SKU-like” identity lock is **Category+Number**:

- Set via `productSetCategoryNumberCallable`.
- Uniqueness is enforced **within a UID**: a Category+Number cannot be used by two products.
- Single-assignment is enforced: a product cannot be assigned Category+Number twice.

The canonical identifier used by the backend is:

- `categoryNumberId = "${CATEGORY}#${NUMBER}"`

This is a deterministic anchor id derived from explicit user inputs (not a guess).

## Canonical writes added for logging

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
