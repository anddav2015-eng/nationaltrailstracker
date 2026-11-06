package com.anddav.nationaltrailstracker.repo

import com.anddav.nationaltrailstracker.data.geo.LegsAssetLoader
import com.anddav.nationaltrailstracker.data.geo.RouteAssetLoader
import com.anddav.nationaltrailstracker.data.trail.TrailAssetLoader
import com.anddav.nationaltrailstracker.domain.RoutePoint
import com.anddav.nationaltrailstracker.domain.map.MapLeg
import com.anddav.nationaltrailstracker.domain.map.MapLineVersion
import com.anddav.nationaltrailstracker.model.Trail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Static, read-only trail data bundled in assets/trails/. */
class TrailRepository(
    private val assetLoader: TrailAssetLoader,
    private val routeLoader: RouteAssetLoader,
    private val legsLoader: LegsAssetLoader,
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

    /** The map route-line versions bundled for this trail; empty if it has no map yet. */
    suspend fun mapLineVersions(trailId: String): List<MapLineVersion> = withContext(Dispatchers.IO) {
        legsLoader.availableVersions(trailId)
    }

    /** The trail's route line cut into landmark-to-landmark legs, for the map. */
    suspend fun mapLegs(trailId: String, version: MapLineVersion): List<MapLeg> = withContext(Dispatchers.IO) {
        legsLoader.load(trailId, version)
    }
}
