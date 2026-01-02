package com.trimsytrack.ui.vm

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PromptStatus
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
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
            val last = AppGraph.tripRepository.latestTripForDay(day)
            val canUseLast = last != null && distanceKm(
                last.storeLatSnapshot,
                last.storeLngSnapshot,
                prompt.storeLatSnapshot,
                prompt.storeLngSnapshot
            ) <= 10.0

            _state.update { prev ->
                // Default behavior: if we likely came from another store today, assume "store -> this store".
                // This makes every confirmed journey populate the internal distance cache graph.
                val autoStartLabel = if (canUseLast) "Last store: ${last!!.storeNameSnapshot}" else prev.startLabel
                val autoStartLat = if (canUseLast) last!!.storeLatSnapshot else prev.startLat
                val autoStartLng = if (canUseLast) last!!.storeLngSnapshot else prev.startLng
                val autoStartStoreId = if (canUseLast) last!!.storeId else prev.startStoreId

                prev.copy(
                    storeName = prompt.storeNameSnapshot,
                    storeLat = prompt.storeLatSnapshot,
                    storeLng = prompt.storeLngSnapshot,
                    canUseLastStore = canUseLast,
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
            val last = AppGraph.tripRepository.latestTripForDay(LocalDate.now()) ?: return@launch
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

    fun confirm(notes: String, onCreated: (Long) -> Unit) {
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
                val route = AppGraph.distanceRepository.getOrComputeDrivingRoute(
                    startLat = startLat,
                    startLng = startLng,
                    destLat = destLat,
                    destLng = destLng,
                    startLocationId = s.startStoreId,
                    endLocationId = store,
                )

                val now = Instant.now()
                val createdAt = promptTriggeredAt ?: now
                val day = promptDay ?: LocalDate.now()
                val tripId = AppGraph.tripRepository.createTrip(
                    TripEntity(
                        createdAt = createdAt,
                        day = day,
                        storeId = store,
                        storeNameSnapshot = storeName,
                        storeLatSnapshot = destLat,
                        storeLngSnapshot = destLng,
                        startLabelSnapshot = s.startLabel ?: "",
                        startLat = startLat,
                        startLng = startLng,
                        distanceMeters = route.distanceMeters,
                        durationMinutes = route.durationMinutes,
                        notes = notes,
                        runId = null,
                        currencyCode = null,
                        mileageRateMicros = null,
                    )
                )

                // Backend-authoritative sync: enqueue create intent + attempt immediate send.
                runCatching {
                    AppGraph.backendSyncRepository.enqueueTripCreate(tripId)
                    AppGraph.backendSyncManager.scheduleImmediate("trip-confirm")
                }

                AppGraph.promptRepository.confirmWithTrip(promptId, tripId, now)

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
