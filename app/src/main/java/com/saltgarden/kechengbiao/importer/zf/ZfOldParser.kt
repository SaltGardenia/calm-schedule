package com.saltgarden.kechengbiao.importer.zf

import com.saltgarden.kechengbiao.importer.core.NodeTextParser
import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 正方旧版课表 HTML 解析（#Table1）。
 *
 * 典型结构：行 = 节次（"第一节"…），列 = 星期一~星期日；
 * 单元格文本两种版式：
 *  A. 链接版：课名<a> + <br> 行 + "{第2-16周|周一,1-2节}" + 教师 + 教室
 *  B. 纯文本版：token 序列，含 "{...}" 的 token 为时间信息
 */
object ZfOldParser {

    private val WEEK_NAME = listOf(
        "星期一" to 1, "星期二" to 2, "星期三" to 3, "星期四" to 4,
        "星期五" to 5, "星期六" to 6, "星期日" to 7, "星期天" to 7,
        "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
        "周五" to 5, "周六" to 6, "周日" to 7, "周天" to 7,
    )

    private val courseProperty = setOf(
        "必修", "选修", "公必", "公选", "专业必修", "专业选修", "任选", "限选",
        "通识", "实验", "上机", "实践", "实训", "讲座", "网络课程",
    )

    fun parse(html: String): List<ImportCourse> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("Table1") ?: return emptyList()
        val courses = mutableListOf<ImportCourse>()

        // 列 → 星期 映射（取自表头行）
        val dayByCol = mutableMapOf<Int, Int>()
        var headerFound = false
        for (tr in table.select("tr")) {
            var col = 0
            for (cell in tr.select("td, th")) {
                val text = cell.text().trim()
                val span = cell.attr("colspan").toIntOrNull() ?: 1
                val day = weekOfText(text)
                if (day != null) {
                    dayByCol[col] = day
                    headerFound = true
                }
                col += span
            }
            if (headerFound) break
        }

        // 数据行
        var nodeCursor = 0
        for (tr in table.select("tr")) {
            val cells = tr.select("td")
            if (cells.isEmpty()) continue
            var rowNode = 0
            var col = 0
            for (cell in cells) {
                val text = cell.text().trim()
                val span = cell.attr("colspan").toIntOrNull() ?: 1
                if (NodeTextParser.isNodeLabel(text)) {
                    NodeTextParser.parseChineseOrDigit(text)?.let { rowNode = it }
                    col += span
                    continue
                }
                if (text.isEmpty() || text == "上午" || text == "下午" || text == "晚上" || isDayHeaderLabel(text)) {
                    col += span
                    continue
                }
                val day = dayByCol[col] ?: (col) // 无表头时按列序猜测
                if (day in 1..7) {
                    courses += parseCell(cell, day, rowNode)
                }
                col += span
            }
            nodeCursor++
            if (rowNode == 0) rowNode = nodeCursor
        }
        return courses
    }

    private fun weekOfText(text: String): Int? =
        WEEK_NAME.firstOrNull { text.contains(it.first) }?.second

    /** 表头是短的纯星期标签；内容单元格常含 "周一"（如 {第1-16周|周一,1-2节}），不能因此跳过 */
    private fun isDayHeaderLabel(text: String): Boolean =
        text.length <= 4 && weekOfText(text) != null

    private fun parseCell(cell: Element, day: Int, rowNode: Int): List<ImportCourse> {
        val result = mutableListOf<ImportCourse>()
        val fullText = cell.text().replace('\u00A0', ' ').trim()
        if (fullText.isEmpty()) return result

        val timeTokens = Regex("\\{[^}]*\\}").findAll(fullText).map { it.value }.toList()
        if (timeTokens.isEmpty()) {
            // 无显式时间信息：整格尝试（依赖行号/列号）
            parseChunk(fullText, day, rowNode)?.let { result.add(it) }
            return result
        }
        if (timeTokens.size <= 1) {
            parseChunk(fullText, day, rowNode)?.let { result.add(it) }
            return result
        }
        // 多门课：按 "{" 分段，段首非时间文本为课程名
        var lastName = ""
        for (seg in fullText.split(Regex("(?=\\{)"))) {
            val timeToken = Regex("\\{[^}]*\\}").find(seg)?.value
            if (timeToken == null) {
                if (seg.isNotBlank()) lastName = seg.trim()
                continue
            }
            val before = seg.substringBefore("{").trim()
            if (before.isNotBlank()) lastName = before
            val after = seg.substringAfter("}").trim()
            parseChunk("$lastName $timeToken $after", day, rowNode)?.let { result.add(it) }
        }
        return result
    }

    /** 解析单个课程块（纯文本） */
    private fun parseChunk(plain0: String, day: Int, rowNode: Int): ImportCourse? {
        val plain = plain0.trim()
        if (plain.isEmpty()) return null

        val timeToken = Regex("\\{[^}]*\\}").find(plain)?.value.orEmpty()
        val timeSource = timeToken.ifEmpty { plain }

        val weeks = Regex("第?(\\d{1,2})(?:-(\\d{1,2}))?周").find(timeSource)?.let {
            WeekTextParser.parse(it.value)
        } ?: emptySet()

        val dayInText = weekOfText(timeSource)
        val nodes = NodeTextParser.parsePairs(timeSource)

        val useDay = if (dayInText != null) dayInText else day
        val (startNode, endNode) = when {
            nodes.isNotEmpty() -> nodes.first().first to nodes.last().second
            rowNode > 0 -> rowNode to rowNode
            else -> 0 to 0
        }
        if (useDay !in 1..7 || startNode == 0 || weeks.isEmpty()) return null

        val rest = plain.replace(Regex("\\{[^}]*\\}"), " ")
        val tokens = rest.split(Regex("[\\s　]+")).filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && tokens.first() in courseProperty) tokens.removeAt(0)
        val name = tokens.removeFirstOrNull()?.trim() ?: return null

        var teacher = ""
        var room = ""
        if (tokens.isNotEmpty()) {
            val last = tokens.last()
            // 不用 MutableList.removeLast()：会被解析为 JDK21 的 List 默认方法，JDK17 运行时 NoSuchMethodError
            room = if (hasRoomMark(last)) tokens.removeAt(tokens.lastIndex) else ""
            teacher = tokens.joinToString(" ")
        }
        return ImportCourse(name, teacher, room, useDay, startNode, endNode, weeks)
    }

    private fun hasRoomMark(s: String): Boolean =
        s.any { it.isDigit() } || listOf("楼", "室", "区", "馆", "中心", "场", "房").any { s.contains(it) }
}
