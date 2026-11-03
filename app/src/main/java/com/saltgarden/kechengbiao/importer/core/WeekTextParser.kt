package com.saltgarden.kechengbiao.importer.core

/**
 * 周次文本解析：各教务系统的周次写法差异极大，此处统一处理。
 *
 * 支持：
 *  "1-16周(单)"  "1-16周(双)"  "1-8周,10-16周"  "3周"  "1,2,4周"
 *  "第3-8周"  "1-16"  "5"  全角逗号/括号  英文 odd/even
 *  "010101…01"（URP 新版周次位串，见 parseBitString）
 */
object WeekTextParser {

    fun parse(text: String): Set<Int> {
        if (text.isBlank()) return emptySet()
        val s = text.trim()
            .replace('，', ',').replace('、', ',')
            .replace("（", "(").replace("）", ")")
            .replace("第", "")
            .replace("周", "")
            .replace(" ", "").replace("　", "")
            .replace("odd", "", ignoreCase = true).replace("even", "", ignoreCase = true)
            .replace("(", "").replace(")", "")
        val weeks = sortedSetOf<Int>()
        for (seg0 in s.split(',')) {
            if (seg0.isBlank()) continue
            val odd = seg0.contains("单")
            val even = seg0.contains("双")
            val seg = seg0.replace("单", "").replace("双", "")
            val nums = Regex("\\d+").findAll(seg).map { it.value.toInt() }.toList()
            when {
                nums.size >= 2 -> {
                    val lo = minOf(nums[0], nums[1])
                    val hi = maxOf(nums[0], nums[1])
                    for (w in lo..hi) {
                        if (odd && w % 2 == 0) continue
                        if (even && w % 2 == 1) continue
                        weeks.add(w)
                    }
                }
                nums.size == 1 -> {
                    val w = nums[0]
                    if (odd && w % 2 == 0) continue
                    if (even && w % 2 == 1) continue
                    weeks.add(w)
                }
            }
        }
        return weeks
    }

    /** URP 新版等使用 "01010101" 位串：第 i 位为 '1' 表示第 i+1 周有课 */
    fun parseBitString(bits: String): Set<Int> =
        bits.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet()

    /** 智能判断：纯 0/1 且长度>=4 时按位串解析，否则按文本解析 */
    fun parseSmart(text: String): Set<Int> {
        val t = text.trim()
        return if (t.length >= 4 && t.all { it == '0' || it == '1' }) parseBitString(t) else parse(t)
    }
}
