package com.saltgarden.kechengbiao.importer.text

import com.saltgarden.kechengbiao.importer.core.NodeTextParser
import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import com.saltgarden.kechengbiao.importer.model.ParsedSchedule

/**
 * 纯文本快速导入：自动嗅探三种格式——
 *  1. 本 App 标准 JSON（见 JsonIO）
 *  2. WakeUp 兼容 CSV：表头 "课程名称,星期,开始节次,结束节次,教师,教室,周次"
 *  3. 竖线行格式：课名|星期|节次|周次|教师|教室
 */
object TextImportParser {

    fun parse(text: String): ParsedSchedule? {
        if (text.isBlank()) return null
        JsonIO.parse(text)?.let { dto ->
            val courses = JsonIO.toImportCourses(dto)
            if (courses.isNotEmpty()) return ParsedSchedule(courses, "标准 JSON", "json")
        }
        parseCsv(text)?.let { if (it.isNotEmpty()) return ParsedSchedule(it, "CSV", "csv") }
        parseLines(text)?.let { if (it.isNotEmpty()) return ParsedSchedule(it, "文本行", "text") }
        return null
    }

    /**
     * WakeUp 兼容 CSV（kotlin-csv 版 CSVParser 的移植）：
     * 表头：课程名称,星期,开始节次,结束节次,教师,教室,周次
     * 周次列如 "1-16周(单)"、"2-8周(双)、10-16周"
     */
    fun parseCsv(text: String): List<ImportCourse>? {
        val lines = text.lines().map { it.trim('\uFEFF') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val header = lines.first()
        if (!header.replace(" ", "").startsWith("课程名称")) return null

        val cols = header.split(",").map { it.trim() }
        fun col(name: String) = cols.indexOfFirst { it.replace(" ", "") == name }
        val iName = col("课程名称")
        val iDay = col("星期")
        val iStart = col("开始节次")
        val iEnd = col("结束节次")
        val iTeacher = col("教师")
        val iRoom = col("教室")
        val iWeek = col("周次")
        if (iName < 0 || iWeek < 0) return null

        val result = mutableListOf<ImportCourse>()
        for (line in lines.drop(1)) {
            val cells = line.split(",").map { it.trim() }
            val name = cells.getOrNull(iName)?.trim() ?: continue
            if (name.isBlank()) continue
            val dayText = cells.getOrNull(iDay).orEmpty()
            val day = dayText.trim().toIntOrNull()
                ?: GenericDay.fromText(dayText)
                ?: continue
            val startNode = cells.getOrNull(iStart)?.toIntOrNull() ?: 1
            val endNode = cells.getOrNull(iEnd)?.toIntOrNull() ?: startNode
            val teacher = cells.getOrNull(iTeacher).orEmpty()
            val room = cells.getOrNull(iRoom).orEmpty()
            // 周次列可能用 、 分隔多段
            val weekText = cells.getOrNull(iWeek).orEmpty().replace('、', ',').replace("第", "")
            val weeks = WeekTextParser.parse(weekText)
            if (weeks.isEmpty()) continue
            result.add(ImportCourse(name, teacher, room, day, startNode, maxOf(startNode, endNode), weeks))
        }
        return result
    }

    /** 竖线行格式：课名|星期|节次|周次|教师|教室 */
    fun parseLines(text: String): List<ImportCourse> {
        val result = mutableListOf<ImportCourse>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split('|', '｜').map { it.trim() }
            if (parts.size < 4) continue
            val name = parts[0]
            if (name.isBlank()) continue
            val day = parts[1].trim().toIntOrNull() ?: GenericDay.fromText(parts[1]) ?: continue
            val nodes = NodeTextParser.parsePairs(parts[2])
                .ifEmpty { NodeTextParser.parsePairs("1") }
            val weeks = WeekTextParser.parseSmart(parts[3])
            if (weeks.isEmpty()) continue
            val teacher = parts.getOrNull(4).orEmpty()
            val room = parts.getOrNull(5).orEmpty()
            val (s, e) = nodes.first()
            result.add(ImportCourse(name, teacher, room, day, s, e, weeks))
        }
        return result
    }
}

private object GenericDay {
    fun fromText(text: String): Int? {
        val names = listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
        for ((cn, d) in names) {
            if (text.contains("周$cn") || text.trim() == cn || text.contains("星期$cn")) return d
        }
        return null
    }
}
