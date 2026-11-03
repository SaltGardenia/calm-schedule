package com.saltgarden.kechengbiao.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 轻量全局偏好（当前课表 id 等） */
class SettingsStore(private val context: Context) {

    private val keyActiveTable = longPreferencesKey("active_table_id")

    val activeTableId: Flow<Long> = context.dataStore.data.map { it[keyActiveTable] ?: 0L }

    suspend fun setActiveTable(id: Long) {
        context.dataStore.edit { it[keyActiveTable] = id }
    }
}
