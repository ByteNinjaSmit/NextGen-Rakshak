package com.rakshak.app.data.datasource

import com.rakshak.app.data.model.Alert
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MeshAlertSourceTest {

    private fun alert(id: String, ageMillis: Long) = Alert(
        id = id,
        childName = "Priya",
        embedding = FloatArray(128) { 0.1f },
        timestamp = System.currentTimeMillis() - ageMillis,
    )

    @Test
    fun expiredAlertsAreFilteredOut() = runTest {
        val fresh = alert("fresh", ageMillis = 60_000L)
        val stale = alert("stale", ageMillis = Constants.ALERT_EXPIRY_MILLIS + 60_000L)
        val source = MeshAlertSource(MutableStateFlow(listOf(fresh, stale)))

        val emitted = source.observeActiveAlerts().first()

        assertEquals(listOf("fresh"), emitted.map { it.id })
    }

    @Test
    fun alertsAreReadFromSharedState_notRebuiltPerCollector() = runTest {
        // The mesh manager owns the list; a second collector — the scan screen
        // opening after Home received the alert — must see it too.
        val shared = MutableStateFlow(listOf(alert("a1", ageMillis = 0L)))

        val onHome = MeshAlertSource(shared).observeActiveAlerts().first()
        val onScan = MeshAlertSource(shared).observeActiveAlerts().first()

        assertEquals(listOf("a1"), onHome.map { it.id })
        assertEquals(onHome.map { it.id }, onScan.map { it.id })
    }
}
