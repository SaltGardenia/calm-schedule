package com.saltgarden.kechengbiao.importer.core

/**
 * 节次文本解析："1-2节" / "1-2节,3-4节" / "[1-2节]" / ")1-2节" / "5节" / "1-2"
 * 新版正方可能出现 "1-2节,3-4节" 这类合并写法，需要拆分/合并为连续段。
 */
object NodeTextParser {

    /** 返回每段 (startNode, endNode)。优先匹配 "X-Y节"，避免把 "1-16周" 误当节次 */
    fun parsePairs(text: String): List<Pair<Int, Int>> {
        if (text.isBlank()) return emptyList()
        // 统一各种连字符/波浪号（输入法与网页常见：‐‑‒–—―－～），否则 "3–4" 会被拆成孤立数字
        val normalized = text.replace(Regex("[\\u2010-\\u2015\\uFF0D~～]"), "-")
        // 先剥离周次信息，避免 "1-16周" 被当作节次区间
        val s = normalized
            .replace(Regex("\\d+\\s*-\\s*\\d+\\s*周"), " ")
            .replace(Regex("\\d+\\s*周"), " ")
        val pairs = mutableListOf<Pair<Int, Int>>()
        // 1) "1-2节" 显式节次区间
        Regex("(\\d+)\\s*-\\s*(\\d+)\\s*节").findAll(s).forEach { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            pairs.add(minOf(a, b) to maxOf(a, b))
        }
        if (pairs.isNotEmpty()) return pairs
        // 2) "5节" 单节
        Regex("(\\d+)\\s*节").findAll(s).forEach { m ->
            val n = m.groupValues[1].toInt(); pairs.add(n to n)
        }
        if (pairs.isNotEmpty()) return pairs
        // 3) 裸区间（(?!\d) 防回溯把 "1-16周" 截成 "1-1"，(?!周) 排除周次）
        Regex("(\\d+)\\s*-\\s*(\\d+)(?!\\d)(?!周)").findAll(s).forEach { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            pairs.add(minOf(a, b) to maxOf(a, b))
        }
        if (pairs.isNotEmpty()) return pairs
        // 4) 孤立数字（排除紧跟"周"的）
        Regex("(\\d+)(?!\\d)(?!周)").findAll(s).forEach { m ->
            val n = m.groupValues[1].toInt(); pairs.add(n to n)
        }
        return pairs
    }

    /**
     * 表格行/列头是否为纯节次标签（"第一节" "第1,2节" "1-2节"）。
     * 课程内容常含 "…[3-4节]"，不能用 contains("节") 判断，否则会整格漏掉。
     */
    fun isNodeLabel(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 8 || !t.endsWith("节")) return false
        val core = t.removePrefix("第").removeSuffix("节")
            .replace(",", "").replace("，", "").replace("、", "")
            .replace(" ", "").replace("　", "")
        if (core.isEmpty()) return false
        return core.all { it.isDigit() || it == '-' || "一二三四五六七八九十".contains(it) }
    }

    /** 多段节次：连续的合并，断开的拆成多个区间 */
    fun mergeContiguous(pairs: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (pairs.isEmpty()) return emptyList()
        val sorted = pairs.map { minOf(it.first, it.second)..maxOf(it.first, it.second) }
            .sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        var cur = sorted.first()
        for (r in sorted.drop(1)) {
            if (r.first <= cur.last + 1) {
                cur = cur.first..maxOf(cur.last, r.last)
            } else {
                merged.add(cur)
                cur = r
            }
        }
        merged.add(cur)
        return merged.map { it.first to it.last }
    }

    /** 中文节数："第三节" → 3（用于正方旧版表头） */
    fun parseChineseOrDigit(text: String): Int? {
        val digit = Regex("\\d+").find(text)?.value?.toIntOrNull()
        if (digit != null) return digit
        val cn = "一二三四五六七八九十"
        val m = Regex("([一二三四五六七八九十]+)").find(text)?.groupValues?.get(1) ?: return null
        return parseChineseNumeral(m)
    }

    fun parseChineseNumeral(s: String): Int? {
        if (s.isEmpty()) return null
        val digits = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (s == "十") return 10
        if (s.length == 1) return digits[s[0]]
        if (s.startsWith("十")) return 10 + (digits[s[1]] ?: return null)
        val tenIdx = s.indexOf('十')
        if (tenIdx < 0) return null
        val tens = (digits[s[0]] ?: return null) * 10
        val rest = if (tenIdx + 1 < s.length) digits[s[tenIdx + 1]] ?: 0 else 0
        return tens + rest
    }
}
