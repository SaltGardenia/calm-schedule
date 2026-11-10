package com.saltgarden.kechengbiao.importer.eams

import com.saltgarden.kechengbiao.importer.model.ImportCourse
import com.saltgarden.kechengbiao.importer.model.ParsedSchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 金智 EAMS5 课表 JSON 深度解析（合肥工业大学等）。
 *
 * 教务页面本身由接口 ws/schedule-table/datum 渲染，返回的 scheduleList 是
 * **逐周记录**：每条含 weekIndex / weekday / startTime / endTime / room / personName。
 * 相比解析页面 HTML（汇总视图会把不同周的教室合并成一段，导致第 N 周教室不准），
 * JSON 能拿到每周真实教室，例如机器视觉周二 3-4 节：第 10 周 318、其余周 317。
 *
 * startTime/endTime 为 "HHmm" 整数（800 = 08:00，1455 = 14:55）。
 */
object EamsJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    data class Payload(
        val nodeTimes: List<NodeTime> = emptyList(),
        val datum: DatumWrap? = null,
    )

    @Serializable
    data class NodeTime(val s: String = "", val e: String = "")

    @Serializable
    data class DatumWrap(val result: Result? = null)

    @Serializable
    data class Result(
        val lessonList: List<Lesson> = emptyList(),
        val scheduleList: List<Schedule> = emptyList(),
    )

    @Serializable
    data class Lesson(
        val id: String = "",
        val courseName: String = "",
        val name: String = "",
        val teacherAssignmentList: List<Teacher> = emptyList(),
    )

    @Serializable
    data class Teacher(val name: String = "")

    @Serializable
    data class Schedule(
        val lessonId: Int = 0,
        val weekday: Int = 0,
        val weekIndex: Int = 0,
        val startTime: Int = 0,
        val endTime: Int = 0,
        val personName: String = "",
        val room: Room? = null,
    )

    @Serializable
    data class Room(val nameZh: String = "")

    fun parse(raw: String): ParsedSchedule? {
        val payload = runCatching {
            json.decodeFromString(Payload.serializer(), raw)
        }.getOrNull() ?: return null
        val schedules = payload.datum?.result?.scheduleList.orEmpty()
            .filter { it.weekday in 1..7 && it.weekIndex > 0 && it.startTime > 0 && it.endTime > 0 }
        if (schedules.isEmpty()) return null

        val lessonById = payload.datum?.result?.lessonList.orEmpty().associateBy { it.id }
        // 作息：页面网格给出的逐节起止优先；缺失时用出现过的起止时间兜底
        val nodeTimes = payload.nodeTimes
            .map { compactToText(it.s) to compactToText(it.e) }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            .ifEmpty {
                schedules.map { compactToText(it.startTime) to compactToText(it.endTime) }
                    .distinct()
                    .sortedBy { EventTimeMinutes.of(it.first) }
            }

        // 逐周记录按 "课程名 + 星期 + 起止时间 + 教室" 归组，得到周次集合；
        // 同一门课不同周换教室时自然拆成多条（如 10 周 318、11-18 周 317）
        data class Key(val name: String, val day: Int, val start: Int, val end: Int, val room: String)
        val grouped = linkedMapOf<Key, MutableSet<Int>>()
        val teacherOf = mutableMapOf<Key, String>()
        for (s in schedules) {
            val lesson = lessonById[s.lessonId.toString()]
            val name = (lesson?.courseName?.takeIf { it.isNotBlank() }
                ?: lesson?.name?.takeIf { it.isNotBlank() }
                ?: "未知课程").trim()
            val room = s.room?.nameZh?.trim().orEmpty()
            val key = Key(name, s.weekday, s.startTime, s.endTime, room)
            grouped.getOrPut(key) { mutableSetOf() }.add(s.weekIndex)
            val teacher = s.personName.trim().ifBlank {
                lesson?.teacherAssignmentList?.map { it.name.trim() }
                    ?.filter { it.isNotEmpty() }?.distinct()?.joinToString("、").orEmpty()
            }
            if (teacher.isNotEmpty()) teacherOf[key] = teacher
        }

        val courses = grouped.map { (key, weeks) ->
            val startMin = EventTimeMinutes.of(compactToText(key.start))
            val endMin = EventTimeMinutes.of(compactToText(key.end))
            val (startNode, endNode) = nodesFor(startMin, endMin, nodeTimes)
            ImportCourse(
                name = key.name,
                teacher = teacherOf[key].orEmpty(),
                room = key.room,
                day = key.day,
                startNode = startNode,
                endNode = endNode,
                weeks = weeks,
            )
        }.filter { it.weeks.isNotEmpty() }

        if (courses.isEmpty()) return null
        return ParsedSchedule(
            courses = courses,
            sourceName = "金智 EAMS（逐周数据）",
            systemId = "eams",
            nodeTimes = nodeTimes,
        )
    }

    /** "800" → "08:00"，"1455" → "14:55" */
    internal fun compactToText(raw: String): String = compactToText(raw.filter { it.isDigit() }.toIntOrNull() ?: 0)

    private fun compactToText(v: Int): String {
        val h = (v / 100).coerceIn(0, 47)
        val m = (v % 100).coerceIn(0, 59)
        return "%02d:%02d".format(h, m)
    }

    /** 起止时间 → 与作息相交的节次范围 */
    private fun nodesFor(
        startMin: Int,
        endMin: Int,
        nodeTimes: List<Pair<String, String>>,
    ): Pair<Int, Int> {
        var first = -1
        var last = -1
        nodeTimes.forEachIndexed { i, (s, e) ->
            val ns = EventTimeMinutes.of(s)
            val ne = EventTimeMinutes.of(e)
            if (ns < endMin && ne > startMin) {
                if (first < 0) first = i
                last = i
            }
        }
        if (first < 0) return 1 to 1
        return (first + 1) to (last + 1)
    }

    /** 轻量时间换算，避免解析器依赖 UI 层 */
    internal object EventTimeMinutes {
        fun of(text: String): Int {
            val parts = text.split(":")
            if (parts.size != 2) return 0
            return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        }
    }
}
