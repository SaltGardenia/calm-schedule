package com.saltgarden.kechengbiao.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 周次与日期换算 */
object WeekCalculator {

    private val MONDAY: DayOfWeek = DayOfWeek.MONDAY

    /** 当前是第几周（第 1 周 = 开学那周，周一为首） */
    fun currentWeek(termStartMillis: Long, today: LocalDate = LocalDate.now()): Int {
        val start = toLocalDate(termStartMillis)
        val startMonday = start.with(TemporalAdjusters.previousOrSame(MONDAY))
        val days = ChronoUnit.DAYS.between(startMonday, today)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    /** 某一周的周一到周日日期列表 */
    fun weekDates(termStartMillis: Long, week: Int): List<LocalDate> {
        val start = toLocalDate(termStartMillis)
        val firstMonday = start.with(TemporalAdjusters.previousOrSame(MONDAY))
        val monday = firstMonday.plusWeeks((week - 1).toLong())
        return (0..6L).map { monday.plusDays(it) }
    }

    /** 根据今天粗略猜测当前学期第 1 周周一：秋学期取 9 月第 1 个周一，春学期取 2 月最后 1 个周一 */
    fun guessCurrentTermStart(today: LocalDate = LocalDate.now()): LocalDate {
        return if (today.monthValue >= 7 || today.monthValue <= 1) {
            // 秋学期（或寒假期间的秋学期归属）
            val year = if (today.monthValue == 1) today.year - 1 else today.year
            firstMondayOfSeptember(year)
        } else {
            // 春学期：2 月最后一个周一
            val febEnd = LocalDate.of(today.year, 2, 28)
            febEnd.with(TemporalAdjusters.previousOrSame(MONDAY))
        }
    }

    private fun firstMondayOfSeptember(year: Int): LocalDate {
        var d = LocalDate.of(year, 9, 1)
        while (d.dayOfWeek != MONDAY) d = d.plusDays(1)
        return d
    }

    /**
     * 读取现有作息，按节数补齐：缺失节从上一节结束 +10 分钟开始，单节 45 分钟。
     * 用于"编辑作息"对话框按当前每日节数逐节展示。
     */
    fun ensureNodeTimes(csv: String, count: Int): List<Pair<String, String>> {
        val parsed = parseNodeTimes(csv).toMutableList()
        while (parsed.size < count) {
            val lastEnd = parsed.lastOrNull()?.second?.let {
                Regex("(\\d{1,2}):(\\d{2})").find(it)?.let { m ->
                    m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                }
            } ?: (8 * 60 + 45)
            val start = lastEnd + 10
            parsed += formatMinutes(start) to formatMinutes(start + 45)
        }
        return parsed.take(count)
    }

    private fun formatMinutes(minutes: Int): String {
        val m = minutes.coerceIn(0, 24 * 60 - 1)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** 默认 12 节作息 */
    fun defaultNodeTimes(): String = listOf(
        "08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40",
        "12:00-12:45", "12:55-13:40", "14:00-14:45", "14:55-15:40",
        "16:00-16:45", "16:55-17:40", "19:00-19:45", "19:55-20:40",
    ).joinToString(",")

    fun parseNodeTimes(csv: String): List<Pair<String, String>> =
        csv.split(",").mapNotNull {
            val parts = it.trim().split("-")
            if (parts.size == 2) parts[0] to parts[1] else null
        }

    /** "1-16" / "1,3,5-15" → Set<Int> */
    fun parseWeeks(csv: String): Set<Int> {
        val weeks = mutableSetOf<Int>()
        for (seg in csv.split(",")) {
            val s = seg.trim()
            if (s.isEmpty()) continue
            val range = Regex("^(\\d+)-(\\d+)$").find(s)
            if (range != null) {
                val (a, b) = range.destructured
                (a.toInt()..b.toInt()).forEach { weeks.add(it) }
            } else {
                s.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        return weeks
    }

    /** Set<Int> → 压缩 CSV："1-16" / "1,3,5-15" */
    fun weeksToCsv(weeks: Collection<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.toSortedSet()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var prev = start
        for (w in sorted.drop(1)) {
            if (w == prev + 1) { prev = w; continue }
            parts += if (start == prev) "$start" else "$start-$prev"
            start = w; prev = w
        }
        parts += if (start == prev) "$start" else "$start-$prev"
        return parts.joinToString(",")
    }

    fun formatDate(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("M.d"))

    private fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
