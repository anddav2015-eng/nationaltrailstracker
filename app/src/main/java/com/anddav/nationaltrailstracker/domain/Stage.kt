package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.model.Landmark
import java.time.LocalDate

/** One stage of a trail, derived from a [com.anddav.nationaltrailstracker.model.StageDef]
 * with its landmark names resolved to the actual [Landmark]s. Never stored. */
data class Stage(
    val index: Int,
    val fromLandmark: Landmark,
    val toLandmark: Landmark,
    val note: String?,
) {
    val miles: Double get() = toLandmark.milesFromStart - fromLandmark.milesFromStart
}

/** A logged stage, decoupled from the Room entity so [StageMaths] stays plain Kotlin. */
data class LoggedStage(
    val stageIndex: Int,
    val steps: Int?,
    val actualMiles: Double? = null,
    val dateWalked: LocalDate = LocalDate.of(1970, 1, 1),
)

/** One point on the progress chart: cumulative miles walked as of [date]. */
data class ChartPoint(val date: LocalDate, val cumulativeMiles: Double)