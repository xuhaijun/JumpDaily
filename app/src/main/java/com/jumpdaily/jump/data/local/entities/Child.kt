package com.jumpdaily.jump.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 孩子档案。一个账号下可添加多个孩子，训练/统计都按 childId 隔离。
 *
 * @param avatar     用 emoji 表情当作头像，省去图片素材，天然卡哇伊
 * @param themeColor 该孩子专属主题色（十六进制），用于首页/训练页个性化
 */
@Entity(tableName = "children")
data class Child(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String = "🐰",
    val themeColor: String = "#FF8FD3",
    val birthYear: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
