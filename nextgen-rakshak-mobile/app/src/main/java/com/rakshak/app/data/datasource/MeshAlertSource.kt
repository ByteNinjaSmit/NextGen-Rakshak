package com.rakshak.app.data.datasource

import com.rakshak.app.data.model.Alert
import com.rakshak.app.networking.mesh.MeshNetworkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Exposes alerts received over the offline mesh as a growing list. */
class MeshAlertSource(
    private val mesh: MeshNetworkManager,
) : AlertDataSource {

    override fun observeActiveAlerts(): Flow<List<Alert>> = flow {
        val byId = LinkedHashMap<String, Alert>()
        emit(emptyList())
        mesh.alerts.collect { alert ->
            byId[alert.id] = alert
            emit(byId.values.toList())
        }
    }
}
