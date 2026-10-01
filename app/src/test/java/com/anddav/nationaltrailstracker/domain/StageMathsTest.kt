package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.model.Landmark
import com.anddav.nationaltrailstracker.model.StageDef
import com.anddav.nationaltrailstracker.model.Trail
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

private const val DELTA = 0.001

/** A small 3-stage trail (0 -> 10 -> 22.5 -> 30 miles) used to test the maths in isolation,
 * without depending on the real bundled data. */
private fun fixtureTrail(): Trail {
    val landmarks = listOf(
        Landmark(name = "Start", milesFromStart = 0.0),
        Landmark(name = "Midpoint A", milesFromStart = 10.0),
        Landmark(name = "Midpoint B", milesFromStart = 22.5),
        Landmark(name = "End", milesFromStart = 30.0),
    )
    val stages = listOf(
        StageDef(fromLandmark = "Start", toLandmark = "Midpoint A"),
        StageDef(fromLandmark = "Midpoint A", toLandmark = "Midpoint B"),
        StageDef(fromLandmark = "Midpoint B", toLandmark = "End"),
    )
    return Trail(
        id = "fixture-trail",
        name = "Fixture Trail",
        route = "Start to End",
        startLabel = "Start",
        endLabel = "End",
        colour = "#000000",
        subtitle = "Test fixture",
        footer = "Test fixture",
        landmarks = landmarks,
        defaultStages = stages,
    )
}

class StageMathsTest {

    @Test
    fun `stage miles is the difference between consecutive landmarks`() {
        val stages = StageMaths.stages(fixtureTrail())
        assertEquals(10.0, stages[0].miles, DELTA)
        assertEquals(12.5, stages[1].miles, DELTA)
        assertEquals(7.5, stages[2].miles, DELTA)
    }

    @Test
    fun `cumulative miles accumulates from the trail start`() {
        val trail = fixtureTrail()
        assertEquals(10.0, StageMaths.cumulativeMilesAt(trail, 0), DELTA)
        assertEquals(22.5, StageMaths.cumulativeMilesAt(trail, 1), DELTA)
        assertEquals(30.0, StageMaths.cumulativeMilesAt(trail, 2), DELTA)
    }

    @Test
    fun `total miles is the full trail length`() {
        assertEquals(30.0, StageMaths.totalMiles(fixtureTrail()), DELTA)
    }

    @Test
    fun `percent complete reflects only logged stages`() {
        val trail = fixtureTrail()
        val logs = listOf(LoggedStage(stageIndex = 0, steps = null)) // 10 of 30 miles
        assertEquals(100.0 / 3.0, StageMaths.percentComplete(trail, logs), DELTA)
    }

    @Test
    fun `percent complete is zero with no logs`() {
        assertEquals(0.0, StageMaths.percentComplete(fixtureTrail(), emptyList()), DELTA)
    }

    @Test
    fun `percent complete is 100 when every stage is logged`() {
        val trail = fixtureTrail()
        val logs = (0..2).map { LoggedStage(stageIndex = it, steps = null) }
        assertEquals(100.0, StageMaths.percentComplete(trail, logs), DELTA)
    }

    @Test
    fun `trail is not complete until every stage has a log`() {
        val trail = fixtureTrail()
        val logs = listOf(LoggedStage(stageIndex = 0, steps = null), LoggedStage(stageIndex = 1, steps = null))
        assertFalse(StageMaths.isComplete(trail, logs))
    }

    @Test
    fun `trail is complete when every stage has a log`() {
        val trail = fixtureTrail()
        val logs = (0..2).map { LoggedStage(stageIndex = it, steps = null) }
        assertTrue(StageMaths.isComplete(trail, logs))
    }

