package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.generic.GenericHtmlParser
import com.saltgarden.kechengbiao.importer.urp.UrpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** URP 平台（FullCalendar 周视图）解析测试，结构取自真实抓取页面（脱敏） */
class UrpParserTest {

    private val html = """
        <html><body>
        <table><tbody><tr>
        <td class="fc-axis" style="width:37px"></td>
        <td><div class="fc-content-col">
          <div class="fc-event-container fc-helper-container"></div>
          <div class="fc-event-container">
            <a class="fc-time-grid-event fc-v-event fc-event fc-start fc-end" style="background-color: rgb(58,135,173);">
              <div class="fc-content">
                <div class="fc-time" data-start="10:00" data-full="10:00 - 11:40"><span>10:00 - 11:40</span></div>
                <div class="fc-title">大气污染控制工程 示范校区卓越楼0304（智慧） 张三、张三、李四、李四</div>
              </div>
              <div class="fc-bg"></div>
            </a>
            <a class="fc-time-grid-event fc-v-event fc-event fc-start fc-end">
              <div class="fc-content">
                <div class="fc-time" data-start="16:00" data-full="16:00 - 17:40"><span>16:00 - 17:40</span></div>
                <div class="fc-title">地图学与地理信息系统 示范校区卓越楼0101 王五</div>
              </div>
              <div class="fc-bg"></div>
            </a>
          </div>
        </div></td>
        <td><div class="fc-content-col"></div></td>
        <td><div class="fc-content-col"></div></td>
        <td><div class="fc-content-col">
          <div class="fc-event-container">
            <a class="fc-time-grid-event fc-v-event fc-event fc-start fc-end">
              <div class="fc-content">
                <div class="fc-time" data-start="19:00" data-full="19:00 - 21:35"><span>19:00 - 21:35</span></div>
                <div class="fc-title">社会主义发展史（四史类） 示范校区卓越楼0314 赵六、赵六</div>
              </div>
              <div class="fc-bg"></div>
            </a>
          </div>
        </div></td>
        <td><div class="fc-content-col"></div></td>
        <td><div class="fc-content-col"></div></td>
        <td><div class="fc-content-col"></div></td>
        </tr></tbody></table>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析 FullCalendar 周视图事件`() {
        val courses = UrpParser.parse(html)
        assertEquals(3, courses.size)

        val first = courses[0]
        assertEquals("大气污染控制工程", first.name)
        assertEquals("示范校区卓越楼0304（智慧）", first.room)
        // 重复教师名去重
        assertEquals("张三、李四", first.teacher)
        assertEquals(1, first.day)
        // 10:00-11:40 → 默认作息第 3-4 节
        assertEquals(3, first.startNode)
        assertEquals(4, first.endNode)
        assertEquals((1..16).toSet(), first.weeks)

        assertEquals(1, courses[1].day)
        assertEquals("王五", courses[1].teacher)

        val night = courses[2]
        assertEquals(4, night.day)
        // 19:00-21:35 → 第 11-13 节（晚间三节连排）
        assertEquals(11, night.startNode)
        assertEquals(13, night.endNode)
    }

    @Test
    fun `自动解析链能选中 URP`() {
        val parsed = GenericHtmlParser.autoParse(html)
        assertNotNull(parsed)
        assertEquals("URP 平台", parsed!!.sourceName)
        assertEquals(3, parsed.courses.size)
    }
}
