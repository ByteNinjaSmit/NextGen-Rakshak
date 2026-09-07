package com.rakshak.app.data.model

/** A confirmed sighting the volunteer submits to the police kiosk. */
data class MatchReport(
    val alertId: String,
    val childName: String,
    val imageUrl: String,
    val volunteerId: String,
    val volunteerRole: String,
    val volunteerName: String = "",
    val confidence: Float,
    val latitude: Double,
    val longitude: Double,
    /**
     * False when no GPS fix was available at confirmation time — [latitude] and
     * [longitude] are then `0.0` and the kiosk must not drop a map pin on them.
     */
    val hasLocation: Boolean = true,
)

/** Where the kiosk currently stands on a reported sighting. */
enum class MatchStatus { PENDING, DISPATCHED, ACCEPTED, DISMISSED }

/**
 * A previously-submitted sighting, as reflected back from the kiosk's review.
 *
 * Carries more than the list strictly needs to render a row because this screen
 * is the volunteer's only record of what they did. After handing a child to
 * police they have no other way to answer "did my report actually go through,
 * where was I, and what happened next" — so the confidence they acted on, the
 * coordinates that were attached, and whether the report is still sitting in the
 * offline queue all belong here rather than only in the kiosk.
 */
data class MatchStatusReport(
    val id: String,
    val alertId: String,
    val childName: String,
    val imageUrl: String,
    val status: MatchStatus,
    val timestampMillis: Long,
    /** Cosine score at the moment the volunteer confirmed. */
    val confidence: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /** False when no GPS fix was available; lat/lng are then meaningless. */
    val hasLocation: Boolean = false,
    /**
     * True for a report still queued on this device because the network was
     * down. It has NOT reached the police kiosk yet, and saying so plainly is the
     * point: a volunteer who believes police were notified will walk away.
     */
    val pendingSync: Boolean = false,
)
