package com.saltgarden.kechengbiao.ui.theme

import androidx.compose.ui.graphics.Color

/** 课程块配色（参考 WakeUp 的柔和色板思路） */
object CourseColors {
    data class Palette(val container: Color, val content: Color)

    private val palettes = listOf(
        Palette(Color(0xFFE3F2FD), Color(0xFF1565C0)),
        Palette(Color(0xFFE8F5E9), Color(0xFF2E7D32)),
        Palette(Color(0xFFFFF3E0), Color(0xFFE65100)),
        Palette(Color(0xFFF3E5F5), Color(0xFF6A1B9A)),
        Palette(Color(0xFFFFEBEE), Color(0xFFC62828)),
        Palette(Color(0xFFE0F7FA), Color(0xFF00838F)),
        Palette(Color(0xFFFFFDE7), Color(0xFF9E9D24)),
        Palette(Color(0xFFEDE7F6), Color(0xFF4527A0)),
        Palette(Color(0xFFE0F2F1), Color(0xFF00695C)),
        Palette(Color(0xFFFCE4EC), Color(0xFFAD1457)),
        Palette(Color(0xFFEFEBE9), Color(0xFF4E342E)),
        Palette(Color(0xFFECEFF1), Color(0xFF37474F)),
    )

    fun get(index: Int): Palette = palettes[((index % palettes.size) + palettes.size) % palettes.size]

    val size: Int get() = palettes.size

    /** 分配下一种颜色（与既有课程尽量错开） */
    fun nextIndex(existing: List<Int>): Int {
        val used = existing.toSet()
        return (0 until size).firstOrNull { it !in used } ?: (existing.maxOrNull()?.plus(1) ?: 0)
    }
}
