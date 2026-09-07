package com.jumpdaily.jump.data.model

/**
 * 积分与奖励体系（儿童向：即时、可视化、够夸张）。
 *
 * 设计原则：
 * - **即时反馈**：每跳一下都有积分入账，孩子能立刻看到数字在涨；
 * - **阶梯惊喜**：每满 10 个触发弹幕 +10 与音效，形成「一小段就有一次喝彩」的节奏；
 * - **看得见的成长**：积分兑换等级称号与奖品库，让孩子有长期奔头。
 */
object Rewards {

    // ===== 积分规则 =====

    /** 每跳 1 个的基础积分。 */
    const val POINT_PER_JUMP = 1

    /** 每满 10 个的额外奖励积分（与弹幕「+10」对应）。 */
    const val BONUS_EVERY_TEN = 10

    /** 连击奖励：连续不间断跳到该数量时额外加分。 */
    val COMBO_BONUS = listOf(
        20 to 5,   // 连跳 20 个 +5
        50 to 15,  // 连跳 50 个 +15
        100 to 40  // 连跳 100 个 +40
    )

    /** 节奏奖励：节奏（个/分）落在该区间时，每隔一段时间额外加分。 */
    val GOOD_CADENCE_RANGE = 60..140
    /** 节奏好评的额外积分与最短触发间隔（毫秒）。 */
    const val BONUS_GOOD_CADENCE = 3
    /** 节奏达标时「小奖品」的发放间隔：2026-09-07 由 12s 拉长到 15s（与 steady/cheer 共用节流，降语音密度）。 */
    const val GOOD_CADENCE_INTERVAL_MS = 15_000L

    /** 达成每日目标的额外积分。 */
    const val BONUS_DAILY_GOAL = 50
    /** 刷新个人最佳记录的额外积分。 */
    const val BONUS_NEW_RECORD = 30

    // ===== 等级称号 =====

    data class Level(val minPoints: Int, val title: String, val emoji: String)

    /** 等级阶梯（由低到高），按累计积分自动升级。 */
    val LEVELS = listOf(
        Level(0, "跳绳小萌新", "🌱"),
        Level(100, "跳绳小能手", "⭐"),
        Level(300, "跳绳小达人", "🔥"),
        Level(700, "跳绳小飞人", "🚀"),
        Level(1500, "跳绳小冠军", "🏆"),
        Level(3000, "跳绳小霸王", "👑")
    )

    /** 根据积分返回当前等级。 */
    fun levelOf(points: Int): Level = LEVELS.lastOrNull { points >= it.minPoints } ?: LEVELS.first()

    /** 下一个等级（已满级返回 null）。 */
    fun nextLevel(points: Int): Level? = LEVELS.firstOrNull { points < it.minPoints }

    /**
     * 距离下一级的进度（0~1）；已满级返回 1。
     * 用于「我的」页的升级进度条。
     */
    fun levelProgress(points: Int): Float {
        val cur = levelOf(points)
        val next = nextLevel(points) ?: return 1f
        val span = (next.minPoints - cur.minPoints).coerceAtLeast(1)
        return ((points - cur.minPoints).toFloat() / span).coerceIn(0f, 1f)
    }

    // ===== 奖品库 =====

    data class Prize(
        val id: String,
        val emoji: String,
        val name: String,
        /** 解锁所需累计积分。 */
        val needPoints: Int,
        val desc: String
    )

    /** 奖品库：积分达标即可兑换（家长兑现，App 只负责「孩子看得见的期待」）。 */
    val PRIZES = listOf(
        Prize("balloon", "🎈", "彩色气球", 50, "挑一个最喜欢的颜色"),
        Prize("lollipop", "🍭", "棒棒糖", 100, "甜甜的小奖励"),
        Prize("sticker", "✨", "闪亮贴纸", 180, "贴在水杯上超好看"),
        Prize("teddy", "🧸", "小熊玩偶", 300, "陪你一起睡觉"),
        Prize("gametime", "🎮", "游戏 15 分钟", 500, "和爸爸妈妈约定好哦"),
        Prize("icecream", "🍦", "冰淇淋", 800, "夏天最棒的味道"),
        Prize("park", "🎡", "周末游乐园", 1500, "一家人一起去玩"),
        Prize("bike", "🚲", "新自行车", 3000, "骑着它去兜风"),
        Prize("mystery", "🎁", "神秘大奖", 6000, "攒够就有大惊喜")
    )

    /** 已解锁（积分达标）的奖品。 */
    fun unlockedPrizes(points: Int): List<Prize> = PRIZES.filter { points >= it.needPoints }

    /** 下一个够得着的奖品（用于「再跳一点点就能拿」的目标感）。 */
    fun nextPrize(points: Int): Prize? = PRIZES.firstOrNull { points < it.needPoints }

    /**
     * 训练途中弹出的「小奖品」：跳得又快又好时随机冒出来加油。
     * 与 PRIZES 分开，避免把「终极大奖」在低积分时误当即时奖励弹出。
     */
    val QUICK_GIFTS = listOf(
        "⭐" to "跳得真棒",
        "🌟" to "节奏超稳",
        "💖" to "好喜欢你",
        "🍭" to "甜甜奖励",
        "🎈" to "飞起来啦",
        "👏" to "为你鼓掌",
        "🔥" to "火力全开",
        "🚀" to "冲上天啦"
    )
}
