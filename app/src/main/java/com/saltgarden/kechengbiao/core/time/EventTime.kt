package com.saltgarden.kechengbiao.core.time

import kotlin.math.roundToInt

/**
 * 日程时间 ↔ 课表网格纵向坐标的换算。
 *
 * 课表格子按"节次"等分，每节有起止时间（如 08:00-08:45）。
 * 日程按真实时间存储，展示时按节的起止时间插值到对应的纵向位置。
 */
object EventTime {

    fun parseMinutes(text: String): Int? {
        val m = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})").find(text) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    fun format(minutes: Int): String {
        val m = minutes.coerceIn(0, 24 * 60 - 1)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun roundTo5(minutes: Int): Int = ((minutes + 2) / 5) * 5

    /** 纵坐标（px）→ 分钟。超出网格时首尾钳制 */
    fun minuteAtOffset(
        yPx: Float,
        nodeHeightPx: Float,
        times: List<Pair<String, String>>,
    ): Int {
        if (times.isEmpty() || nodeHeightPx <= 0f) return 0
        val nodeF = (yPx / nodeHeightPx).coerceIn(0f, times.size - 0.0001f)
        val idx = nodeF.toInt()
        val frac = nodeF - idx
        val (s, e) = times[idx]
        val start = parseMinutes(s) ?: (8 * 60 + idx * 45)
        val end = parseMinutes(e) ?: (start + 45)
        return roundTo5((start + frac * (end - start)).roundToInt())
    }

    /** 分钟 → 纵坐标（px）。超出第一/最后一节时钳制到边界 */
    fun offsetForMinute(
        minute: Int,
        nodeHeightPx: Float,
        times: List<Pair<String, String>>,
    ): Float {
        if (times.isEmpty()) return 0f
        for (idx in times.indices) {
            val start = parseMinutes(times[idx].first) ?: (8 * 60 + idx * 45)
            val end = parseMinutes(times[idx].second) ?: (start + 45)
            if (minute < start) return idx * nodeHeightPx
            if (minute <= end) {
                val span = (end - start).coerceAtLeast(1)
                return (idx + (minute - start).toFloat() / span) * nodeHeightPx
            }
        }
        return times.size * nodeHeightPx
    }
}
