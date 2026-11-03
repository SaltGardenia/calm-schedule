package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.text.JsonIO
import com.saltgarden.kechengbiao.importer.text.TextImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextImportParserTest {

    @Test
    fun `WakeUp兼容CSV导入`() {
        val csv = """
            课程名称,星期,开始节次,结束节次,教师,教室,周次
            高等数学,1,1,2,王教授,教1-201,1-16周(单)
            大学英语,3,3,4,李老师,外语楼305,1-16周
        """.trimIndent()
        val parsed = TextImportParser.parse(csv)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.courses.size)
        val math = parsed.courses.first { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), math.weeks)
    }

    @Test
    fun `竖线行格式导入`() {
        val text = """
            高等数学|1|1-2|1-16周(单)|王教授|教1-201
            体育|5|8-9|3周|钱老师|田径场
        """.trimIndent()
        val parsed = TextImportParser.parse(text)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.courses.size)
        val pe = parsed.courses.first { it.name == "体育" }
        assertEquals(5, pe.day)
        assertEquals(8, pe.startNode)
        assertEquals(9, pe.endNode)
        assertEquals(setOf(3), pe.weeks)
    }

    @Test
    fun `标准JSON导入`() {
        val json = """
            {
              "app": "kechengbiao",
              "tableName": "2025秋",
              "termStartDate": "2025-09-08",
              "nodeCount": 12,
              "courses": [
                {"name": "数据结构", "teacher": "赵老师", "room": "机房5楼", "day": 2, "startNode": 1, "endNode": 4, "weeks": [1,2,3,10,11]}
              ]
            }
        """.trimIndent()
        val parsed = TextImportParser.parse(json)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.courses.size)
        val c = parsed.courses.first()
        assertEquals("数据结构", c.name)
        assertEquals(setOf(1, 2, 3, 10, 11), c.weeks)
        val dto = JsonIO.parse(json)
        assertEquals("2025-09-08", JsonIO.termStartOf(dto!!)!!.toString())
    }

    @Test
    fun `无法识别的文本返回null`() {
        assertNull(TextImportParser.parse("随便写点东西，不是课表"))
    }
}
