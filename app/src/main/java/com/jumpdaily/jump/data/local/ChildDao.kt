package com.jumpdaily.jump.data.local

import androidx.room.*
import com.jumpdaily.jump.data.local.entities.Child
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Child>>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun getById(id: Long): Child?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(child: Child): Long

    @Update
    suspend fun update(child: Child)

    @Delete
    suspend fun delete(child: Child)
}
