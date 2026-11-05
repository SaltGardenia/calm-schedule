package com.saltgarden.kechengbiao.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 轻量全局偏好（当前课表 id、时间轴自定义显示范围等） */
class SettingsStore(private val context: Context) {

    private val keyActiveTable = longPreferencesKey("active_table_id")
    private val keyTimelineStart = stringPreferencesKey("timeline_start")
    private val keyTimelineEnd = stringPreferencesKey("timeline_end")
    private val keyHideLunchBreak = booleanPreferencesKey("hide_lunch_break")

    val activeTableId: Flow<Long> = context.dataStore.data.map { it[keyActiveTable] ?: 0L }

    /** 时间轴自定义显示范围（"HH:mm"）；空串表示未自定义（按作息与节数自动计算） */
    val timelineStart: Flow<String> = context.dataStore.data.map { it[keyTimelineStart] ?: "" }
    val timelineEnd: Flow<String> = context.dataStore.data.map { it[keyTimelineEnd] ?: "" }

    /** 折叠午休时段（12:00-14:00 从时间轴中压缩），默认关闭 */
    val hideLunchBreak: Flow<Boolean> = context.dataStore.data.map { it[keyHideLunchBreak] ?: false }

    suspend fun setActiveTable(id: Long) {
        context.dataStore.edit { it[keyActiveTable] = id }
    }

    suspend fun setTimelineRange(start: String, end: String) {
        context.dataStore.edit {
            it[keyTimelineStart] = start
            it[keyTimelineEnd] = end
        }
    }

    suspend fun setHideLunchBreak(v: Boolean) {
        context.dataStore.edit { it[keyHideLunchBreak] = v }
    }
}
