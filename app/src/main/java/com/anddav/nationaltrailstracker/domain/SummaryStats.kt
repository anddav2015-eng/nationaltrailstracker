package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.model.Trail

/** Progress totals across every trail, shown on the Summary screen. Never stored. */
data class SummaryStats(
    val trailsComplete: Int,
    val trailsStarted: Int,
    val trailsTotal: Int,
    /** Trails with no stages yet (e.g. the England Coast Path placeholder), left out of [totalMiles]. */
    val trailsUnplanned: Int,
    val stagesDone: Int,
    val stagesTotal: Int,
    val milesWalked: Double,
    val totalMiles: Double,
    val stepsWalked: Int,
) {
    val milesRemaining: Double get() = (totalMiles - milesWalked).coerceAtLeast(0.0)

    val percentOfTotalMiles: Double
        get() = if (totalMiles <= 0.0) 0.0 else (milesWalked / totalMiles * 100).coerceIn(0.0, 100.0)

    companion object {
        /** [logsByTrail] is keyed by trail id; trails with no entry have no logs. */
        fun from(trails: List<Trail>, logsByTrail: Map<String, List<LoggedStage>>): SummaryStats {
            val logsFor = { trail: Trail -> logsByTrail[trail.id].orEmpty() }
            return SummaryStats(
                trailsComplete = trails.count { StageMaths.isComplete(it, logsFor(it)) },
                trailsStarted = trails.count { logsFor(it).isNotEmpty() },
                trailsTotal = trails.size,
                trailsUnplanned = trails.count { it.defaultStages.isEmpty() },
                stagesDone = trails.sumOf { logsFor(it).size },
                stagesTotal = trails.sumOf { it.defaultStages.size },
                milesWalked = trails.sumOf { StageMaths.milesWalked(it, logsFor(it)) },
                totalMiles = trails.sumOf { StageMaths.totalMiles(it) },
                stepsWalked = trails.sumOf { StageMaths.stepsWalked(logsFor(it)) },
            )
        }
    }
}
