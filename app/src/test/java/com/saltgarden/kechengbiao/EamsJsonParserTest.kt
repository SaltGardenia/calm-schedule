package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.eams.EamsJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * EAMS 逐周课表 JSON 解析测试。
 * 关键点：scheduleList 是"每周一条"，不同周换教室时必须拆成不同周次的课程条目
 * （合肥工业大学机器视觉：第 10 周 318、11-18 周 317）。
 */
class EamsJsonParserTest {

    private val payload = """
        {
          "nodeTimes": [
            {"s":"800","e":"845"},{"s":"855","e":"940"},
            {"s":"1000","e":"1045"},{"s":"1055","e":"1140"},
            {"s":"1400","e":"1445"},{"s":"1455","e":"1540"}
          ],
          "datum": {
            "result": {
              "lessonList": [
                {"id":"9001","courseName":"机器视觉",
                 "teacherAssignmentList":[{"name":"张三"}]},
                {"id":"9002","courseName":"操作系统",
                 "teacherAssignmentList":[{"name":"李四"}]}
              ],
              "scheduleList": [
                {"lessonId":9001,"weekday":2,"weekIndex":10,"startTime":1000,"endTime":1140,"personName":"张三","room":{"nameZh":"新安学堂318"}},
                {"lessonId":9001,"weekday":2,"weekIndex":11,"startTime":1000,"endTime":1140,"personName":"张三","room":{"nameZh":"新安学堂317"}},
                {"lessonId":9001,"weekday":2,"weekIndex":12,"startTime":1000,"endTime":1140,"personName":"张三","room":{"nameZh":"新安学堂317"}},
                {"lessonId":9002,"weekday":1,"weekIndex":1,"startTime":800,"endTime":940,"personName":"李四","room":{"nameZh":"新安学堂317"}}
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `逐周教室不同时拆分为不同周次条目`() {
        val parsed = EamsJsonParser.parse(payload)
        assertNotNull(parsed)
        val courses = parsed!!.courses

        // 机器视觉：10 周(318) 一条 + 11-12 周(317) 一条；操作系统 1-2 节一条
        val machine = courses.filter { it.name == "机器视觉" }
        assertEquals(2, machine.size)

        val week10 = machine.first { 10 in it.weeks }
        assertEquals("新安学堂318", week10.room)
        assertEquals(setOf(10), week10.weeks)
        assertEquals(2, week10.day)
        assertEquals(3, week10.startNode)
        assertEquals(4, week10.endNode)

        val laterWeeks = machine.first { 11 in it.weeks }
        assertEquals("新安学堂317", laterWeeks.room)
        assertEquals(setOf(11, 12), laterWeeks.weeks)

        val os = courses.first { it.name == "操作系统" }
        assertEquals("李四", os.teacher)
        assertEquals(1, os.startNode)
        assertEquals(2, os.endNode)
        assertEquals(setOf(1), os.weeks)

        // 作息随解析结果带回（导入时写入课表）
        assertEquals(6, parsed.nodeTimes?.size)
        assertEquals("08:00" to "08:45", parsed.nodeTimes?.first())
    }
}
