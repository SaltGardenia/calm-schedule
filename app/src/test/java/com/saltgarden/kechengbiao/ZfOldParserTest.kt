package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.zf.ZfOldParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZfOldParserTest {

    private fun loadHtml(): String =
        javaClass.classLoader!!.getResourceAsStream("parser/zf_old_kb.html")!!
            .readBytes().decodeToString()

    @Test
    fun `解析Table1课表`() {
        val courses = ZfOldParser.parse(loadHtml())
        assertTrue("至少解析出 4 门课，实际 ${courses.size}", courses.size >= 4)

        val math = courses.first { it.name == "高等数学A" }
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals("王教授", math.teacher)
        assertEquals("教1-201", math.room)
        assertEquals((1..16).toSet(), math.weeks)

        val english = courses.first { it.name == "大学英语" }
        assertEquals(3, english.day)
        assertEquals(1, english.startNode)
        assertEquals(2, english.endNode)

        val ds = courses.first { it.name == "数据结构" }
        assertEquals(2, ds.day)
        assertEquals(3, ds.startNode)
        assertEquals(4, ds.endNode)
        assertEquals((2..16).toSet(), ds.weeks)
        assertEquals("赵老师", ds.teacher)
        assertEquals("机房5楼", ds.room)

        val pe = courses.first { it.name == "体育" }
        assertEquals(5, pe.day)
        assertEquals(8, pe.startNode)
        assertEquals(9, pe.endNode)
        assertEquals((3..8).toSet(), pe.weeks)
    }
}
