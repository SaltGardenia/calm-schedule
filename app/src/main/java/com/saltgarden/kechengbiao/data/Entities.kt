package com.saltgarden.kechengbiao.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 课表（一个用户可有多张课表，如不同学期） */
@Serializable
@Entity(tableName = "timetables")
data class TableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 第 1 周周一的 0 点（本地时区 epoch millis），用于推算当前周次 */
    val termStartDate: Long,
    /** 每日节数 */
    val nodeCount: Int = 12,
    /** 最大周数 */
    val maxWeek: Int = 25,
    /** 作息时间 CSV："08:00-08:45,08:55-09:40,..." 共 nodeCount 条 */
    val nodeTimes: String = "",
    /** 是否显示周六周日 */
    val showWeekend: Boolean = true,
    /** 是否显示非本周课程（默认关闭：只看当前周的课） */
    val showOtherWeek: Boolean = false,
    /** 是否显示午休时段（12:00-14:00），默认折叠 */
    val showLunchBreak: Boolean = false,
)

/** 一门课程的一次/一段固定上课安排 */
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = TableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tableId")]
)
@Serializable
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tableId") val tableId: Long,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    /** 星期几，1=周一 … 7=周日 */
    val day: Int,
    /** 起始节次（1 起，含） */
    val startNode: Int,
    /** 结束节次（含） */
    val endNode: Int,
    /** 周次 CSV："1-16" / "1,3,5-15"（已解析为显式集合的压缩写法） */
    val weeks: String,
    /** 颜色索引，见 ui.theme.CourseColors */
    val colorIndex: Int = 0,
    /** 来源：manual / zf_new / zf_old / qz / web / csv / json / html */
    val source: String = CourseEntity.SOURCE_MANUAL,
    val remark: String = "",
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_ZF_NEW = "zf_new"
        const val SOURCE_ZF_OLD = "zf_old"
        const val SOURCE_QZ = "qz"
        const val SOURCE_WEB = "web"
        const val SOURCE_CSV = "csv"
        const val SOURCE_JSON = "json"
        const val SOURCE_HTML = "html"
    }
}

/**
 * 一次性日程（非课程）：落在具体日期 + 起止时间上，不做周次重复。
 * 在课表空白区域长按滑动选择时间后创建。
 */
@Entity(
    tableName = "events",
    indices = [Index("date")]
)
@Serializable
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属课表（日程只在创建它的课表里显示） */
    @ColumnInfo(name = "tableId") val tableId: Long = 0,
    /** 具体日期，ISO 格式 yyyy-MM-dd */
    val date: String,
    /** 开始时间：当天 0 点起的分钟数（如 14:30 → 870） */
    val startMinute: Int,
    /** 结束时间：当天 0 点起的分钟数 */
    val endMinute: Int,
    val title: String,
    val location: String = "",
    val remark: String = "",
)
