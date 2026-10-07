package com.anddav.nationaltrailstracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import com.anddav.nationaltrailstracker.data.local.toLoggedStage
import com.anddav.nationaltrailstracker.domain.ChartPoint
import com.anddav.nationaltrailstracker.domain.Stage
import com.anddav.nationaltrailstracker.domain.StageMaths
import com.anddav.nationaltrailstracker.model.Landmark
import com.anddav.nationaltrailstracker.model.Trail
import com.anddav.nationaltrailstracker.repo.ProgressRepository
import com.anddav.nationaltrailstracker.repo.TrailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StageRow(
    val stage: Stage,
    val cumulativeMiles: Double,
    /** How far through the whole trail the end of this stage is, 0-100. */
    val percentAtEnd: Double,
    /** How far through the whole trail the start of this stage is, 0-100. */
    val percentAtStart: Double,
    val milesToEnd: Double,
    val log: StageLogEntity?,
)

data class LandmarkRow(
    val landmark: Landmark,
    val legMiles: Double?,
    val milesToEnd: Double,
    val percent: Double,
)

data class TrailDetailUiState(
    val isLoading: Boolean = true,
    val trail: Trail? = null,
    val stageRows: List<StageRow> = emptyList(),
    val landmarkRows: List<LandmarkRow> = emptyList(),
    val chartPoints: List<ChartPoint> = emptyList(),
)

class TrailDetailViewModel(
    private val trailId: String,
    private val trailRepository: TrailRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrailDetailUiState())
    val uiState: StateFlow<TrailDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trail = trailRepository.trail(trailId)
            progressRepository.observeForTrail(trailId).collect { logs ->
                _uiState.value = buildUiState(trail, logs)
            }
        }
    }

    fun saveLog(
        stageIndex: Int,
        dateWalked: LocalDate,
        steps: Int?,
        notes: String?,
        actualMiles: Double?,
    ) {
        viewModelScope.launch {
            progressRepository.saveLog(
                StageLogEntity(
                    trailId = trailId,
                    stageIndex = stageIndex,
                    dateWalked = dateWalked,
                    steps = steps,
                    notes = notes,
                    actualMiles = actualMiles,
                ),
            )
        }
    }

    fun deleteLog(log: StageLogEntity) {
        viewModelScope.launch {
            progressRepository.deleteLog(log)
        }
    }

    private fun buildUiState(trail: Trail, logs: List<StageLogEntity>): TrailDetailUiState {
        val logsByIndex = logs.associateBy { it.stageIndex }
        val totalMiles = StageMaths.totalMiles(trail)
        val percentOf = { miles: Double -> if (totalMiles > 0.0) miles / totalMiles * 100 else 0.0 }
        val stageRows = StageMaths.stages(trail).map { stage ->
            val cumulativeMiles = StageMaths.cumulativeMilesAt(trail, stage.index)
            StageRow(
                stage = stage,
                cumulativeMiles = cumulativeMiles,
                percentAtEnd = percentOf(cumulativeMiles),
                percentAtStart = percentOf(cumulativeMiles - stage.miles),
                milesToEnd = totalMiles - cumulativeMiles,
                log = logsByIndex[stage.index],
            )
        }
        val landmarkRows = trail.landmarks.mapIndexed { i, landmark ->
            val next = trail.landmarks.getOrNull(i + 1)
            LandmarkRow(
                landmark = landmark,
                legMiles = next?.let { it.milesFromStart - landmark.milesFromStart },
                milesToEnd = totalMiles - landmark.milesFromStart,
                percent = if (totalMiles > 0.0) landmark.milesFromStart / totalMiles * 100 else 0.0,
            )
        }
        val chartPoints = StageMaths.cumulativeMilesByDate(trail, logs.map(StageLogEntity::toLoggedStage))
        return TrailDetailUiState(
            isLoading = false,
            trail = trail,
            stageRows = stageRows,
            landmarkRows = landmarkRows,
            chartPoints = chartPoints,
        )
    }
}