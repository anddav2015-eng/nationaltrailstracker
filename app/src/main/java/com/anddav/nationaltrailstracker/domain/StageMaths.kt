package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.model.Trail

/** Every derived value shown in the UI (stage/cumulative miles, % complete, trail-complete).
 * Pure Kotlin, no Android types, so it's unit testable without an emulator. */
object StageMaths {

    fun stages(trail: Trail): List<Stage> {
        val byName = trail.landmarks.associateBy { it.name }
        return trail.defaultStages.mapIndexed { index, def ->
            val from = byName.getValue(def.fromLandmark)
            val to = byName.getValue(def.toLandmark)
            Stage(index = index, fromLandmark = from, toLandmark = to, note = def.note)
        }
    }

    /** Cumulative miles from the trail start to the end of [stageIndex] (0-based, inclusive). */
    fun cumulativeMilesAt(trail: Trail, stageIndex: Int): Double {
        val trailStages = stages(trail)
        require(stageIndex in trailStages.indices) { "stageIndex $stageIndex out of range" }
        return trailStages[stageIndex].toLandmark.milesFromStart - trailStages.first().fromLandmark.milesFromStart
    }

    /** For each landmark (in [Trail.landmarks] order), the 0-based index of the stage it falls in,
     * or null if no stage covers it. A stage owns the landmarks after its start up to and including
     * its end, so a shared boundary landmark belongs to the stage that finishes there; the very
     * first stage also owns its start landmark. */
    fun stageIndexPerLandmark(trail: Trail): List<Int?> {
        val positionOf = trail.landmarks.withIndex().associate { (i, landmark) -> landmark.name to i }
        val owner = arrayOfNulls<Int>(trail.landmarks.size)
        trail.defaultStages.forEachIndexed { stageIndex, def ->
            val from = positionOf.getValue(def.fromLandmark)
            val to = positionOf.getValue(def.toLandmark)
            val first = if (stageIndex == 0) from else from + 1
            for (i in first..to) owner[i] = stageIndex
        }
        return owner.toList()
    }

    fun totalMiles(trail: Trail): Double {
        val trailStages = stages(trail)
        if (trailStages.isEmpty()) return 0.0
        return trailStages.last().toLandmark.milesFromStart - trailStages.first().fromLandmark.milesFromStart
    }

    /** Sums, per logged stage, [LoggedStage.actualMiles] when the walker recorded one (e.g. an
     * overrun day), falling back to the nominal stage distance otherwise. */
    fun milesWalked(trail: Trail, logs: List<LoggedStage>): Double {
        val trailStages = stages(trail).associateBy { it.index }
        return logs.sumOf { log ->
            log.actualMiles ?: trailStages[log.stageIndex]?.miles ?: 0.0
        }
    }

    fun stepsWalked(logs: List<LoggedStage>): Int =
        logs.sumOf { it.steps ?: 0 }

    fun percentComplete(trail: Trail, logs: List<LoggedStage>): Double {
        val total = totalMiles(trail)
        if (total <= 0.0) return 0.0
        return (milesWalked(trail, logs) / total * 100).coerceIn(0.0, 100.0)
    }

    fun isComplete(trail: Trail, logs: List<LoggedStage>): Boolean {
        val loggedIndices = logs.map { it.stageIndex }.toSet()
        val trailStages = stages(trail)
        return trailStages.isNotEmpty() && trailStages.all { it.index in loggedIndices }
    }

    /** Cumulative miles walked by calendar date (chronological order, not logging order - stages
     * can be logged out of sequence). Multiple stages logged on the same date collapse into one
     * point. */
    fun cumulativeMilesByDate(trail: Trail, logs: List<LoggedStage>): List<ChartPoint> {
        val trailStages = stages(trail).associateBy { it.index }
        val milesByDate = logs
            .groupBy { it.dateWalked }
            .mapValues { (_, dayLogs) ->
                dayLogs.sumOf { log -> log.actualMiles ?: trailStages[log.stageIndex]?.miles ?: 0.0 }
            }
        var cumulative = 0.0
        return milesByDate.toSortedMap().map { (date, miles) ->
            cumulative += miles
            ChartPoint(date, cumulative)
        }
    }
}