    @Test
    fun `miles walked sums only the logged stages`() {
        val trail = fixtureTrail()
        val logs = listOf(LoggedStage(stageIndex = 0, steps = null), LoggedStage(stageIndex = 2, steps = null))
        assertEquals(17.5, StageMaths.milesWalked(trail, logs), DELTA) // stage 0 (10) + stage 2 (7.5)
    }

    @Test
    fun `miles walked uses actualMiles over the nominal stage distance when overrun is logged`() {
        val trail = fixtureTrail()
        // Stage 0 is nominally 10 miles; the walker actually covered 16 (ate into stage 1's territory).
        val logs = listOf(LoggedStage(stageIndex = 0, steps = null, actualMiles = 16.0))
        assertEquals(16.0, StageMaths.milesWalked(trail, logs), DELTA)
    }

    @Test
    fun `percent complete reflects actualMiles overrun, capped at 100`() {
        val trail = fixtureTrail() // 30 miles total
        val logs = listOf(LoggedStage(stageIndex = 0, steps = null, actualMiles = 40.0))
        assertEquals(100.0, StageMaths.percentComplete(trail, logs), DELTA)
    }

    @Test
    fun `miles walked falls back to the nominal stage distance when actualMiles is absent`() {
        val trail = fixtureTrail()
        val logs = listOf(LoggedStage(stageIndex = 0, steps = null, actualMiles = null))
        assertEquals(10.0, StageMaths.milesWalked(trail, logs), DELTA)
    }

    @Test
    fun `steps walked sums logged steps, ignoring stages logged without steps`() {
        val logs = listOf(
            LoggedStage(stageIndex = 0, steps = 20000),
            LoggedStage(stageIndex = 1, steps = null),
            LoggedStage(stageIndex = 2, steps = 15000),
        )
        assertEquals(35000, StageMaths.stepsWalked(logs))
    }

    @Test
    fun `cumulative miles by date is chronological regardless of logging order`() {
        val trail = fixtureTrail()
        // Logged out of order: stage 2 (7.5mi) first, then stage 0 (10mi) on an earlier date.
        val logs = listOf(
            LoggedStage(stageIndex = 2, steps = null, dateWalked = LocalDate.of(2026, 6, 10)),
            LoggedStage(stageIndex = 0, steps = null, dateWalked = LocalDate.of(2026, 6, 1)),
        )
        val points = StageMaths.cumulativeMilesByDate(trail, logs)
        assertEquals(2, points.size)
        assertEquals(LocalDate.of(2026, 6, 1), points[0].date)
        assertEquals(10.0, points[0].cumulativeMiles, DELTA)
        assertEquals(LocalDate.of(2026, 6, 10), points[1].date)
        assertEquals(17.5, points[1].cumulativeMiles, DELTA)
    }

    @Test
    fun `cumulative miles by date collapses multiple stages logged on the same day`() {
        val trail = fixtureTrail()
        val sameDay = LocalDate.of(2026, 6, 1)
        val logs = listOf(
            LoggedStage(stageIndex = 0, steps = null, dateWalked = sameDay),
            LoggedStage(stageIndex = 1, steps = null, dateWalked = sameDay),
        )
        val points = StageMaths.cumulativeMilesByDate(trail, logs)
        assertEquals(1, points.size)
        assertEquals(22.5, points[0].cumulativeMiles, DELTA) // stage 0 (10) + stage 1 (12.5)
    }

    // --- Real bundled data: North Downs Way, generated by tools/convert_trails.py ---

    @Test
    fun `North Downs Way is 11 stages and 132point8 miles`() {
        val json = Json { ignoreUnknownKeys = true }
        val file = File("src/main/assets/trails/north-downs-way.json")
        assertTrue("expected ${file.absolutePath} to exist - run tools/convert_trails.py", file.exists())
        val trail = json.decodeFromString(Trail.serializer(), file.readText())

        val stages = StageMaths.stages(trail)
        assertEquals(11, stages.size)
        assertEquals(132.8, StageMaths.totalMiles(trail), DELTA)
    }
}
