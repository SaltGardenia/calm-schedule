package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.core.time.Timeline
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.core.time.nodeEndMinute
import com.saltgarden.kechengbiao.core.time.nodeStartMinute
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {

    private val times = listOf(
        "08:00" to "08:45",
        "08:55" to "09:40",
        "10:00" to "10:45",
        "10:55" to "11:40",
    )

    @Test
    fun `时间轴按整点对齐`() {
        val t = Timeline.of(times)
        assertEquals(8 * 60, t.startMinute)
        assertEquals(12 * 60, t.endMinute) // 11:40 → 向上取整到 12:00
        assertEquals(listOf(480, 540, 600, 660, 720), t.hourMarks)
    }

    @Test
    fun `分钟与坐标换算`() {
        val t = Timeline.of(times)
        val h = 100f
        assertEquals(0f, t.yPx(480, h))
        assertEquals(100f, t.yPx(540, h))
        assertEquals(200f, t.yPx(600, h))
        assertEquals(480, t.minuteAt(0f, h))
        assertEquals(540, t.minuteAt(100f, h))
        assertEquals(720, t.minuteAt(10_000f, h))
        assertEquals(0f, t.yPx(400, h)) // 早于起点钳制
    }

    @Test
    fun `半小时吸附`() {
        assertEquals(480, Timeline.snap30(478))
        assertEquals(510, Timeline.snap30(500))
        assertEquals(540, Timeline.snap30(533))
    }

    @Test
    fun `节次时间换算`() {
        assertEquals(480, nodeStartMinute(1, times))
        assertEquals(525, nodeEndMinute(1, times))
        assertEquals(535, nodeStartMinute(2, times))
        assertEquals(580, nodeEndMinute(2, times))
        // 越界节点用兜底：08:00 + (n-1)*45
        assertEquals(480 + 9 * 45, nodeStartMinute(10, times))
    }

    @Test
    fun `时间轴范围随每日节数伸缩`() {
        // 作息只有 4 节，节数设为 6 时自动补齐两节（上一节结束+10 起、45 分钟/节）
        val filled = WeekCalculator.ensureNodeTimes(
            times.joinToString(",") { "${it.first}-${it.second}" }, 6
        )
        assertEquals(6, filled.size)
        // 第 6 节：第 5 节 11:50-12:35，第 6 节 12:45-13:30 → 向后取整到 14:00
        val t = Timeline.of(filled)
        assertEquals(13 * 60 + 30, nodeEndMinute(6, filled))
        assertEquals(14 * 60, t.endMinute)
        assertEquals(8 * 60, t.startMinute)

        // 节数缩回 3：总长随之缩短（10:45 结束 → 11:00）
        val shrunk = WeekCalculator.ensureNodeTimes(
            times.joinToString(",") { "${it.first}-${it.second}" }, 3
        )
        assertEquals(3, shrunk.size)
        assertEquals(11 * 60, Timeline.of(shrunk).endMinute)
    }

    @Test
    fun `折叠午休时段`() {
        val base = Timeline(8 * 60, 18 * 60)
        val t = base.copy(collapsed = 12 * 60..14 * 60)
        // 总高度少 2 小时（10 小时 - 2 小时折叠）
        assertEquals(8 * 60, t.totalMinutes)
        // 12:00 与 14:00 渲染在同一位置；13:00 也吸附到折叠点
        assertEquals(t.y(12 * 60), t.y(14 * 60))
        assertEquals(t.y(12 * 60), t.y(13 * 60))
        // 15:00 紧跟折叠点之后（虚拟轴 13:00 处）
        assertEquals(t.y(12 * 60) + t.hourHeight, t.y(15 * 60))
        // 11:00 不受影响（08:00 起 3 小时 = 186dp）
        assertEquals(186f, t.y(11 * 60).value, 0.01f)
        // 整点分割线跳过 13:00
        assertEquals(listOf(480, 540, 600, 660, 720, 900, 960, 1020, 1080), t.hourMarks)
        // minuteAt 反算：折叠点后一小时对应 15:00
        assertEquals(15 * 60, t.minuteAt(t.yPx(15 * 60, 100f), 100f))
    }
}
