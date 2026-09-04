package com.jumpdaily.jump.data.model

import com.jumpdaily.jump.data.local.entities.JumpRecord

/**
 * 汇总统计快照，作为成就解锁与统计页的数据源。
 */
data class Stats(
    val totalCount: Int = 0,
    val bestCount: Int = 0,
    val activeDays: Int = 0,
    val streak: Int = 0,
    val todayCount: Int = 0,
    val history: List<JumpRecord> = emptyList()
)

/**
 * 成就徽章。解锁条件用谓词表达，纯函数、可测试。
 */
data class Badge(
    val id: String,
    val title: String,
    val emoji: String,
    val desc: String,
    val isUnlocked: (Stats) -> Boolean
)

/** 内置成就目录。后续可扩展更多关卡式成就。 */
object BadgeCatalog {
    val ALL = listOf(
        Badge("first_jump", "第一次跳跃", "🌟", "完成第一次跳绳打卡", { it.totalCount > 0 }),
        Badge("rookie_100", "小试身手", "🎯", "累计跳满 100 个", { it.totalCount >= 100 }),
        Badge("brave_500", "百发百中", "💯", "累计跳满 500 个", { it.totalCount >= 500 }),
        Badge("master_1000", "跳绳小将", "🏅", "累计跳满 1000 个", { it.totalCount >= 1000 }),
        Badge("best_50", "稳如老狗", "🔥", "单次突破 50 个", { it.bestCount >= 50 }),
        Badge("best_100", "花样少年", "🚀", "单次突破 100 个", { it.bestCount >= 100 }),
        Badge("streak_3", "坚持三天", "📅", "连续打卡 3 天", { it.streak >= 3 }),
        Badge("streak_7", "一周达人", "⚡", "连续打卡 7 天", { it.streak >= 7 }),
        Badge("today_30", "今日之星", "⭐", "今天跳满 30 个", { it.todayCount >= 30 }),
        Badge("today_100", "今日王者", "👑", "今天跳满 100 个", { it.todayCount >= 100 })
    )

    fun evaluate(stats: Stats): List<Badge> = ALL.filter { it.isUnlocked(stats) }
}
