package com.jumpdaily.jump.data.local

import androidx.room.*
import com.jumpdaily.jump.data.local.entities.JumpRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface JumpRecordDao {
    @Insert
    suspend fun insert(record: JumpRecord): Long

    @Query("SELECT * FROM jump_records WHERE childId = :childId ORDER BY date DESC, createdAt DESC")
    fun observeByChild(childId: Long): Flow<List<JumpRecord>>

    @Query(
        "SELECT * FROM jump_records " +
            "WHERE childId = :childId AND date >= :from AND date <= :to " +
            "ORDER BY date ASC"
    )
    fun observeInRange(childId: Long, from: Long, to: Long): Flow<List<JumpRecord>>

    @Query("SELECT COALESCE(SUM(count), 0) FROM jump_records WHERE childId = :childId")
    fun totalCount(childId: Long): Flow<Int>

    @Query("SELECT COALESCE(MAX(count), 0) FROM jump_records WHERE childId = :childId")
    fun bestCount(childId: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(count), 0) FROM jump_records WHERE childId = :childId AND date = :date")
    fun dayCount(childId: Long, date: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT date) FROM jump_records WHERE childId = :childId")
    fun activeDays(childId: Long): Flow<Int>

    @Query("DELETE FROM jump_records WHERE childId = :childId")
    suspend fun deleteByChild(childId: Long)

    @Delete
    suspend fun delete(record: JumpRecord)
}
