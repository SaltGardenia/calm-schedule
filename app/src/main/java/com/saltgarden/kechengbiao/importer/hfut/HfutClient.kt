package com.saltgarden.kechengbiao.importer.hfut

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.saltgarden.kechengbiao.importer.eams.EamsJsonParser
import com.saltgarden.kechengbiao.importer.eams.EamsParser
import com.saltgarden.kechengbiao.importer.model.ImportException
import com.saltgarden.kechengbiao.importer.model.ParsedSchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 合肥工业大学原生导入（同 HFUT-Schedule 的深度适配）：
 *  CAS 统一认证（WebVPN 通道）登录 → 走 EAMS 的 JSON 接口取逐周课表，
 *  不再依赖 WebView 页面解析，因此能拿到"每周"的真实教室（如第 10 周 318、其余周 317）。
 *
 * 登录字段与接口均来自对该校站点结构的实测：
 *  - GET  .../cas/checkInitParams            → Set-Cookie LOGIN_FLAVORING（AES 密钥）+ needCaptcha
 *  - GET  .../cas/login?service=...          → execution / JSESSIONID
 *  - GET  .../cas/vercode                    → 验证码图片
 *  - POST .../cas/login (username/password/execution/_eventId/capcha)
 *  - GET  .../eams5-student/neusoft-sso/login → 建立教务会话
 *  - GET  for-std/course-table[/info/{sid}]   → studentId / bizTypeId / 作息
 *  - GET  for-std/course-table/get-data       → lessonIds
 *  - POST ws/schedule-table/datum             → 逐周课表 JSON
 */
class HfutClient {

