package com.jumpdaily.jump.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.jumpdaily.jump.data.local.entities.JumpRecord
import com.jumpdaily.jump.data.model.Badge
import com.jumpdaily.jump.data.model.BadgeCatalog
import com.jumpdaily.jump.data.model.Stats
import com.jumpdaily.jump.data.repository.JumpRepository
import com.jumpdaily.jump.util.DAY_MS
import com.jumpdaily.jump.util.dayStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 统计 / 历史 / 成就三页共享：根据当前孩子派生记录、统计、成就与周柱状图数据。
 */
class RecordsViewModel(private val repo: JumpRepository) : ViewModel() {

    private val _childId = MutableStateFlow<Long?>(null)
    val childId: StateFlow<Long?> = _childId.asStateFlow()

    fun setChild(id: Long?) { _childId.value = id }

    fun deleteRecord(record: JumpRecord) = viewModelScope.launch { repo.delete(record) }

    val records: StateFlow<List<JumpRecord>> = _childId.flatMapLatest { id ->
        id?.let { repo.records(it) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<Stats> = _childId.flatMapLatest { id ->
        id?.let { repo.stats(it) } ?: flowOf(Stats())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())

    val badges: StateFlow<List<Badge>> = stats.map { BadgeCatalog.evaluate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 该孩子的统计数据是否已完成「真实首帧」加载。
     *
     * 关键：stats / badges 都是 stateIn 且带初始值（空 Stats / 空列表），
     * 调用方若在首帧之前就去 `badges.first()` 做「成就基线快照」，会拿到空列表，
     * 随后真实成就到达时会被误判成「新解锁」，导致每次打开 App 都弹成就框。
     * 因此对外暴露本标志，让种子化逻辑能等到真实数据到达后再快照。
     */
    val statsLoaded: StateFlow<Boolean> = _childId.flatMapLatest { id ->
        if (id == null) flowOf(false) else repo.stats(id).map { true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 本周打卡（周一 ~ 周日，共 7 格，从星期一开始排列）。今天的格子标签为「今天」并高亮。 */
    val weekly: StateFlow<List<Pair<String, Int>>> = records.map { list ->
        val map = list.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.count } }
        val today = dayStart(System.currentTimeMillis())
        val cal = java.util.Calendar.getInstance()
        // 计算本周一 0 点：DAY_OF_WEEK 中 1=周日 … 7=周六，转为「距周一的天数」
        cal.timeInMillis = today
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val daysSinceMonday = if (dow == java.util.Calendar.SUNDAY) 6 else dow - java.util.Calendar.MONDAY
        val monday = today - daysSinceMonday * DAY_MS
        val wdName = arrayOf("一", "二", "三", "四", "五", "六", "日")
        (0..6).map { i ->
            val d = monday + i * DAY_MS
            val label = if (d == today) "今天" else "周${wdName[i]}"
            label to (map[d] ?: 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    class Factory(private val repo: JumpRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecordsViewModel::class.java))
                return RecordsViewModel(repo) as T
            throw IllegalArgumentException("Unknown VM: $modelClass")
        }
    }
}
