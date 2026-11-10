package com.saltgarden.kechengbiao.importer.eams

import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 金智 EAMS5（树维）课表解析：合肥工业大学等使用。
 *
 * 页面不是 table，而是绝对定位的卡片：
 *   div.course-table > div.columns.weekday × 7（周一…周日）
 *     └ div.card-view (style="top:Npx;height:Npx")
 *         └ div.card-content
 *             <strong>课程代码</strong>
 *             <p>课程名<br>地点1(9~12周),地点2（临时）(1~4周)  星期 (1,2)</p>
 *
 * 时间列以整点分隔，每小时 60px、首行 08:00；卡片 (a,b) 即第 a-b 节。
 */
object EamsParser {

    fun parse(html: String): List<ImportCourse> {
        val doc = Jsoup.parse(html)
        val columns = doc.select("div.course-table div.columns.weekday")
        if (columns.size < 5) return emptyList()

        val result = mutableListOf<ImportCourse>()
        columns.forEachIndexed { idx, column ->
            val day = (idx + 1).coerceAtMost(7)
            column.select("div.card-view").forEach { card ->
                parseCard(card, day)?.let { result.addAll(it) }
            }
        }
        return result
    }

    private fun parseCard(card: Element, day: Int): List<ImportCourse>? {
        val content = card.selectFirst("div.card-content") ?: card
        val p = content.selectFirst("p") ?: return null
        val lines = p.html()
            .split(Regex("(?i)<br\\s*/?>"))
            .map { Jsoup.parse(it).text().trim() }
            .filter { it.isNotBlank() }
        val name = lines.firstOrNull() ?: return null
        // 去掉尾部的“星期 (a,b)”标记后再按逗号切分地点（(a,b) 内的逗号会误切）
        val rest = lines.drop(1).joinToString(" ")
            .replace(Regex("\\(\\d{1,2}\\s*,\\s*\\d{1,2}\\)\\s*$"), " ")
            .replace(Regex("\\s+\\d{1,2}\\s*$"), " ")
            .trim()

        val periods = Regex("\\((\\d{1,2})\\s*,\\s*(\\d{1,2})\\)").find(p.text())
        val (startNode, endNode) = if (periods != null) {
            val a = periods.groupValues[1].toInt()
            val b = periods.groupValues[2].toInt()
            minOf(a, b) to maxOf(a, b)
        } else {
            nodesFromGeometry(card)
        }
        if (startNode <= 0) return null

        val segments = parseLocations(rest)
        if (segments.isEmpty()) {
            val weeks = WeekTextParser.parse(rest)
            return if (weeks.isEmpty()) null
            else listOf(ImportCourse(name, "", "", day, startNode, endNode, weeks))
        }
        return segments.map { (room, weeks) ->
            ImportCourse(name, "", room, day, startNode, endNode, weeks)
        }
    }

    /** "新安学堂317(9~12周),计算中心103（临时）(1~4周)" → (地点, 周次) 列表 */
    private fun parseLocations(text: String): List<Pair<String, Set<Int>>> {
        val out = mutableListOf<Pair<String, Set<Int>>>()
        text.split(',', '，')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { seg ->
                val m = Regex("^(.*?)[（(]\\s*(\\d{1,2})(?:\\s*[~～—–-]\\s*(\\d{1,2}))?\\s*周[^）)]*[）)]")
                    .find(seg)
                if (m != null) {
                    val room = m.groupValues[1].trim().trimEnd('（', '(')
                    val lo = m.groupValues[2].toInt()
                    val hi = m.groupValues[3].toIntOrNull() ?: lo
                    out.add(room to (lo..hi).toSet())
                } else {
                    val weeks = WeekTextParser.parse(seg)
                    if (weeks.isNotEmpty()) {
                        val room = seg.substringBefore('(').substringBefore('（').trim()
                        out.add(room to weeks)
                    }
                }
            }
        return out
    }

    /** 缺少 (a,b) 时用卡片位置兜底：每小时 60px、08:00 起，按 45 分钟一节换算 */
    private fun nodesFromGeometry(card: Element): Pair<Int, Int> {
        val style = card.attr("style")
        val top = Regex("top:\\s*(-?\\d+(?:\\.\\d+)?)px").find(style)
            ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val height = Regex("height:\\s*(-?\\d+(?:\\.\\d+)?)px").find(style)
            ?.groupValues?.get(1)?.toFloatOrNull() ?: 90f
        val startMinute = (8 * 60 + top).toInt()
        val endMinute = (startMinute + height).toInt()
        val s = ((startMinute - 8 * 60) / 45 + 1).coerceIn(1, 16)
        val e = ((endMinute - 8 * 60) / 45 + 1).coerceIn(s, 16)
        return s to e
    }

    /**
     * 判断抓取页面是否为"第 N 周"单周视图：
     * 教务的"全部周次"汇总视图给出的是默认教室，按周视图才是当周真实教室（可能不同）。
     * 周次下拉是 selectize 组件，选中值在 select 后的 div.item[data-value] 上。
     */
    fun detectSingleWeek(html: String): Int? {
        val doc = Jsoup.parse(html)
        val select = doc.selectFirst("select#weeks") ?: return null
        val item = select.nextElementSibling()?.selectFirst("div.item[data-value]")
            ?: doc.selectFirst("div.selectize-input div.item[data-value]")
            ?: return null
        return item.attr("data-value").trim().toIntOrNull()
    }

    /**
     * 从页面网格的 blank-unit 提取学校真实作息（每节起止时间）：
     *   <div class="blank-unit" start-time="800" end-time="845">
     * 时间为 "hmm" 紧凑格式（800=08:00，1455=14:55）。每天重复一份，去重后得到逐节作息。
     */
    fun extractNodeTimes(html: String): List<Pair<String, String>>? {
        val doc = Jsoup.parse(html)
        val units = doc.select("div.blank-unit[start-time]").map { it.attr("start-time") to it.attr("end-time") }
        if (units.size < 4) return null
        val out = mutableListOf<Pair<String, String>>()
        for ((s, e) in units) {
            val pair = formatCompact(s) to formatCompact(e)
            // 网格按星期重复一整套 blank-unit，按全集去重（保序）
            if (pair !in out) out.add(pair)
        }
        return out.takeIf { it.size >= 4 }
    }

    /** "800" → "08:00"，"1455" → "14:55"，"45"（纯分钟）→ "00:45" */
    private fun formatCompact(raw: String): String {
        val v = raw.filter { it.isDigit() }
        val (h, m) = when (v.length) {
            in 0..2 -> 0 to (v.toIntOrNull() ?: 0)
            3 -> v.substring(0, 1).toInt() to v.substring(1).toInt()
            else -> v.substring(0, v.length - 2).toInt() to v.takeLast(2).toInt()
        }
        return "%02d:%02d".format(h.coerceIn(0, 47), m.coerceIn(0, 59))
    }
}
