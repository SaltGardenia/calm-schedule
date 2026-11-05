package com.saltgarden.kechengbiao.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 应用内检查更新（GitHub Releases 渠道）：
 *  1. 查询 api.github.com 最新 Release 的 tag 与 APK 资产；
 *  2. 与当前 versionName 比较语义化版本；
 *  3. 下载到 cacheDir 后通过系统安装器拉起安装。
 *
 * 数据安全：更新只替换应用本体；课表数据在 /data/data 私有目录（Room + 自动备份），
 * 覆盖安装不清理，Room 迁移链负责旧库结构升级，升级后无需重新导入。
 */
object AppUpdater {

    private const val RELEASE_API =
        "https://api.github.com/repos/SaltGardenia/calm-schedule/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    @Serializable
    data class ReleaseDto(
        val tag_name: String = "",
        val body: String = "",
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    data class AssetDto(val name: String = "", val browser_download_url: String = "")

    data class ReleaseInfo(val version: String, val apkUrl: String, val notes: String)

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }.getOrDefault("0.0.0")

    /** 查询最新 Release；无 APK 资产或网络失败返回 null */
    suspend fun checkLatest(): ReleaseInfo? = runCatching {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val dto = json.decodeFromString(
                ReleaseDto.serializer(), resp.body!!.string()
            )
            val apk = dto.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
            ReleaseInfo(
                version = dto.tag_name.removePrefix("v").removePrefix("V"),
                apkUrl = apk.browser_download_url,
                notes = dto.body,
            )
        }
    }.getOrNull()

    /** 语义化版本比较：latest 是否严格大于 current（忽略非数字前后缀） */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    /** 下载 APK 到 cacheDir/update.apk，onProgress 回调 0-100 */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): File {
        val request = Request.Builder().url(url).build()
        val response = http.newCall(request).execute()
        require(response.isSuccessful) { "下载失败：HTTP ${response.code}" }
        val body = response.body ?: throw IllegalStateException("下载失败：空响应")
        val total = body.contentLength().coerceAtLeast(1)
        val out = File(context.cacheDir, "update.apk")
        body.byteStream().use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                var lastPct = -1
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    done += read
                    val pct = (done * 100 / total).toInt()
                    if (pct != lastPct) {
                        lastPct = pct
                        onProgress(pct)
                    }
                }
            }
        }
        return out
    }

    /** 拉起系统安装器（需用户授予"安装未知应用"权限，系统会自行引导） */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        context.startActivity(intent)
    }
}
