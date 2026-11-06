package com.anddav.nationaltrailstracker.ui.map

/** Map settings kept in one place so the tile provider is easy to swap. */
object MapConfig {
    /** OpenFreeMap: free, no API key, provided as-is. Don't use tile.openstreetmap.org (usage policy). */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** Font stack served by OpenFreeMap's glyph server, for our landmark labels. */
    val LABEL_FONT = arrayOf("Noto Sans Regular")

    /** Shown when there's no connection and the base map can't load: just a plain background. */
    const val OFFLINE_STYLE_JSON =
        """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#f4f0e8"}}]}"""

    /**
     * Saving a stage for offline use: the stage's bounds plus this margin (degrees, ~1 km), from an
     * overview zoom down to OpenFreeMap's most detailed tiles (z14; closer zooms reuse them).
     */
    const val OFFLINE_MARGIN_LAT = 0.01
    const val OFFLINE_MARGIN_LON = 0.015
    const val OFFLINE_MIN_ZOOM = 6.0
    const val OFFLINE_MAX_ZOOM = 14.0

    /**
     * Credits for trails with a map, shown in the trail's ⓘ dialog. The base-map credit is also on
     * the map itself, behind MapLibre's ⓘ button (from the OpenFreeMap style).
     */
    const val MAP_SOURCES =
        "Route line: © OpenStreetMap contributors (ODbL), with gaps and elevation from National Trails / " +
            "Natural England and Natural Resources Wales (Open Government Licence). " +
            "Landmark positions: Nominatim, © OpenStreetMap contributors. " +
            "Base map: OpenFreeMap, © OpenMapTiles, data © OpenStreetMap contributors."
}
