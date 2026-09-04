package com.jumpdaily.jump.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jumpdaily.jump.data.local.entities.Child
import com.jumpdaily.jump.data.repository.ChildrenRepository
import com.jumpdaily.jump.data.repository.DEFAULT_DAILY_GOAL
import com.jumpdaily.jump.data.repository.DEFAULT_WEEKLY_GOAL
import com.jumpdaily.jump.data.model.CountMode
import com.jumpdaily.jump.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 会话级 ViewModel（activity 作用域共享）：
 * 维护当前选中的孩子、孩子列表与全局偏好（音效/灵敏度），切换孩子时各页面自动刷新。
 */
class SessionViewModel(
    private val prefs: PreferencesRepository,
    private val childrenRepo: ChildrenRepository
) : ViewModel() {

    private val _currentChildId = MutableStateFlow<Long?>(null)
    val currentChildId: StateFlow<Long?> = _currentChildId.asStateFlow()

    val children: StateFlow<List<Child>> =
        childrenRepo.children.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentChild: StateFlow<Child?> = combine(_currentChildId, children) { id, list ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val soundEnabled: StateFlow<Boolean> =
        prefs.soundEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val sensitivity: StateFlow<Float> =
        prefs.sensitivity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    /** 每日 / 每周跳绳目标，供首页与统计页展示进度。 */
    val dailyGoal: StateFlow<Int> =
        prefs.dailyGoal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DAILY_GOAL)
    val weeklyGoal: StateFlow<Int> =
        prefs.weeklyGoal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_WEEKLY_GOAL)

    /** 当前计数方式（传感器 / 摄像头），供首页选择跳转目标。 */
    val countMode: StateFlow<CountMode> =
        prefs.countMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CountMode.SENSOR)

    init {
        viewModelScope.launch {
            val list = childrenRepo.children.first()
            if (list.isEmpty()) {
                // 首次启动：种一个示例孩子，避免空状态
                val id = childrenRepo.add("小跳", "🐰", "#FF8FD3", null)
                prefs.setCurrentChildId(id)
                _currentChildId.value = id
                prefs.markSeeded()
            } else {
                val saved = prefs.currentChildId.first()
                _currentChildId.value = saved ?: list.first().id
                if (saved == null) prefs.setCurrentChildId(list.first().id)
            }
        }
    }

    fun setCurrentChild(id: Long) {
        viewModelScope.launch {
            prefs.setCurrentChildId(id)
            _currentChildId.value = id
        }
    }

    class Factory(
        private val prefs: PreferencesRepository,
        private val childrenRepo: ChildrenRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SessionViewModel::class.java))
                return SessionViewModel(prefs, childrenRepo) as T
            throw IllegalArgumentException("Unknown VM: $modelClass")
        }
    }
}
