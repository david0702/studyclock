package com.dsh.studyclock.ui

import androidx.compose.ui.graphics.Color

/** 分段色块用的调色板，按记录 id 稳定取色。 */
val SegmentPalette: List<Color> = listOf(
    Color(0xFF2F9E8F),
    Color(0xFF3B7BC4),
    Color(0xFF7A6FD0),
    Color(0xFFB2699E),
    Color(0xFFE0803A),
    Color(0xFFB9A11A),
    Color(0xFF5A9E4B),
    Color(0xFF8C6A4A),
)

fun segmentColor(index: Int): Color = SegmentPalette[index.mod(SegmentPalette.size)]
