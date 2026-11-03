package com.saltgarden.kechengbiao.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TableDao {
    @Query("SELECT * FROM timetables ORDER BY id")
    fun observeAll(): Flow<List<TableEntity>>

    @Query("SELECT * FROM timetables WHERE id = :id")
    fun observe(id: Long): Flow<TableEntity?>

    @Query("SELECT * FROM timetables WHERE id = :id")
    suspend fun get(id: Long): TableEntity?

    @Query("SELECT * FROM timetables ORDER BY id LIMIT 1")
    suspend fun first(): TableEntity?

    @Query("SELECT * FROM timetables")
    suspend fun listAll(): List<TableEntity>

    @Insert
    suspend fun insert(table: TableEntity): Long

    @Update
    suspend fun update(table: TableEntity)

    @Query("DELETE FROM timetables WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY day, startNode")
    fun observeByTable(tableId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE tableId = :tableId ORDER BY day, startNode")
    suspend fun listByTable(tableId: Long): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun get(id: Long): CourseEntity?

    @Insert
    suspend fun insert(course: CourseEntity): Long

    @Insert
    suspend fun insertAll(courses: List<CourseEntity>): List<Long>

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("DELETE FROM courses WHERE tableId = :tableId")
    suspend fun deleteByTable(tableId: Long)

    @Query("SELECT COUNT(*) FROM courses WHERE tableId = :tableId")
    suspend fun countByTable(tableId: Long): Int

    @Query("SELECT * FROM courses")
    suspend fun listAll(): List<CourseEntity>
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY date, startMinute")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE date = :date ORDER BY startMinute")
    fun observeByDate(date: String): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Insert
    suspend fun insertAll(events: List<EventEntity>): List<Long>

    @Query("SELECT * FROM events")
    suspend fun listAll(): List<EventEntity>

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
