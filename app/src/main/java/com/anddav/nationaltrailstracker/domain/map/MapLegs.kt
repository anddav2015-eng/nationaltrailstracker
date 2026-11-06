package com.anddav.nationaltrailstracker.domain.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class GeoPoint(val lat: Double, val lon: Double)

data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** A named point to mark on the map, e.g. a stage's start or end landmark. */
data class MapMarker(val name: String, val point: GeoPoint)

/** One landmark-to-landmark piece of a trail's route line, in walking order. */
data class MapLeg(
    val index: Int,
    /** The default stage this leg belongs to, or null if no stage covers it. */
    val stageIndex: Int?,
    val from: String,
    val to: String,
    val points: List<GeoPoint>,
)

object MapLegs {
    /** The bundled legs file for a trail, written by tools/build_legs.py. */
    fun assetPath(trailId: String) = "trails/$trailId-legs.geojson"

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses a <slug>-legs.geojson file: one LineString feature per leg, [lon, lat(, ele)]. */
    fun parse(geoJson: String): List<MapLeg> {
        val features = json.parseToJsonElement(geoJson).jsonObject["features"]?.jsonArray ?: return emptyList()
        return features.map { feature ->
            val obj = feature.jsonObject
            val props = obj["properties"]?.jsonObject ?: JsonObject(emptyMap())
            val coords = obj["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
            MapLeg(
                index = props["leg"]!!.jsonPrimitive.int,
                stageIndex = props["stage"]?.jsonPrimitive?.intOrNull,
                from = props["from"]?.jsonPrimitive?.content.orEmpty(),
                to = props["to"]?.jsonPrimitive?.content.orEmpty(),
                points = coords.map { c ->
                    val pair = c.jsonArray
                    GeoPoint(lat = pair[1].jsonPrimitive.double, lon = pair[0].jsonPrimitive.double)
                },
            )
        }.sortedBy { it.index }
    }

    fun stageLegs(legs: List<MapLeg>, stageIndex: Int): List<MapLeg> = legs.filter { it.stageIndex == stageIndex }

    /** Joins consecutive legs into one line, dropping the repeated point where each pair meets. */
    fun join(legs: List<MapLeg>): List<GeoPoint> {
        val out = ArrayList<GeoPoint>(legs.sumOf { it.points.size })
        for (leg in legs) {
            for ((i, p) in leg.points.withIndex()) {
                if (i == 0 && out.isNotEmpty() && out.last() == p) continue
                out.add(p)
            }
        }
        return out
    }

    /** The stage's start and end landmarks, taken from its first and last legs. */
    fun stageEnds(stageLegs: List<MapLeg>): Pair<MapMarker, MapMarker>? {
        val first = stageLegs.firstOrNull { it.points.isNotEmpty() } ?: return null
        val last = stageLegs.lastOrNull { it.points.isNotEmpty() } ?: return null
        return MapMarker(first.from, first.points.first()) to MapMarker(last.to, last.points.last())
    }

    fun bounds(points: List<GeoPoint>): GeoBounds? {
        if (points.isEmpty()) return null
        return GeoBounds(
            south = points.minOf { it.lat },
            west = points.minOf { it.lon },
            north = points.maxOf { it.lat },
            east = points.maxOf { it.lon },
        )
    }
}
