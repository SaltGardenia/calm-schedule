package com.saltgarden.kechengbiao.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
    private val keyHfutUser = stringPreferencesKey("hfut_user")
    private val keyHfutPassword = stringPreferencesKey("hfut_password")

    val activeTableId: Flow<Long> = context.dataStore.data.map { it[keyActiveTable] ?: 0L }

    /** 合肥工业大学原生登录：记住的账号密码（仅存本机私有目录，用于下次免输入） */
    val hfutUser: Flow<String> = context.dataStore.data.map { it[keyHfutUser] ?: "" }
    val hfutPassword: Flow<String> = context.dataStore.data.map { it[keyHfutPassword] ?: "" }

    suspend fun setHfutCredentials(user: String, password: String) {
        context.dataStore.edit {
            it[keyHfutUser] = user
            it[keyHfutPassword] = password
        }
    }

    /** 取消"记住账号密码"时清除本机保存的凭据 */
    suspend fun clearHfutCredentials() {
        context.dataStore.edit {
            it.remove(keyHfutUser)
            it.remove(keyHfutPassword)
        }
    }

    /** 时间轴自定义显示范围（"HH:mm"）；空串表示未自定义（按作息与节数自动计算） */
    val timelineStart: Flow<String> = context.dataStore.data.map { it[keyTimelineStart] ?: "" }
    val timelineEnd: Flow<String> = context.dataStore.data.map { it[keyTimelineEnd] ?: "" }

    suspend fun setActiveTable(id: Long) {
        context.dataStore.edit { it[keyActiveTable] = id }
    }

    suspend fun setTimelineRange(start: String, end: String) {
        context.dataStore.edit {
            it[keyTimelineStart] = start
            it[keyTimelineEnd] = end
        }
    }
}
