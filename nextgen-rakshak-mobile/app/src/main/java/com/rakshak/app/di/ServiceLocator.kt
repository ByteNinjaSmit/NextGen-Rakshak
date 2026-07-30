package com.rakshak.app.di

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.rakshak.app.data.auth.AuthService
import com.rakshak.app.data.auth.FirebaseAuthService
import com.rakshak.app.data.auth.GoogleSignInClient
import com.rakshak.app.data.datasource.FirestoreAlertSource
import com.rakshak.app.data.datasource.FirestoreMatchSource
import com.rakshak.app.data.datasource.FirestoreVolunteerSource
import com.rakshak.app.data.datasource.MeshAlertSource
import com.rakshak.app.data.local.AppDatabase
import com.rakshak.app.data.local.VolunteerStore
import com.rakshak.app.data.repository.AlertRepository
import com.rakshak.app.data.repository.DefaultAlertRepository
import com.rakshak.app.data.repository.DefaultMatchRepository
import com.rakshak.app.data.repository.DefaultVolunteerRepository
import com.rakshak.app.data.repository.MatchRepository
import com.rakshak.app.data.repository.VolunteerRepository
import com.rakshak.app.domain.matching.CosineEmbeddingComparator
import com.rakshak.app.domain.matching.FaceMatcher
import com.rakshak.app.domain.usecase.ReportMatchUseCase
import com.rakshak.app.ml.MlKitFaceDetector
import com.rakshak.app.ml.TFLiteEmbeddingExtractor
import com.rakshak.app.networking.FcmTokenProvider
import com.rakshak.app.networking.mesh.MeshNetworkManager
import com.rakshak.app.utils.LocationProvider

/**
 * Minimal manual dependency injection. Keeps wiring in one place without pulling
 * in Hilt. Swap for Hilt if the graph grows.
 */
object ServiceLocator {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val authService: AuthService by lazy { FirebaseAuthService() }

    fun auth(): AuthService = authService

    @Volatile
    private var meshInstance: MeshNetworkManager? = null

    /** Singleton mesh manager — one radio connection shared app-wide. */
    fun mesh(context: Context): MeshNetworkManager = meshInstance ?: synchronized(this) {
        meshInstance ?: MeshNetworkManager(context.applicationContext).also { meshInstance = it }
    }

    fun alertRepository(context: Context): AlertRepository =
        DefaultAlertRepository(
            online = FirestoreAlertSource(firestore),
            mesh = MeshAlertSource(mesh(context)),
        )

    fun matchRepository(context: Context): MatchRepository =
        DefaultMatchRepository(
            source = FirestoreMatchSource(firestore, authService),
            pendingDao = AppDatabase.get(context).pendingMatchDao(),
            meshRelay = mesh(context)::relayMatch,
        )

    fun volunteerStore(context: Context): VolunteerStore =
        VolunteerStore(context.applicationContext)

    fun volunteerSource(): FirestoreVolunteerSource = FirestoreVolunteerSource(firestore)

    fun googleSignIn(context: Context): GoogleSignInClient =
        GoogleSignInClient(context.applicationContext)

    fun volunteerRepository(context: Context): VolunteerRepository =
        DefaultVolunteerRepository(
            source = volunteerSource(),
            fcmToken = FcmTokenProvider(),
            auth = authService,
            locationProvider = LocationProvider(context.applicationContext),
        )

    @Volatile
    private var extractorInstance: TFLiteEmbeddingExtractor? = null

    /**
     * Singleton embedding extractor. The TFLite interpreter holds native memory
     * and is never closed, so building one per scan session would reload the
     * model and leak the previous interpreter each time the screen is opened.
     */
    private fun embeddingExtractor(context: Context): TFLiteEmbeddingExtractor =
        extractorInstance ?: synchronized(this) {
            extractorInstance
                ?: TFLiteEmbeddingExtractor(context.applicationContext).also { extractorInstance = it }
        }

    fun faceMatcher(context: Context): FaceMatcher =
        FaceMatcher(
            detector = MlKitFaceDetector(),
            extractor = embeddingExtractor(context),
            comparator = CosineEmbeddingComparator(),
        )

    fun reportMatchUseCase(context: Context): ReportMatchUseCase =
        ReportMatchUseCase(matchRepository(context), LocationProvider(context.applicationContext))
}
