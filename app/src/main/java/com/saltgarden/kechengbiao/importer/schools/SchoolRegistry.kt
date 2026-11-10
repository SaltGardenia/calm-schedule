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
            entrance = "统一身份认证 · 应用内登录自动导入",
            steps = listOf(
                "输入学号与密码（验证码会自动识别填充）",
                "点击「登录并导入」，自动完成登录与课表抓取",
                "核对识别结果后点「覆盖导入」",
                "如登录失败，可改用网页登录作为兜底",
            ),
        ),
        School(
            id = "imu",
            name = "内蒙古大学",
            url = "https://jwxt.imu.edu.cn/login",
            entrance = "高校教学管理与服务平台（URP）",
            steps = listOf(
                "登录高校教学管理与服务平台",
                "打开「学生课表查询」（信息查询菜单）",
                "确认显示本学期课表",
                "点击底部「抓取本页课表」完成导入",
            ),
        ),
    )

    fun byId(id: String): School? = schools.firstOrNull { it.id == id }
}
