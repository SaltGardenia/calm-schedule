package com.saltgarden.kechengbiao.importer.qz

import com.saltgarden.kechengbiao.importer.core.SchoolHttpClient
import com.saltgarden.kechengbiao.importer.model.ImportException

/**
 * 强智教务（jsxsd）原生登录客户端 —— 实验性。
 *
 * 许多强智部署的登录密码经过页面 JS 的 encoded 编码；部分老版本接受明文。
 * 若原生登录失败，请改用"网页登录（通用兜底）"通道（与 WakeUp 一致的做法）。
 *
 * 登录：POST /jsxsd/xk/LoginToXk  USER_NAME / PASSWORD / RANDOMCODE
 * 课表：GET  /jsxsd/xskb/xskb_list.do（HTML，#kbtable）
 */
class QzClient(private val http: SchoolHttpClient, private val baseUrl: String) {

    sealed class LoginResult {
        object Success : LoginResult()
        data class Failure(val message: String) : LoginResult()
    }

    private fun u(path: String) = baseUrl.trimEnd('/') + path

    fun fetchCaptcha(): ByteArray = http.getBytes(u("/jsxsd/verifycode.servlet"), u("/jsxsd/xk/LoginToXk"))

    fun login(user: String, password: String, captcha: String?): LoginResult {
        val loginUrl = u("/jsxsd/xk/LoginToXk")
        val resp = http.postText(
            loginUrl, loginUrl,
            mapOf(
                "USER_NAME" to user,
                "PASSWORD" to password,
                "RANDOMCODE" to captcha.orEmpty(),
            )
        )
        return when {
            resp.contains("验证码错误") || resp.contains("验证码不正确") -> LoginResult.Failure("验证码错误")
            resp.contains("密码错误") || resp.contains("用户名或密码") -> LoginResult.Failure("用户名或密码错误")
            resp.isBlank() -> LoginResult.Failure("登录无响应")
            else -> {
                // 成功后跳转框架页 /jsxsd/framework/xmain*.jsp / main
                if (resp.contains("xmain") || resp.contains("framework") || resp.contains("main")
                    || resp.contains("登录成功")
                ) LoginResult.Success
                else LoginResult.Failure("登录失败（该校可能要求密码加密，请改用网页登录导入）")
            }
        }
    }

    fun fetchScheduleHtml(): String {
        val html = http.getText(u("/jsxsd/xskb/xskb_list.do"), u("/jsxsd/framework/xmain.jsp"))
        if (html.contains("登录") && !html.contains("kbtable")) {
            throw ImportException("登录状态已失效，请重新登录")
        }
        if (!html.contains("kbtable")) {
            throw ImportException("未在课表页面找到 #kbtable（可能该校版式不同，请改用网页登录导入）")
        }
        return html
    }
}
