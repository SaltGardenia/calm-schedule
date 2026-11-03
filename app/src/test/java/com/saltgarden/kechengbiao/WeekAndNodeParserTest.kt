package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.importer.core.NodeTextParser
import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeekAndNodeParserTest {

    @Test
    fun `当前周计算`() {
        val start = LocalDate.of(2026, 9, 7)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(1, WeekCalculator.currentWeek(start, LocalDate.of(2026, 9, 7)))
        assertEquals(1, WeekCalculator.currentWeek(start, LocalDate.of(2026, 9, 13)))
        assertEquals(2, WeekCalculator.currentWeek(start, LocalDate.of(2026, 9, 14)))
        assertEquals(8, WeekCalculator.currentWeek(start, LocalDate.of(2026, 10, 26)))
        assertEquals(9, WeekCalculator.currentWeek(start, LocalDate.of(2026, 11, 3)))
        assertEquals(9, WeekCalculator.currentWeek(start, LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 10, 26), WeekCalculator.weekDates(start, 8).first())
    }

    @Test
    fun `作息按节数补齐`() {
        val rows = WeekCalculator.ensureNodeTimes("08:00-08:45,08:55-09:40", 4)
        assertEquals(4, rows.size)
        assertEquals("08:00" to "08:45", rows[0])
        assertEquals("08:55" to "09:40", rows[1])
        assertEquals("09:50" to "10:35", rows[2])
        assertEquals("10:45" to "11:30", rows[3])
    }

    @Test
    fun `连续周次`() {
        assertEquals((1..16).toSet(), WeekTextParser.parse("1-16周"))
        assertEquals((1..16).toSet(), WeekTextParser.parse("1-16"))
        assertEquals((1..16).toSet(), WeekTextParser.parse("第1-16周"))
    }

    @Test
    fun `单双周`() {
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), WeekTextParser.parse("1-16周(单)"))
        assertEquals(setOf(2, 4, 6, 8, 10, 12, 14, 16), WeekTextParser.parse("1-16周(双)"))
        assertEquals(setOf(2, 4, 6, 8), WeekTextParser.parse("1-8周（双）"))
    }

    @Test
    fun `多段周次`() {
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16),
            WeekTextParser.parse("1-8周,10-16周"))
        assertEquals(setOf(3), WeekTextParser.parse("3周"))
        assertEquals(setOf(1, 2, 4), WeekTextParser.parse("1,2,4周"))
        assertEquals(setOf(1, 2, 4), WeekTextParser.parse("1、2、4周"))
    }

    @Test
    fun `位串周次`() {
        // 第1、3、5 周有课
        assertEquals(setOf(1, 3, 5), WeekTextParser.parseBitString("1010100000000000"))
        assertEquals(setOf(1, 3, 5), WeekTextParser.parseSmart("1010100000000000"))
    }

    @Test
    fun `节次区间`() {
        assertEquals(listOf(1 to 2), NodeTextParser.parsePairs("1-2节"))
        assertEquals(listOf(3 to 4), NodeTextParser.parsePairs("3-4节"))
        assertEquals(listOf(5 to 5), NodeTextParser.parsePairs("5节"))
    }

    @Test
    fun `节次多段`() {
        assertEquals(listOf(1 to 2, 3 to 4), NodeTextParser.parsePairs("1-2节,3-4节"))
        assertEquals(listOf(1 to 4), NodeTextParser.mergeContiguous(NodeTextParser.parsePairs("1-2节,3-4节")))
        assertEquals(listOf(1 to 2, 4 to 5), NodeTextParser.mergeContiguous(NodeTextParser.parsePairs("1-2节,4-5节")))
    }

    @Test
    fun `周次文本不误判为节次`() {
        // "1-16周" 不应被当作节次 1-16
        assertEquals(emptyList<Pair<Int, Int>>(), NodeTextParser.parsePairs("第1-16周"))
        assertEquals((1..16).toSet(), WeekTextParser.parse("第1-16周"))
    }

    @Test
    fun `输入法的破折号变体`() {
        // 输入法/网页常把 "-" 换成 en dash(–)、em dash(—)、全角－
        assertEquals(listOf(3 to 4), NodeTextParser.parsePairs("3 – 4"))
        assertEquals(listOf(3 to 4), NodeTextParser.parsePairs("3 — 4"))
        assertEquals(listOf(3 to 4), NodeTextParser.parsePairs("3 － 4"))
        assertEquals(listOf(1 to 2), NodeTextParser.parsePairs("1–2节"))
    }

    @Test
    fun `合并连续节次`() {
        assertEquals(listOf(1 to 4), NodeTextParser.mergeContiguous(listOf(1 to 2, 3 to 4)))
        assertEquals(listOf(1 to 1), NodeTextParser.mergeContiguous(listOf(1 to 1)))
    }

    @Test
    fun `中文节数`() {
        assertEquals(3, NodeTextParser.parseChineseOrDigit("第三节"))
        assertEquals(1, NodeTextParser.parseChineseOrDigit("第一节"))
        assertEquals(10, NodeTextParser.parseChineseOrDigit("第十节"))
        assertEquals(12, NodeTextParser.parseChineseOrDigit("第十二节"))
        assertEquals(2, NodeTextParser.parseChineseOrDigit("第二节"))
    }
}
