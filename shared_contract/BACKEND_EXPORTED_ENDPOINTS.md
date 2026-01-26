# Backend exported endpoints (authoritative)

This file is the single source of truth for what the backend **actually exports** in this workspace.

Source of truth:
- HTTP gateway routing: [functions/src/index.ts](../functions/src/index.ts)
- Callable exports: [functions/lib/index.js](../functions/lib/index.js) (generated build output)

Hard rule:
- If an endpoint is not listed under **Implemented (exported)** below, it is **not** part of the deployed surface.

---

## Implemented (exported)

### A) HTTP gateway (`apiV1/<route>`)

All routes are `POST` and require:
- Header: `Authorization: Bearer <Firebase ID token>`
- JSON body (most canonical routes require `clientProtocolVersion`)

Startup + law:
- `health`
- `handshakeGet`
- `uidEnsure`
- `lawGet`
- `lawAccept`
- `lawContractGet`

Identity + deletion:
- `uidDelete`

Presence (PC support):
- `pc/presence/heartbeat`

Canonical inventory + purchase:
- `productCreate`
- `productSetCategoryNumber`
- `productSetWeight`
- `productSetStorageLocation`
- `receiptCreate`
- `receiptRowCreate`
- `productCostAllocate`

TrimsyTrack sync surfaces:
- `driverdataGet`
- `driverdataPut`
- `drivingTripCreate`

Sales + bookkeeping:
- `saleCreate`
- `saleReceiptLink`
- `ledgerEntryCreate`
- `accountingVerificationCreate`

Storage system:
- `storageSlotCreate`
- `storageSlotArchive`
- `storagePlaceProduct`
- `storageRemoveProduct`
- `storageMoveProduct`
- `storageSnapshotGet`

Diagnostics:
- `invariantsOpen`

Operator-only (admin):
- `opsGetSafetyMode`
- `opsSetSafetyMode`

### B) Firebase Functions callables (`*Callable`)

Global:
- `health`

Canonical + gating:
- `handshakeGetCallable`

Identity + deletion:
- `uidDeleteCallable`

Law:
- `lawGetCallable`
- `lawAcceptCallable`
- `lawContractGetCallable`

Canonical inventory + purchase:
- `productCreateCallable`
- `productSetCategoryNumberCallable`
- `productSetWeightCallable`
- `productSetStorageLocationCallable`
- `receiptCreateCallable`
- `receiptRowCreateCallable`
- `productCostAllocateCallable`

Sales + bookkeeping:
- `saleCreateCallable`
- `saleReceiptLinkCallable`
- `ledgerEntryCreateCallable`
- `accountingVerificationCreateCallable`

Storage system:
- `storageSlotCreateCallable`
- `storageSlotArchiveCallable`
- `storagePlaceProductCallable`
- `storageRemoveProductCallable`
- `storageMoveProductCallable`
- `storageSnapshotGetCallable`

Diagnostics:
- `invariantsOpenCallable`

Internal-only:
- `selfTestCallable` (requires `INTERNAL_SELF_TEST_EMAIL` configuration)

Other (not client API):
- `pcPresenceSweepOfflineScheduled` (scheduled)
- `phoneFcmTokenSetCallable` (mobile support)

---

## Planned (NOT available in this workspace)

Do not build clients against these unless you implement + export them first:
- Driving journal: no explicit trip↔receipt link endpoint. Trip↔receipt association is derived in TrimsyApp and stored on `receiptCreate` via `drivingTripId`.
- Receipt media + receipt snapshot endpoints (e.g. `receiptMediaSet`, `receiptMediaListByReceipt`, `receiptSnapshotGet`)
- Finalize snapshot endpoints (e.g. `finalizeLogSnapshotCreate`, `finalizeLogSnapshotGet`, `finalizeLogSnapshotsListByProduct`)
