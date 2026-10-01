package com.anddav.nationaltrailstracker.ui.trails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import com.anddav.nationaltrailstracker.data.local.toLoggedStage
import com.anddav.nationaltrailstracker.domain.StageMaths
import com.anddav.nationaltrailstracker.model.Trail
import com.anddav.nationaltrailstracker.repo.ProgressRepository
import com.anddav.nationaltrailstracker.repo.TrailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrailListItem(
    val id: String,
    val name: String,
    val colourHex: String,
    val stagesDone: Int,
    val stagesTotal: Int,
    val milesWalked: Double,
    val totalMiles: Double,
    val percentComplete: Double,
    val isComplete: Boolean,
)

data class TrailsListUiState(
    val isLoading: Boolean = true,
    val trails: List<TrailListItem> = emptyList(),
    val trailsComplete: Int = 0,
    val trailsTotal: Int = 0,
    val totalMilesWalked: Double = 0.0,
)

class TrailsListViewModel(
    private val trailRepository: TrailRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrailsListUiState())
    val uiState: StateFlow<TrailsListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trails = trailRepository.allTrails()
            progressRepository.observeAll().collect { logs ->
                _uiState.value = buildUiState(trails, logs)
            }
        }
    }

    private fun buildUiState(trails: List<Trail>, logs: List<StageLogEntity>): TrailsListUiState {
        val logsByTrail = logs.groupBy { it.trailId }
        val items = trails.map { trail ->
            val loggedStages = logsByTrail[trail.id].orEmpty().map(StageLogEntity::toLoggedStage)
            TrailListItem(
                id = trail.id,
                name = trail.name,
                colourHex = trail.colour,
                stagesDone = loggedStages.size,
                stagesTotal = trail.defaultStages.size,
                milesWalked = StageMaths.milesWalked(trail, loggedStages),
                totalMiles = StageMaths.totalMiles(trail),
                percentComplete = StageMaths.percentComplete(trail, loggedStages),
                isComplete = StageMaths.isComplete(trail, loggedStages),
            )
        }
        return TrailsListUiState(
            isLoading = false,
            trails = items,
            trailsComplete = items.count { it.isComplete },
            trailsTotal = items.size,
            totalMilesWalked = items.sumOf { it.milesWalked },
        )
    }

    /** Plain suspend functions (not viewModelScope.launch) so the caller - the SAF file-picker
     * flow in TrailsListScreen - can await completion and show success/error feedback. */
    suspend fun exportBackup(): String = progressRepository.exportBackupJson()

    suspend fun importBackup(text: String) = progressRepository.importBackupJson(text)
}