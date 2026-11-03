package com.saltgarden.kechengbiao.importer.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 网页登录抓取课表（通用兜底通道，与 WakeUp 的 WebViewLoginFragment 同思路）：
 * 用户在 WebView 中完成教务系统登录（含 CAS/验证码/加密等一切站内逻辑），
 * 打开课表页面后注入 JS 收集主文档 + iframe 的 HTML，回传给导入流程解析。
 */
class WebViewGrabActivity : Activity() {

    private lateinit var webView: WebView
    private var grabbed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val grabBtn = Button(this).apply { text = "抓取本页课表"; setOnClickListener { collectAndFinish() } }
        val doneBtn = Button(this).apply { text = "返回"; setOnClickListener { finish() } }
        bar.addView(grabBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(doneBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(webView)
        root.addView(bar)
        setContentView(root)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = SchoolUa
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // 不再自动抓取：统一身份认证/WebVPN 门户页常含"课程表"等字样，自动探测会误抓。
                // 由用户打开课表页后手动点击"抓取本页课表"。
            }
        }
        webView.addJavascriptInterface(Collector(), "local_obj")

        val url = intent.getStringExtra(EXTRA_URL)
        if (!url.isNullOrBlank()) webView.loadUrl(url)
    }

    private inner class Collector {
        @JavascriptInterface
        fun showSource(html: String) {
            if (grabbed) return
            runOnUiThread {
                grabbed = true
                // 大页面（EAMS/WebVPN 等）HTML 常超过 Intent 1MB 限制：
                // 结果放在进程内共享变量，Intent 只传小页面兜底
                lastHtml = html
                grabbedHtml.value = html
                // 调试用：保留最近一次抓取，便于适配新学校时分析页面结构
                runCatching {
                    openFileOutput("last_grab.html", MODE_PRIVATE)
                        .use { it.write(html.toByteArray()) }
                }
                Toast.makeText(this@WebViewGrabActivity, "已抓取页面，正在解析…", Toast.LENGTH_SHORT).show()
                val data = Intent()
                if (html.length < 200_000) data.putExtra(EXTRA_HTML, html)
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }

    private fun injectCollector() {
        webView.evaluateJavascript(
            """
            (function(){
                function clean(doc){
                    var drop = doc.querySelectorAll('script,style,link,noscript,svg');
                    for (var i = drop.length - 1; i >= 0; i--) { drop[i].parentNode.removeChild(drop[i]); }
                    return doc.documentElement ? doc.documentElement.outerHTML : '';
                }
                var html = clean(document.cloneNode(true));
                var frames = document.querySelectorAll('iframe,frame');
                for (var i = 0; i < frames.length; i++) {
                    try { html += clean(frames[i].contentDocument.cloneNode(true)); } catch (e) {}
                }
                window.local_obj.showSource(html);
            })()
            """.trimIndent(), null
        )
    }

    private fun collectAndFinish() {
        if (grabbed) return
        injectCollector()
        webView.postDelayed({
            if (!grabbed) {
                Toast.makeText(this, "未抓到内容，请确认已打开课表页面后重试", Toast.LENGTH_SHORT).show()
                grabbed = false
            }
        }, 2500)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_HTML = "extra_html"
        const val REQUEST_GRAB = 4001

        /** 抓取结果通道（同进程内简单共享） */
        val grabbedHtml = MutableStateFlow<String?>(null)

        /** 最近一次抓取的 HTML：页面过大时 Intent 无法承载，改从这里取 */
        @Volatile
        var lastHtml: String? = null
            private set

        private const val SchoolUa =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun start(activity: Activity, url: String?) {
            activity.startActivityForResult(
                Intent(activity, WebViewGrabActivity::class.java).putExtra(EXTRA_URL, url),
                REQUEST_GRAB
            )
        }
    }
}
