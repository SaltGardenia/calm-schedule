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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

    /** 折叠午休时段（12:00-14:00） */
    val hideLunchBreak: StateFlow<Boolean> = container.settings.hideLunchBreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTimelineRange(start: String, end: String) = viewModelScope.launch {
        container.settings.setTimelineRange(start, end)
    }

    fun setHideLunchBreak(v: Boolean) = viewModelScope.launch {
        container.settings.setHideLunchBreak(v)
    }

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
    fun setNodeCount(n: Int) = updateTable { it.copy(nodeCount = n.coerceIn(6, 16)) }
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

    companion object {
        fun defaultNodeTimes(): String = WeekCalculator.defaultNodeTimes()
    }
}
