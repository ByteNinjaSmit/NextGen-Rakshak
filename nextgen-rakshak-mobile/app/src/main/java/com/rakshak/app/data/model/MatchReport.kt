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

/** Where the kiosk currently stands on a reported sighting. */
enum class MatchStatus { PENDING, DISPATCHED, ACCEPTED, DISMISSED }

/** A previously-submitted sighting, as reflected back from the kiosk's review. */
data class MatchStatusReport(
    val id: String,
    val alertId: String,
    val childName: String,
    val imageUrl: String,
    val status: MatchStatus,
    val timestampMillis: Long,
)
