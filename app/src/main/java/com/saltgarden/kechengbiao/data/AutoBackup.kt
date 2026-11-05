package com.saltgarden.kechengbiao.data

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** 自动备份文件结构（版本化，便于后续升级兼容） */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val savedAt: Long = 0,
    val activeTableId: Long = 0,
    val tables: List<TableEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
)

/**
 * 数据自动备份 / 自动恢复（无用户入口）：
 *  - 每次数据变更后节流写入 files/auto_backup.json（先写临时文件再替换，避免写坏）；
 *  - 启动时若数据库为空且有备份，自动恢复（覆盖升级/迁移异常导致的丢失场景）；
 *  - 文件位于应用私有目录，卸载才会清除，正常升级安装不会丢失。
 */
class AutoBackup(
    private val file: File,
    private val db: AppDatabase,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val lastSaveAt = AtomicLong(0L)

    /** 数据变更后调用；300ms 内的多次变更合并为一次写盘 */
    fun scheduleSave() {
        val now = System.currentTimeMillis()
        val last = lastSaveAt.get()
        if (now - last < 300L) return
        lastSaveAt.set(now)
        scope.launch {
            mutex.withLock {
                runCatching { save() }
            }
        }
    }

    private suspend fun save() {
        val backup = db.withTransaction {
            BackupFile(
                savedAt = System.currentTimeMillis(),
                tables = db.tableDao().listAll(),
                courses = db.courseDao().listAll(),
                events = db.eventDao().listAll(),
            )
        }
        val text = json.encodeToString(BackupFile.serializer(), backup)
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    /**
     * 数据库为空时从备份恢复。
     * @return 恢复出的活动课表 id；无需恢复时返回 null
     */
    suspend fun restoreIfEmpty(): Long? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        if (db.tableDao().first() != null) return@withContext null
        val backup = runCatching {
            json.decodeFromString(BackupFile.serializer(), file.readText())
        }.getOrNull() ?: return@withContext null
        if (backup.tables.isEmpty()) return@withContext null

        db.withTransaction {
            backup.tables.forEach { db.tableDao().insert(it) }
            if (backup.courses.isNotEmpty()) db.courseDao().insertAll(backup.courses)
            if (backup.events.isNotEmpty()) {
                // 旧版备份的日程没有 tableId（=0），统一归到第一张课表
                val fallbackTableId = backup.activeTableId.takeIf { it > 0 }
                    ?: backup.tables.first().id
                db.eventDao().insertAll(
                    backup.events.map { if (it.tableId == 0L) it.copy(tableId = fallbackTableId) else it }
                )
            }
        }
        backup.activeTableId.takeIf { it > 0 } ?: backup.tables.first().id
    }

    /** 供调试/统计：最近备份时间 */
    fun lastBackupAt(): Long = if (file.exists()) file.lastModified() else 0L
}
