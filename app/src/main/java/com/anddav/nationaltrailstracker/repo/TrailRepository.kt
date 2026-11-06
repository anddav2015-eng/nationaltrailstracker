package com.anddav.nationaltrailstracker.repo

import com.anddav.nationaltrailstracker.data.geo.LegsAssetLoader
import com.anddav.nationaltrailstracker.data.geo.RouteAssetLoader
import com.anddav.nationaltrailstracker.data.trail.TrailAssetLoader
import com.anddav.nationaltrailstracker.domain.RoutePoint
import com.anddav.nationaltrailstracker.domain.map.MapLeg
import com.anddav.nationaltrailstracker.domain.map.MapLegs
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

    /**
     * The trail's route line for "Where am I", in table mileage, or null if it hasn't been built yet.
     * Uses the map's (OpenStreetMap) line when the trail has one, otherwise the older assets/geo line.
     */
    suspend fun routeLine(trail: Trail): List<RoutePoint>? = withContext(Dispatchers.IO) {
        if (legsLoader.hasMap(trail.id)) {
            MapLegs.toRoutePoints(legsLoader.load(trail.id), trail.landmarks.associate { it.name to it.milesFromStart })
        } else {
            trail.gpxAsset?.let(routeLoader::load)
        }
    }

    /** True when a map route line is bundled for this trail. */
    suspend fun hasMap(trailId: String): Boolean = withContext(Dispatchers.IO) {
        legsLoader.hasMap(trailId)
    }

    /** The trail's map route line cut into landmark-to-landmark legs. */
    suspend fun mapLegs(trailId: String): List<MapLeg> = withContext(Dispatchers.IO) {
        legsLoader.load(trailId)
    }
}
