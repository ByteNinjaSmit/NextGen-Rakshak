package com.rakshak.app

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.rakshak.app.data.datasource.FirestoreAlertSource
import com.rakshak.app.di.ServiceLocator
import com.rakshak.app.networking.MatchSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Application entry point. Firebase auto-initialises via its ContentProvider.
 * Also bridges online <-> mesh so alerts reach offline peers and mesh-relayed
 * matches get uploaded by whichever device has internet.
 *
 * Both bridges are gated on there being a signed-in account, and are rebuilt
 * whenever that changes. They are deliberately *not* allowed to create a session
 * of their own: an anonymous sign-in here would give every launch a Firebase
 * account whether or not anyone signed in, which both masks the stale-profile
 * check in `LoginViewModel` (it treats a null uid as "the session is gone") and
 * mints throwaway accounts for users who never got past the login screen.
 */
class RakshakApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        MatchSyncWorker.schedule(this)

        val mesh = ServiceLocator.mesh(this)
        val auth = ServiceLocator.auth()

        // Bridge online -> mesh: push new alerts out to offline peers, and push
        // closures out too. A device with internet is the only one that can see
        // a case being resolved, so it has to carry that news to the ones without.
        scope.launch {
            auth.uidFlow()
                .flatMapLatest { uid ->
                    if (uid == null) emptyFlow()
                    else FirestoreAlertSource(FirebaseFirestore.getInstance()).observeChanges()
                }
                .collect { snapshot ->
                    snapshot.closedIds.forEach(mesh::forget)
                    snapshot.active.forEach(mesh::broadcast)
                }
        }

        // Upload matches that arrived over the mesh from offline volunteers.
        //
        // Deliberately collected once, and not gated on the auth state like the
        // alert bridge above: `mesh.matches` replays its buffer to each new
        // collector, so restarting this on every sign-in would re-upload the
        // last 32 sightings as duplicate documents. A write attempted while
        // signed out is not lost either — the repository queues it in Room and
        // MatchSyncWorker retries once there is a session.
        scope.launch {
            val repo = ServiceLocator.matchRepository(this@RakshakApplication)
            mesh.matches.collect { repo.report(it) }
        }
    }
}
