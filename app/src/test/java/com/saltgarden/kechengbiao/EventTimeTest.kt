package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.core.time.EventTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventTimeTest {

    private val times = listOf(
        "08:00" to "08:45",
        "08:55" to "09:40",
        "10:00" to "10:45",
    )

    @Test
    fun `时间解析与格式化`() {
        assertEquals(480, EventTime.parseMinutes("08:00"))
        assertEquals(870, EventTime.parseMinutes("14:30"))
        assertEquals(870, EventTime.parseMinutes("14：30"))
        assertNull(EventTime.parseMinutes("不是时间"))
        assertEquals("08:00", EventTime.format(480))
        assertEquals("23:59", EventTime.format(1439))
        assertEquals(505, EventTime.roundTo5(503))
    }

    @Test
    fun `坐标与时间互转`() {
        val h = 100f
        // 第 1 节中点：08:22.5 → 四舍五入到 5 分钟 = 08:25
        assertEquals(505, EventTime.minuteAtOffset(h * 0.5f, h, times))
        // 第 2 节起点：08:55
        assertEquals(535, EventTime.minuteAtOffset(h, h, times))
        // 超出末尾：钳制到最后一节末（10:45）
        assertEquals(645, EventTime.minuteAtOffset(h * 10f, h, times))
        // 08:00 → 顶部 0
        assertEquals(0f, EventTime.offsetForMinute(480, h, times))
        // 09:40 → 第 2 节末 = 200
        assertEquals(200f, EventTime.offsetForMinute(580, h, times))
        // 08:22 → 第 1 节内插值
        assertEquals(48.9f, EventTime.offsetForMinute(502, h, times), 0.1f)
        // 早于第一节：钳制到顶部
        assertEquals(0f, EventTime.offsetForMinute(400, h, times))
    }
}
