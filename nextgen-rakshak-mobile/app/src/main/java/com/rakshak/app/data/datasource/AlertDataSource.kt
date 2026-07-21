package com.rakshak.app.data.datasource

import com.rakshak.app.data.model.Alert
import kotlinx.coroutines.flow.Flow

/**
 * Source of active alerts. (SOLID: Open/Closed — new transports like mesh can be
 * added by implementing this interface without changing consumers.)
 */
interface AlertDataSource {
    fun observeActiveAlerts(): Flow<List<Alert>>
}
