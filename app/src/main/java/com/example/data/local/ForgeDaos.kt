package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectConfigDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectConfigEntity): Long

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProjectConfigEntity?

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BuildRecordDao {
    @Query("SELECT * FROM builds ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BuildRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(build: BuildRecordEntity): Long

    @Query("SELECT * FROM builds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BuildRecordEntity?

    @Query("DELETE FROM builds WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SigningKeyDao {
    @Query("SELECT * FROM signing_keys ORDER BY isDefault DESC, createdAt DESC")
    fun observeAll(): Flow<List<SigningKeyEntity>>

    @Query("SELECT * FROM signing_keys ORDER BY isDefault DESC, createdAt DESC")
    suspend fun getAllOnce(): List<SigningKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: SigningKeyEntity): Long

    @Query("SELECT * FROM signing_keys WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SigningKeyEntity?

    @Query("DELETE FROM signing_keys WHERE id = :id")
    suspend fun deleteById(id: Long)
}
