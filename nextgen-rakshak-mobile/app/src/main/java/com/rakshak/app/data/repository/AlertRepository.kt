package com.rakshak.app.data.repository

import com.rakshak.app.data.datasource.AlertDataSource
import com.rakshak.app.data.model.Alert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Abstraction consumers depend on (SOLID: Dependency Inversion). */
interface AlertRepository {
    fun observeActiveAlerts(): Flow<List<Alert>>
}

/**
 * Merges alerts from every source (Firestore online + offline mesh), de-duped by
 * id. Online data wins when the same alert arrives on both. Adding a source
 * needs no change to consumers (SOLID: Open/Closed).
 */
class DefaultAlertRepository(
    private val online: AlertDataSource,
    private val mesh: AlertDataSource,
) : AlertRepository {

    override fun observeActiveAlerts(): Flow<List<Alert>> =
        combine(online.observeActiveAlerts(), mesh.observeActiveAlerts()) { onlineAlerts, meshAlerts ->
            val byId = LinkedHashMap<String, Alert>()
            meshAlerts.forEach { byId[it.id] = it }
            onlineAlerts.forEach { byId[it.id] = it } // online overrides mesh copy
            byId.values.toList()
        }
}
