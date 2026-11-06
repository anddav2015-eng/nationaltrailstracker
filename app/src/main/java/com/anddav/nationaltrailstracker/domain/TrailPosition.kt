package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.model.Landmark
import com.anddav.nationaltrailstracker.model.Trail

/** Everything the "Where am I" screen shows for one GPS fix. Never stored. */
data class TrailPosition(
    val milesAlong: Double,
    val offRouteMetres: Double,
    val isOffRoute: Boolean,
    /** The last landmark at or before [milesAlong]; null only if the route starts after it. */
    val landmarkBehind: Landmark?,
    /** The next landmark after [milesAlong]; null at or past the end. */
    val landmarkAhead: Landmark?,
    /** The stage containing [milesAlong]; null for a trail with no stages. */
    val stage: Stage?,
    val milesToStageEnd: Double?,
    val milesToTrailEnd: Double,
) {
    companion object {
        /** More than this far from the line and the screen warns the walker. */
        const val OFF_ROUTE_THRESHOLD_METRES = 500.0

        fun from(trail: Trail, fix: RouteFix): TrailPosition {
            val miles = fix.milesAlong
            val stages = StageMaths.stages(trail)
            // On a boundary, report the stage that starts there - that's the one being walked next.
            val stage = stages.firstOrNull { miles >= it.fromLandmark.milesFromStart && miles < it.toLandmark.milesFromStart }
                ?: stages.lastOrNull()?.takeIf { miles >= it.toLandmark.milesFromStart }
                ?: stages.firstOrNull()
            val trailEnd = stages.lastOrNull()?.toLandmark?.milesFromStart
                ?: trail.landmarks.lastOrNull()?.milesFromStart
                ?: 0.0
            return TrailPosition(
                milesAlong = miles,
                offRouteMetres = fix.offRouteMetres,
                isOffRoute = fix.offRouteMetres > OFF_ROUTE_THRESHOLD_METRES,
                landmarkBehind = trail.landmarks.lastOrNull { it.milesFromStart <= miles },
                landmarkAhead = trail.landmarks.firstOrNull { it.milesFromStart > miles },
                stage = stage,
                milesToStageEnd = stage?.let { (it.toLandmark.milesFromStart - miles).coerceAtLeast(0.0) },
                milesToTrailEnd = (trailEnd - miles).coerceAtLeast(0.0),
            )
        }
    }
}
