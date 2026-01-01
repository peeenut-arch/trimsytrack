package com.trimsytrack.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.PromptEventEntity
import com.trimsytrack.data.entities.TripEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class TodayViewModel : ViewModel() {
    private val day = LocalDate.now()

    val prompts: StateFlow<List<PromptEventEntity>> =
        AppGraph.promptRepository.observeToday(day)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trips: StateFlow<List<TripEntity>> =
        AppGraph.tripRepository.observeToday(day)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    object Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TodayViewModel() as T
        }
    }
}
