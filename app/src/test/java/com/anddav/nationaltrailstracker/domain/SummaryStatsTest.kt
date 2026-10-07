package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.model.Landmark
import com.anddav.nationaltrailstracker.model.StageDef
import com.anddav.nationaltrailstracker.model.Trail
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DELTA = 0.001

/** A trail with stages of the given lengths, e.g. [10.0, 20.0] -> 0 -> 10 -> 30 miles. */
private fun trail(id: String, vararg stageMiles: Double): Trail {
    var miles = 0.0
    val landmarks = mutableListOf(Landmark(name = "L0", milesFromStart = 0.0))
    stageMiles.forEachIndexed { i, length ->
        miles += length
        landmarks += Landmark(name = "L${i + 1}", milesFromStart = miles)
    }
    return Trail(
        id = id,
        name = id,
        route = "",
        startLabel = "",
        endLabel = "",
        colour = "#000000",
        subtitle = "",
        footer = "",
        landmarks = if (stageMiles.isEmpty()) emptyList() else landmarks,
        defaultStages = stageMiles.indices.map { StageDef(fromLandmark = "L$it", toLandmark = "L${it + 1}") },
    )
}

class SummaryStatsTest {

    private val trails = listOf(
        trail("a", 10.0, 20.0), // 30 miles, fully walked below
        trail("b", 25.0, 25.0, 50.0), // 100 miles, one stage walked
        trail("c", 70.0), // 70 miles, not started
        trail("placeholder"), // no stages yet
    )

    private val logs = mapOf(
        "a" to listOf(LoggedStage(stageIndex = 0, steps = 20_000), LoggedStage(stageIndex = 1, steps = 40_000)),
        "b" to listOf(LoggedStage(stageIndex = 2, steps = null)),
    )

    @Test
    fun `miles walked and the total are summed across all trails`() {
        val stats = SummaryStats.from(trails, logs)
        assertEquals(80.0, stats.milesWalked, DELTA)
        assertEquals(200.0, stats.totalMiles, DELTA)
        assertEquals(120.0, stats.milesRemaining, DELTA)
    }

    @Test
    fun `percent of total miles is across every trail, not per trail`() {
        assertEquals(40.0, SummaryStats.from(trails, logs).percentOfTotalMiles, DELTA)
    }

    @Test
    fun `steps are summed across all trails, ignoring stages logged without steps`() {
        assertEquals(60_000, SummaryStats.from(trails, logs).stepsWalked)
    }

    @Test
    fun `counts trails and stages`() {
        val stats = SummaryStats.from(trails, logs)
        assertEquals(1, stats.trailsComplete)
        assertEquals(2, stats.trailsStarted)
        assertEquals(4, stats.trailsTotal)
        assertEquals(1, stats.trailsUnplanned)
        assertEquals(3, stats.stagesDone)
        assertEquals(6, stats.stagesTotal)
    }

    @Test
    fun `nothing logged gives zeroes, not a divide-by-zero`() {
        val stats = SummaryStats.from(trails, emptyMap())
        assertEquals(0.0, stats.percentOfTotalMiles, DELTA)
        assertEquals(0, stats.stepsWalked)
        assertEquals(0.0, SummaryStats.from(emptyList(), emptyMap()).percentOfTotalMiles, DELTA)
    }
}
