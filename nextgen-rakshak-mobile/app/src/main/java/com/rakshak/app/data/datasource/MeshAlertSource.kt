package com.rakshak.app.data.datasource

import com.rakshak.app.data.model.Alert
import com.rakshak.app.networking.mesh.MeshNetworkManager
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * Exposes the alerts [MeshNetworkManager] has collected over the offline mesh.
 *
 * The alerts themselves live in the mesh manager, not here: this source is
 * rebuilt every time a screen asks for one, and a per-instance list would start
 * empty each time — so a volunteer who received an alert on Home and then opened
 * the scan screen would be scanning against nothing.
 *
 * The only state this adds is time. An alert expires by the clock rather than in
 * response to any packet, so the mesh manager cannot emit at the moment one goes
 * stale; the ticker re-applies the cut-off periodically instead.
 */
class MeshAlertSource(
    private val received: Flow<List<Alert>>,
    private val expiryCheckMillis: Long = EXPIRY_CHECK_MILLIS,
) : AlertDataSource {

    constructor(mesh: MeshNetworkManager) : this(mesh.alerts)

    override fun observeActiveAlerts(): Flow<List<Alert>> =
        combine(received, ticker()) { alerts, _ -> alerts.filterNot(::isExpired) }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(expiryCheckMillis)
        }
    }

    private fun isExpired(alert: Alert): Boolean =
        System.currentTimeMillis() - alert.timestamp > Constants.ALERT_EXPIRY_MILLIS

    private companion object {
        /**
         * How often the expiry cut-off is re-applied. A minute is far finer than
         * the 8 h TTL it polices and costs one list filter, so the extra
         * precision is free.
         */
        const val EXPIRY_CHECK_MILLIS = 60_000L
    }
}
