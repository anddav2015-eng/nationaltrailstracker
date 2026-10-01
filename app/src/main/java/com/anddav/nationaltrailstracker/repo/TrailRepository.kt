package com.anddav.nationaltrailstracker.repo

import com.anddav.nationaltrailstracker.data.trail.TrailAssetLoader
import com.anddav.nationaltrailstracker.model.Trail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Static, read-only trail data bundled in assets/trails/. */
class TrailRepository(private val assetLoader: TrailAssetLoader) {

    suspend fun allTrails(): List<Trail> = withContext(Dispatchers.IO) {
        assetLoader.loadAllTrails()
    }

    suspend fun trail(id: String): Trail = withContext(Dispatchers.IO) {
        assetLoader.loadTrail(id)
    }
}