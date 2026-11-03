package com.saltgarden.kechengbiao.importer.core

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** 教务系统 HTTP 客户端：内存 CookieJar + GBK/UTF-8 自动识别 */
class SchoolHttpClient {

    private val cookieStore = linkedMapOf<String, MutableList<Cookie>>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                synchronized(list) {
                    for (c in cookies) {
                        list.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                        list.add(c)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookieStore[url.host].orEmpty().filter { it.matches(url) }
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getText(url: String, referer: String? = null): String =
        execute("GET", url, referer, null).use { readText(it) }

    fun postText(url: String, referer: String? = null, form: Map<String, String>): String =
        execute("POST", url, referer, form).use { readText(it) }

    fun getBytes(url: String, referer: String? = null): ByteArray =
        execute("GET", url, referer, null).use { it.body?.bytes() ?: ByteArray(0) }

    /** 供需要检查状态码/响应头的场景 */
    fun execute(method: String, url: String, referer: String?, form: Map<String, String>?): Response {
        val builder = Request.Builder().url(url).header("User-Agent", DESKTOP_UA)
        referer?.let { builder.header("Referer", it) }
        if (form != null) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            builder.post(body)
        } else {
            builder.get()
        }
        return client.newCall(builder.build()).execute()
    }

    fun readText(response: Response): String {
        val bytes = response.body?.bytes() ?: ByteArray(0)
        val ct = response.header("Content-Type") ?: ""
        val declared = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)
        return decodeHtml(bytes, declared)
    }

    fun clearCookies() = cookieStore.clear()

    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** 老教务常见 GB2312/GBK 页面，按声明或 BOM/乱码探测解码 */
        fun decodeHtml(bytes: ByteArray, declaredCharset: String?): String {
            if (bytes.isEmpty()) return ""
            val head = bytes.decodeToString(0, minOf(bytes.size, 2048))
            val meta = Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
            val name = (declaredCharset ?: meta)?.lowercase()
            return when {
                name == null -> bytes.decodeToString()
                name.contains("gb") || name.contains("2312") -> String(bytes, charset("GB18030"))
                else -> try {
                    String(bytes, charset(name))
                } catch (e: Exception) {
                    bytes.decodeToString()
                }
            }
        }
    }
}
