package com.saltgarden.kechengbiao

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.saltgarden.kechengbiao.data.AppDatabase
import com.saltgarden.kechengbiao.data.AutoBackup
import com.saltgarden.kechengbiao.data.SettingsStore
import com.saltgarden.kechengbiao.data.TimetableRepository
import java.io.File

/** 应用级依赖容器：数据库 / 仓库 / 偏好设置 */
class AppContainer(appContext: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        appContext, AppDatabase::class.java, "kechengbiao.db"
    )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
        )
        .fallbackToDestructiveMigration()
        .build()

    val settings = SettingsStore(appContext)

    /** 自动备份（无用户入口）：启动恢复 + 变更落盘 */
    val autoBackup = AutoBackup(File(appContext.filesDir, "auto_backup.json"), database)

    val repository = TimetableRepository(database, autoBackup)
}

class KcbApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
