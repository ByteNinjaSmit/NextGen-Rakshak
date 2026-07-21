package com.rakshak.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingMatchDao {

    @Insert
    suspend fun insert(match: PendingMatchEntity)

    @Query("SELECT * FROM pending_matches ORDER BY id ASC")
    suspend fun all(): List<PendingMatchEntity>

    @Delete
    suspend fun delete(match: PendingMatchEntity)

    @Query("SELECT COUNT(*) FROM pending_matches")
    suspend fun count(): Int
}
