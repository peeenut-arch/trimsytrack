package com.trimsytrack.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripDetailViewModel(private val tripId: Long) : ViewModel() {
    private val _trip = MutableStateFlow<TripEntity?>(null)
    val trip: StateFlow<TripEntity?> = _trip

    init {
        viewModelScope.launch {
            _trip.value = AppGraph.tripRepository.get(tripId)
        }
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
