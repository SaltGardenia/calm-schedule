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

    /** 灰度化（与参考实现一致：不二值化、不缩放，避免破坏彩色笔画） */
    private fun preprocess(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) +
                    0.114 * Color.blue(pixel)).toInt().coerceIn(0, 255)
                out.setPixel(x, y, Color.rgb(gray, gray, gray))
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
