# Backend Migration Notes

## Changes Made (January 2026)

### Old Backend System Removed
The previous backend sync system has been completely removed in preparation for a new backend implementation:

**Removed Components:**
- `data/sync/BackendSyncApi.kt`
- `data/sync/BackendSyncManager.kt`
- `data/sync/BackendSyncMode.kt`
- `data/sync/BackendSyncModels.kt`
- `data/sync/BackendSyncRepository.kt`
- `data/sync/BackendSyncWorker.kt`
- `data/dao/SyncOutboxDao.kt`
- `data/entities/SyncOutboxEntity.kt`

**Modified Files (with TODOs for new backend):**
- `TrimsyApp.kt` - Removed old backend initialization
- `AppGraph.kt` - Removed old backend repository instances
- `TripConfirmViewModel.kt` - Removed old sync calls after trip creation
- `ManualTripScreen.kt` - Removed old sync calls after trip creation
- `SettingsScreen.kt` - Disabled "Sync Now" button
- `AppNavHost.kt` - Removed syncOutboxDao migration calls
- `AppDatabase.kt` - Removed SyncOutboxEntity from entities list
- `Migrations.kt` - Added MIGRATION_12_13 to drop sync_outbox table

### What's Kept for New Backend

The following are still in place and should be used as reference for the new system:

1. **DATA_CONTRACT.md** - Full specification of data flow and sync requirements
2. **HANDOVER.md** - Build documentation with multi-app isolation details
3. **Backend HTTP Client** - `AppGraph.backendHttpClient` with auth interceptor
4. **Driver Data Sync** - `DriverDataRepository` and `DriverDataSyncManager` for full snapshots
5. **Network Layer** - `BackendRequestInterceptor` for Firebase auth + app/profile headers

### Data Wipe Capability

A new script has been created to completely reset the app:
- **clear_app_data.ps1** - Uninstalls app, clears all build artifacts and local data

Run with: `.\clear_app_data.ps1`

### Next Steps for New Backend

When implementing the new backend system:

1. Create new sync package: `data/sync/`
2. Implement new API interface based on DATA_CONTRACT.md requirements
3. Update TODOs in:
   - `TrimsyApp.kt` (initialization)
   - `AppGraph.kt` (repository registration)
   - `TripConfirmViewModel.kt` (trip sync trigger)
   - `ManualTripScreen.kt` (trip sync trigger)
   - `SettingsScreen.kt` (manual sync button)

4. Key Requirements from DATA_CONTRACT.md:
   - All requests must include: Authorization, X-App-Id, X-Profile-Id headers
   - Incremental sync: Trip creation via outbox pattern with idempotency
   - Bulk sync: DriverData snapshots (already implemented)
   - Evidence files: Never synced to backend (PC sync only)
   - Stores: Never synced to backend (local only)

### Database Schema Changes

Database version bumped from 12 to 13:
- Dropped `sync_outbox` table
- All other tables remain intact
- App will auto-migrate on next launch

### Code Search Patterns

If you need to find all old backend references that were removed:
```powershell
# Search for any remaining references
Get-ChildItem -Recurse -Include *.kt | Select-String "BackendSyncRepository|BackendSyncManager|syncOutboxDao"
```

All critical references have been removed or commented with TODO markers.
