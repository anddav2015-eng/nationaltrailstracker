package com.anddav.nationaltrailstracker.repo

import com.anddav.nationaltrailstracker.data.geo.RouteAssetLoader
import com.anddav.nationaltrailstracker.data.trail.TrailAssetLoader
import com.anddav.nationaltrailstracker.domain.RoutePoint
import com.anddav.nationaltrailstracker.model.Trail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Static, read-only trail data bundled in assets/trails/. */
class TrailRepository(
    private val assetLoader: TrailAssetLoader,
    private val routeLoader: RouteAssetLoader,
) {

    suspend fun allTrails(): List<Trail> = withContext(Dispatchers.IO) {
        assetLoader.loadAllTrails()
    }

    suspend fun trail(id: String): Trail = withContext(Dispatchers.IO) {
        assetLoader.loadTrail(id)
    }

    /** The trail's route line for "Where am I", or null if it hasn't been built yet. */
    suspend fun routeLine(trail: Trail): List<RoutePoint>? = withContext(Dispatchers.IO) {
        trail.gpxAsset?.let(routeLoader::load)
    }
}