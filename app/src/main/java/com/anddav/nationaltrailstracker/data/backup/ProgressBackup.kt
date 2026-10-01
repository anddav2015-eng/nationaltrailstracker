package com.anddav.nationaltrailstracker.data.backup

import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** The JSON shape for a progress backup - used both for the bundled seed-progress.json (the
 * owner's existing progress, imported once on first launch) and for user-triggered export/import
 * backups, since both are "here's a list of logged stages" in the same format. */
@Serializable
data class ProgressBackupFile(val stageLogs: List<StageLogBackupEntry>)

@Serializable
data class StageLogBackupEntry(
    val trailId: String,
    val stageIndex: Int,
    val dateWalked: String,
    val steps: Int? = null,
    val notes: String? = null,
    val actualMiles: Double? = null,
)

fun StageLogEntity.toBackupEntry() = StageLogBackupEntry(
    trailId = trailId,
    stageIndex = stageIndex,
    dateWalked = dateWalked.toString(),
    steps = steps,
    notes = notes,
    actualMiles = actualMiles,
)

fun StageLogBackupEntry.toEntity() = StageLogEntity(
    trailId = trailId,
    stageIndex = stageIndex,
    dateWalked = LocalDate.parse(dateWalked),
    steps = steps,
    notes = notes,
    actualMiles = actualMiles,
)
