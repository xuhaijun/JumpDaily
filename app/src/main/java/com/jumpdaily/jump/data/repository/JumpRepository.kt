package com.jumpdaily.jump.data.repository

import com.jumpdaily.jump.data.local.AppDatabase
import com.jumpdaily.jump.data.local.entities.JumpRecord
import com.jumpdaily.jump.data.model.Stats
import com.jumpdaily.jump.util.computeStreak
import com.jumpdaily.jump.util.dayStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 跳绳记录仓库：保存训练结果，并提供统计聚合。
 */
class JumpRepository(private val db: AppDatabase) {

    suspend fun save(record: JumpRecord): Long = db.jumpRecordDao().insert(record)

    suspend fun delete(record: JumpRecord) = db.jumpRecordDao().delete(record)

    fun records(childId: Long): Flow<List<JumpRecord>> =
        db.jumpRecordDao().observeByChild(childId)

    fun totalCount(childId: Long): Flow<Int> = db.jumpRecordDao().totalCount(childId)
    fun bestCount(childId: Long): Flow<Int> = db.jumpRecordDao().bestCount(childId)
    fun activeDays(childId: Long): Flow<Int> = db.jumpRecordDao().activeDays(childId)
    fun todayCount(childId: Long): Flow<Int> =
        db.jumpRecordDao().dayCount(childId, dayStart(System.currentTimeMillis()))

    /** 组合出统计快照（含连续打卡天数）。 */
    fun stats(childId: Long): Flow<Stats> = combine(
        db.jumpRecordDao().totalCount(childId),
        db.jumpRecordDao().bestCount(childId),
        db.jumpRecordDao().activeDays(childId),
        db.jumpRecordDao().dayCount(childId, dayStart(System.currentTimeMillis())),
        db.jumpRecordDao().observeByChild(childId)
    ) { total, best, activeDays, today, history ->
        Stats(
            totalCount = total,
            bestCount = best,
            activeDays = activeDays,
            streak = computeStreak(history.map { it.date }),
            todayCount = today,
            history = history
        )
    }
}
