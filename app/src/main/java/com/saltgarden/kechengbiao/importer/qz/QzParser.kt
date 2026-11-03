package com.saltgarden.kechengbiao.importer.qz

import com.saltgarden.kechengbiao.importer.core.NodeTextParser
import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import org.jsoup.Jsoup

/**
 * 强智教务课表解析（#kbtable）。
 *
 * 结构（参考 WakeUp QzParser 及其 4 个变体）：
 *  行 = 大节（每行 2 小节），列 = 星期；单元格内 div.kbcontent（或 .kbcontent1）
 *  同一 div 内多门课用 "-----" 分隔；
 *  字段靠 title 属性定位：title="老师" / title="教室" / title="周次(节次)" / title="周次" / title="节次"
 *  周次(节次) 形如 "1-16周(单)" 或 "1-16周(单) [1-2节]"
 */
object QzParser {

    fun parse(html: String): List<ImportCourse> {
        val doc = Jsoup.parse(html)
        val table = doc.getElementById("kbtable") ?: return emptyList()
        val courses = mutableListOf<ImportCourse>()

        // 表头列 → 星期
        val dayByCol = mutableMapOf<Int, Int>()
        for (tr in table.select("tr")) {
            var col = 0
            var found = false
            for (cell in tr.select("td, th")) {
                val text = cell.text().trim()
                val day = weekOfText(text)
                val span = cell.attr("colspan").toIntOrNull() ?: 1
                if (day != null) {
                    dayByCol[col] = day
                    found = true
                }
                col += span
            }
            if (found) break
        }

        // 表头行不占节次行号
        val weekNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        val allRows = table.select("tr")
        val headerRowIdx = allRows.indexOfFirst { tr -> weekNames.any { tr.text().contains(it) } }

        var rowIndex = 0
        allRows.forEachIndexed { trIdx, tr ->
            val tds = tr.select("td")
            if (tds.isEmpty()) return@forEachIndexed
            if (trIdx == headerRowIdx) return@forEachIndexed
            rowIndex++
            var col = 0
            for (td in tds) {
                val span = td.attr("colspan").toIntOrNull() ?: 1
                val text = td.text().trim()
                if (NodeTextParser.isNodeLabel(text)) {
                    col += span
                    continue
                }
                val day = dayByCol[col] ?: (col + 1)
                if (day in 1..7) {
                    courses += parseCell(td, day, rowIndex)
                }
                col += span
            }
        }
        return courses
    }

    private fun weekOfText(text: String): Int? {
        val names = listOf(
            "星期一" to 1, "星期二" to 2, "星期三" to 3, "星期四" to 4, "星期五" to 5, "星期六" to 6,
            "星期日" to 7, "星期天" to 7, "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
            "周五" to 5, "周六" to 6, "周日" to 7, "周天" to 7,
        )
        return names.firstOrNull { text.contains(it.first) }?.second
    }

    private fun parseCell(td: org.jsoup.nodes.Element, day: Int, row: Int): List<ImportCourse> {
        val result = mutableListOf<ImportCourse>()
        val contentDivs = td.select("div.kbcontent, div.kbcontent1").ifEmpty { td.select("div") }
        val htmls = if (contentDivs.isEmpty()) listOf(td.html()) else contentDivs.map { it.html() }
        for (divHtml in htmls) {
            val chunks = divHtml.split(Regex("[-]{3,}"))
            for (chunk in chunks) {
                val c = parseChunk(chunk, day, row)
                if (c != null) result.add(c)
            }
        }
        return result
    }

    private fun parseChunk(chunkHtml: String, day: Int, row: Int): ImportCourse? {
        val doc = Jsoup.parseBodyFragment(chunkHtml)
        val plain = doc.text().trim()
        if (plain.isEmpty() || plain == " ") return null

        val teacher = doc.selectFirst("[title=老师]")?.text().orEmpty().trim()
        val roomEl = doc.selectFirst("[title=教室]")?.text().orEmpty().trim()
        val groupEl = doc.selectFirst("[title=分组]")?.text().orEmpty().trim()
        val room = listOf(roomEl, groupEl).filter { it.isNotEmpty() }.joinToString("/")

        val weekNodeText = doc.selectFirst("[title=\"周次(节次)\"]")?.text().orEmpty().trim()
        val weekOnly = doc.selectFirst("[title=周次]")?.text().orEmpty().trim()
        val nodeOnly = doc.selectFirst("[title=节次]")?.text().orEmpty().trim()

        // 课程名：第一个 <br>（或 <font）之前的文本
        val cut = chunkHtml.indexOfFirst { c -> c == '<' }
        val namePart = if (cut > 0) chunkHtml.substring(0, cut) else chunkHtml
        var name = Jsoup.parseBodyFragment(namePart).text().trim()
        if (name.isEmpty()) name = plain.substringBefore("老师").substringBefore("周次").trim()
        if (name.isEmpty()) return null

        // 周次与节次
        val timeText = weekNodeText.ifEmpty { weekOnly }
        val weeks = WeekTextParser.parse(timeText.substringBefore("[").removeSuffix("(周)"))
            .ifEmpty { WeekTextParser.parse(timeText) }
        val nodes = when {
            weekNodeText.contains("[") -> NodeTextParser.parsePairs(
                weekNodeText.substringAfter("[", "").substringBefore("]")
            )
            nodeOnly.isNotBlank() -> NodeTextParser.parsePairs(nodeOnly)
            else -> emptyList()
        }
        val merged = NodeTextParser.mergeContiguous(nodes)
        val (startNode, endNode) = if (merged.isNotEmpty()) {
            merged.first().first to merged.last().second
        } else {
            // 无节次信息：按行推算，每行 = 2 小节
            val s = row * 2 - 1
            s to (s + 1)
        }
        if (weeks.isEmpty()) return null
        return ImportCourse(name, teacher, room, day, startNode, endNode, weeks)
    }
}
