package com.anddav.nationaltrailstracker.di

import android.content.Context
import androidx.room.Room
import com.anddav.nationaltrailstracker.data.local.AppDatabase
import com.anddav.nationaltrailstracker.data.seed.SeedProgressLoader
import com.anddav.nationaltrailstracker.data.trail.TrailAssetLoader
import com.anddav.nationaltrailstracker.repo.ProgressRepository
import com.anddav.nationaltrailstracker.repo.TrailRepository
import kotlinx.serialization.json.Json

/** Manual constructor injection, no Hilt - see CLAUDE.md. One instance lives on
 * [com.anddav.nationaltrailstracker.NationalTrailsApplication] for the app's lifetime. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val database = Room.databaseBuilder(
        applicationContext,
        AppDatabase::class.java,
        "national-trails-tracker.db",
    ).build()

    val trailRepository = TrailRepository(TrailAssetLoader(applicationContext, json))

    val progressRepository = ProgressRepository(
        dao = database.stageLogDao(),
        seedLoader = SeedProgressLoader(applicationContext, json),
        json = json,
    )
}