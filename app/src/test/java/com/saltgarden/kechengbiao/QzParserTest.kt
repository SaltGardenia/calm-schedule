package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.qz.QzParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QzParserTest {

    private fun loadHtml(): String =
        javaClass.classLoader!!.getResourceAsStream("parser/qz_kb.html")!!
            .readBytes().decodeToString()

    @Test
    fun `解析kbtable课表`() {
        val courses = QzParser.parse(loadHtml())
        assertTrue("至少解析出 3 门课，实际 ${courses.size}", courses.size >= 3)

        val english = courses.first { it.name == "大学英语" }
        assertEquals(1, english.day)
        assertEquals(1, english.startNode)
        assertEquals(2, english.endNode)
        assertEquals("李老师", english.teacher)
        assertEquals("外语楼305", english.room)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), english.weeks)

        // 带节次的强智变体："1-8周,10-16周 [3-4节]" 在星期三第1大节行
        val ds = courses.first { it.name == "数据结构" }
        assertEquals(3, ds.day)
        assertEquals(3, ds.startNode)
        assertEquals(4, ds.endNode)
        assertEquals((1..8).toSet() + (10..16).toSet(), ds.weeks)
        assertEquals("机房5楼", ds.room)

        val math = courses.first { it.name == "高等数学A(上)" }
        assertEquals(2, math.day)
        assertEquals(3, math.startNode)
        assertEquals(4, math.endNode)
    }
}
