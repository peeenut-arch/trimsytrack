package com.trimsytrack

import android.content.Context
import androidx.room.Room
import com.trimsytrack.data.AppDatabase
import com.trimsytrack.data.DistanceRepository
import com.trimsytrack.data.PromptRepository
import com.trimsytrack.data.RegionRepository
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.StoreRepository
import com.trimsytrack.data.TripRepository
import com.trimsytrack.distance.RoutesApi
import com.trimsytrack.distance.RoutesDistanceService
import com.trimsytrack.geofence.GeofenceSyncManager
import com.trimsytrack.data.Migrations
import com.trimsytrack.data.sync.BackendSyncManager
import com.trimsytrack.data.sync.BackendSyncRepository
import com.trimsytrack.notifications.Notifications
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

    lateinit var storeRepository: StoreRepository
        private set

    lateinit var promptRepository: PromptRepository
        private set

    lateinit var tripRepository: TripRepository
        private set

    lateinit var distanceRepository: DistanceRepository
        private set

    lateinit var regionRepository: RegionRepository
        private set

    lateinit var geofenceSyncManager: GeofenceSyncManager
        private set

    lateinit var backendSyncRepository: BackendSyncRepository
        private set

    lateinit var backendSyncManager: BackendSyncManager
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            appContext = context.applicationContext

            settings = SettingsStore(appContext)

            db = Room.databaseBuilder(appContext, AppDatabase::class.java, "trimsytrack.db")
                .addMigrations(
                    Migrations.MIGRATION_3_4,
                    Migrations.MIGRATION_4_5,
                )
                .fallbackToDestructiveMigration()
                .build()

            regionRepository = RegionRepository(appContext)
            storeRepository = StoreRepository(db.storeDao(), regionRepository)
            promptRepository = PromptRepository(db.promptDao())
            tripRepository = TripRepository(db.tripDao(), db.attachmentDao(), db.runDao())
            distanceRepository = DistanceRepository(db.distanceCacheDao(), buildRoutesService())

            backendSyncRepository = BackendSyncRepository(appContext, settings)
            backendSyncManager = BackendSyncManager(appContext)

            Notifications.ensureChannels(appContext)
            geofenceSyncManager = GeofenceSyncManager(appContext, settings, storeRepository)

            initialized = true
        }
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
}
