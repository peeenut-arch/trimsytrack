package com.trimsytrack.ui.vm

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.trimsytrack.AppGraph
import com.trimsytrack.data.BUSINESS_HOME_LOCATION_ID
import com.trimsytrack.data.SettingsStore
import com.trimsytrack.data.entities.DistanceMethod
import com.trimsytrack.data.entities.PlaceType
import com.trimsytrack.data.entities.PromptStatus
import com.trimsytrack.data.entities.TripEntity
import com.trimsytrack.util.PlaceNameNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import kotlin.math.*

data class TripConfirmState(
    val storeName: String? = null,
    val storeLat: Double? = null,
    val storeLng: Double? = null,

    val startLabel: String? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val startStoreId: String? = null,

    val canUseLastStore: Boolean = false,
    val canUseCurrentLocation: Boolean = false,
    val canConfirm: Boolean = false,

    val isConfirming: Boolean = false,
    val error: String? = null,
)

class TripConfirmViewModel(
    app: Application,
    private val promptId: Long,
) : AndroidViewModel(app) {

    private val fused = LocationServices.getFusedLocationProviderClient(app)

    private val _state = MutableStateFlow(TripConfirmState())
    val state: StateFlow<TripConfirmState> = _state

    private var storeId: String? = null
    private var promptTriggeredAt: Instant? = null
    private var promptDay: LocalDate? = null
    private var hasBusinessHomeTripToday: Boolean = false
    private var businessHomeLat: Double? = null
    private var businessHomeLng: Double? = null

    init {
        viewModelScope.launch {
            val prompt = AppGraph.promptRepository.get(promptId)
            if (prompt == null) {
                _state.update { it.copy(error = "Prompt not found") }
                return@launch
            }

            storeId = prompt.storeId
            promptTriggeredAt = prompt.triggeredAt
            promptDay = prompt.triggeredAt.atZone(ZoneId.systemDefault()).toLocalDate()

            val day = promptDay ?: LocalDate.now()
            businessHomeLat = AppGraph.settings.businessHomeLat.first()
            businessHomeLng = AppGraph.settings.businessHomeLng.first()

            hasBusinessHomeTripToday = runCatching {
                val existing = AppGraph.tripRepository.listTripsBetweenDays(day, day)
                val homeLat = businessHomeLat
                val homeLng = businessHomeLng
                if (homeLat == null || homeLng == null) {
                    false
                } else {
                    existing.any { t ->
                        t.startLabelSnapshot == "Business home" ||
                            distanceKm(t.startLat, t.startLng, homeLat, homeLng) <= 0.2
                    }
                }
            }.getOrDefault(false)

            val last = AppGraph.tripRepository.latestTripForDay(day)
            val canUseLast = when {
                last == null -> false
                businessHomeLat == null || businessHomeLng == null -> true
                else -> hasBusinessHomeTripToday
            }

            _state.update { prev ->
                val homeLat = businessHomeLat
                val homeLng = businessHomeLng

                // Required behavior:
                // - First confirmed trip of the day must start at Business Home (when configured).
                // - Only after a Business-Home-start trip exists for the day may we start from last store/current.
                val autoStartFromHome = (homeLat != null && homeLng != null && !hasBusinessHomeTripToday)

                val autoStartLabel = when {
                    canUseLast -> "Last store: ${last!!.storeNameSnapshot}"
                    autoStartFromHome -> "Business home"
                    else -> prev.startLabel
                }

                val autoStartLat = when {
                    canUseLast -> last!!.storeLatSnapshot
                    autoStartFromHome -> homeLat
                    else -> prev.startLat
                }

                val autoStartLng = when {
                    canUseLast -> last!!.storeLngSnapshot
                    autoStartFromHome -> homeLng
                    else -> prev.startLng
                }

                val autoStartStoreId = when {
                    canUseLast -> last!!.storeId
                    autoStartFromHome -> BUSINESS_HOME_LOCATION_ID
                    else -> prev.startStoreId
                }

                prev.copy(
                    storeName = prompt.storeNameSnapshot,
                    storeLat = prompt.storeLatSnapshot,
                    storeLng = prompt.storeLngSnapshot,
                    canUseLastStore = canUseLast,
                    canUseCurrentLocation = (homeLat == null || homeLng == null || hasBusinessHomeTripToday),
                    startLabel = autoStartLabel,
                    startLat = autoStartLat,
                    startLng = autoStartLng,
                    startStoreId = autoStartStoreId,
                )
            }
            recomputeCanConfirm()
        }
    }

    fun useLastStoreStart() {
        viewModelScope.launch {
            val day = promptDay ?: LocalDate.now()
            val last = AppGraph.tripRepository.latestTripForDay(day) ?: return@launch
            _state.update {
                it.copy(
                    startLabel = "Last store: ${last.storeNameSnapshot}",
                    startLat = last.storeLatSnapshot,
                    startLng = last.storeLngSnapshot,
                    startStoreId = last.storeId,
                    error = null
                )
            }
            recomputeCanConfirm()
        }
    }

    @SuppressLint("MissingPermission")
    fun useCurrentLocationStart() {
        val homeLat = businessHomeLat
        val homeLng = businessHomeLng
        if (homeLat != null && homeLng != null && !hasBusinessHomeTripToday) {
            _state.update { it.copy(error = "First trip of the day must start at Business home") }
            return
        }
        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    _state.update { it.copy(error = "No last known location available") }
                    return@addOnSuccessListener
                }
                _state.update {
                    it.copy(
                        startLabel = "Current location",
                        startLat = loc.latitude,
                        startLng = loc.longitude,
                        startStoreId = null,
                        error = null
                    )
                }
                recomputeCanConfirm()
            }
            .addOnFailureListener {
                _state.update { s -> s.copy(error = "Failed to read location") }
            }
    }

    fun confirm(notes: String, businessPurpose: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val destLat = s.storeLat
            val destLng = s.storeLng
            val startLat = s.startLat
            val startLng = s.startLng
            val storeName = s.storeName
            val store = storeId

            if (destLat == null || destLng == null || startLat == null || startLng == null || storeName == null || store == null) {
                _state.update { it.copy(error = "Missing start or destination") }
                return@launch
            }

            _state.update { it.copy(isConfirming = true, error = null) }

            try {
                val routeResult = runCatching {
                    AppGraph.distanceRepository.getOrComputeDrivingRoute(
                        startLat = startLat,
                        startLng = startLng,
                        destLat = destLat,
                        destLng = destLng,
                        startLocationId = s.startStoreId,
                        endLocationId = store,
                    )
                }

                val route = routeResult.getOrElse {
                    AppGraph.distanceRepository.estimateStraightLineRoute(
                        startLat = startLat,
                        startLng = startLng,
                        destLat = destLat,
                        destLng = destLng,
                    )
                }
                val distanceMethod = if (routeResult.isSuccess) DistanceMethod.MAPS else DistanceMethod.GPS_STRAIGHT_LINE

                val now = Instant.now()
                val createdAt = promptTriggeredAt ?: now
                val day = promptDay ?: LocalDate.now()
                val endedAt = createdAt
                val startedAt = endedAt.minusSeconds((route.durationMinutes.toLong().coerceAtLeast(0)) * 60L)
                val tz = ZoneId.systemDefault().id
                val profileId = AppGraph.settings.profileId.first().ifBlank { "default" }
                val citySnapshot = runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AppGraph.storeRepository.getStore(store)?.city
                    }
                }.getOrNull().orEmpty()

                val normalizedStoreNameSnapshot = if (PlaceNameNormalizer.isPostOmbudName(storeName)) {
                    PlaceNameNormalizer.formatPostOmbudDisplayName(name = storeName, city = citySnapshot)
                } else {
                    storeName
                }
                val tripId = AppGraph.tripRepository.createTrip(
                    TripEntity(
                        profileId = profileId,
                        createdAt = createdAt,
                        day = day,
                        startedAt = startedAt,
                        endedAt = endedAt,
                        timeZoneId = tz,
                        storeId = store,
                        storeNameSnapshot = normalizedStoreNameSnapshot,
                        citySnapshot = citySnapshot,
                        storeLatSnapshot = destLat,
                        storeLngSnapshot = destLng,
                        endPlaceType = PlaceType.STORE,
                        startLabelSnapshot = s.startLabel ?: "",
                        startLat = startLat,
                        startLng = startLng,
                        distanceMeters = route.distanceMeters,
                        distanceMethod = distanceMethod,
                        durationMinutes = route.durationMinutes,
                        notes = notes,
                        businessPurpose = businessPurpose,
                        supplierOrArea = null,
                        isBusiness = true,
                        runId = null,
                        currencyCode = null,
                        mileageRateMicros = null,
                    )
                )

                // TODO: Add new backend sync call here when ready
                runCatching {
                    // AppGraph.backendSyncRepository.enqueueTripCreate(tripId)
                }

                AppGraph.promptRepository.confirmWithTrip(promptId, tripId, now)

                _state.update { it.copy(isConfirming = false, error = null) }
                onCreated(tripId)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Failed", isConfirming = false) }
            }
        }
    }

    private fun recomputeCanConfirm() {
        _state.update { s ->
            s.copy(canConfirm = s.startLat != null && s.startLng != null && !s.isConfirming)
        }
    }

    companion object {
        fun factory(promptId: Long) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TripConfirmViewModel(AppGraph.appContext as Application, promptId) as T
            }
        }
    }
}

private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
