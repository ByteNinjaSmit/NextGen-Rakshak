package com.rakshak.app.data.model

/**
 * The signed-in volunteer. Role gates what they can do (police can also create).
 *
 * [name] and [email] come from Google sign-in and are blank for the demo
 * phone-only path. They matter because the officer receiving a match needs to
 * know who reported it — the synopsis's "trusted, pre-registered volunteer" model
 * depends on a sighting being attributable to a real person.
 */
data class Volunteer(
    val id: String,
    val phone: String,
    val role: String, // "police" | "ncc" | "ngo" | "community"
    val name: String = "",
    val email: String = "",
)
