package com.anddav.nationaltrailstracker.domain.map

/** Names saved offline areas: "thames-path/stage-3" (0-based stage index) or "thames-path/stage-all". */
object OfflineMapKeys {
    private const val ALL = "all"
    private val KEY = Regex("""^([\w-]+)/stage-(\d+|$ALL)$""")

    fun forStage(trailId: String, stageIndex: Int?): String = "$trailId/stage-${stageIndex ?: ALL}"

    /** The trail id and stage index (null for the whole trail), or null if the key isn't one of ours. */
    fun parse(key: String): Pair<String, Int?>? {
        val match = KEY.matchEntire(key) ?: return null
        val (trailId, stage) = match.destructured
        return trailId to stage.toIntOrNull()
    }
}
