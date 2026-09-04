package com.jumpdaily.jump.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jumpdaily.jump.data.local.entities.Child
import com.jumpdaily.jump.data.local.entities.JumpRecord

@Database(
    entities = [Child::class, JumpRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun jumpRecordDao(): JumpRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jump_daily.db"
                )
                    // 演示阶段：结构变更直接重建，避免迁移样板代码
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
