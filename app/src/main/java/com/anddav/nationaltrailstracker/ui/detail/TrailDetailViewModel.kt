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
    /** Distance from the previous landmark; null for the first one. */
    val milesFromPrevious: Double?,
    val cumulativeMiles: Double,
    val milesToEnd: Double,
    val percent: Double,
)

/** The landmarks within one stage (or, with a null [stageIndex], ones no stage covers). */
data class LandmarkGroup(
    val stageIndex: Int?,
    val walked: Boolean,
    val rows: List<LandmarkRow>,
)

data class TrailDetailUiState(
    val isLoading: Boolean = true,
    val trail: Trail? = null,
    val stageRows: List<StageRow> = emptyList(),
    val landmarkGroups: List<LandmarkGroup> = emptyList(),
    val chartPoints: List<ChartPoint> = emptyList(),
    val progress: TrailProgress = TrailProgress(),
)

/** The walker's totals for this trail, shown in the header. */
data class TrailProgress(
    val stagesDone: Int = 0,
    val stagesTotal: Int = 0,
    val milesWalked: Double = 0.0,
    val totalMiles: Double = 0.0,
    val percentComplete: Double = 0.0,
    val stepsWalked: Int = 0,
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
            val previous = trail.landmarks.getOrNull(i - 1)
            LandmarkRow(
                landmark = landmark,
                milesFromPrevious = previous?.let { landmark.milesFromStart - it.milesFromStart },
                cumulativeMiles = landmark.milesFromStart,
                milesToEnd = totalMiles - landmark.milesFromStart,
                percent = percentOf(landmark.milesFromStart),
            )
        }
        // Consecutive landmarks with the same owning stage form one group.
        val landmarkGroups = mutableListOf<LandmarkGroup>()
        StageMaths.stageIndexPerLandmark(trail).zip(landmarkRows).forEach { (stageIndex, row) ->
            val last = landmarkGroups.lastOrNull()
            if (last != null && last.stageIndex == stageIndex) {
                landmarkGroups[landmarkGroups.lastIndex] = last.copy(rows = last.rows + row)
            } else {
                val walked = stageIndex != null && logsByIndex.containsKey(stageIndex)
                landmarkGroups += LandmarkGroup(stageIndex = stageIndex, walked = walked, rows = listOf(row))
            }
        }
        val loggedStages = logs.map(StageLogEntity::toLoggedStage)
        val chartPoints = StageMaths.cumulativeMilesByDate(trail, loggedStages)
        val progress = TrailProgress(
            stagesDone = logs.size,
            stagesTotal = stageRows.size,
            milesWalked = StageMaths.milesWalked(trail, loggedStages),
            totalMiles = totalMiles,
            percentComplete = StageMaths.percentComplete(trail, loggedStages),
            stepsWalked = StageMaths.stepsWalked(loggedStages),
        )
        return TrailDetailUiState(
            isLoading = false,
            trail = trail,
            stageRows = stageRows,
            landmarkGroups = landmarkGroups,
            chartPoints = chartPoints,
            progress = progress,
        )
    }
}