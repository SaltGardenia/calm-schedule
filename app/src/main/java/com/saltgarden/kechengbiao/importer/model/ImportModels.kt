package com.saltgarden.kechengbiao.importer.model

/** 导入过程中的中间课程表示（尚未入库） */
data class ImportCourse(
    val name: String,
    val teacher: String = "",
    val room: String = "",
    /** 1=周一 … 7=周日 */
    val day: Int,
    val startNode: Int,
    val endNode: Int,
    val weeks: Set<Int>,
)

/** 一次解析的结果：课程列表 + 来源描述；部分来源可附带学校真实作息（逐节起止） */
data class ParsedSchedule(
    val courses: List<ImportCourse>,
    val sourceName: String,
    val systemId: String = "",
    /** 学校真实作息（"08:00" to "08:45" 列表）；null = 未知，保留课表现有作息 */
    val nodeTimes: List<Pair<String, String>>? = null,
    /** 抓取页面为"第 N 周"单周视图时给出的周次：只按该周修正课程/教室，不动其他周 */
    val singleWeek: Int? = null,
)

class ImportException(message: String) : Exception(message)

/** 支持的导入通道 */
enum class SchoolSystem(val id: String, val label: String, val hint: String) {
    ZF_NEW("zf_new", "正方教务（新版）", "地址形如 jwgl.xxx.edu.cn/jwglxt"),
    ZF_OLD("zf_old", "正方教务（旧版）", "地址形如 jw.xxx.edu.cn（default2.aspx 登录）"),
    QZ("qz", "强智教务", "地址形如 jw.xxx.edu.cn/jsxsd"),
    WEB("web", "网页登录（通用兜底）", "任意教务系统（含统一认证/金智 EAMS）：登录后自动抓取页面解析，如 HFUT：jxglstu.hfut.edu.cn/eams5-student"),
}
