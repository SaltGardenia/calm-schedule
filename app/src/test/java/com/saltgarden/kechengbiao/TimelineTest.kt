package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.core.time.Timeline
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
}
