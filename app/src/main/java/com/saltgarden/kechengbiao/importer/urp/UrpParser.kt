package com.saltgarden.kechengbiao.importer.urp

import com.saltgarden.kechengbiao.core.time.EventTime
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import org.jsoup.Jsoup

/**
 * URP 高校教学管理与服务平台课表解析（内蒙古大学等）。
 *
 * 页面为 FullCalendar 周视图：7 个 `div.fc-content-col` 按周一~周日排列，
 * 每个事件是 `a.fc-time-grid-event`，内含
 * `div.fc-time[data-full="HH:mm - HH:mm"]` 与 `div.fc-title`（"课名 地点 教师、教师"）。
 *
 * 注意：该视图**不携带周次信息**（只渲染当前周），周次统一按 1-16 导入，
 * 用户可在课程编辑中逐条修改。
 */
object UrpParser {

    /** 节次反推的参考作息：默认 12 节补齐到 16 节，覆盖晚间三节连排（如 19:00-21:35） */
    private val referenceNodeTimes =
        WeekCalculator.ensureNodeTimes(WeekCalculator.defaultNodeTimes(), 16)

    fun parse(html: String): List<ImportCourse> {
        val doc = Jsoup.parse(html)
        val cols = doc.select("div.fc-content-col")
        if (cols.size < 5) return emptyList()

        val courses = mutableListOf<ImportCourse>()
        cols.forEachIndexed { i, col ->
            val day = i + 1
            for (a in col.select("a.fc-time-grid-event")) {
                val full = a.selectFirst("div.fc-time")?.attr("data-full") ?: continue
                val (startMin, endMin) = parseRange(full) ?: continue
                val title = a.selectFirst("div.fc-title")?.text()?.trim() ?: continue
                if (title.isEmpty()) continue
                val (name, room, teacher) = splitTitle(title)
                val (startNode, endNode) = nodesFor(startMin, endMin)
                courses += ImportCourse(
                    name = name,
                    teacher = teacher,
                    room = room,
                    day = day,
                    startNode = startNode,
                    endNode = endNode,
                    weeks = (1..16).toSet(),
                )
            }
        }
        return courses
    }

    /** "10:00 - 11:40" / "10:00-11:40" → 起止分钟 */
    private fun parseRange(text: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})").find(text) ?: return null
        val s = EventTime.parseMinutes(m.groupValues[1]) ?: return null
        val e = EventTime.parseMinutes(m.groupValues[2]) ?: return null
        return s to e
    }

    /** "课名 地点 教师A、教师B" 三段拆分；教师名做去重（URP 会按周段重复列出） */
    private fun splitTitle(title: String): Triple<String, String, String> {
        val tokens = title.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return Triple("", "", "")
        val name = tokens[0]
        var room = ""
        val rest = mutableListOf<String>()
        // 第二段若形如教室（含数字或楼/馆/室字样）视为地点，其余并入教师
        for ((i, t) in tokens.drop(1).withIndex()) {
            if (i == 0 && (t.any { it.isDigit() } || Regex("[楼馆室区]").containsMatchIn(t))) {
                room = t
            } else {
                rest += t
            }
        }
        val teacher = rest.joinToString("")
            .split("、", "，", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("、")
        return Triple(name, room, teacher)
    }

    /** 起止时间 → 参考作息中与之相交的节次范围 */
    private fun nodesFor(startMin: Int, endMin: Int): Pair<Int, Int> {
        var first = -1
        var last = -1
        referenceNodeTimes.forEachIndexed { i, (s, e) ->
            val ns = EventTime.parseMinutes(s) ?: return@forEachIndexed
            val ne = EventTime.parseMinutes(e) ?: return@forEachIndexed
            if (ns < endMin && ne > startMin) {
                if (first < 0) first = i
                last = i
            }
        }
        if (first < 0) first = 0
        if (last < first) last = first
        return (first + 1) to (last + 1)
    }
}
