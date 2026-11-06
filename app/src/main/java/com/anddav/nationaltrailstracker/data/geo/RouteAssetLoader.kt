package com.anddav.nationaltrailstracker.data.geo

import android.content.Context
import com.anddav.nationaltrailstracker.domain.RoutePoint
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Reads a trail's simplified route line from assets/geo/<slug>.json, as written by
 * tools/build_geo.py: an array of [lat, lon, cumulativeMiles] triples. */
class RouteAssetLoader(private val context: Context, private val json: Json) {

    fun load(assetPath: String): List<RoutePoint> {
        val text = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return json.decodeFromString(ListSerializer(ListSerializer(Double.serializer())), text).map { triple ->
            require(triple.size == 3) { "$assetPath: expected [lat, lon, miles], got $triple" }
            RoutePoint(lat = triple[0], lon = triple[1], miles = triple[2])
        }
    }
}
