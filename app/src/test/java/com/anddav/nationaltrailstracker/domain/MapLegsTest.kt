package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.domain.map.GeoBounds
import com.anddav.nationaltrailstracker.domain.map.GeoPoint
import com.anddav.nationaltrailstracker.domain.map.MapLegs
import com.anddav.nationaltrailstracker.domain.map.MapMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLegsTest {

    // Three legs: 0 and 1 in stage 0, 2 in stage 1. Listed out of order to check sorting.
    private val sample = """
        {"type":"FeatureCollection","properties":{"trail":"t"},"features":[
          {"type":"Feature","properties":{"leg":2,"stage":1,"from":"C","to":"D","tableMiles":1.0,"lineMiles":1.1},
           "geometry":{"type":"LineString","coordinates":[[0.2,51.2,5.0],[0.3,51.3,6.0]]}},
          {"type":"Feature","properties":{"leg":0,"stage":0,"from":"A","to":"B","tableMiles":1.0,"lineMiles":1.0},
           "geometry":{"type":"LineString","coordinates":[[0.0,51.0,1.0],[0.05,51.05,2.0],[0.1,51.1,3.0]]}},
          {"type":"Feature","properties":{"leg":1,"stage":0,"from":"B","to":"C","tableMiles":1.0,"lineMiles":1.0},
           "geometry":{"type":"LineString","coordinates":[[0.1,51.1,3.0],[0.2,51.2,5.0]]}}
        ]}
    """.trimIndent()

    @Test
    fun `parse reads legs in leg order with lat and lon the right way round`() {
        val legs = MapLegs.parse(sample)
        assertEquals(listOf(0, 1, 2), legs.map { it.index })
        assertEquals(GeoPoint(lat = 51.0, lon = 0.0), legs[0].points.first())
        assertEquals("A", legs[0].from)
        assertEquals("B", legs[0].to)
        assertEquals(1, legs[2].stageIndex)
    }

    @Test
    fun `parse accepts a null stage`() {
        val json = """{"features":[{"properties":{"leg":0,"stage":null,"from":"A","to":"B"},
            "geometry":{"type":"LineString","coordinates":[[0.0,51.0],[0.1,51.1]]}}]}"""
        assertNull(MapLegs.parse(json).single().stageIndex)
    }

    @Test
    fun `join drops the shared point where legs meet`() {
        val stage = MapLegs.stageLegs(MapLegs.parse(sample), 0)
        val line = MapLegs.join(stage)
        assertEquals(4, line.size)
        assertEquals(GeoPoint(51.1, 0.1), line[2])
        assertEquals(line.distinct(), line)
    }

    @Test
    fun `stage ends are the first leg's start and the last leg's end`() {
        val stage = MapLegs.stageLegs(MapLegs.parse(sample), 0)
        val (start, end) = MapLegs.stageEnds(stage)!!
        assertEquals(MapMarker("A", GeoPoint(51.0, 0.0)), start)
        assertEquals(MapMarker("C", GeoPoint(51.2, 0.2)), end)
    }

    @Test
    fun `a stage with no legs has no ends or bounds`() {
        val stage = MapLegs.stageLegs(MapLegs.parse(sample), 7)
        assertTrue(stage.isEmpty())
        assertNull(MapLegs.stageEnds(stage))
        assertNull(MapLegs.bounds(MapLegs.join(stage)))
    }

    @Test
    fun `bounds cover every point`() {
        val all = MapLegs.join(MapLegs.parse(sample))
        assertEquals(GeoBounds(south = 51.0, west = 0.0, north = 51.3, east = 0.3), MapLegs.bounds(all))
    }
}
