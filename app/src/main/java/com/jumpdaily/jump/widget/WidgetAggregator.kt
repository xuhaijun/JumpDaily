package com.jumpdaily.jump.widget

import com.jumpdaily.jump.data.model.WidgetSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 并行聚合多个「互相独立」的数据源，得到一张 [WidgetSnapshot]。
 *
 * ## ⚠️ 认知：async 异常延迟暴露（最容易踩的并发坑）
 * `async { ... }` 在创建处**不会**立刻抛出异常——异常被「挂」在返回的 `Deferred` 上，
 * **只有调用 `await()` 时才会真正抛出**。如果你创建了 `async` 却忘了 `await()`，
 * 在 `supervisorScope` / `SupervisorJob()` 下这个异常会被**静默吞掉**
 * （仅打印一条 "Exception in deferred was not handled" 警告），调用方永远不知道失败过、
 * 还可能拿到错误的组合结果。这就是「async 异常延迟暴露」——失败被推迟、且可能被吞。
 *
 * ### 本实现的取舍（结构化并发 fail-fast）
 * 这里用 `coroutineScope`（**不是** `supervisorScope`）包裹四个 `async`：
 * - 任一源失败 → coroutineScope **取消其余兄弟**并让整个聚合作用域失败，异常**不会**被静默吞；
 * - 同时每个结果都显式 `await()`，要么拿到真实值，要么让异常在此处立即上抛。
 * 即把「延迟暴露」转化为「在 await 处立即暴露 + 作用域兜底失败」双保险。
 *
 * ### 反例（切勿这样写，会吞异常）
 * ```
 * supervisorScope {
 *     val a = async { riskyA() }   // riskyA 抛错
 *     val b = async { riskyB() }
 *     // 漏了 a.await() / b.await() → a 的异常被静默吞掉，调用方拿到错误结果且不报错
 * }
 * ```
 *
 * 行为由 [WidgetAggregatorTest] 单测固化：某源抛错时异常必须上抛（非吞掉）。
 */
object WidgetAggregator {

    suspend fun aggregate(
        today: suspend () -> Int,
        streak: suspend () -> Int,
        name: suspend () -> String,
        total: suspend () -> Int
    ): WidgetSnapshot = coroutineScope {
        // 四个源互相独立，并行启动（IO 上的 DB 查询真正并发）
        val todayDef = async { today() }
        val streakDef = async { streak() }
        val nameDef = async { name() }
        val totalDef = async { total() }
        // 逐一 await：失败会在这一行立即抛出，coroutineScope 再让整体失败（fail-fast）
        WidgetSnapshot(
            todayCount = todayDef.await(),
            streak = streakDef.await(),
            childName = nameDef.await(),
            totalCount = totalDef.await()
        )
    }
}
