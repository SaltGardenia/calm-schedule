package com.saltgarden.kechengbiao.importer.hfut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * 验证码识别（合肥工业大学 CAS 登录）：
 * 应用内携带 eng 训练数据（assets/tessdata/eng.traineddata），首次使用时复制到私有目录，
 * 再以单行模式 + 字符白名单识别数字字母组合。识别结果会回填到输入框，用户可手工修正。
 */
object CaptchaOcr {

    private const val WHITELIST = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    @Volatile
    private var ready = false

    private fun ensureModel(context: Context): File? = runCatching {
        val dir = File(context.filesDir, "tessdata").apply { mkdirs() }
        val model = File(dir, "eng.traineddata")
        if (!model.exists() || model.length() < 100_000) {
            context.assets.open("tessdata/eng.traineddata").use { input ->
                model.outputStream().use { output -> input.copyTo(output) }
            }
        }
        model
    }.getOrNull()

    /** 灰度 + 对比度拉伸 + 二值化 + 放大 2 倍：验证码噪点多、字小，直接识别准确率很低 */
    private fun preprocess(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val gray = IntArray(width * height)
        var min = 255
        var max = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val g = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) +
                    0.114 * Color.blue(pixel)).toInt().coerceIn(0, 255)
                gray[y * width + x] = g
                if (g < min) min = g
                if (g > max) max = g
            }
        }
        // 对比度拉伸到 0..255，再以中值阈值二值化
        val span = (max - min).coerceAtLeast(1)
        val out = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val stretched = (gray[y * width + x] - min) * 255 / span
                val v = if (stretched < 128) 0 else 255
                val color = Color.rgb(v, v, v)
                // 2x 放大（Tesseract 对 ~30px 字高识别最好）
                out.setPixel(x * 2, y * 2, color)
                out.setPixel(x * 2 + 1, y * 2, color)
                out.setPixel(x * 2, y * 2 + 1, color)
                out.setPixel(x * 2 + 1, y * 2 + 1, color)
            }
        }
        return out
    }

    /** 识别失败返回空串（不阻塞登录，用户可手填） */
    fun recognize(context: Context, bitmap: Bitmap): String {
        val model = ensureModel(context) ?: return ""
        val api = TessBaseAPI()
        return try {
            if (!api.init(context.filesDir.absolutePath, "eng")) return ""
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
            api.setVariable("tessedit_char_whitelist", WHITELIST)
            api.setImage(preprocess(bitmap))
            (api.utF8Text ?: "").replace(Regex("[^0-9A-Za-z]"), "").take(6)
        } catch (e: Throwable) {
            ""
        } finally {
            runCatching { api.recycle() }
            ready = model.exists()
        }
    }
}
