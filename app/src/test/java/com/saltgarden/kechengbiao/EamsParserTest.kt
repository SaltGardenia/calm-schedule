package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.eams.EamsParser
import com.saltgarden.kechengbiao.importer.generic.GenericHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EamsParserTest {

    private fun loadHtml(): String =
        javaClass.classLoader!!.getResourceAsStream("parser/eams_kb.html")!!
            .readBytes().decodeToString()

    @Test
    fun `解析EAMS卡片课表`() {
        val courses = EamsParser.parse(loadHtml())
        // 周一：两个地点分段（9~12 周 / 1~4 周）；周二：一段
        assertEquals(3, courses.size)

        val main = courses.first { it.name == "数据结构" && it.room == "教1楼101" }
        assertEquals(1, main.day)
        assertEquals(1, main.startNode)
        assertEquals(2, main.endNode)
        assertEquals((9..12).toSet(), main.weeks)

        val temp = courses.first { it.name == "数据结构" && it.room == "机房B（临时）" }
        assertEquals((1..4).toSet(), temp.weeks)
        assertEquals(1, temp.day)
        assertEquals(1, temp.startNode)

        val os = courses.first { it.name == "操作系统" }
        assertEquals(2, os.day)
        assertEquals(3, os.startNode)
        assertEquals(4, os.endNode)
        assertEquals((5..16).toSet(), os.weeks)
        assertEquals("教2楼202", os.room)
    }

    @Test
    fun `自动解析链优先识别EAMS`() {
        val parsed = GenericHtmlParser.autoParse(loadHtml())
        assertNotNull(parsed)
        assertEquals("eams", parsed!!.systemId)
        assertEquals(3, parsed.courses.size)
    }
}
