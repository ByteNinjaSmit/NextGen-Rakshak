package com.rakshak.app.domain.usecase

import android.graphics.Bitmap
import com.rakshak.app.data.datasource.SightingPhotoUploader
import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.MatchRepository
import com.rakshak.app.utils.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles a volunteer confirming a match: captures GPS, uploads the captured
 * face as sighting evidence, and submits the report.
 * (SOLID: Single Responsibility — one action, one class.)
 *
 * Runs on [Dispatchers.IO]. The caller is a ViewModel coroutine, which starts on
 * the main thread, and this does JPEG compression and a network upload — doing
 * that on the main thread freezes the dialog for as long as it takes, which
 * reads to the volunteer as the Confirm button not working.
 */
class ReportMatchUseCase(
    private val matchRepository: MatchRepository,
    private val locationProvider: LocationProvider,
    private val photoUploader: SightingPhotoUploader,
) {
    suspend operator fun invoke(
        alert: Alert,
        volunteer: Volunteer,
        confidence: Float,
        faceCrop: Bitmap,
    ) = withContext(Dispatchers.IO) {
        // Bounded: FusedLocation can take ~30 s for a cold fix indoors, and the
        // volunteer is holding a child while the confirm button spins. A sighting
        // recorded without coordinates (hasLocation = false) beats a 30 s hang.
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationProvider.current() }
        // The sighting photo must be the face actually seen, not the alert's own
        // photo, or the kiosk's side-by-side review compares a picture to itself.
        // If the upload fails (offline, quota), fall back to the alert photo so the
        // report — the more important half — still goes out. Bounded: with no
        // network at all, Storage's putBytes() retries internally (App Check
        // token fetch backoff) rather than failing fast, so an unbounded await
        // here hangs Confirm forever and the report — and its mesh fallback —
        // never happens. A slow/absent upload should not block the report.
        val sightingImageUrl = runCatching {
            withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { photoUploader.upload(alert.id, faceCrop) }
        }.getOrNull() ?: alert.imageUrl
        matchRepository.report(
            MatchReport(
                alertId = alert.id,
                childName = alert.childName,
                imageUrl = sightingImageUrl,
                volunteerId = volunteer.id,
                volunteerName = volunteer.name,
                volunteerRole = volunteer.role,
                confidence = confidence,
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
                hasLocation = location != null,
            )
        )
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 6_000L
        const val UPLOAD_TIMEOUT_MS = 6_000L
    }
}
