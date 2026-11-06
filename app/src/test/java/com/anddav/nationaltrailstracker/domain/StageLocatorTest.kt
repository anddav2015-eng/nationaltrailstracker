package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.domain.map.GeoPoint
import com.anddav.nationaltrailstracker.domain.map.MapLeg
import com.anddav.nationaltrailstracker.domain.map.StageLocator
import com.anddav.nationaltrailstracker.domain.map.StagePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageLocatorTest {

    // A straight line due north along longitude 0. 0.01 deg of latitude is ~0.69 miles.
    private fun leg(index: Int, stage: Int, from: String, to: String, lat0: Double, lat1: Double) =
        MapLeg(index, stage, from, to, listOf(GeoPoint(lat0, 0.0), GeoPoint((lat0 + lat1) / 2, 0.0), GeoPoint(lat1, 0.0)))

    private val legs = listOf(
        leg(0, 0, "A", "B", 51.00, 51.01),
        leg(1, 1, "B", "C", 51.01, 51.02),
        leg(2, 1, "C", "D", 51.02, 51.03),
        leg(3, 2, "D", "E", 51.03, 51.04),
    )
    private val degreeMiles = StageLocator.haversineMiles(51.0, 0.0, 51.01, 0.0) / 0.01

    @Test
    fun `stage length is the sum of its legs along the line`() {
        assertEquals(0.02 * degreeMiles, StageLocator(legs, 1).stageMiles, 1e-6)
    }

    @Test
    fun `a point inside the stage gives miles along and to the end`() {
        val pos = StageLocator(legs, 1).locate(51.015, 0.0) as StagePosition.InStage
        assertEquals(0.005 * degreeMiles, pos.milesAlong, 1e-3)
        assertEquals(0.015 * degreeMiles, pos.milesToEnd, 1e-3)
        assertEquals("D", pos.endName)
        assertEquals(0.0, pos.offRouteMetres, 0.5)
    }

    @Test
    fun `a point beside the line reports how far off it is`() {
        // 0.005 deg of longitude at 51 deg N is ~350 m.
        val pos = StageLocator(legs, 1).locate(51.02, 0.005) as StagePosition.InStage
        assertEquals(350.0, pos.offRouteMetres, 10.0)
    }

    @Test
    fun `a point on an earlier stage is before the start`() {
        val pos = StageLocator(legs, 1).locate(51.004, 0.0)
        assertTrue(pos is StagePosition.BeforeStart)
        pos as StagePosition.BeforeStart
        assertEquals(0.006 * degreeMiles, pos.milesToStart, 1e-3)
        assertEquals("B", pos.startName)
    }

    @Test
    fun `a point on a later stage is past the end`() {
        val pos = StageLocator(legs, 1).locate(51.035, 0.0) as StagePosition.PastEnd
        assertEquals(0.005 * degreeMiles, pos.milesPastEnd, 1e-3)
        assertEquals("D", pos.endName)
    }

    @Test
    fun `jitter just before the stage start still counts as in the stage`() {
        val pos = StageLocator(legs, 1).locate(51.0099, 0.0)
        assertTrue(pos is StagePosition.InStage)
        assertEquals(0.0, (pos as StagePosition.InStage).milesAlong, 1e-9)
    }
}
