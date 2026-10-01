package com.anddav.nationaltrailstracker.data.trail

import android.content.Context
import com.anddav.nationaltrailstracker.model.Trail
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Reads the bundled, read-only trail data from assets/trails/. */
class TrailAssetLoader(private val context: Context, private val json: Json) {

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Trail ids in display order, as written by tools/convert_trails.py. */
    fun loadIndex(): List<String> =
        json.decodeFromString(ListSerializer(String.serializer()), readAsset("trails/index.json"))

    fun loadTrail(id: String): Trail =
        json.decodeFromString(Trail.serializer(), readAsset("trails/$id.json"))

    fun loadAllTrails(): List<Trail> = loadIndex().map(::loadTrail)
}