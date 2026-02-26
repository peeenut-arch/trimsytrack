package com.trimsytrack

import android.content.Context
import androidx.room.Room
import com.trimsytrack.data.AppDatabase
import com.trimsytrack.data.DistanceRepository
import com.trimsytrack.data.PingRepository
import com.trimsytrack.data.PromptRepository
import com.trimsytrack.data.RegionRepository
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.StoreRepository
import com.trimsytrack.data.TripRepository
import com.trimsytrack.data.driverdata.DriverDataRepository
import com.trimsytrack.data.driverdata.DriverDataSyncManager
import com.trimsytrack.data.canonical.CanonicalWriteEnqueuer
import com.trimsytrack.data.canonical.CanonicalWritesSyncManager
import com.trimsytrack.data.sync.SyncDatabase
import com.trimsytrack.data.trackevents.TrackEventEmitter
import com.trimsytrack.data.trackevents.TrackEventsRepository
import com.trimsytrack.data.trackevents.TrackEventsSyncManager
import com.trimsytrack.backend.CanonicalApi
import com.trimsytrack.distance.RoutesApi
import com.trimsytrack.distance.RoutesDistanceService
import com.trimsytrack.geofence.GeofenceSyncManager
import com.trimsytrack.notifications.Notifications
import com.trimsytrack.network.BackendRequestInterceptor
import com.trimsytrack.debug.DebuggHttpInterceptor
import com.trimsytrack.system.SystemCallablesService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object AppGraph {
    @Volatile
    private var initialized = false

    lateinit var appContext: Context
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var db: AppDatabase
        private set

    lateinit var syncDb: SyncDatabase
        private set

    lateinit var storeRepository: StoreRepository
        private set

    lateinit var promptRepository: PromptRepository
        private set

    lateinit var pingRepository: PingRepository
        private set

    lateinit var tripRepository: TripRepository
        private set

    lateinit var distanceRepository: DistanceRepository
        private set

    lateinit var regionRepository: RegionRepository
        private set

    lateinit var geofenceSyncManager: GeofenceSyncManager
        private set

    // TODO: Add new backend sync repository here when ready

    lateinit var trackEventsRepository: TrackEventsRepository
        private set

    lateinit var trackEventsSyncManager: TrackEventsSyncManager
        private set

    lateinit var trackEventEmitter: TrackEventEmitter
        private set

    lateinit var driverDataRepository: DriverDataRepository
        private set

    lateinit var driverDataSyncManager: DriverDataSyncManager
        private set

    lateinit var backendHttpClient: OkHttpClient
        private set

    lateinit var systemCallables: SystemCallablesService
        private set

    lateinit var canonicalApi: CanonicalApi
        private set

    lateinit var canonicalWritesSyncManager: CanonicalWritesSyncManager
        private set

    lateinit var canonicalWriteEnqueuer: CanonicalWriteEnqueuer
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            // System identity lock: TrimsyTRACK must never drift to any other app_id.
            check(BuildConfig.APP_ID == "trimsytrack") {
                "Identity lock violated: BuildConfig.APP_ID=${BuildConfig.APP_ID} (expected trimsytrack)"
            }

            appContext = context.applicationContext

            settings = SettingsStore(appContext)

            systemCallables = SystemCallablesService(settings)

            backendHttpClient = buildBackendHttpClient()

            canonicalWritesSyncManager = CanonicalWritesSyncManager(appContext)

            db = Room.databaseBuilder(appContext, AppDatabase::class.java, "trimsytrack.db")
                .fallbackToDestructiveMigration()
                .build()

            syncDb = Room.databaseBuilder(appContext, SyncDatabase::class.java, "trimsytrack.sync.db")
                .addMigrations(com.trimsytrack.data.sync.SyncMigrations.MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()

            // Canonical API (backend truth writes)
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizeBackendBaseUrl())
                .client(backendHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            canonicalApi = retrofit.create(CanonicalApi::class.java)

            canonicalWriteEnqueuer = CanonicalWriteEnqueuer(settings, syncDb.canonicalWriteOutboxDao())

            regionRepository = RegionRepository(appContext)
            storeRepository = StoreRepository(db.storeDao(), regionRepository, settings)
            promptRepository = PromptRepository(db.promptDao(), settings)
            pingRepository = PingRepository(db.pingDao(), settings)
            trackEventsRepository = TrackEventsRepository(settings)
            trackEventsSyncManager = TrackEventsSyncManager(appContext)
            trackEventEmitter = TrackEventEmitter(syncDb.trackEventOutboxDao(), trackEventsSyncManager)

            tripRepository = TripRepository(
                tripDao = db.tripDao(),
                attachmentDao = db.attachmentDao(),
                runDao = db.runDao(),
                settings = settings,
                appContext = appContext,
                trackEventEmitter = trackEventEmitter,
                canonicalWriteEnqueuer = canonicalWriteEnqueuer,
            )
            distanceRepository = DistanceRepository(db.distanceCacheDao(), buildRoutesService(), settings)

            // TODO: Initialize new backend sync repository here when ready

            driverDataRepository = DriverDataRepository(appContext, settings)
            driverDataSyncManager = DriverDataSyncManager(appContext)
            // NOTE: Snapshots are checkpoints on top of canonical truth.
            // We intentionally do NOT run the "instant sync on any DB change" loop here,
            // to avoid spamming `driverdataPut` on routine local mutations.
            // Checkpoints are instead triggered explicitly (e.g. after a HOME-ending trip is canonically acked).

            Notifications.ensureChannels(appContext)
            geofenceSyncManager = GeofenceSyncManager(appContext, settings, storeRepository)

            initialized = true
        }
    }

    private fun buildBackendHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(BackendRequestInterceptor())
            .addInterceptor(DebuggHttpInterceptor())
            .addInterceptor(logging)
            .build()
    }

    private fun buildRoutesService(): RoutesDistanceService {
        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        return RoutesDistanceService(
            retrofit.create(RoutesApi::class.java),
            appContext
        )
    }

    private fun normalizeBackendBaseUrl(): String {
        val base = BuildConfig.BACKEND_API_BASE.trim()
        check(base.isNotBlank()) { "Missing BACKEND_API_BASE" }
        return if (base.endsWith("/")) base else "$base/"
    }
}
