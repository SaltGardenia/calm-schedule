package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.generic.GenericHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GenericHtmlParserTest {

    @Test
    fun `自动识别强智HTML`() {
        val html = javaClass.classLoader!!.getResourceAsStream("parser/qz_kb.html")!!
            .readBytes().decodeToString()
        val parsed = GenericHtmlParser.autoParse(html)
        assertNotNull(parsed)
        assertEquals("qz", parsed!!.systemId)
        assertEquals(3, parsed.courses.size)
    }

    @Test
    fun `自动识别正方旧版HTML`() {
        val html = javaClass.classLoader!!.getResourceAsStream("parser/zf_old_kb.html")!!
            .readBytes().decodeToString()
        val parsed = GenericHtmlParser.autoParse(html)
        assertNotNull(parsed)
        assertEquals("zf_old", parsed!!.systemId)
    }

    @Test
    fun `自由文本单元格解析`() {
        val courses = GenericHtmlParser.parseFreeTextCell(
            "高等数学 {第1-16周|周一,1-2节} 王教授 教1-201", 1, 1
        )
        assertEquals(1, courses.size)
        val c = courses.first()
        assertEquals("高等数学", c.name)
        assertEquals((1..16).toSet(), c.weeks)
        assertEquals(1, c.startNode)
        assertEquals(2, c.endNode)
    }

    @Test
    fun `EAMS单元格解析`() {
        // 合肥工业大学 EAMS：课程代码 + 课名 + 教学楼教室 + (9~12周) + 节次
        val courses = GenericHtmlParser.parseFreeTextCell(
            "0509112-001 操作系统 新安学堂317(9~12周) 1-2节", 1, 1
        )
        assertEquals(1, courses.size)
        val c = courses.first()
        assertEquals("操作系统", c.name)
        assertEquals((9..12).toSet(), c.weeks)
        assertEquals(1, c.startNode)
        assertEquals(2, c.endNode)
    }
}
