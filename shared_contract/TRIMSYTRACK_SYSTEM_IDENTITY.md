# TrimsyTRACK System Identity (Non-Negotiable)

This document exists to prevent **cross-app confusion**.

If anything in code or docs contradicts this file, that is **drift**.

## System name

- Product: **TrimsyTRACK**
- Domain: **driving journal / trips**
- Android package: `com.trimsytrack`

## Backend app identity lock

TrimsyTRACK must identify itself to the backend with:

```json
{ "app_id": "trimsytrack" }
```

Rules:
- TrimsyTRACK sync surfaces **MUST reject** any request where `app_id != trimsytrack`.
- These requirements must not be relaxed or repurposed without an explicit, intentional decision.

## TrimsyTRACK backend surfaces (this is the set)

- Startup / gates: `handshakeGet` (+ law gates)
- Snapshot sync (DriverData): `driverdataGet`, `driverdataPut`
- Canonical trip truth: `drivingTripCreate`
- Optional telemetry-like incremental log (capability-gated): `trackEventsBatchPut`, `trackEventsSinceGet`

## Must NOT be confused with TrimsyApp

**TrimsyApp** is a separate product (photos + purchase receipts + Finalize Log) and uses:

```json
{ "app_id": "trimsyapp" }
```

Hard boundary:
- TrimsyTRACK must not call TrimsyApp-only write endpoints (e.g. `receiptCreate`).
- TrimsyApp must not call TrimsyTRACK-only trip/snapshot endpoints.

## Change control (required)

Any change to TrimsyTRACK backend surfaces or identity rules requires all of:
1. Update this document.
2. Update the backend enforcement (runtime checks).
3. Update/add backend contract tests that fail if the change regresses.
