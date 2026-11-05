package com.saltgarden.kechengbiao.importer.schools

/**
 * 支持导入的学校注册表：每所学校单独适配（登录入口 + 操作步骤）。
 * 新增学校时在此添加一条配置即可，无需改动导入界面。
 */
data class School(
    val id: String,
    val name: String,
    /** 登录入口（含统一认证 / WebVPN 跳转） */
    val url: String,
    /** 入口说明（卡片副标题） */
    val entrance: String,
    /** 操作步骤提示（按顺序展示给用户） */
    val steps: List<String>,
)

object SchoolRegistry {

    val schools: List<School> = listOf(
        School(
            id = "hfut",
            name = "合肥工业大学",
            url = "https://webvpn.hfut.edu.cn/http/77726476706e69737468656265737421" +
                "f3f652d22f367d44300d8db9d6562d/cas/login" +
                "?service=https%3A%2F%2Fwebvpn.hfut.edu.cn%2Flogin%3Fcas_login%3Dtrue",
            entrance = "WebVPN 统一身份认证",
            steps = listOf(
                "登录统一身份认证（WebVPN）",
                "打开「信息门户」",
                "进入「教务系统 - 学生端」",
                "打开「我的课表」",
                "点击底部「抓取本页课表」完成导入",
            ),
        ),
    )

    fun byId(id: String): School? = schools.firstOrNull { it.id == id }
}
