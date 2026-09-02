package com.rakshak.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MeshDao {

    @Query("SELECT * FROM mesh_alerts")
    suspend fun allAlerts(): List<MeshAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlert(entity: MeshAlertEntity)

    @Query("DELETE FROM mesh_alerts WHERE alertId = :alertId")
    suspend fun deleteAlert(alertId: String)

    @Query("DELETE FROM mesh_alerts WHERE receivedAt < :cutoff")
    suspend fun pruneAlerts(cutoff: Long)

    @Query("SELECT * FROM mesh_seen")
    suspend fun allSeen(): List<MeshSeenEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putSeen(entity: MeshSeenEntity)

    @Query("DELETE FROM mesh_seen WHERE seenAt < :cutoff")
    suspend fun pruneSeen(cutoff: Long)
}
