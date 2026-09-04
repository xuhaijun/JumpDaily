package com.jumpdaily.jump.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jumpdaily.jump.data.local.entities.Child
import com.jumpdaily.jump.data.repository.ChildrenRepository
import com.jumpdaily.jump.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 孩子管理：列表 / 新增 / 切换 / 删除 / 编辑。 */
class ChildrenViewModel(
    private val repo: ChildrenRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    val children: StateFlow<List<Child>> =
        repo.children.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, avatar: String, color: String, birthYear: Int?) = viewModelScope.launch {
        val id = repo.add(name, avatar, color, birthYear)
        prefs.setCurrentChildId(id)
    }

    fun select(id: Long) = viewModelScope.launch { prefs.setCurrentChildId(id) }

    fun delete(child: Child) = viewModelScope.launch { repo.delete(child) }

    fun update(child: Child) = viewModelScope.launch { repo.update(child) }

    class Factory(
        private val repo: ChildrenRepository,
        private val prefs: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChildrenViewModel::class.java))
                return ChildrenViewModel(repo, prefs) as T
            throw IllegalArgumentException("Unknown VM: $modelClass")
        }
    }
}
