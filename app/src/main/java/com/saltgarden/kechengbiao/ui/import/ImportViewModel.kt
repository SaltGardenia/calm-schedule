package com.saltgarden.kechengbiao.ui.import

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saltgarden.kechengbiao.KcbApplication
import com.saltgarden.kechengbiao.data.CourseEntity
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.importer.core.SchoolHttpClient
import com.saltgarden.kechengbiao.importer.generic.GenericHtmlParser
import com.saltgarden.kechengbiao.importer.hfut.CaptchaOcr
import com.saltgarden.kechengbiao.importer.hfut.HfutClient
import com.saltgarden.kechengbiao.importer.model.ImportCourse
import com.saltgarden.kechengbiao.importer.model.ParsedSchedule
import com.saltgarden.kechengbiao.importer.model.SchoolSystem
import com.saltgarden.kechengbiao.importer.qz.QzClient
import com.saltgarden.kechengbiao.importer.qz.QzParser
import com.saltgarden.kechengbiao.importer.text.JsonIO
import com.saltgarden.kechengbiao.importer.text.TextImportParser
import com.saltgarden.kechengbiao.importer.zf.ZfNewClient
import com.saltgarden.kechengbiao.importer.zf.ZfOldClient
import com.saltgarden.kechengbiao.ui.theme.CourseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Month

enum class ImportStep { Choose, Login, Preview }

data class ImportUiState(
    val step: ImportStep = ImportStep.Choose,
    val system: SchoolSystem = SchoolSystem.ZF_NEW,
    val host: String = "",
    val needsCaptcha: Boolean = false,
    val captchaBitmap: android.graphics.Bitmap? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val parsed: ParsedSchedule? = null,
    val selected: Set<Int> = emptySet(),
    /** 新版正方：学年与学期 */
    val xnm: String = defaultXnm().toString(),
    val xqmTerm: Int = defaultXqmTerm(),
    /** 合肥工业大学原生登录弹窗 */
    val hfutLoginVisible: Boolean = false,
    val hfutBusy: Boolean = false,
    val hfutNeedCaptcha: Boolean = false,
    val hfutCaptcha: android.graphics.Bitmap? = null,
    val hfutCaptchaText: String = "",
    val hfutError: String? = null,
) {
    companion object {
        fun defaultXnm(): Int {
            val now = LocalDate.now()
            return when {
                now.month >= Month.AUGUST -> now.year
                now.month == Month.JANUARY -> now.year - 1
                else -> now.year - 1
            }
        }

        fun defaultXqmTerm(): Int {
            val now = LocalDate.now()
            return when {
                now.month >= Month.AUGUST -> 1
                now.month == Month.JANUARY -> 1
                else -> 2
            }
        }
    }
}

