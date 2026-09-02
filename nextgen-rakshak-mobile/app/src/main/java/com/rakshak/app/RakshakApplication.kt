package com.rakshak.app

import android.app.Application
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.firestore.FirebaseFirestore
import com.rakshak.app.data.datasource.FirestoreAlertSource
import com.rakshak.app.data.model.Alert
import com.rakshak.app.di.ServiceLocator
import com.rakshak.app.networking.MatchSyncWorker
import com.rakshak.app.networking.mesh.MeshThumbnail
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
    private val imageLoader by lazy { ImageLoader(this) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        MatchSyncWorker.schedule(this)
        ServiceLocator.connectivityMonitor(this) // start tracking internet for the mesh gateway bit

        val mesh = ServiceLocator.mesh(this)
        val auth = ServiceLocator.auth()

        // Bridge online -> mesh: push new alerts (with a face thumbnail baked in
        // so an offline peer can render the parent's photo) and closures out to
        // peers that have no way to see either for themselves.
        scope.launch {
            auth.uidFlow()
                .flatMapLatest { uid ->
                    if (uid == null) emptyFlow()
                    else FirestoreAlertSource(FirebaseFirestore.getInstance()).observeChanges()
                }
                .collect { snapshot ->
                    snapshot.closedIds.forEach(mesh::forget)
                    snapshot.active.forEach { alert ->
                        scope.launch { mesh.broadcast(withThumbnail(alert)) }
                    }
                }
        }

        // Upload matches that arrived over the mesh from offline volunteers, and
        // ACK each one back along the mesh so the relaying devices can stop
        // carrying it.
        //
        // Deliberately collected once, and not gated on auth state like the alert
        // bridge: `mesh.matches` replays its buffer to each new collector, so
        // restarting this on every sign-in would re-upload the last 32 sightings.
        // A write attempted while signed out is not lost — the repository queues
        // it in Room and MatchSyncWorker retries once there is a session.
        scope.launch {
            val repo = ServiceLocator.matchRepository(this@RakshakApplication)
            mesh.matches.collect { relayed ->
                runCatching { repo.report(relayed.report) }
                    .onSuccess { mesh.ackMatch(relayed.messageId) }
            }
        }

        // The mesh itself is brought up by MeshService, which MainActivity starts
        // once the runtime permissions are granted — a foreground service may not
        // be started from Application.onCreate when the process was spawned in the
        // background (FCM, WorkManager).
    }

    /**
     * Fetch the alert's photo once (this device has internet — it is the bridge)
     * and attach a ~2-3 KB thumbnail. Best-effort: on any failure the alert
     * travels without one and the offline dialog falls back to name + score.
     */
    private suspend fun withThumbnail(alert: Alert): Alert {
        if (alert.thumbnail != null || alert.imageUrl.isBlank()) return alert
        val bitmap = runCatching {
            val request = ImageRequest.Builder(this)
                .data(alert.imageUrl)
                .allowHardware(false)
                .build()
            ((imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull() ?: return alert
        val thumb = MeshThumbnail.encode(bitmap) ?: return alert
        return alert.copy(thumbnail = thumb)
    }
}
