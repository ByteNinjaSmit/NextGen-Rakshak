package com.rakshak.app.data.model

/**
 * The signed-in volunteer. Role gates what they can do (police can also create).
 *
 * [name], [email] and [photoUrl] come from Google sign-in and are blank for the
 * demo phone-only path. They matter because the officer receiving a match needs
 * to know who reported it — the synopsis's "trusted, pre-registered volunteer"
 * model depends on a sighting being attributable to a real person, and a face
 * next to the name is the fastest form of that.
 *
 * [simSubscriptionId] records *which* inserted SIM [phone] was read from, so a
 * later app open re-reads the same line instead of silently switching to the
 * other one on a dual-SIM phone. [phoneIsManual] marks a number the volunteer
 * typed themselves, which the SIM re-read must never overwrite.
 */
data class Volunteer(
    val id: String,
    val phone: String,
    val role: String, // "volunteer" (mobile) | "officer" / "police" (web portal)
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val simSubscriptionId: Int = -1,
    val phoneIsManual: Boolean = false,
    /**
     * False while [phone] has not made it to `volunteers/{uid}` yet — the write
     * failed, or the device was offline when the SIM was read. Local-only, like
     * [simSubscriptionId] and [phoneIsManual]; the point of keeping it on disk is
     * that the next app open retries instead of leaving the officer with a stale
     * number because nothing changed the second time.
     */
    val phoneSynced: Boolean = false,
    /**
     * False while [name]/[email]/[photoUrl] have not made it to `volunteers/{uid}`
     * yet. Local-only, like [phoneSynced] — without it, a `register()` call that
     * fails once (offline, a rules rejection) never retries: [LoginViewModel]'s
     * identity refresh only re-pushes on a *change* to the Google profile, so an
     * account whose first write never landed would otherwise stay missing its
     * email/photo in Firestore forever, even though the device itself has always
     * known them.
     */
    val identitySynced: Boolean = false,
)
