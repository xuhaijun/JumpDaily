package com.jumpdaily.jump.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次跳绳训练记录。
 *
 * @param date        当天 0 点时间戳，用于按天分组/统计（同一天多次训练会存多条）
 * @param count       本次跳绳个数
 * @param durationSec 本次时长（秒）
 * @param calories    估算消耗（千卡）
 * @param maxStreak   本次最长连续不中断个数（用于评价节奏稳定性）
 * @param avgCadence  平均频率（个/分钟）
 */
@Entity(
    tableName = "jump_records",
    indices = [Index("childId"), Index("date")]
)
data class JumpRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val count: Int,
    val durationSec: Int,
    val calories: Int,
    val maxStreak: Int,
    val avgCadence: Int,
    val date: Long,
    val createdAt: Long = System.currentTimeMillis()
)
