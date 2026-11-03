package com.saltgarden.kechengbiao.data

import androidx.room.withTransaction
import com.saltgarden.kechengbiao.core.time.WeekCalculator
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** 课表数据仓库：UI 与导入都经由它读写 */
class TimetableRepository(
    private val db: AppDatabase,
    private val backup: AutoBackup,
) {

    private val tableDao = db.tableDao()
    private val courseDao = db.courseDao()
    private val eventDao = db.eventDao()

    fun observeTables(): Flow<List<TableEntity>> = tableDao.observeAll()

    fun observeTable(id: Long): Flow<TableEntity?> = tableDao.observe(id)

    fun observeCourses(tableId: Long): Flow<List<CourseEntity>> = courseDao.observeByTable(tableId)

    suspend fun getTable(id: Long): TableEntity? = tableDao.get(id)

    suspend fun listCourses(tableId: Long): List<CourseEntity> = courseDao.listByTable(tableId)

    suspend fun insertTable(table: TableEntity): Long = tableDao.insert(table).also { backup.scheduleSave() }

    suspend fun updateTable(table: TableEntity) {
        tableDao.update(table)
        backup.scheduleSave()
    }

    suspend fun deleteTable(id: Long) {
        tableDao.delete(id)
        backup.scheduleSave()
    }

    suspend fun getCourse(id: Long): CourseEntity? = courseDao.get(id)

    suspend fun insertCourse(course: CourseEntity): Long =
        courseDao.insert(course).also { backup.scheduleSave() }

    suspend fun updateCourse(course: CourseEntity) {
        courseDao.update(course)
        backup.scheduleSave()
    }

    suspend fun deleteCourse(course: CourseEntity) {
        courseDao.delete(course)
        backup.scheduleSave()
    }

    /** 一次性日程（无周次重复）：按日期保存 */
    fun observeEvents(): Flow<List<EventEntity>> = eventDao.observeAll()

    suspend fun insertEvent(event: EventEntity): Long =
        eventDao.insert(event).also { backup.scheduleSave() }

    suspend fun updateEvent(event: EventEntity) {
        eventDao.update(event)
        backup.scheduleSave()
    }

    suspend fun deleteEvent(event: EventEntity) {
        eventDao.delete(event)
        backup.scheduleSave()
    }

    /** 覆盖式导入：先清空该课表再写入（与 WakeUp 的 coverImport 行为一致） */
    suspend fun importReplace(tableId: Long, courses: List<CourseEntity>) {
        db.withTransaction {
            courseDao.deleteByTable(tableId)
            courseDao.insertAll(courses.map { it.copy(id = 0, tableId = tableId) })
        }
        backup.scheduleSave()
    }

    /** 追加式导入：与既有课程同天/同节次/同周次重叠超过半数视为重复，跳过 */
    suspend fun importMerge(tableId: Long, courses: List<CourseEntity>) {
        val snapshot = courseDao.listByTable(tableId)
        val toInsert = courses.filter { new -> snapshot.none { overlaps(it, new) } }
            .map { it.copy(id = 0, tableId = tableId) }
        db.withTransaction { courseDao.insertAll(toInsert) }
        backup.scheduleSave()
    }

    private fun overlaps(a: CourseEntity, b: CourseEntity): Boolean {
        if (a.name != b.name || a.day != b.day) return false
        val nodeOverlap = a.startNode <= b.endNode && b.startNode <= a.endNode
        if (!nodeOverlap) return false
        val wa = WeekCalculator.parseWeeks(a.weeks)
        val wb = WeekCalculator.parseWeeks(b.weeks)
        val common = wa.intersect(wb).size
        return common * 2 >= minOf(wa.size, wb.size).coerceAtLeast(1)
    }

    /** 首次启动：确保至少有一张课表；返回可用的 tableId */
    suspend fun ensureBootstrap(): Long {
        tableDao.first()?.let { return it.id }
        // 数据库为空（如覆盖升级异常）时，尝试从自动备份恢复
        backup.restoreIfEmpty()
        tableDao.first()?.let { return it.id }
        return tableDao.insert(defaultTable()).also { backup.scheduleSave() }
    }

    fun defaultTable(): TableEntity = TableEntity(
        name = "我的课表",
        termStartDate = WeekCalculator.guessCurrentTermStart().toEpochMilliCompat(),
        nodeCount = 12,
        maxWeek = 25,
        nodeTimes = WeekCalculator.defaultNodeTimes(),
    )

    private fun LocalDate.toEpochMilliCompat(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
