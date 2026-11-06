package com.anddav.nationaltrailstracker.domain.map

import com.anddav.nationaltrailstracker.domain.RouteLocator
import com.anddav.nationaltrailstracker.domain.RoutePoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a location is relative to one stage, measured along the map's route line. */
sealed interface StagePosition {
    /** Distance from the location to the nearest point on the whole trail line. */
    val offRouteMetres: Double

    data class InStage(
        val milesAlong: Double,
        val stageMiles: Double,
        val endName: String,
        override val offRouteMetres: Double,
    ) : StagePosition {
        val milesToEnd: Double get() = (stageMiles - milesAlong).coerceAtLeast(0.0)
    }

    data class BeforeStart(val milesToStart: Double, val startName: String, override val offRouteMetres: Double) :
        StagePosition

    data class PastEnd(val milesPastEnd: Double, val endName: String, override val offRouteMetres: Double) :
        StagePosition
}

/**
 * Places a location against a stage using the map line (not the table mileages, which can differ).
 * The location is snapped to the whole trail line, so being before or after the stage can be told
 * apart from being alongside it. Pure Kotlin; reuses [RouteLocator] for the snapping.
 */
class StageLocator(legs: List<MapLeg>, stageIndex: Int) {

    private val locator: RouteLocator
    private val stageStartMiles: Double
    private val stageEndMiles: Double
    private val startName: String
    private val endName: String

    init {
        val stageLegs = MapLegs.stageLegs(legs, stageIndex)
        require(stageLegs.isNotEmpty()) { "Stage $stageIndex has no legs" }
        // Cumulative miles at the start of each leg, along the joined line.
        var miles = 0.0
        val routePoints = ArrayList<RoutePoint>()
        val legStart = HashMap<Int, Double>()
        val legEnd = HashMap<Int, Double>()
        for (leg in legs) {
            legStart[leg.index] = miles
            for ((i, p) in leg.points.withIndex()) {
                val prev = routePoints.lastOrNull()
                if (i == 0 && prev != null && prev.lat == p.lat && prev.lon == p.lon) continue
                if (prev != null) miles += haversineMiles(prev.lat, prev.lon, p.lat, p.lon)
                routePoints.add(RoutePoint(p.lat, p.lon, miles))
            }
            legEnd[leg.index] = miles
        }
        locator = RouteLocator(routePoints)
        stageStartMiles = legStart.getValue(stageLegs.first().index)
        stageEndMiles = legEnd.getValue(stageLegs.last().index)
        startName = stageLegs.first().from
        endName = stageLegs.last().to
    }

    val stageMiles: Double get() = stageEndMiles - stageStartMiles

    fun locate(lat: Double, lon: Double): StagePosition {
        val fix = locator.locate(lat, lon)
        val m = fix.milesAlong
        return when {
            m < stageStartMiles - EDGE_TOLERANCE_MILES ->
                StagePosition.BeforeStart(stageStartMiles - m, startName, fix.offRouteMetres)
            m > stageEndMiles + EDGE_TOLERANCE_MILES ->
                StagePosition.PastEnd(m - stageEndMiles, endName, fix.offRouteMetres)
            else -> StagePosition.InStage(
                milesAlong = (m - stageStartMiles).coerceIn(0.0, stageMiles),
                stageMiles = stageMiles,
                endName = endName,
                offRouteMetres = fix.offRouteMetres,
            )
        }
    }

    companion object {
        /** Within this of a stage end counts as at it, so GPS jitter doesn't flip the message. */
        const val EDGE_TOLERANCE_MILES = 0.05
        /** More than this from the line gets an off-route warning (same as Where am I). */
        const val OFF_ROUTE_WARNING_METRES = 500.0

        private const val EARTH_RADIUS_MILES = 3958.7613

        fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_MILES * asin(sqrt(h))
        }
    }
}
