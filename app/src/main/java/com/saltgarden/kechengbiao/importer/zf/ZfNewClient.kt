package com.saltgarden.kechengbiao.importer.zf

import com.saltgarden.kechengbiao.importer.core.NodeTextParser
import com.saltgarden.kechengbiao.importer.core.SchoolHttpClient
import com.saltgarden.kechengbiao.importer.core.WeekTextParser
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import com.saltgarden.kechengbiao.importer.model.ImportException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.math.BigInteger
import java.security.KeyFactory
import java.util.Base64
import javax.crypto.Cipher
import java.security.spec.RSAPublicKeySpec

/**
 * 新版正方教务系统（jwglxt）客户端。
 *
 * 接口规格参考 openschoolcn/zfn_api（已在本仓库 docs 中引用）：
 *  - 登录页  GET  /xtgl/login_slogin.html（#csrftoken；input#yzm 表示需要验证码）
 *  - 公钥    GET  /xtgl/login_getPublicKey.html → {modulus, exponent}（base64→hex）
 *  - 验证码  GET  /kaptcha
 *  - 登录    POST /xtgl/login_slogin.html（yhm / mm(RSA) / yzm）
 *  - 课表    POST /kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151（xnm=学年, xqm=学期²×3）
 */
class ZfNewClient(private val http: SchoolHttpClient, private val baseUrl: String) {

    sealed class LoginResult {
        object Success : LoginResult()
        data class Failure(val message: String) : LoginResult()
    }

    private fun u(path: String) = baseUrl.trimEnd('/') + path

    /** 拉取登录页信息：csrfToken 与是否需要验证码 */
    fun fetchLoginInfo(): Pair<String, Boolean> {
        val pageUrl = u("/xtgl/login_slogin.html")
        val doc = Jsoup.parse(http.getText(pageUrl), pageUrl)
        val token = doc.selectFirst("#csrftoken")?.attr("value").orEmpty()
        val needCaptcha = doc.selectFirst("input#yzm") != null
        return token to needCaptcha
    }

    fun fetchCaptcha(): ByteArray = http.getBytes(u("/kaptcha"), u("/xtgl/login_slogin.html"))

    fun encryptPassword(password: String, modulusB64: String, exponentB64: String): String {
        val dec = Base64.getDecoder()
        val modText = String(dec.decode(modulusB64))
        val expText = String(dec.decode(exponentB64))
        // zfn_api：base64 解码后得到的是 hex 文本
        val n = modText.toBigIntegerOrNull(16) ?: BigInteger(1, dec.decode(modulusB64))
        val e = expText.toBigIntegerOrNull(16) ?: BigInteger(1, dec.decode(exponentB64))
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(RSAPublicKeySpec(n, e))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

    fun login(user: String, password: String, captcha: String?): LoginResult {
        val pageUrl = u("/xtgl/login_slogin.html")
        val (token, needCaptcha) = fetchLoginInfo()
        val pk = http.getText(u("/xtgl/login_getPublicKey.html"), pageUrl)
        val pkObj = Json.parseToJsonElement(pk).jsonObject
        val modulus = pkObj["modulus"]?.jsonPrimitive?.content
            ?: throw ImportException("获取登录公钥失败：$baseUrl")
        val exponent = pkObj["exponent"]?.jsonPrimitive?.content
            ?: throw ImportException("获取登录公钥失败：$baseUrl")
        val encrypted = encryptPassword(password, modulus, exponent)

        val form = buildMap {
            put("csrftoken", token)
            put("yhm", user)
            put("mm", encrypted)
            if (needCaptcha) put("yzm", captcha.orEmpty())
        }
        val resp = http.postText(pageUrl, pageUrl, form)
        val doc = Jsoup.parse(resp, pageUrl)
        val tips = doc.selectFirst("p#tips")?.text().orEmpty()
        return when {
            tips.isBlank() && !resp.contains("用户登录") -> LoginResult.Success
            tips.isBlank() -> LoginResult.Failure("登录失败（会话未建立）")
            tips.contains("验证码") -> LoginResult.Failure("验证码错误")
            tips.contains("用户名或密码") -> LoginResult.Failure("用户名或密码错误")
            else -> LoginResult.Failure(tips)
        }
    }

    /**
     * 拉取课表 JSON。
     * @param xnm 学年起始年，如 2025 表示 2025-2026 学年
     * @param xqm 学期码：1→3，2→12，3→27
     */
    fun fetchScheduleJson(xnm: String, xqm: String): String {
        val postUrl = u("/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151")
        val resp = http.postText(
            postUrl,
            u("/kbcx/xskbcx_cxXsgrkbIndex.html?gnmkdm=N2151"),
            mapOf("xnm" to xnm, "xqm" to xqm)
        )
        if (resp.contains("用户登录") || resp.contains("login_slogin")) {
            throw ImportException("登录状态已失效，请重新登录")
        }
        return resp
    }

    fun parseSchedule(json: String): List<ImportCourse> {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (e: Exception) {
            throw ImportException("课表数据不是合法 JSON（可能登录已失效或地址不对）")
        }
        val kbList = root["kbList"]?.jsonArray ?: return emptyList()
        val result = mutableListOf<ImportCourse>()
        for (el in kbList) {
            val o = el.jsonObject
            val name = o["kcmc"]?.jsonPrimitive?.content?.trim().orEmpty()
            val teacher = o["xm"]?.jsonPrimitive?.content?.trim().orEmpty()
            val room = o["cdmc"]?.jsonPrimitive?.content?.trim().orEmpty()
            val day = o["xqj"]?.jsonPrimitive?.content?.trim()?.toIntOrNull() ?: continue
            val weeks = WeekTextParser.parse(o["zcd"]?.jsonPrimitive?.content.orEmpty())
            val pairs = NodeTextParser.parsePairs(o["jc"]?.jsonPrimitive?.content.orEmpty())
            val merged = NodeTextParser.mergeContiguous(pairs)
            if (name.isEmpty() || weeks.isEmpty() || merged.isEmpty()) continue
            for ((s, e) in merged) {
                result.add(ImportCourse(name, teacher, room, day, s, e, weeks))
            }
        }
        return result
    }

    companion object {
        /** 学期序号（1/2/3）→ xqm 参数 */
        fun xqmOf(term: Int): String = (term * term * 3).toString()
    }
}
