package com.anddav.nationaltrailstracker.data.geo

import android.content.Context
import com.anddav.nationaltrailstracker.domain.map.MapLeg
import com.anddav.nationaltrailstracker.domain.map.MapLegs
import com.anddav.nationaltrailstracker.domain.map.MapLineVersion

/** Reads the per-leg route lines written by tools/build_legs.py from assets/trails/. */
class LegsAssetLoader(private val context: Context) {

    private val trailAssets: Set<String> by lazy { context.assets.list("trails")?.toSet().orEmpty() }

    /** The line versions bundled for this trail, in [MapLineVersion] order. Empty if it has no map. */
    fun availableVersions(trailId: String): List<MapLineVersion> =
        MapLineVersion.entries.filter { it.assetPath(trailId).removePrefix("trails/") in trailAssets }

    fun load(trailId: String, version: MapLineVersion): List<MapLeg> {
        val text = context.assets.open(version.assetPath(trailId)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return MapLegs.parse(text)
    }
}
