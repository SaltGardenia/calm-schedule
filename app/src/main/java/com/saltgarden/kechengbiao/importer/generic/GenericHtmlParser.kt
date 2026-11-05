package com.saltgarden.kechengbiao.importer.generic

import com.saltgarden.kechengbiao.importer.core.NodeTextParser
import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import com.saltgarden.kechengbiao.importer.eams.EamsParser
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import com.saltgarden.kechengbiao.importer.model.ParsedSchedule
import com.saltgarden.kechengbiao.importer.qz.QzParser
import com.saltgarden.kechengbiao.importer.zf.ZfOldParser
import org.jsoup.Jsoup

/**
 * 通用 HTML 课表解析链：自动尝试已知解析器，选产出最多的一路。
 * 这是"网页登录（通用兜底）"通道的核心：理论上任何把课表渲染成
 * 表格 / 标准列表的教务系统都能被识别。
 */
object GenericHtmlParser {

    fun autoParse(html: String): ParsedSchedule? {
        data class Candidate(val name: String, val systemId: String, val parse: () -> List<ImportCourse>)

        val candidates = listOf(
            Candidate("强智教务", "qz") { QzParser.parse(html) },
            Candidate("正方旧版", "zf_old") { ZfOldParser.parse(html) },
            Candidate("金智 EAMS", "eams") { EamsParser.parse(html) },
            Candidate("URP 平台", "urp") { com.saltgarden.kechengbiao.importer.urp.UrpParser.parse(html) },
            Candidate("通用课程列表", "list") { parseCourseListTable(html) },
            Candidate("通用网格", "grid") { parseGenericGrid(html) },
        )
        return candidates
            .map { (name, sysId, f) -> ParsedSchedule(f(), name, sysId) }
            .filter { it.courses.isNotEmpty() }
            .maxByOrNull { it.courses.size }
    }

    /**
     * 列表式课表（如旧 URP displayTag）：表头含 课程/教师/周次/星期/节次
     */
    fun parseCourseListTable(html: String): List<ImportCourse> {
        val doc = Jsoup.parse(html)
        for (table in doc.select("table")) {
            val headerCells = table.select("tr").firstOrNull()?.select("td, th") ?: continue
            val headers = headerCells.map { it.text().trim() }
            val idxName = headers.indexOfFirst { it.contains("课程") }
            val idxTeacher = headers.indexOfFirst { it.contains("教师") }
            val idxWeek = headers.indexOfFirst { it.contains("周次") }
            val idxDay = headers.indexOfFirst { it.contains("星期") }
            val idxNode = headers.indexOfFirst { it.contains("节次") || it.contains("节数") }
            val idxRoom = headers.indexOfFirst { it.contains("教室") }
            if (idxName < 0 || idxWeek < 0 || (idxDay < 0 && idxNode < 0)) continue

            val courses = mutableListOf<ImportCourse>()
            for (tr in table.select("tr").drop(1)) {
                val tds = tr.select("td")
                if (tds.size < headers.size - 1) continue
                val name = tds.getOrNull(idxName)?.text()?.trim().orEmpty()
                if (name.isEmpty()) continue
                val teacher = tds.getOrNull(idxTeacher)?.text()?.trim().orEmpty()
                val room = tds.getOrNull(idxRoom)?.text()?.trim().orEmpty()
                val weeks = WeekTextParser.parseSmart(tds.getOrNull(idxWeek)?.text().orEmpty())
                val day = tds.getOrNull(idxDay)?.text()?.trim()?.let { dayFromText(it) } ?: 0
                val nodes = NodeTextParser.mergeContiguous(
                    NodeTextParser.parsePairs(tds.getOrNull(idxNode)?.text().orEmpty())
                )
                if (weeks.isEmpty() || day !in 1..7) continue
                if (nodes.isEmpty()) continue
                for ((s, e) in nodes) {
                    courses.add(ImportCourse(name, teacher, room, day, s, e, weeks))
                }
            }
            if (courses.isNotEmpty()) return courses
        }
        return emptyList()
    }

