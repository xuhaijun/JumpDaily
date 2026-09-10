package com.jumpdaily.jump.data.repository

import com.jumpdaily.jump.data.model.WidgetSnapshot
import com.jumpdaily.jump.widget.WidgetAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 桌面小组件数据来源：聚合「当前孩子 + 今日个数 + 连续天数 + 累计总数」成一张静态快照。
 *
 * 纯本地（Room + DataStore），无网络、无外部账号，符合小组件「即点即看」的轻量定位。
 * 并行聚合交给 [WidgetAggregator]（用 coroutineScope + async + await 实现 fail-fast，
 * 见其 KDoc 关于「async 异常延迟暴露」的说明）。
 */
class WidgetRepository(
    private val jumpRepository: JumpRepository,
    private val childrenRepository: ChildrenRepository,
    private val prefsRepository: PreferencesRepository
) {
    /**
     * 拉取小组件快照。返回 [WidgetSnapshot.empty] 的情况：
     * - 用户尚未在 App 里选中任何孩子（DataStore 无 current_child_id）；
     * - 聚合过程中任一数据源抛异常（由 [WidgetAggregator] 的 fail-fast 上抛后在此兜底）。
     */
    suspend fun snapshot(): WidgetSnapshot = withContext(Dispatchers.IO) {
        val childId = prefsRepository.currentChildId.first() ?: return@withContext WidgetSnapshot.empty()
        runCatching {
            WidgetAggregator.aggregate(
                today = { jumpRepository.todayCountNow(childId) },
                streak = { jumpRepository.streakNow(childId) },
                name = { childrenRepository.getById(childId)?.name ?: "小朋友" },
                total = { jumpRepository.totalCountNow(childId) }
            )
        }.getOrDefault(WidgetSnapshot.empty())
    }
}
