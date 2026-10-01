package com.anddav.nationaltrailstracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.anddav.nationaltrailstracker.domain.LoggedStage
import java.time.LocalDate

/** One logged stage. [trailId] + [stageIndex] identify the stage within the bundled trail JSON
 * (see [com.anddav.nationaltrailstracker.model.Trail.defaultStages]). */
@Entity(
    tableName = "stage_logs",
    indices = [Index(value = ["trailId", "stageIndex"], unique = true)],
)
data class StageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: String,
    val stageIndex: Int,
    val dateWalked: LocalDate,
    val steps: Int?,
    val notes: String?,
    val actualMiles: Double?,
)

fun StageLogEntity.toLoggedStage() = LoggedStage(
    stageIndex = stageIndex,
    steps = steps,
    actualMiles = actualMiles,
    dateWalked = dateWalked,
)