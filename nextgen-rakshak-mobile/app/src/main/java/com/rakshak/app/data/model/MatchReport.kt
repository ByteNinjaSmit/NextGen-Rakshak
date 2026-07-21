package com.rakshak.app.data.model

/** A confirmed sighting the volunteer submits to the police kiosk. */
data class MatchReport(
    val alertId: String,
    val childName: String,
    val imageUrl: String,
    val volunteerId: String,
    val volunteerRole: String,
    val confidence: Float,
    val latitude: Double,
    val longitude: Double,
)
