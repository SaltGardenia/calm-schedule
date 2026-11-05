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

    @Test
    fun `从页面网格提取学校真实作息`() {
        // 结构取自真实抓取页面（blank-unit 每天重复一份，紧凑时间 800=08:00 / 1455=14:55）
        val html = """
            <html><body>
            <div class="course-table">
              <div class="blank-unit" start-time="800" end-time="845"></div>
              <div class="blank-unit" start-time="855" end-time="940"></div>
              <div class="blank-unit" start-time="1000" end-time="1045"></div>
              <div class="blank-unit" start-time="1055" end-time="1140"></div>
              <div class="blank-unit" start-time="1400" end-time="1445"></div>
              <div class="blank-unit" start-time="1455" end-time="1540"></div>
              <div class="blank-unit" start-time="800" end-time="845"></div>
              <div class="blank-unit" start-time="855" end-time="940"></div>
              <div class="blank-unit" start-time="1000" end-time="1045"></div>
              <div class="blank-unit" start-time="1055" end-time="1140"></div>
              <div class="blank-unit" start-time="1400" end-time="1445"></div>
              <div class="blank-unit" start-time="1455" end-time="1540"></div>
            </div>
            </body></html>
        """.trimIndent()
        val times = com.saltgarden.kechengbiao.importer.eams.EamsParser.extractNodeTimes(html)
        assertNotNull(times)
        // 每天重复的网格去重后得到 6 节
        assertEquals(6, times!!.size)
        assertEquals("08:00" to "08:45", times[0])
        assertEquals("14:00" to "14:45", times[4])
        assertEquals("14:55" to "15:40", times[5])
    }
}
