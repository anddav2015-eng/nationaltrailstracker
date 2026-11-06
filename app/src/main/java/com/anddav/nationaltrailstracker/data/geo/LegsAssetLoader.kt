package com.anddav.nationaltrailstracker.data.geo

import android.content.Context
import com.anddav.nationaltrailstracker.domain.map.MapLeg
import com.anddav.nationaltrailstracker.domain.map.MapLegs

/** Reads the per-leg map route line written by tools/build_legs.py (assets/trails/<slug>-legs.geojson). */
class LegsAssetLoader(private val context: Context) {

    private val trailAssets: Set<String> by lazy { context.assets.list("trails")?.toSet().orEmpty() }

    fun hasMap(trailId: String): Boolean = MapLegs.assetPath(trailId).removePrefix("trails/") in trailAssets

    fun load(trailId: String): List<MapLeg> {
        val text = context.assets.open(MapLegs.assetPath(trailId)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return MapLegs.parse(text)
    }
}
