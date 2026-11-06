package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.model.Landmark
import com.anddav.nationaltrailstracker.model.StageDef
import com.anddav.nationaltrailstracker.model.Trail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

private const val METRES_PER_DEGREE_LAT = 111_195.0

class RouteLocatorTest {

    // Due north along the Greenwich meridian: 0.1 degrees of latitude, labelled 0 to 10 miles.
    private val straight = RouteLocator(
        listOf(
            RoutePoint(lat = 51.0, lon = 0.0, miles = 0.0),
            RoutePoint(lat = 51.1, lon = 0.0, miles = 10.0),
        ),
    )

    @Test
    fun `a point on the line is zero metres off route at the interpolated mileage`() {
        val fix = straight.locate(lat = 51.05, lon = 0.0)
        assertEquals(5.0, fix.milesAlong, 1e-9)
        assertEquals(0.0, fix.offRouteMetres, 1e-6)
    }

    @Test
    fun `a point to the side snaps perpendicular to the line`() {
        val metresPerDegreeLon = METRES_PER_DEGREE_LAT * cos(Math.toRadians(51.025))
        val fix = straight.locate(lat = 51.025, lon = 300.0 / metresPerDegreeLon)
        assertEquals(2.5, fix.milesAlong, 1e-6)
        assertEquals(300.0, fix.offRouteMetres, 0.5)
    }

    @Test
    fun `a point beyond the end clamps to the final mileage`() {
        val fix = straight.locate(lat = 51.2, lon = 0.0)
        assertEquals(10.0, fix.milesAlong, 1e-9)
        assertEquals(0.1 * METRES_PER_DEGREE_LAT, fix.offRouteMetres, 1.0)
    }

    @Test
    fun `picks the nearest leg of a bent route`() {
        // North for 0.1 degrees, then east for 0.1 degrees.
        val bent = RouteLocator(
            listOf(
                RoutePoint(51.0, 0.0, 0.0),
                RoutePoint(51.1, 0.0, 7.0),
                RoutePoint(51.1, 0.1, 11.4),
            ),
        )
        val fix = bent.locate(lat = 51.101, lon = 0.05)
        assertEquals(9.2, fix.milesAlong, 1e-6) // halfway along the second leg
        assertEquals(0.001 * METRES_PER_DEGREE_LAT, fix.offRouteMetres, 0.5)
    }

    @Test
    fun `repeated points do not break the search`() {
        val withDuplicate = RouteLocator(
            listOf(RoutePoint(51.0, 0.0, 0.0), RoutePoint(51.0, 0.0, 0.0), RoutePoint(51.1, 0.0, 10.0)),
        )
        assertEquals(5.0, withDuplicate.locate(51.05, 0.0).milesAlong, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a route needs at least two points`() {
        RouteLocator(listOf(RoutePoint(51.0, 0.0, 0.0)))
    }
}

class TrailPositionTest {

    // Landmarks at 0, 10, 22.5 and 30 miles; stages Start-A, A-B, B-End.
    private val trail = Trail(
        id = "fixture",
        name = "Fixture",
        route = "",
        startLabel = "",
        endLabel = "",
        colour = "#000000",
        subtitle = "",
        footer = "",
        landmarks = listOf(
            Landmark("Start", 0.0),
            Landmark("A", 10.0),
            Landmark("B", 22.5),
            Landmark("End", 30.0),
        ),
        defaultStages = listOf(StageDef("Start", "A"), StageDef("A", "B"), StageDef("B", "End")),
    )

    @Test
    fun `mid-stage fix reports the stage, landmarks either side and miles to go`() {
        val position = TrailPosition.from(trail, RouteFix(milesAlong = 14.0, offRouteMetres = 20.0))
        assertEquals(1, position.stage?.index)
        assertEquals("A", position.landmarkBehind?.name)
        assertEquals("B", position.landmarkAhead?.name)
        assertEquals(8.5, position.milesToStageEnd!!, 1e-9)
        assertEquals(16.0, position.milesToTrailEnd, 1e-9)
        assertFalse(position.isOffRoute)
    }

    @Test
    fun `a fix exactly on a stage boundary belongs to the stage that starts there`() {
        val position = TrailPosition.from(trail, RouteFix(milesAlong = 10.0, offRouteMetres = 0.0))
        assertEquals(1, position.stage?.index)
        assertEquals("A", position.landmarkBehind?.name)
        assertEquals(12.5, position.milesToStageEnd!!, 1e-9)
    }

    @Test
    fun `at the very end the last stage has nothing left`() {
        val position = TrailPosition.from(trail, RouteFix(milesAlong = 30.0, offRouteMetres = 0.0))
        assertEquals(2, position.stage?.index)
        assertNull(position.landmarkAhead)
        assertEquals(0.0, position.milesToStageEnd!!, 1e-9)
        assertEquals(0.0, position.milesToTrailEnd, 1e-9)
    }

    @Test
    fun `more than 500 metres from the line is off route`() {
        assertFalse(TrailPosition.from(trail, RouteFix(5.0, 500.0)).isOffRoute)
        assertTrue(TrailPosition.from(trail, RouteFix(5.0, 500.1)).isOffRoute)
    }
}
