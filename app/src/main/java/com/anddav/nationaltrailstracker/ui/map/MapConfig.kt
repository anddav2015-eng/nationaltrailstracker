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

    const val OFFICIAL_ROUTE_CREDIT = "Route: National Trails / Natural England and Natural Resources Wales"
    const val OSM_ROUTE_CREDIT = "Route: © OpenStreetMap contributors"
    const val BASE_MAP_CREDIT = "Map: OpenFreeMap, © OpenStreetMap contributors"
}
