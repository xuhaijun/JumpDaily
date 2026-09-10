package com.jumpdaily.jump.data.model

/**
 * 桌面小组件所需的一次性数据快照（纯本地、无网络）。
 *
 * 与 [com.jumpdaily.jump.data.model.Stats] 区分：Stats 是 Compose 页面用的「持续观察流」，
 * 这里只是小组件 on-demand 拉取的一张「静快照」。
 */
data class WidgetSnapshot(
    /** 当前孩子昵称（无选中孩子时兜底为「小朋友」）。 */
    val childName: String,
    /** 今日已跳个数。 */
    val todayCount: Int,
    /** 连续打卡天数。 */
    val streak: Int,
    /** 累计总个数。 */
    val totalCount: Int
) {
    companion object {
        /** 无数据（未选孩子 / 拉取失败）时的占位快照。 */
        fun empty() = WidgetSnapshot(childName = "小朋友", todayCount = 0, streak = 0, totalCount = 0)
    }
}
