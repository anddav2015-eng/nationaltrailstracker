package com.anddav.nationaltrailstracker.repo

import com.anddav.nationaltrailstracker.data.backup.ProgressBackupFile
import com.anddav.nationaltrailstracker.data.backup.toBackupEntry
import com.anddav.nationaltrailstracker.data.backup.toEntity
import com.anddav.nationaltrailstracker.data.local.StageLogDao
import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import com.anddav.nationaltrailstracker.data.local.toLoggedStage
import com.anddav.nationaltrailstracker.data.seed.SeedProgressLoader
import com.anddav.nationaltrailstracker.domain.LoggedStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** The walker's logged progress, stored in Room. Separate from the static trail data. */
class ProgressRepository(
    private val dao: StageLogDao,
    private val seedLoader: SeedProgressLoader,
    private val json: Json,
) {
    fun observeAll(): Flow<List<StageLogEntity>> = dao.observeAll()

    fun observeForTrail(trailId: String): Flow<List<StageLogEntity>> = dao.observeForTrail(trailId)

    fun observeLoggedStagesForTrail(trailId: String): Flow<List<LoggedStage>> =
        observeForTrail(trailId).map { logs -> logs.map(StageLogEntity::toLoggedStage) }

    /** Creates or replaces the log for [StageLogEntity.trailId] + [StageLogEntity.stageIndex] -
     * the unique index on that pair plus REPLACE conflict resolution makes this an upsert
     * regardless of whether a log already exists for the stage, so callers never need to look up
     * an id first. Pass id = 0 (the default) for both new and edited logs. */
    suspend fun saveLog(log: StageLogEntity) {
        dao.insert(log.copy(id = 0))
    }

    suspend fun deleteLog(log: StageLogEntity) {
        dao.delete(log)
    }

    /** Imports the owner's existing progress (assets/seed-progress.json) the first time the app
     * runs, i.e. while the stage_logs table is still empty. Safe to call on every launch. */
    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            dao.insertAll(seedLoader.load())
        }
    }

    suspend fun exportBackupJson(): String {
        val entries = dao.getAllOnce().map { it.toBackupEntry() }
        return json.encodeToString(ProgressBackupFile.serializer(), ProgressBackupFile(entries))
    }

    /** Replaces all current progress with the contents of a previously exported backup file. */
    suspend fun importBackupJson(text: String) {
        val backup = json.decodeFromString(ProgressBackupFile.serializer(), text)
        dao.deleteAll()
        dao.insertAll(backup.stageLogs.map { it.toEntity() })
    }
}