package com.saltgarden.kechengbiao.core.time

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 课表纵向时间轴：以整小时为分割线，课程/日程按真实时间定位。
 *
 * 例如作息 08:00-08:45, 08:55-09:40 … → 时间轴 08:00 ~ 18:00，
 * 每小时一条分割线（09:00、10:00 …），不再与节次边界对齐。
 */
data class Timeline(
    val startMinute: Int,
    val endMinute: Int,
    val hourHeight: Dp = 62.dp,
) {
    init {
        require(endMinute > startMinute) { "时间轴范围非法" }
    }

    val totalMinutes: Int get() = endMinute - startMinute

    val height: Dp get() = hourHeight * (totalMinutes / 60f)

    /** 分钟 → 纵向 dp（钳制在时间轴内） */
    fun y(minute: Int): Dp =
        hourHeight * ((minute.coerceIn(startMinute, endMinute) - startMinute) / 60f)

    /** 分钟 → 纵向 px */
    fun yPx(minute: Int, hourHeightPx: Float): Float =
        hourHeightPx * ((minute.coerceIn(startMinute, endMinute) - startMinute) / 60f)

    /** 纵向 px → 分钟（钳制在时间轴内） */
    fun minuteAt(yPx: Float, hourHeightPx: Float): Int {
        if (hourHeightPx <= 0f) return startMinute
        val minute = startMinute + (yPx / hourHeightPx * 60f).roundToInt()
        return minute.coerceIn(startMinute, endMinute)
    }

    /** 整点分割线（含首尾）：08:00、09:00 … 18:00 */
    val hourMarks: List<Int>
        get() = generateSequence(startMinute) { it + 60 }.takeWhile { it <= endMinute }.toList()

    companion object {
        /** 半小时（半格）吸附，用于拖动选择 */
        fun snap30(minute: Int): Int = ((minute + 15) / 30) * 30

        /** 由作息时间构造：起点向下取整到整点，终点向上取整到整点 */
        fun of(nodeTimes: List<Pair<String, String>>): Timeline {
            val starts = nodeTimes.mapNotNull { EventTime.parseMinutes(it.first) }
            val ends = nodeTimes.mapNotNull { EventTime.parseMinutes(it.second) }
            val rawStart = starts.minOrNull() ?: (8 * 60)
            val rawEnd = ends.maxOrNull() ?: (18 * 60)
            val start = (rawStart / 60) * 60
            val end = ((rawEnd + 59) / 60) * 60
            return Timeline(start, maxOf(end, start + 60))
        }
    }
}

/** 第 node（1 起）节的开始分钟；作息缺失时按 08:00 起每节 45 分钟兜底 */
fun nodeStartMinute(node: Int, nodeTimes: List<Pair<String, String>>): Int {
    val idx = (node - 1).coerceAtLeast(0)
    val fallback = 8 * 60 + idx * 45
    return nodeTimes.getOrNull(idx)?.let { EventTime.parseMinutes(it.first) } ?: fallback
}

/** 第 node（1 起）节的结束分钟 */
fun nodeEndMinute(node: Int, nodeTimes: List<Pair<String, String>>): Int {
    val idx = (node - 1).coerceAtLeast(0)
    val start = nodeStartMinute(node, nodeTimes)
    return nodeTimes.getOrNull(idx)?.let { EventTime.parseMinutes(it.second) } ?: (start + 45)
}
