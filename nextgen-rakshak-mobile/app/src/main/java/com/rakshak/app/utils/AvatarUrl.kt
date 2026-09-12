package com.rakshak.app.utils

/**
 * Google account avatars come back from Firebase sized for a 96 px slot
 * (`…/photo.jpg=s96-c`). Rendered into the 100 dp circle on the profile screen
 * that is visibly soft on any modern display, so the size hint is rewritten to
 * the size actually being drawn.
 *
 * Any URL without that hint — a non-Google provider, a bare Storage link — is
 * returned unchanged.
 */
object AvatarUrl {

    private val SIZE_HINT = Regex("=s\\d+(-c)?$")

    fun sized(url: String, px: Int): String {
        if (url.isBlank()) return url
        val match = SIZE_HINT.find(url) ?: return url
        val cropped = match.groupValues[1].isNotEmpty()
        return url.removeRange(match.range) + "=s$px" + if (cropped) "-c" else ""
    }
}
