package com.anddav.nationaltrailstracker.domain

import kotlin.math.cos
import kotlin.math.sqrt

/** One vertex of a trail's simplified route line, with the trail mileage at that point. */
data class RoutePoint(val lat: Double, val lon: Double, val miles: Double)

/** Where a GPS fix sits relative to the route line. */
data class RouteFix(
    /** Trail mileage of the nearest point on the route. */
    val milesAlong: Double,
    /** Straight-line distance from the fix to that nearest point. */
    val offRouteMetres: Double,
)

/**
 * Snaps a location to the nearest segment of a route polyline. Pure Kotlin, no Android types.
 *
 * Distances use a local equirectangular projection centred on the query point. Over the few
 * hundred metres that matter for "nearest segment" that's accurate to well under a metre, and much
 * simpler than full geodesics.
 */
class RouteLocator(private val points: List<RoutePoint>) {

    init {
        require(points.size >= 2) { "A route needs at least two points, got ${points.size}" }
    }

    fun locate(lat: Double, lon: Double): RouteFix {
        val metresPerDegreeLon = METRES_PER_DEGREE_LAT * cos(Math.toRadians(lat))
        fun x(p: RoutePoint) = (p.lon - lon) * metresPerDegreeLon
        fun y(p: RoutePoint) = (p.lat - lat) * METRES_PER_DEGREE_LAT

        var bestDistanceSq = Double.MAX_VALUE
        var bestMiles = points.first().miles
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val ax = x(a)
            val ay = y(a)
            val dx = x(b) - ax
            val dy = y(b) - ay
            val lengthSq = dx * dx + dy * dy
            // The query point is the origin, so project (-ax, -ay) onto the segment.
            val t = if (lengthSq == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSq).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val distanceSq = px * px + py * py
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestMiles = a.miles + t * (b.miles - a.miles)
            }
        }
        return RouteFix(milesAlong = bestMiles, offRouteMetres = sqrt(bestDistanceSq))
    }

    private companion object {
        /** Mean metres per degree of latitude; varies by <0.5% across Great Britain. */
        const val METRES_PER_DEGREE_LAT = 111_195.0
    }
}
