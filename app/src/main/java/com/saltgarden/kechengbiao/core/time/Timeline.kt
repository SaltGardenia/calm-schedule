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
    /** 折叠时段（如 720..840 表示 12:00-14:00 午休压缩成一个窄条）；null = 不折叠 */
    val collapsed: IntRange? = null,
    /** 折叠窄条的高度 */
    val collapseHeight: Dp = 32.dp,
) {
    init {
        require(endMinute > startMinute) { "时间轴范围非法" }
        require(collapsed == null || (collapsed.first >= startMinute && collapsed.last < endMinute)) {
            "折叠时段必须落在时间轴内"
        }
    }

    private val collapseSpan: Int = collapsed?.let { it.last - it.first } ?: 0

    /** 折叠段之外的分钟数（线性段总长） */
    val totalMinutes: Int get() = endMinute - startMinute - collapseSpan

    val height: Dp
        get() = hourHeight * (totalMinutes / 60f) + if (collapsed != null) collapseHeight else 0.dp

    /** 折叠段之前的线性段高度 */
    private fun preHeight(hourHeight: Dp): Dp =
        collapsed?.let { hourHeight * ((it.first - startMinute) / 60f) } ?: 0.dp

    /** 分钟 → 纵向 dp：折叠段外线性映射，段内在窄条中按比例插值 */
    fun y(minute: Int): Dp {
        val c = collapsed ?: return hourHeight * ((minute.coerceIn(startMinute, endMinute) - startMinute) / 60f)
        val m = minute.coerceIn(startMinute, endMinute)
        return when {
            m <= c.first -> hourHeight * ((m - startMinute) / 60f)
            m <= c.last -> preHeight(hourHeight) + collapseHeight * ((m - c.first).toFloat() / collapseSpan)
            else -> preHeight(hourHeight) + collapseHeight + hourHeight * ((m - c.last) / 60f)
        }
    }

    /** 分钟 → 纵向 px */
    fun yPx(minute: Int, hourHeightPx: Float): Float {
        if (collapsed == null) return hourHeightPx * ((minute.coerceIn(startMinute, endMinute) - startMinute) / 60f)
        return y(minute).value * (hourHeightPx / hourHeight.value)
    }

    /** 纵向 px → 分钟（钳制在时间轴内） */
    fun minuteAt(yPx: Float, hourHeightPx: Float): Int {
        if (hourHeightPx <= 0f) return startMinute
        val c = collapsed
        if (c == null) {
            val minute = startMinute + (yPx / hourHeightPx * 60f).roundToInt()
            return minute.coerceIn(startMinute, endMinute)
        }
        val scale = hourHeightPx / hourHeight.value
        val prePx = preHeight(hourHeight).value * scale
        val bandPx = collapseHeight.value * scale
        val m = when {
            yPx < prePx -> startMinute + (yPx / hourHeightPx * 60f).roundToInt()
            yPx < prePx + bandPx ->
                c.first + ((yPx - prePx) / bandPx * collapseSpan).roundToInt()
            else -> c.last + ((yPx - prePx - bandPx) / hourHeightPx * 60f).roundToInt()
        }
        return m.coerceIn(startMinute, endMinute)
    }

    /** 整点分割线（含首尾）：折叠段只保留两端整点（如 12:00、14:00），中间整点（13:00）不显示 */
    val hourMarks: List<Int>
        get() {
            val c = collapsed
            return generateSequence(startMinute) { it + 60 }
                .takeWhile { it <= endMinute }
                .filter { c == null || it <= c.first || it >= c.last }
                .toList()
        }

    /** 折叠段中点的真实分钟（用于绘制 ⋯ 指示），未折叠返回 null */
    val collapseMidMinute: Int?
        get() = collapsed?.let { (it.first + it.last) / 2 }

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
