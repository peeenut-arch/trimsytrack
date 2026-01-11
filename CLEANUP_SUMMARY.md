# TrimsyTRACK - Backend Migration & Data Wipe Summary

## Completed: January 9, 2026

### ✅ Old Backend System Removed

All components of the previous backend sync system have been completely removed:

#### Deleted Files:
- `app/src/main/java/com/trimsytrack/data/sync/` (entire directory)
  - BackendSyncApi.kt
  - BackendSyncManager.kt
  - BackendSyncMode.kt
  - BackendSyncModels.kt
  - BackendSyncRepository.kt
  - BackendSyncWorker.kt
- `app/src/main/java/com/trimsytrack/data/dao/SyncOutboxDao.kt`
- `app/src/main/java/com/trimsytrack/data/entities/SyncOutboxEntity.kt`

#### Modified Files (with TODO markers for new backend):

1. **TrimsyApp.kt**
   - Removed BackendSyncMode import
   - Removed backendSyncManager.applySchedule() call
   - Added TODO comment for new backend initialization

2. **AppGraph.kt**
   - Removed BackendSyncManager and BackendSyncRepository imports
   - Removed backendSyncRepository and backendSyncManager properties
   - Added TODO placeholders for new backend system
   - Kept backendHttpClient and auth interceptor (needed for new backend)

3. **AppDatabase.kt**
   - Removed SyncOutboxEntity from entities list
   - Removed syncOutboxDao() method
   - Bumped database version from 12 to 13
   - Added migration comment

4. **Migrations.kt**
   - Added MIGRATION_12_13 to drop sync_outbox table
   - Migration will run automatically on next app launch

5. **TripConfirmViewModel.kt**
   - Removed backendSyncRepository.enqueueTripCreate() call
   - Removed backendSyncManager.scheduleNow() call
   - Added TODO comment for new backend sync

6. **ManualTripScreen.kt**
   - Removed backendSyncRepository.enqueueTripCreate() call
   - Removed backendSyncManager.scheduleNow() call
   - Added TODO comment for new backend sync

7. **SettingsScreen.kt**
   - Disabled "Sync Now" button
   - Changed button text to "Synka nu (ej tillgänglig)"
   - Added TODO comment for new backend trigger

8. **AppNavHost.kt**
   - Commented out syncOutboxDao().claimUnscoped() calls
   - Commented out syncOutboxDao().rekeyProfile() calls
   - Added comments marking old backend system

### ✅ Data Wiped

Successfully cleared all app data:

1. **App Uninstalled from Device**
   - Ran `adb uninstall com.trimsytrack`
   - All user data, trips, images, and settings removed from device

2. **Build Artifacts Cleared**
   - Deleted `app/build` directory
   - Ran `gradlew clean`
   - All compiled code and cached files removed

3. **Database Will Reset**
   - Next app install will create fresh database (version 13)
   - No trips, prompts, attachments, or user data will exist
   - App will be in completely clean state

### 📝 Documentation Created

1. **BACKEND_MIGRATION.md**
   - Comprehensive guide of what was removed
   - What's kept for new backend (DATA_CONTRACT.md, etc.)
   - Step-by-step guide for implementing new backend
   - Key requirements from data contract
   - Code search patterns to find any missed references

2. **clear_app_data.ps1**
   - PowerShell script to wipe all app data
   - Interactive confirmation
   - Uninstalls app, clears build, runs Gradle clean
   - Can be run anytime: `.\clear_app_data.ps1`

3. **CLEANUP_SUMMARY.md** (this file)
   - Complete record of all changes made

### 🔍 Verification

- ✅ No compilation errors
- ✅ All old backend references removed or commented with TODOs
- ✅ Database migration added
- ✅ Build artifacts cleared
- ✅ App uninstalled from device

### 📋 What's Preserved for New Backend

The following are intentionally kept and should guide the new backend implementation:

1. **DATA_CONTRACT.md** - Complete data contract specification
   - Multi-app isolation (app_id system)
   - Profile scoping (profileId)
   - Incremental sync (trip outbox pattern)
   - Bulk sync (DriverData snapshots)
   - Evidence policy (never synced to backend)
   - Store policy (local-only, never synced)

2. **HANDOVER.md** - Build documentation
   - Multi-app setup (trimsytrack vs trimsyapp)
   - Firebase authentication
   - Background work patterns

3. **Backend Infrastructure (Still Active)**
   - `AppGraph.backendHttpClient` - HTTP client with logging
   - `BackendRequestInterceptor` - Adds auth + app/profile headers
   - `DriverDataRepository` - Full snapshot upload/download
   - `DriverDataSyncManager` - Daily snapshot scheduling

### 🎯 Next Steps for New Backend

When you're ready to implement the new backend:

1. **Create New Sync Package**
   ```
   app/src/main/java/com/trimsytrack/data/sync/
   ```

2. **Implement Core Components**
   - New sync API interface (based on DATA_CONTRACT.md)
   - New sync repository for trip outbox
   - New sync manager for scheduling
   - New sync worker for background execution

3. **Update TODOs**
   - Search codebase for "TODO: Add new backend" comments
   - Update all marked locations:
     - TrimsyApp.kt (initialization)
     - AppGraph.kt (repository setup)
     - TripConfirmViewModel.kt (sync trigger)
     - ManualTripScreen.kt (sync trigger)
     - SettingsScreen.kt (manual sync button)

4. **Follow DATA_CONTRACT.md Requirements**
   - Include Authorization, X-App-Id, X-Profile-Id headers
   - Use idempotency keys for trip creation
   - Handle backend canonicalization
   - Never sync evidence or stores to backend

### 🚀 App is Ready

The app is now in a completely clean state:
- No old backend code
- No user data
- Fresh database on next install
- All documentation preserved for new backend implementation

Run `.\gradlew.bat installDebug` to build and install the fresh app.
