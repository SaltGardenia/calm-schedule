package com.saltgarden.kechengbiao.importer.zf

import com.saltgarden.kechengbiao.importer.core.SchoolHttpClient
import com.saltgarden.kechengbiao.importer.model.ImportException
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * 旧版正方教务系统（ASP.NET WebForms）客户端。
 *
 * 登录：POST /default2.aspx（__VIEWSTATE/txtUserName/TextBox2/txtSecretCode/RadioButtonList1=学生/Button1）
 * 课表：GET  /xskbcx.aspx?xh=学号 → 取 xnd/xqd/VIEWSTATE/姓名
 *       POST /xskbcx.aspx?xh=学号&xm=姓名(GB2312)&gnmkdm=N121603（__EVENTTARGET=xnd）
 */
class ZfOldClient(private val http: SchoolHttpClient, private val baseUrl: String) {

    sealed class LoginResult {
        object Success : LoginResult()
        data class Failure(val message: String) : LoginResult()
    }

    private fun u(path: String) = baseUrl.trimEnd('/') + path

    fun fetchCaptcha(): ByteArray = http.getBytes(u("/CheckCode.aspx"), u("/default2.aspx"))

    fun login(user: String, password: String, captcha: String?): LoginResult {
        val pageUrl = u("/default2.aspx")
        val pageHtml = http.getText(pageUrl)
        val viewState = Jsoup.parse(pageHtml, pageUrl)
            .selectFirst("input[name=__VIEWSTATE]")?.attr("value").orEmpty()

        val form = mapOf(
            "__VIEWSTATE" to viewState,
            "txtUserName" to user,
            "TextBox2" to password,
            "txtSecretCode" to captcha.orEmpty(),
            "RadioButtonList1" to "学生",
            "Button1" to "",
            "lbLanguage" to "",
            "hidPdrs" to "",
            "hidsc" to "",
        )
        val resp = http.postText(pageUrl, pageUrl, form)
        return when {
            resp.contains("验证码不正确") -> LoginResult.Failure("验证码错误")
            resp.contains("密码错误") || resp.contains("用户名或密码不正确") -> LoginResult.Failure("用户名或密码错误")
            resp.contains("请耐心排队") -> LoginResult.Failure("系统排队中，请稍后再试")
            resp.contains("验证码") && resp.contains("input") -> LoginResult.Failure("验证码错误")
            else -> {
                // 成功后一般跳转到 xs_main.aspx?xh=学号，页面出现欢迎语
                if (resp.contains("欢迎您") || resp.contains("同学，你好") || resp.contains("xs_main.aspx")) {
                    LoginResult.Success
                } else {
                    LoginResult.Failure("登录失败（返回页面无法识别，请确认地址与账号）")
                }
            }
        }
    }

    /** 课表页参数 */
    data class KbPage(val html: String, val viewState: String, val name: String, val xnd: String, val xqd: String)

    /** 获取课表页。xnd=学年（如 2025），xqd=学期（1/2/3）；为空则用服务器默认学期 */
    fun fetchSchedulePage(user: String, xnd: String?, xqd: String?): KbPage {
        val firstUrl = u("/xskbcx.aspx?xh=$user")
        val firstHtml = http.getText(firstUrl, u("/xs_main.aspx?xh=$user"))
        if (firstHtml.contains("用户登录") || firstHtml.contains("default2.aspx")) {
            throw ImportException("登录状态已失效，请重新登录")
        }
        val doc = Jsoup.parse(firstHtml, firstUrl)
        val viewState = doc.selectFirst("input[name=__VIEWSTATE]")?.attr("value").orEmpty()
        val name = Regex("姓名[：:]\\s*([^<\\s　]+)").find(firstHtml)?.groupValues?.get(1).orEmpty()

        // 两个下拉：第 1 个是学年，第 2 个是学期
        val selects = doc.select("select")
        val selXnd = selects.getOrNull(0)
        val selXqd = selects.getOrNull(1)
        val defaultXnd = selXnd?.selectFirst("option[selected]")?.attr("value")
            ?: selXnd?.selectFirst("option")?.attr("value").orEmpty()
        val defaultXqd = selXqd?.selectFirst("option[selected]")?.attr("value")
            ?: selXqd?.selectFirst("option")?.attr("value").orEmpty()

        val useXnd = xnd?.takeIf { it.isNotBlank() } ?: defaultXnd
        val useXqd = xqd?.takeIf { it.isNotBlank() } ?: defaultXqd
        if (useXnd.isBlank() || useXqd.isBlank() || viewState.isBlank()) {
            return KbPage(firstHtml, viewState, name, useXnd, useXqd)
        }

        val nameEnc = if (name.isBlank()) "" else URLEncoder.encode(name, "GB2312")
        val postUrl = u("/xskbcx.aspx?xh=$user&xm=$nameEnc&gnmkdm=N121603")
        val resp = http.postText(
            postUrl, postUrl,
            mapOf(
                "__EVENTTARGET" to "xnd",
                "__EVENTARGUMENT" to "",
                "__VIEWSTATE" to viewState,
                "xnd" to useXnd,
                "xqd" to useXqd,
            )
        )
        if (resp.isBlank() || resp.contains("您本学期课所选学分小于")) {
            return KbPage(firstHtml, viewState, name, useXnd, useXqd)
        }
        val respViewState = Jsoup.parse(resp, postUrl)
            .selectFirst("input[name=__VIEWSTATE]")?.attr("value") ?: viewState
        return KbPage(resp, respViewState, name, useXnd, useXqd)
    }
}
