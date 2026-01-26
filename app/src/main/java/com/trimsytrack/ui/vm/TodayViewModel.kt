package com.trimsytrack.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trimsytrack.AppGraph
import com.trimsytrack.data.DistanceRepository
import com.trimsytrack.data.entities.PingEventEntity
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

class TodayViewModel : ViewModel() {
    private val day = LocalDate.now()

    val prompts: StateFlow<List<PromptEventEntity>> =
        AppGraph.promptRepository.observeToday(day)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pings: StateFlow<List<PingEventEntity>> =
        AppGraph.pingRepository.observeToday(day)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trips: StateFlow<List<TripEntity>> =
        AppGraph.tripRepository.observeToday(day)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(pings, trips) { pingList, tripList ->
                pingList to tripList
            }.collect { (pingList, tripList) ->
                if (pingList.isEmpty() || tripList.isEmpty()) return@collect

                // TripDao.observeByDay returns DESC; we need chronological for a stable "last trip before ping" anchor.
                val tripsAsc = tripList.sortedBy { it.endedAt }
                var tripIndex = 0
                var lastTrip: TripEntity? = null

                for (ping in pingList) {
                    while (tripIndex < tripsAsc.size && !tripsAsc[tripIndex].endedAt.isAfter(ping.occurredAt)) {
                        lastTrip = tripsAsc[tripIndex]
                        tripIndex++
                    }

                    val anchor = lastTrip ?: continue

                    // If already snapped *with an explicit trip anchor*, never recompute.
                    val hasSnapshot = ping.routeDistanceFromPrevMeters != null && ping.routeDurationFromPrevMinutes != null
                    if (hasSnapshot && ping.routeAnchorTripId != null) continue
                    if (ping.id <= 0L) continue

                    // Optimization: same store => 0 distance, no external call.
                    if (anchor.storeId == ping.storeId) {
                        runCatching {
                            AppGraph.db.pingDao().setRouteSnapshot(
                                pingId = ping.id,
                                distanceMeters = 0,
                                durationMinutes = 0,
                                source = "SAME_STORE",
                                computedAt = Instant.now(),
                                anchorTripId = anchor.id,
                            )
                        }
                        continue
                    }

                    val startLocationId = "storelocation:${anchor.storeId}"
                    val endLocationId = "storelocation:${ping.storeId}"

                    runCatching {
                        AppGraph.distanceRepository.getOrComputeDrivingRoute(
                            startLat = anchor.storeLatSnapshot,
                            startLng = anchor.storeLngSnapshot,
                            destLat = ping.storeLatSnapshot,
                            destLng = ping.storeLngSnapshot,
                            startLocationId = startLocationId,
                            endLocationId = endLocationId,
                        )
                    }.onSuccess { metrics: DistanceRepository.RouteMetrics ->
                        // Persist snapshot onto the ping row so it never changes (for this anchor).
                        runCatching {
                            AppGraph.db.pingDao().setRouteSnapshot(
                                pingId = ping.id,
                                distanceMeters = metrics.distanceMeters,
                                durationMinutes = metrics.durationMinutes,
                                source = metrics.source,
                                computedAt = Instant.now(),
                                anchorTripId = anchor.id,
                            )
                        }
                    }.onFailure {
                        // Leave missing; UI can show placeholder.
                    }
                }
            }
        }
    }

    object Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TodayViewModel() as T
        }
    }
}
