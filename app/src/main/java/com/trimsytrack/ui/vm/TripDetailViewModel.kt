package com.trimsytrack.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripDetailViewModel(private val tripId: Long) : ViewModel() {
    private val _trip = MutableStateFlow<TripEntity?>(null)
    val trip: StateFlow<TripEntity?> = _trip

    init {
        viewModelScope.launch {
            AppGraph.tripRepository.observeTrip(tripId).collect { t ->
                _trip.value = t
            }
        }
    }

    fun updateTrip(entity: TripEntity) {
        viewModelScope.launch {
            val previous = _trip.value
            val isUserMutation = previous != null && previous != entity
            val desired = if (isUserMutation) {
                entity.copy(
                    syncStatus = SyncStatus.PENDING,
                    syncErrorMachineCode = null,
                    syncErrorMessage = null,
                )
            } else {
                entity
            }
            AppGraph.tripRepository.updateTrip(desired)
        }
    }

    fun retrySync() {
        val t = _trip.value ?: return
        updateTrip(
            t.copy(
                syncStatus = SyncStatus.PENDING,
                syncErrorMachineCode = null,
                syncErrorMessage = null,
            )
        )
    }

    companion object {
        fun factory(tripId: Long) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TripDetailViewModel(tripId) as T
            }
        }
    }
}
