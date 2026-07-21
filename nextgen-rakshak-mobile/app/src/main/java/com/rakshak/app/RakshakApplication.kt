package com.rakshak.app

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.rakshak.app.data.datasource.FirestoreAlertSource
import com.rakshak.app.di.ServiceLocator
import com.rakshak.app.networking.MatchSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Firebase auto-initialises via its ContentProvider.
 * Also bridges online <-> mesh so alerts reach offline peers and mesh-relayed
 * matches get uploaded by whichever device has internet.
 */
class RakshakApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MatchSyncWorker.schedule(this)

        val mesh = ServiceLocator.mesh(this)

        // Re-broadcast alerts received online to the offline mesh.
        scope.launch {
            FirestoreAlertSource(FirebaseFirestore.getInstance())
                .observeActiveAlerts()
                .collect { alerts -> alerts.forEach(mesh::broadcast) }
        }

        // Upload matches that arrived over the mesh from offline volunteers.
        scope.launch {
            ServiceLocator.auth().ensureSignedIn() // Firestore writes need request.auth
            val repo = ServiceLocator.matchRepository(this@RakshakApplication)
            mesh.matches.collect { repo.report(it) }
        }
    }
}
