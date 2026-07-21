package com.rakshak.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rakshak.app.data.model.MatchReport

/** A match confirmed while offline, queued for upload when connectivity returns. */
@Entity(tableName = "pending_matches")
data class PendingMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertId: String,
    val childName: String,
    val imageUrl: String,
    val volunteerId: String,
    val volunteerRole: String,
    val confidence: Float,
    val latitude: Double,
    val longitude: Double,
) {
    fun toReport() = MatchReport(
        alertId = alertId,
        childName = childName,
        imageUrl = imageUrl,
        volunteerId = volunteerId,
        volunteerRole = volunteerRole,
        confidence = confidence,
        latitude = latitude,
        longitude = longitude,
    )

    companion object {
        fun from(report: MatchReport) = PendingMatchEntity(
            alertId = report.alertId,
            childName = report.childName,
            imageUrl = report.imageUrl,
            volunteerId = report.volunteerId,
            volunteerRole = report.volunteerRole,
            confidence = report.confidence,
            latitude = report.latitude,
            longitude = report.longitude,
        )
    }
}
