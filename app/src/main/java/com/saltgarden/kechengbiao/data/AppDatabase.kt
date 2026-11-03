package com.saltgarden.kechengbiao.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TableEntity::class, CourseEntity::class, EventEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tableDao(): TableDao
    abstract fun courseDao(): CourseDao
    abstract fun eventDao(): EventDao

    companion object {
        /** v1 → v2：新增一次性日程表（不破坏已有课表数据） */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`startNode` INTEGER NOT NULL, " +
                        "`endNode` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`location` TEXT NOT NULL, " +
                        "`remark` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_date` ON `events` (`date`)")
            }
        }

        /** v2 → v3：日程从"节次"改为"真实起止时间"（开发期结构升级，重置日程数据） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `events`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`startMinute` INTEGER NOT NULL, " +
                        "`endMinute` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`location` TEXT NOT NULL, " +
                        "`remark` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_date` ON `events` (`date`)")
            }
        }

        /** v3 → v4："淡化显示非本周"改为"显示非本周课程"语义，统一重置为关闭 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE timetables SET showOtherWeek = 0")
            }
        }
    }
}
