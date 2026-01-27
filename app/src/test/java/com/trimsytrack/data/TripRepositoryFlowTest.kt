package com.trimsytrack.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trimsytrack.data.canonical.CanonicalWriteEnqueuerLike
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.data.sync.SyncDatabase
import com.trimsytrack.data.trackevents.TrackEventEmitterLike
import com.trimsytrack.logic.RunGrouping
import com.trimsytrack.logic.TripTimes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TripRepositoryFlowTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var syncDb: SyncDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: TripRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        syncDb = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        settings = SettingsStore(context)

        val emitter = object : TrackEventEmitterLike {
            override suspend fun emitRunCompleted(runId: Long?, tripId: Long, endedAt: Instant, reason: String) {
                // no-op for JVM tests
            }
        }

        val canonicalNoop = object : CanonicalWriteEnqueuerLike {
            override suspend fun enqueueDrivingTripCreate(trip: TripEntity): Boolean = true
        }

        repo = TripRepository(
            tripDao = db.tripDao(),
            attachmentDao = db.attachmentDao(),
            runDao = db.runDao(),
            settings = settings,
            appContext = context,
            trackEventEmitter = emitter,
            canonicalWriteEnqueuer = canonicalNoop,
        )

        runBlocking {
            // Satisfy TripRepository.requireUid() precondition.
            settings.setBackendIdentityUid("TEST_UID")
        }
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        runCatching { syncDb.close() }
    }

    private fun baseTrip(
        endedAt: Instant,
        endPlaceType: PlaceType,
        storeId: String,
        storeName: String,
        runId: Long? = null,
    ): TripEntity {
        val tz = ZoneId.of("UTC")
        val day: LocalDate = endedAt.atZone(tz).toLocalDate()
        val startedAt = TripTimes.deriveStartedAt(endedAt = endedAt, durationMinutes = 10)

        return TripEntity(
            uid = "", // TripRepository will fill this from settings
            createdAt = endedAt,
            day = day,
            startedAt = startedAt,
            endedAt = endedAt,
            timeZoneId = tz.id,
            storeId = storeId,
            storeLocationId = storeId,
            storeNameSnapshot = storeName,
            citySnapshot = "",
            storeLatSnapshot = 0.0,
            storeLngSnapshot = 0.0,
            endPlaceType = endPlaceType,
            endAddressSnapshot = null,
            startLabelSnapshot = "Start",
            startLat = 0.0,
            startLng = 0.0,
            startPlaceType = PlaceType.HOME,
            startAddressSnapshot = null,
            distanceMeters = 1000,
            durationMinutes = 10,
            notes = "",
            businessPurpose = SettingsStore.DEFAULT_BUSINESS_PURPOSE,
            runId = runId,
            currencyCode = null,
            mileageRateMicros = null,
        )
    }

    @Test
    fun createTrip_assignsRunId_and_homeTrip_reusesOpenRunId() = runBlocking {
        val t1Id = repo.createTrip(
            baseTrip(
                endedAt = Instant.parse("2026-01-26T08:00:00Z"),
                endPlaceType = PlaceType.STORE,
                storeId = "store:A",
                storeName = "A",
            )
        )
        val t1 = repo.get(t1Id)
        assertNotNull(t1)
        assertNotNull(t1.runId)

        val t2Id = repo.createTrip(
            baseTrip(
                endedAt = Instant.parse("2026-01-26T09:00:00Z"),
                endPlaceType = PlaceType.STORE,
                storeId = "store:B",
                storeName = "B",
            )
        )
        val t2 = repo.get(t2Id)
        assertNotNull(t2)
        assertEquals(t1.runId, t2.runId)

        // Simulates the "Complete to Home" flows that pass runId=null and rely on ensureRunIdForNewTrip.
        val homeId = repo.createTrip(
            baseTrip(
                endedAt = Instant.parse("2026-01-26T10:00:00Z"),
                endPlaceType = PlaceType.HOME,
                storeId = BUSINESS_HOME_LOCATION_ID,
                storeName = "Business home",
            )
        )
        val home = repo.get(homeId)
        assertNotNull(home)
        assertEquals(t1.runId, home.runId)

        val all = db.tripDao().listAll(uid = "TEST_UID")
        val grouped = all.groupBy { RunGrouping.key(it) }

        // There should be exactly one completed run group.
        assertEquals(1, grouped.size)
        val group = grouped.values.single()
        assertTrue(RunGrouping.isCompletedRun(group))
        assertEquals(2, RunGrouping.stopCount(group))

        val tripNumber = repo.completedTripNumberForTrip(homeId)
        assertEquals(1, tripNumber)
    }
}
