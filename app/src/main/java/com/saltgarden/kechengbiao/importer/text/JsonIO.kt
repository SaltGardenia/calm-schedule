package com.saltgarden.kechengbiao.importer.text

import com.saltgarden.kechengbiao.data.CourseEntity
import com.saltgarden.kechengbiao.data.TableEntity
import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** 本 App 的标准课表 JSON 交换格式（导出/导入共用） */
@Serializable
data class CourseJson(
    val name: String,
    val teacher: String = "",
    val room: String = "",
    /** 1=周一 … 7=周日 */
    val day: Int,
    val startNode: Int,
    val endNode: Int,
    val weeks: List<Int> = emptyList(),
)

@Serializable
data class ScheduleJson(
    val app: String = "kechengbiao",
    val version: Int = 1,
    val tableName: String? = null,
    /** yyyy-MM-dd，第 1 周周一 */
    val termStartDate: String? = null,
    val nodeCount: Int? = null,
    val maxWeek: Int? = null,
    val nodeTimes: String? = null,
    val courses: List<CourseJson> = emptyList(),
)

object JsonIO {

    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; isLenient = true }

    fun export(table: TableEntity, courses: List<CourseEntity>): String {
        val dto = ScheduleJson(
            tableName = table.name,
            termStartDate = java.time.Instant.ofEpochMilli(table.termStartDate)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
            nodeCount = table.nodeCount,
            maxWeek = table.maxWeek,
            nodeTimes = table.nodeTimes,
            courses = courses.map {
                CourseJson(
                    name = it.name, teacher = it.teacher, room = it.room,
                    day = it.day, startNode = it.startNode, endNode = it.endNode,
                    weeks = WeekTextParser.parseSmart(it.weeks).sorted(),
                )
            }
        )
        return json.encodeToString(ScheduleJson.serializer(), dto)
    }

    /** 解析 JSON 文本；失败返回 null（用于嗅探格式） */
    fun parse(text: String): ScheduleJson? = try {
        json.decodeFromString(ScheduleJson.serializer(), text.trim())
    } catch (e: Exception) {
        null
    }

    fun toImportCourses(s: ScheduleJson): List<ImportCourse> =
        s.courses.filter { it.day in 1..7 && it.startNode in 1..30 && it.weeks.isNotEmpty() }
            .map { ImportCourse(it.name, it.teacher, it.room, it.day, it.startNode, it.endNode, it.weeks.toSet()) }

    fun termStartOf(s: ScheduleJson): LocalDate? = s.termStartDate?.let {
        try { LocalDate.parse(it) } catch (e: Exception) { null }
    }
}
