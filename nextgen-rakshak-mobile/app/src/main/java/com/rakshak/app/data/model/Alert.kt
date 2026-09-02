package com.rakshak.app.data.model

/**
 * A missing-child alert. Mirrors the Firestore `alerts` document.
 *
 * @property embedding 128-d MobileFaceNet face embedding of the child's photo.
 * @property timestamp When the alert was raised, in **epoch milliseconds** — it is
 *   compared directly against `System.currentTimeMillis()` for mesh expiry and
 *   for the elapsed-time display, so seconds here would break both.
 */
data class Alert(
    val id: String = "",
    val childName: String = "",
    val age: Int = 0,
    val gender: String = "",
    val clothingDesc: String = "",
    val parentContact: String = "",
    val imageUrl: String = "",
    val embedding: FloatArray = FloatArray(0),
    val lastSeen: String = "",
    /** Free-text scars/marks/accessories not covered by clothingDesc. */
    val identifyingMarks: String = "",
    val status: String = "active",
    val timestamp: Long = 0L,
) {
    // FloatArray needs explicit equals/hashCode for correct data-class behaviour.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Alert) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
