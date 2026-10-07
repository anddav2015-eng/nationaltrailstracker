package com.anddav.nationaltrailstracker.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import com.anddav.nationaltrailstracker.data.local.toLoggedStage
import com.anddav.nationaltrailstracker.domain.SummaryStats
import com.anddav.nationaltrailstracker.repo.ProgressRepository
import com.anddav.nationaltrailstracker.repo.TrailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SummaryStatsUiState(
    val isLoading: Boolean = true,
    val stats: SummaryStats? = null,
)

class SummaryStatsViewModel(
    private val trailRepository: TrailRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryStatsUiState())
    val uiState: StateFlow<SummaryStatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trails = trailRepository.allTrails()
            progressRepository.observeAll().collect { logs ->
                val logsByTrail = logs.groupBy(StageLogEntity::trailId)
                    .mapValues { (_, trailLogs) -> trailLogs.map(StageLogEntity::toLoggedStage) }
                _uiState.value = SummaryStatsUiState(
                    isLoading = false,
                    stats = SummaryStats.from(trails, logsByTrail),
                )
            }
        }
    }
}