    companion object {
        private const val WEBVPN = "https://webvpn.hfut.edu.cn"
        /** CAS 通道 token（登录页用） */
        private const val CAS_PATH = "/http/77726476706e69737468656265737421f3f652d22f367d44300d8db9d6562d"
        /** 教务系统通道 token */
        private const val EAMS_PATH = "/http/77726476706e69737468656265737421faef469034247d1e760e9cb8d6502720ede479"
        private const val SERVICE = "http://jxglstu.hfut.edu.cn/eams5-student/neusoft-sso/login"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    /** 进程内 Cookie（CAS + WebVPN + 教务共用） */
    private val cookies = linkedMapOf<String, Cookie>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
            cookieList.forEach { cookies[it.name] = it }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.toList()
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val casBase get() = WEBVPN + CAS_PATH
    private val eamsBase get() = WEBVPN + EAMS_PATH + "/eams5-student"

    /** 登录前探测：是否需要验证码；同时拿到 AES 密钥 */
    data class InitInfo(val needCaptcha: Boolean)

    @Serializable
    private data class InitParams(val needCaptcha: Boolean = false)

    fun checkInit(): InitInfo {
        val url = "$casBase/cas/checkInitParams"
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        val body = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code !in 200..399) {
                throw ImportException("无法连接合肥工业大学统一认证（HTTP ${resp.code}）")
            }
            resp.body?.string().orEmpty()
        }
        val need = runCatching {
            json.decodeFromString(InitParams.serializer(), body).needCaptcha
        }.getOrElse { body.contains("\"needCaptcha\":true") }
        return InitInfo(needCaptcha = need)
    }

    /** 验证码图片（调用前需已完成 checkInit） */
    fun fetchCaptcha(): Bitmap? {
        val url = "$casBase/cas/vercode?timestamp=${System.currentTimeMillis()}"
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        val bytes = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.bytes()
        } ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    /** 取登录表单 execution 值 */
    private fun fetchExecution(): String {
        val url = "$casBase/cas/login?service=${enc(SERVICE)}"
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        val html = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val m = Regex("""name=["']execution["'][^>]*value=["']([^"']+)["']""").find(html)
            ?: Regex("""value=["']([^"']+)["'][^>]*name=["']execution["']""").find(html)
        return m?.groupValues?.get(1) ?: "e1s1"
    }

    /** AES/ECB 加密密码（与教务登录页一致） */
    private fun encryptPassword(password: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keyBytes = key.toByteArray().copyOf(16)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        return Base64.encodeToString(cipher.doFinal(password.toByteArray()), Base64.NO_WRAP)
    }

    /**
     * 登录并抓取课表。
     * @param captcha 验证码（needCaptcha=false 时忽略）
     */
    fun loginAndFetch(username: String, password: String, captcha: String): ParsedSchedule {
        // 1) AES 密钥来自 LOGIN_FLAVORING cookie
        checkInit()
        val key = cookies["LOGIN_FLAVORING"]?.value
            ?: throw ImportException("未获取到登录密钥，请稍后重试")

        // 2) 登录
        val execution = fetchExecution()
        val form = FormBody.Builder()
            .add("username", username)
            .add("password", encryptPassword(password, key))
            .add("execution", execution)
            .add("_eventId", "submit")
            .add("capcha", captcha)
            .build()
        val loginUrl = "$casBase/cas/login?service=${enc(SERVICE)}"
        http.newCall(
            Request.Builder().url(loginUrl).header("User-Agent", UA).post(form).build()
        ).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (body.contains("用户名或密码错误") || body.contains("密码错误")) {
                throw ImportException("用户名或密码错误")
            }
            if (body.contains("验证码") && body.contains("登录")) {
                // 仍停留在登录页 → 验证码或凭据问题
                throw ImportException("登录未通过：请检查验证码是否正确")
            }
        }

        // 3) 建立教务会话
        runCatching {
            http.newCall(
                Request.Builder().url("$eamsBase/neusoft-sso/login").header("User-Agent", UA).build()
            ).execute().close()
        }

        // 4) 学生 id
        val tableUrl = "$eamsBase/for-std/course-table"
        val (studentId, tableHtml) = http.newCall(
            Request.Builder().url(tableUrl).header("User-Agent", UA).build()
        ).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val html = resp.body?.string().orEmpty()
            val sid = Regex("""course-table/info/(\d+)""").find(finalUrl)?.groupValues?.get(1)
                ?: Regex("""course-table/info/(\d+)""").find(html)?.groupValues?.get(1)
                ?: Regex("""button[^>]*value=["'](\d{4,})["']""").find(html)?.groupValues?.get(1)
                ?: throw ImportException("登录成功但未找到学号信息，请改用网页登录")
            sid to html
        }

        // 5) bizTypeId
        val infoHtml = http.newCall(
            Request.Builder().url("$eamsBase/for-std/course-table/info/$studentId")
                .header("User-Agent", UA).build()
        ).execute().use { it.body?.string().orEmpty() }
        val bizTypeId = Regex("""bizTypeId\s*:\s*(\d+)""").find(infoHtml)?.groupValues?.get(1)
            ?: Regex("""bizTypeId\s*:\s*(\d+)""").find(tableHtml)?.groupValues?.get(1)
            ?: throw ImportException("未找到课表参数（bizTypeId）")

        // 6) 学期 id（页面学期下拉的选中值）
        val semesterId = Regex("""<select[^>]*id=["']allSemesters["'][^>]*>(.*?)</select>""", RegexOption.DOT_MATCHES_ALL)
            .find(tableHtml)?.groupValues?.get(1)
            ?.let { inner ->
                Regex("""<option[^>]*value=["'](\d+)["'][^>]*selected""").find(inner)?.groupValues?.get(1)
                    ?: Regex("""<option[^>]*selected[^>]*value=["'](\d+)["']""").find(inner)?.groupValues?.get(1)
            }
            ?: Regex("""allSemesters.{0,400}?value=["'](\d+)["'][^>]*selected""", RegexOption.DOT_MATCHES_ALL)
                .find(tableHtml)?.groupValues?.get(1)
            ?: throw ImportException("未找到当前学期信息")

        // 7) lessonIds
        val dataUrl = "$eamsBase/for-std/course-table/get-data" +
            "?bizTypeId=$bizTypeId&semesterId=$semesterId&dataId=$studentId"
        val dataJson = http.newCall(
            Request.Builder().url(dataUrl).header("User-Agent", UA).build()
        ).execute().use { it.body?.string().orEmpty() }
        val lessonIds = runCatching {
            json.parseToJsonElement(dataJson).let { el ->
                (el as? JsonObject)?.get("lessonIds") as? JsonArray
            }?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
        }.getOrNull().orEmpty()
        if (lessonIds.isEmpty()) throw ImportException("未获取到课程列表，请确认本学期已选课")

        // 8) 逐周课表 JSON
        val bodyJson = buildJsonObject {
            put("lessonIds", JsonArray(lessonIds.map { JsonPrimitive(it) }))
            put("studentId", studentId.toIntOrNull() ?: 0)
            put("weekIndex", "")
        }.toString()
        val datumJson = http.newCall(
            Request.Builder().url("$eamsBase/ws/schedule-table/datum")
                .header("User-Agent", UA)
                .header("Content-Type", "application/json")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().use { it.body?.string().orEmpty() }
        if (!datumJson.contains("\"scheduleList\"")) {
            throw ImportException("教务系统未返回课表数据，请稍后重试或改用网页登录")
        }

        // 9) 作息（页面网格自带逐节起止）
        val nodeTimes = EamsParser.extractNodeTimes(tableHtml).orEmpty()

        val payload = buildJsonObject {
            put(
                "nodeTimes",
                JsonArray(nodeTimes.map { (s, e) ->
                    buildJsonObject { put("s", s); put("e", e) }
                })
            )
            put("datum", json.parseToJsonElement(datumJson))
        }.toString()

        return EamsJsonParser.parse(payload)
            ?: throw ImportException("课表数据解析失败，请改用网页登录")
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /** 仅用于调试：密码摘要，避免任何地方明文落盘 */
    internal fun digest(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }.take(8)
}
