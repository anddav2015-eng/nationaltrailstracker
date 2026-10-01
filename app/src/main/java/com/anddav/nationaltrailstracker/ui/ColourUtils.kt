package com.anddav.nationaltrailstracker.ui

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

fun parseHexColor(hex: String): Color = Color(hex.toColorInt())

/** The source data has one colour per trail, not per stage. The owner's HTML trackers give each
 * stage its own chip colour, so this derives a brightness ramp along the trail's base colour -
 * closest guess without the original CSS; easy to swap for real per-stage colours later. */
fun stageChipColour(baseHex: String, index: Int, total: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(baseHex.toColorInt(), hsv)
    val t = if (total <= 1) 0f else index / (total - 1).toFloat()
    hsv[2] = (0.6f + 0.35f * t).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}