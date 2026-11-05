package com.saltgarden.kechengbiao.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saltgarden.kechengbiao.KcbApplication
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import com.saltgarden.kechengbiao.data.CourseEntity
import com.saltgarden.kechengbiao.data.EventEntity
import com.saltgarden.kechengbiao.data.TableEntity
import com.saltgarden.kechengbiao.ui.theme.CourseColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as KcbApplication).container
    private val repo = container.repository

    val tables: StateFlow<List<TableEntity>> = repo.observeTables()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val table: StateFlow<TableEntity?> =
        combine(repo.observeTables(), container.settings.activeTableId) { list, activeId ->
            list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val courses: StateFlow<List<CourseEntity>> = table
        .filterNotNull()
        .distinctUntilChangedBy { it.id }
        .flatMapLatest { repo.observeCourses(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今天是第几周（由开学日期推算） */
    val todayWeek: StateFlow<Int> = table
        .map { t -> t?.let { WeekCalculator.currentWeek(it.termStartDate) } ?: 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /** 时间轴自定义显示范围（"HH:mm"，空串=按作息与节数自动计算） */
    val timelineRange: StateFlow<Pair<String, String>> =
        combine(container.settings.timelineStart, container.settings.timelineEnd) { s, e -> s to e }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "" to "")

    /** 全部一次性日程（按日期存储，界面按当前页日期过滤展示） */
    val events: StateFlow<List<EventEntity>> = repo.observeEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val id = repo.ensureBootstrap()
            // 启动即写一次自动备份，保证旧版本升级后也立即有备份
            container.autoBackup.scheduleSave()
            val active = container.settings.activeTableId.first()
            if (active == 0L) container.settings.setActiveTable(id)
        }
    }

    fun selectTable(id: Long) = viewModelScope.launch {
        container.settings.setActiveTable(id)
    }

    fun addCourse(course: CourseEntity) = viewModelScope.launch {
        repo.insertCourse(course)
    }

    fun updateCourse(course: CourseEntity) = viewModelScope.launch {
        repo.updateCourse(course)
    }

    fun deleteCourse(course: CourseEntity) = viewModelScope.launch {
        repo.deleteCourse(course)
    }

    fun addEvent(event: EventEntity) = viewModelScope.launch {
        repo.insertEvent(event)
    }

    fun updateEvent(event: EventEntity) = viewModelScope.launch {
        repo.updateEvent(event)
    }

    fun deleteEvent(event: EventEntity) = viewModelScope.launch {
        repo.deleteEvent(event)
    }

    fun updateTable(table: TableEntity) = viewModelScope.launch {
        repo.updateTable(table)
    }

    fun setTermStartDate(date: LocalDate) = viewModelScope.launch {
        table.value?.let { repo.updateTable(it.copy(termStartDate = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())) }
    }

    fun nextColorIndex(): Int {
        val used = courses.value.map { it.colorIndex }
        return CourseColors.nextIndex(used)
    }
}
