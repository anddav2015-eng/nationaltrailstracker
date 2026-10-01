package com.anddav.nationaltrailstracker.data.seed

import android.content.Context
import com.anddav.nationaltrailstracker.data.backup.ProgressBackupFile
import com.anddav.nationaltrailstracker.data.backup.toEntity
import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import kotlinx.serialization.json.Json

/** Loads the owner's existing progress (assets/seed-progress.json), imported into Room on
 * first launch only - see [com.anddav.nationaltrailstracker.repo.ProgressRepository.seedIfEmpty]. */
class SeedProgressLoader(private val context: Context, private val json: Json) {

    fun load(): List<StageLogEntity> {
        val text = context.assets.open("seed-progress.json").bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val backup = json.decodeFromString(ProgressBackupFile.serializer(), text)
        return backup.stageLogs.map { it.toEntity() }
    }
}
