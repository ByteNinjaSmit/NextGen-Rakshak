package com.rakshak.app.domain.usecase

import com.rakshak.app.data.model.Alert
import com.rakshak.app.data.model.MatchReport
import com.rakshak.app.data.model.Volunteer
import com.rakshak.app.data.repository.MatchRepository
import com.rakshak.app.utils.LocationProvider

/**
 * Handles a volunteer confirming a match: captures GPS and submits the report.
 * (SOLID: Single Responsibility — one action, one class.)
 */
class ReportMatchUseCase(
    private val matchRepository: MatchRepository,
    private val locationProvider: LocationProvider,
) {
    suspend operator fun invoke(alert: Alert, volunteer: Volunteer, confidence: Float) {
        val location = locationProvider.current()
        matchRepository.report(
            MatchReport(
                alertId = alert.id,
                childName = alert.childName,
                imageUrl = alert.imageUrl,
                volunteerId = volunteer.id,
                volunteerRole = volunteer.role,
                confidence = confidence,
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
            )
        )
    }
}
