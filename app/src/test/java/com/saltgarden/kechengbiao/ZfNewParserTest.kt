package com.saltgarden.kechengbiao

import com.saltgarden.kechengbiao.importer.core.SchoolHttpClient
import com.saltgarden.kechengbiao.importer.zf.ZfNewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 新版正方 kbList JSON 解析测试（夹具模拟真实接口返回结构） */
class ZfNewParserTest {

    // parseSchedule 不发起网络请求，仅为构造完整性传入空客户端
    private val client = ZfNewClient(SchoolHttpClient(), "http://test")

    private fun loadFixture(): String {
        return javaClass.classLoader!!
            .getResourceAsStream("parser/zf_new_kb.json")!!
            .readBytes().decodeToString()
    }

    @Test
    fun `解析课表JSON`() {
        val courses = client.parseSchedule(loadFixture())
        assertEquals(4, courses.size)

        val math = courses.first { it.name == "高等数学A(上)" }
        assertEquals(1, math.day)
        assertEquals(1, math.startNode)
        assertEquals(2, math.endNode)
        assertEquals("王教授", math.teacher)
        assertEquals("教1-201", math.room)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), math.weeks)

        val english = courses.first { it.name == "大学英语" }
        assertEquals(3, english.day)
        assertEquals((1..16).toSet(), english.weeks)

        val ds = courses.first { it.name == "数据结构" }
        assertEquals(2, ds.day)
        assertEquals(1, ds.startNode)
        assertEquals(4, ds.endNode)
        assertEquals((1..8).toSet() + (10..16).toSet(), ds.weeks)
    }

    @Test
    fun `学期码映射`() {
        assertEquals("3", ZfNewClient.xqmOf(1))
        assertEquals("12", ZfNewClient.xqmOf(2))
        assertEquals("27", ZfNewClient.xqmOf(3))
        assertTrue(ZfNewClient.xqmOf(3).toInt() > ZfNewClient.xqmOf(2).toInt())
    }
}
