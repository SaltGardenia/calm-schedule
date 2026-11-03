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
            )
        }
    }

    fun toggle(index: Int) = _state.update {
        val sel = it.selected.toMutableSet()
        if (!sel.add(index)) sel.remove(index)
        it.copy(selected = sel)
    }

    fun selectAll(selected: Boolean) = _state.update {
        val p = it.parsed ?: return@update it
        it.copy(selected = if (selected) p.courses.indices.toSet() else emptySet())
    }

    /** 确认导入（replace=true 覆盖当前课表） */
    fun confirmImport(replace: Boolean) = viewModelScope.launch(Dispatchers.IO) {
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
            if (replace) container.repository.importReplace(tableId, entities)
            else container.repository.importMerge(tableId, entities)

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
                android.widget.Toast.makeText(
                    getApplication(), "已导入 ${entities.size} 条课程安排", android.widget.Toast.LENGTH_SHORT
                ).show()
                _state.update {
                    it.copy(step = ImportStep.Choose, parsed = null, selected = emptySet(), busy = false, error = null)
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = friendly(e, "写入数据库失败")) }
        }
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