/** 导入流程编排：选择通道 → （登录/抓取）→ 解析预览 → 入库 */
class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as KcbApplication).container
    private val http = SchoolHttpClient()

    private var zfNew: ZfNewClient? = null
    private var zfOld: ZfOldClient? = null
    private var qz: QzClient? = null

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun selectSystem(s: SchoolSystem) {
        _state.update { it.copy(system = s, error = null, parsed = null, step = ImportStep.Choose) }
    }

    fun updateHost(h: String) = _state.update { it.copy(host = h) }

    fun updateXnm(v: String) = _state.update { it.copy(xnm = v.filter { c -> c.isDigit() }.take(4)) }

    fun updateXqmTerm(term: Int) = _state.update { it.copy(xqmTerm = term.coerceIn(1, 3)) }

    fun backToChoose() = _state.update {
        it.copy(step = ImportStep.Choose, parsed = null, error = null, busy = false, captchaBitmap = null)
    }

    /** 进入登录页：拉取验证码/登录参数 */
    fun beginLogin() = viewModelScope.launch(Dispatchers.IO) {
        val host = normalizeHost(_state.value.host)
        if (host.isBlank()) {
            _state.update { it.copy(error = "请输入教务系统地址，如 jw.xxx.edu.cn") }
            return@launch
        }
        _state.update { it.copy(busy = true, error = null, host = host) }
        try {
            when (_state.value.system) {
                SchoolSystem.ZF_NEW -> {
                    val client = ZfNewClient(http, host)
                    zfNew = client
                    val (_, needCaptcha) = client.fetchLoginInfo()
                    val bmp = if (needCaptcha) decodeCaptcha(client.fetchCaptcha()) else null
                    _state.update {
                        it.copy(step = ImportStep.Login, needsCaptcha = needCaptcha, captchaBitmap = bmp, busy = false)
                    }
                }
                SchoolSystem.ZF_OLD -> {
                    val client = ZfOldClient(http, host)
                    zfOld = client
                    val bmp = decodeCaptcha(client.fetchCaptcha())
                    _state.update {
                        it.copy(step = ImportStep.Login, needsCaptcha = true, captchaBitmap = bmp, busy = false)
                    }
                }
                SchoolSystem.QZ -> {
                    val client = QzClient(http, host)
                    qz = client
                    val bmp = decodeCaptcha(client.fetchCaptcha())
                    _state.update {
                        it.copy(step = ImportStep.Login, needsCaptcha = true, captchaBitmap = bmp, busy = false)
                    }
                }
                SchoolSystem.WEB -> Unit // 由界面启动 WebView
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = friendly(e, "无法连接教务系统")) }
        }
    }

    fun refreshCaptcha() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val bmp = when (_state.value.system) {
                SchoolSystem.ZF_NEW -> zfNew?.let { decodeCaptcha(it.fetchCaptcha()) }
                SchoolSystem.ZF_OLD -> zfOld?.let { decodeCaptcha(it.fetchCaptcha()) }
                SchoolSystem.QZ -> qz?.let { decodeCaptcha(it.fetchCaptcha()) }
                SchoolSystem.WEB -> null
            }
            if (bmp != null) _state.update { it.copy(captchaBitmap = bmp, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(error = friendly(e, "验证码获取失败")) }
        }
    }

    fun submitLogin(user: String, password: String, captcha: String) =
        viewModelScope.launch(Dispatchers.IO) {
            if (user.isBlank() || password.isBlank()) {
                _state.update { it.copy(error = "请输入账号和密码") }
                return@launch
            }
            _state.update { it.copy(busy = true, error = null) }
            try {
                val s = _state.value
                val courses: List<ImportCourse> = when (s.system) {
                    SchoolSystem.ZF_NEW -> {
                        val client = zfNew ?: throw IllegalStateException("请先返回选择学校系统")
                        when (val r = client.login(user, password, captcha)) {
                            is ZfNewClient.LoginResult.Failure -> throw RuntimeException(r.message)
                            ZfNewClient.LoginResult.Success -> Unit
                        }
                        val xqm = ZfNewClient.xqmOf(s.xqmTerm)
                        client.parseSchedule(client.fetchScheduleJson(s.xnm, xqm))
                    }
                    SchoolSystem.ZF_OLD -> {
                        val client = zfOld ?: throw IllegalStateException("请先返回选择学校系统")
                        when (val r = client.login(user, password, captcha)) {
                            is ZfOldClient.LoginResult.Failure -> throw RuntimeException(r.message)
                            ZfOldClient.LoginResult.Success -> Unit
                        }
                        com.saltgarden.kechengbiao.importer.zf.ZfOldParser.parse(
                            client.fetchSchedulePage(user, null, null).html
                        )
                    }
                    SchoolSystem.QZ -> {
                        val client = qz ?: throw IllegalStateException("请先返回选择学校系统")
                        when (val r = client.login(user, password, captcha)) {
                            is QzClient.LoginResult.Failure -> throw RuntimeException(r.message)
                            QzClient.LoginResult.Success -> Unit
                        }
                        QzParser.parse(client.fetchScheduleHtml())
                    }
                    SchoolSystem.WEB -> emptyList()
                }
                if (courses.isEmpty()) throw RuntimeException("未解析到课程：该校版式可能暂未适配，请尝试网页登录导入")
                setParsed(ParsedSchedule(courses, s.system.label, s.system.id))
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = friendly(e, "导入失败")) }
                // 登录失败一般伴随验证码更换
                if (_state.value.needsCaptcha) refreshCaptcha()
            }
        }

    /** WebView 抓取的 HTML 回调 */
    fun importHtml(html: String) = viewModelScope.launch(Dispatchers.Default) {
        _state.update { it.copy(busy = true, error = null) }
        try {
            val parsed = GenericHtmlParser.autoParse(html)
            if (parsed == null) {
                _state.update { it.copy(busy = false, error = "未能从页面识别出课表（请先在网页中打开课表页再抓取）") }
            } else {
                setParsed(parsed)
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = friendly(e, "解析失败")) }
        }
    }

    /** 粘贴文本 / 文件导入 */
    fun importText(text: String) = viewModelScope.launch(Dispatchers.Default) {
        _state.update { it.copy(busy = true, error = null) }
        rememberRawJson(text)
        try {
            val parsed = TextImportParser.parse(text)
            if (parsed == null) {
                _state.update { it.copy(busy = false, error = "无法识别文本格式：支持标准 JSON / WakeUp 兼容 CSV / 竖线行格式") }
            } else {
                setParsed(parsed)
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = friendly(e, "解析失败")) }
        }
    }

    private fun setParsed(parsed: ParsedSchedule) {
        _state.update {
            it.copy(
                step = ImportStep.Preview,
                busy = false,
                error = null,
                parsed = parsed,
                selected = parsed.courses.indices.toSet(),
                hfutLoginVisible = false,
                hfutBusy = false,
                hfutError = null,
            )
        }
    }

    // ---- 合肥工业大学：原生登录 + 全自动抓取 ----

    private var hfutClient: HfutClient? = null

    /** 脱敏调试日志（步骤/长度/状态码），便于排查登录问题；不记录账号密码 */
    private fun hfutLog(msg: String) {
        runCatching {
            getApplication<Application>().openFileOutput("login_debug.log", Application.MODE_APPEND)
                .use { it.write("[${System.currentTimeMillis()}] $msg\n".toByteArray()) }
        }
    }

    private fun hfutClientInstance(): HfutClient =
        hfutClient ?: HfutClient(::hfutLog).also { hfutClient = it }

    /** 打开原生登录弹窗：探测是否需要验证码，并自动识别验证码 */
    fun openHfutLogin() = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(hfutLoginVisible = true, hfutBusy = true, hfutError = null) }
        try {
            val client = hfutClientInstance()
            val init = client.checkInit()
            val bmp = if (init.needCaptcha) client.fetchCaptcha() else null
            val ocr = if (bmp != null) withContext(Dispatchers.Default) {
                CaptchaOcr.recognize(getApplication(), bmp)
            } else ""
            _state.update {
                it.copy(
                    hfutBusy = false,
                    hfutNeedCaptcha = init.needCaptcha,
                    hfutCaptcha = bmp,
                    hfutCaptchaText = ocr,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(hfutBusy = false, hfutError = friendly(e, "无法连接统一认证"))
            }
        }
    }

    /** 刷新验证码并重新识别 */
    fun refreshHfutCaptcha() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val client = hfutClientInstance()
            val bmp = client.fetchCaptcha()
            val ocr = if (bmp != null) withContext(Dispatchers.Default) {
                CaptchaOcr.recognize(getApplication(), bmp)
            } else ""
            _state.update { it.copy(hfutCaptcha = bmp, hfutCaptchaText = ocr, hfutError = null) }
        } catch (e: Exception) {
            _state.update { it.copy(hfutError = friendly(e, "验证码获取失败")) }
        }
    }

    fun dismissHfutLogin() = _state.update {
        it.copy(hfutLoginVisible = false, hfutError = null)
    }

    /** 登录并自动抓取解析课表；成功后直接进入预览 */
    fun submitHfutLogin(
        user: String,
        password: String,
        captcha: String,
        remember: Boolean = true,
    ) = viewModelScope.launch(Dispatchers.IO) {
        if (user.isBlank() || password.isBlank()) {
            _state.update { it.copy(hfutError = "请输入学号与密码") }
            return@launch
        }
        _state.update { it.copy(hfutBusy = true, hfutError = null) }
        try {
            val client = hfutClientInstance()
            val parsed = client.loginAndFetch(user.trim(), password, captcha.trim())
            if (remember) {
                container.settings.setHfutCredentials(user.trim(), password)
            } else {
                container.settings.clearHfutCredentials()
            }
            _state.update { it.copy(hfutBusy = false) }
            setParsed(parsed)
        } catch (e: Exception) {
            _state.update { it.copy(hfutBusy = false, hfutError = friendly(e, "登录失败")) }
            // 验证码一般一次性，失败后自动刷新
            if (_state.value.hfutNeedCaptcha) refreshHfutCaptcha()
        }
    }

    /** 供登录弹窗预填上次账号密码 */
    suspend fun savedHfutCredentials(): Pair<String, String> =
        container.settings.hfutUser.first() to container.settings.hfutPassword.first()

    fun toggle(index: Int) = _state.update {
        val sel = it.selected.toMutableSet()
        if (!sel.add(index)) sel.remove(index)
        it.copy(selected = sel)
    }

    fun selectAll(selected: Boolean) = _state.update {
        val p = it.parsed ?: return@update it
        it.copy(selected = if (selected) p.courses.indices.toSet() else emptySet())
    }

    /** 确认导入（replace=true 覆盖当前课表）；成功后回调 onImported（失败停在原地显示错误） */
    fun confirmImport(replace: Boolean, onImported: (() -> Unit)? = null) = viewModelScope.launch(Dispatchers.IO) {
        val s = _state.value
        val parsed = s.parsed ?: return@launch
        // 直接读 DataStore 首个值：stateIn(WhileSubscribed) 在无人订阅时会一直是初值 0
        val tableId = container.settings.activeTableId.first()
        if (tableId <= 0L) {
            _state.update { it.copy(error = "没有可用课表") }
            return@launch
        }
        _state.update { it.copy(busy = true, error = null) }
        try {
            val colorByName = mutableMapOf<String, Int>()
            val existing = container.repository.listCourses(tableId)
            existing.forEach { colorByName.putIfAbsent(it.name, it.colorIndex) }

            val entities = parsed.courses
                .filterIndexed { i, _ -> i in s.selected }
                .map { c ->
                    CourseEntity(
                        tableId = tableId,
                        name = c.name,
                        teacher = c.teacher,
                        room = c.room,
                        day = c.day,
                        startNode = c.startNode,
                        endNode = c.endNode,
                        weeks = WeekCalculator.weeksToCsv(c.weeks),
                        colorIndex = colorByName.getOrPut(c.name) {
                            CourseColors.nextIndex(colorByName.values.toList())
                        },
                        source = parsed.systemId.ifBlank { CourseEntity.SOURCE_MANUAL },
                    )
                }
            // 单周视图（教务"第 N 周"页面）：只按该周修正课程与教室，不动其他周。
            // 教务的"全部周次"汇总视图给的是默认教室，按周视图才是当周真实教室（可能不同）。
            val weekView = parsed.singleWeek
            val weekFixed = if (weekView != null) {
                mergeWeekView(tableId, weekView, entities, existing)
            } else {
                if (replace) container.repository.importReplace(tableId, entities)
                else container.repository.importMerge(tableId, entities)
                entities.size
            }

            // 节数按导入实际情况初始化 = 导入后全部课程的最晚结束节次；
            // 作息：来源页面自带学校真实作息时（如金智 EAMS）写入完整作息 CSV，
            // 否则在现有作息上扩展到覆盖最晚节次（避免兜底网格"时间倒流"）
            container.repository.getTable(tableId)?.let { t ->
                val latestNode = container.repository.listCourses(tableId)
                    .maxOfOrNull { it.endNode } ?: 0
                if (latestNode > 0) {
                    val schoolTimes = parsed.nodeTimes
                    val csv = when {
                        schoolTimes != null ->
                            WeekCalculator.ensureNodeTimes(
                                schoolTimes.joinToString(",") { "${it.first}-${it.second}" },
                                latestNode,
                            )
                        else -> WeekCalculator.ensureNodeTimes(
                            t.nodeTimes.ifBlank { WeekCalculator.defaultNodeTimes() },
                            latestNode,
                        )
                    }.joinToString(",") { "${it.first}-${it.second}" }
                    container.repository.updateTable(t.copy(nodeCount = latestNode, nodeTimes = csv))
                }
            }

            // JSON 导入若带开学日期/作息，则同步课表设置
            if (parsed.systemId == "json") {
                JsonIO.parse(rawJsonForExport ?: return@launch)?.let { dto ->
                    JsonIO.termStartOf(dto)?.let { date ->
                        container.repository.getTable(tableId)?.let { t ->
                            container.repository.updateTable(t.copy(termStartDate = date.toEpochMilliCompat()))
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                val msg = if (weekView != null) {
                    "已按第 $weekView 周更新 $weekFixed 处课程（其他周不变）"
                } else {
                    "已导入 $weekFixed 条课程安排"
                }
                android.widget.Toast.makeText(
                    getApplication(), msg, android.widget.Toast.LENGTH_SHORT
                ).show()
                _state.update {
                    it.copy(step = ImportStep.Choose, parsed = null, selected = emptySet(), busy = false, error = null)
                }
                onImported?.invoke()
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = friendly(e, "写入数据库失败")) }
        }
    }

    /**
     * 按"第 N 周"视图修正课程：对每条抓取课程，找到同课名+同星期+同节次的现有课程，
     * 若该周已包含且教室一致则跳过；否则把该周从原课程周次中拆出，单独按当周教室建一条。
     * 不删除任何其他周的数据。返回实际修正/新增的条数。
     */
    private suspend fun mergeWeekView(
        tableId: Long,
        week: Int,
        incoming: List<CourseEntity>,
        existing: List<CourseEntity>,
    ): Int {
        var changed = 0
        val current = existing.toMutableList()
        for (c in incoming) {
            val same = current.filter {
                it.name == c.name && it.day == c.day &&
                    it.startNode == c.startNode && it.endNode == c.endNode
            }
            // 该周已与抓取值一致 → 无需处理
            if (same.any { it.room == c.room && WeekCalculator.parseWeeks(it.weeks).contains(week) }) continue

            val covering = same.firstOrNull { WeekCalculator.parseWeeks(it.weeks).contains(week) }
            if (covering != null) {
                val rest = WeekCalculator.parseWeeks(covering.weeks) - week
                if (rest.isEmpty()) {
                    container.repository.deleteCourse(covering)
                    current.remove(covering)
                } else {
                    val updated = covering.copy(weeks = WeekCalculator.weeksToCsv(rest))
                    container.repository.updateCourse(updated)
                    current[current.indexOf(covering)] = updated
                }
            }
            val single = c.copy(weeks = week.toString())
            container.repository.insertCourse(single)
            current += single
            changed++
        }
        return changed
    }

    // JSON 导入的原文（用于读取开学日期等元数据）；importText 时记录
    private var rawJsonForExport: String? = null

    fun rememberRawJson(text: String) {
        if (JsonIO.parse(text) != null) rawJsonForExport = text
    }

    private fun decodeCaptcha(bytes: ByteArray) =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun normalizeHost(raw: String): String {
        var h = raw.trim()
        if (h.isBlank()) return h
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
        return h.trimEnd('/')
    }

    private fun friendly(e: Exception, fallback: String): String =
        e.message?.takeIf { it.isNotBlank() } ?: e.toString().take(120).let { fallback }

    private fun LocalDate.toEpochMilliCompat(): Long =
        atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