    /**
     * 网格式课表：任意表，第一行表头含 星期一..星期日（或 周一..），行首格为节次。
     * 从单元格自由文本中抽取 周次/节次/教师/教室。
     */
    fun parseGenericGrid(html: String): List<ImportCourse> {
        val doc = Jsoup.parse(html)
        var best: List<ImportCourse> = emptyList()
        for (table in doc.select("table")) {
            val rows = table.select("tr")
            if (rows.size < 3) continue
            val dayByCol = mutableMapOf<Int, Int>()
            for (tr in rows.take(2)) {
                var col = 0
                for (cell in tr.select("td, th")) {
                    val day = dayFromText(cell.text())
                    val span = cell.attr("colspan").toIntOrNull() ?: 1
                    if (day != null) { dayByCol[col] = day }
                    col += span
                }
            }
            if (dayByCol.size < 5) continue

            val courses = mutableListOf<ImportCourse>()
            var rowIdx = 0
            for (tr in rows) {
                val tds = tr.select("td")
                if (tds.isEmpty()) continue
                rowIdx++
                var col = 0
                for (td in tds) {
                    val span = td.attr("colspan").toIntOrNull() ?: 1
                    val text = td.text().trim()
                    if (NodeTextParser.isNodeLabel(text)) {
                        col += span
                        continue
                    }
                    if (text.isEmpty() || text == "上午" || text == "下午" || text == "晚上" ||
                        (text.length <= 4 && dayFromText(text) != null)
                    ) {
                        col += span
                        continue
                    }
                    val day = dayByCol[col] ?: 0
                    if (day in 1..7) {
                        parseFreeTextCell(text, day, rowIdx)?.let { courses.addAll(it) }
                    }
                    col += span
                }
            }
            if (courses.size > best.size) best = courses
        }
        return best
    }

    /** 从自由文本中解析课程块：课程名 + (第X-Y周) + (周X) + (X-Y节) + 教师 + 教室。
     *  单元格常为 "课名 {...} 教师 教室"，在 "{" 前补空格按块拆分，无时间信息的块视为课程名。 */
    fun parseFreeTextCell(text: String, day: Int, rowNode: Int): List<ImportCourse> {
        val normalized = text.replace("{", " {")
        val chunks = normalized.split(Regex("(?=\\{)|\\s{2,}|\n+"))
            .map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<ImportCourse>()
        var pendingName = ""
        for (chunk in chunks) {
            // 周次写法兼容 EAMS/网页的波浪号与各种破折号（如 "9~12周"）
            val weeks = Regex("第?(\\d{1,2})(?:\\s*[-~～—–－]\\s*(\\d{1,2}))?\\s*周").find(chunk)
                ?.let { WeekTextParser.parse(it.value) } ?: emptySet()
            val dayInText = dayFromText(chunk)
            // 教室/教师里的数字（如 "教1-201"）不是节次，限制在合理范围
            val nodes = NodeTextParser.mergeContiguous(
                NodeTextParser.parsePairs(chunk).filter { (a, b) -> a in 1..20 && b in 1..20 }
            )
            if (weeks.isEmpty() && nodes.isEmpty()) {
                // 无时间信息的块视为课程名候选，供下一个含时间的块使用
                if (pendingName.isBlank()) pendingName = cleanCourseName(chunk)
                continue
            }
            if (weeks.isEmpty()) continue
            val useDay = dayInText ?: day
            val (s, e) = if (nodes.isNotEmpty()) nodes.first().first to nodes.last().second
            else rowNode to rowNode
            if (useDay !in 1..7 || s == 0) continue
            val name = if (pendingName.isNotBlank()) pendingName else extractCourseName(chunk)
            pendingName = ""
            if (name.isBlank()) continue
            result.add(ImportCourse(name, "", "", useDay, s, e, weeks))
        }
        return result
    }

    /**
     * 提取课程名：EAMS 单元格常为 "课程代码 课程名 教学楼+教室(周次) 节次"。
     * 优先取“不含数字的中文词”，避免把课程代码/教室当成课名。
     */
    private fun extractCourseName(chunk: String): String {
        val tokens = cleanCourseName(chunk)
            .split(Regex("[\\s　]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        val codeLike = Regex("^\\d{4,}[-\\d]*$")
        val cjkTokens = tokens.filter { tok ->
            !codeLike.matches(tok) && tok.any { it in '\u4e00'..'\u9fff' }
        }
        val noDigit = cjkTokens.firstOrNull { tok -> tok.none { it.isDigit() } }
        return noDigit ?: cjkTokens.firstOrNull() ?: tokens.first()
    }

    /** 去掉时间/星期标记，得到课程名候选 */
    private fun cleanCourseName(chunk: String): String = chunk
        .replace(Regex("\\{[^}]*\\}"), " ")
        .replace(Regex("第?\\d{1,2}(?:\\s*[-~～—–－]\\s*\\d{1,2})?\\s*周[^\\s]*"), " ")
        .replace(Regex("周[一二三四五六日天天]"), " ")
        .replace(Regex("\\d{1,2}\\s*[-~～—–－]\\s*\\d{1,2}\\s*节?"), " ")
        .replace(Regex("\\d+\\s*节"), " ")
        .replace("{", "").replace("}", "")
        .trim()

    fun dayFromText(text: String): Int? {
        val names = listOf(
            "星期一" to 1, "星期二" to 2, "星期三" to 3, "星期四" to 4, "星期五" to 5, "星期六" to 6,
            "星期日" to 7, "星期天" to 7, "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
            "周五" to 5, "周六" to 6, "周日" to 7, "周天" to 7,
        )
        return names.firstOrNull { text.contains(it.first) }?.second
    }
}
