package com.jumpdaily.jump.data.repository

import com.jumpdaily.jump.data.local.AppDatabase
import com.jumpdaily.jump.data.local.entities.Child
import kotlinx.coroutines.flow.Flow

/**
 * 孩子档案仓库：负责增删改查，数据按 Room 持久化。
 */
class ChildrenRepository(private val db: AppDatabase) {

    val children: Flow<List<Child>> = db.childDao().observeAll()

    suspend fun getById(id: Long): Child? = db.childDao().getById(id)

    /** 新增孩子，返回自增主键 id。 */
    suspend fun add(
        name: String,
        avatar: String,
        themeColor: String,
        birthYear: Int?
    ): Long = db.childDao().insert(
        Child(name = name, avatar = avatar, themeColor = themeColor, birthYear = birthYear)
    )

    suspend fun update(child: Child) = db.childDao().update(child)

    suspend fun delete(child: Child) {
        db.childDao().delete(child)
        // 联动删除该孩子的训练记录，避免孤儿数据
        db.jumpRecordDao().deleteByChild(child.id)
    }
}
