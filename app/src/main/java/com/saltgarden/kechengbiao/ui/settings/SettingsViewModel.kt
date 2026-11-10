package com.saltgarden.kechengbiao.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saltgarden.kechengbiao.KcbApplication
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.data.CourseEntity
import com.saltgarden.kechengbiao.data.TableEntity
import com.saltgarden.kechengbiao.importer.text.JsonIO
import com.saltgarden.kechengbiao.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as KcbApplication).container
    private val repo = container.repository

    val tables: StateFlow<List<TableEntity>> = repo.observeTables()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val table: StateFlow<TableEntity?> =
        combine(repo.observeTables(), container.settings.activeTableId) { list, activeId ->
            list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 时间轴自定义显示范围（"HH:mm"，空串=自动） */
    val timelineStart: StateFlow<String> = container.settings.timelineStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val timelineEnd: StateFlow<String> = container.settings.timelineEnd
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** 是否显示午休时段（12:00-14:00）；关闭时时间轴折叠该时段。按课表独立设置 */
    val showLunchBreak: StateFlow<Boolean> = table
        .map { t -> t?.showLunchBreak ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTimelineRange(start: String, end: String) = viewModelScope.launch {
        container.settings.setTimelineRange(start, end)
    }

    fun setShowLunchBreak(v: Boolean) = updateTable { it.copy(showLunchBreak = v) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val courses: StateFlow<List<CourseEntity>> = table
        .filterNotNull()
        .distinctUntilChangedBy { it.id }
        .flatMapLatest { repo.observeCourses(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun switchTable(id: Long) = viewModelScope.launch { container.settings.setActiveTable(id) }

    fun addTable() = viewModelScope.launch {
        val id = repo.insertTable(repo.defaultTable().copy(name = "新课表 ${tables.value.size + 1}"))
        container.settings.setActiveTable(id)
    }

    fun deleteTable(id: Long) = viewModelScope.launch {
        if (tables.value.size <= 1) return@launch
        val currentActive = container.settings.activeTableId.first()
        repo.deleteTable(id)
        if (currentActive == id) {
            tables.value.firstOrNull { it.id != id }?.let {
                container.settings.setActiveTable(it.id)
            }
        }
    }

    fun updateTable(transform: (TableEntity) -> TableEntity) = viewModelScope.launch {
        table.value?.let { repo.updateTable(transform(it)) }
    }

    fun renameTable(id: Long, name: String) = viewModelScope.launch {
        tables.value.firstOrNull { it.id == id }?.let {
            repo.updateTable(it.copy(name = name))
        }
    }
    fun setTermStart(date: LocalDate) = updateTable {
        it.copy(termStartDate = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    /** 调整每日节数：不可低于本课表最晚的课程结束节次（导入按实际情况初始化，后续可调大） */
    fun setNodeCount(n: Int) = updateTable { t ->
        val floor = courses.value.maxOfOrNull { it.endNode } ?: 6
        val lower = maxOf(floor, 6)
        t.copy(nodeCount = n.coerceIn(lower, maxOf(lower, 16)))
    }
    fun setMaxWeek(n: Int) = updateTable { it.copy(maxWeek = n.coerceIn(10, 40)) }
    fun setShowWeekend(v: Boolean) = updateTable { it.copy(showWeekend = v) }
    fun setShowOtherWeek(v: Boolean) = updateTable { it.copy(showOtherWeek = v) }
    fun setNodeTimes(csv: String) = updateTable { it.copy(nodeTimes = csv) }

    /** 导出标准 JSON 到用户选择的位置 */
    fun exportJson(uri: Uri, onResult: (Boolean) -> Unit) {
        val t = table.value ?: run { onResult(false); return }
        val list = courses.value
        viewModelScope.launch {
            try {
                val json = JsonIO.export(t, list)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun termStartDate(): LocalDate? = table.value?.let {
        java.time.Instant.ofEpochMilli(it.termStartDate)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }

    // ---- 检查更新（GitHub Releases）----

    data class UpdateUiState(
        val checking: Boolean = false,
        val message: String = "",
        /** -1 = 未在下载；0-100 = 下载进度 */
        val downloadProgress: Int = -1,
        val readyToInstall: Boolean = false,
        /** 发现新版本（按钮应变为「下载并安装」） */
        val updateAvailable: Boolean = false,
        /** 已是最新 */
        val upToDate: Boolean = false,
    )

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    private var pendingApk: java.io.File? = null

    fun checkUpdate() {
        if (_update.value.checking) return
        _update.value = UpdateUiState(checking = true, message = "正在检查更新…")
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val release = AppUpdater.checkLatest()
            when {
                release == null -> _update.value =
                    UpdateUiState(message = "检查失败：无法连接 GitHub，请稍后重试")
                !AppUpdater.isNewer(release.version, AppUpdater.currentVersion(context)) ->
                    _update.value = UpdateUiState(
                        message = "已是最新版本 v${AppUpdater.currentVersion(context)}",
                        upToDate = true,
                    )
                else -> _update.value = UpdateUiState(
                    message = "发现新版本 v${release.version}",
                    updateAvailable = true,
                )
            }
        }
    }

    fun downloadUpdate() {
        if (_update.value.downloadProgress >= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val release = AppUpdater.checkLatest() ?: run {
                _update.value = UpdateUiState(message = "下载失败：无法连接 GitHub")
                return@launch
            }
            _update.value = UpdateUiState(downloadProgress = 0, message = "下载中…")
            runCatching {
                AppUpdater.downloadApk(context, release.apkUrl) { pct ->
                    _update.value = _update.value.copy(downloadProgress = pct)
                }
            }.onSuccess { apk ->
                pendingApk = apk
                _update.value = UpdateUiState(
                    message = "下载完成，点击「安装」完成更新（课表数据不受影响）",
                    readyToInstall = true,
                )
            }.onFailure { e ->
                _update.value = UpdateUiState(message = "下载失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun installUpdate() {
        val apk = pendingApk ?: return
        AppUpdater.install(getApplication(), apk)
    }

    companion object {
        fun defaultNodeTimes(): String = WeekCalculator.defaultNodeTimes()
    }
}
