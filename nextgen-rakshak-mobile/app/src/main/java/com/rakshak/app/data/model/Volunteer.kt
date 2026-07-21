package com.rakshak.app.data.model

/** The signed-in volunteer. Role gates what they can do (police can also create). */
data class Volunteer(
    val id: String,
    val phone: String,
    val role: String, // "police" | "ncc" | "ngo" | "community"
)
