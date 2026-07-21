package com.rakshak.app.domain.matching

import android.graphics.Bitmap
import com.rakshak.app.data.model.Alert
import com.rakshak.app.ml.EmbeddingExtractor
import com.rakshak.app.ml.FaceDetector
import com.rakshak.app.ml.FacePreprocessor
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the on-device pipeline: detect faces -> preprocess -> extract
 * embedding -> compare against active alerts. (SOLID: Single Responsibility —
 * it only coordinates; each step lives in its own class.)
 */
class FaceMatcher(
    private val detector: FaceDetector,
    private val extractor: EmbeddingExtractor,
    private val comparator: EmbeddingComparator,
    private val threshold: Float = Constants.SIMILARITY_THRESHOLD,
) {
    /** Returns the best match per detected face that clears the threshold. */
    suspend fun match(frame: Bitmap, activeAlerts: List<Alert>): List<FaceMatch> =
        withContext(Dispatchers.Default) {
            if (activeAlerts.isEmpty()) return@withContext emptyList()

            val faces = detector.detect(frame)
            faces.mapNotNull { face ->
                val aligned = FacePreprocessor.cropAndResize(frame, face.boundingBox)
                val embedding = extractor.extract(aligned)

                var bestAlert: Alert? = null
                var bestScore = threshold
                for (alert in activeAlerts) {
                    if (alert.embedding.isEmpty()) continue
                    val score = comparator.similarity(embedding, alert.embedding)
                    if (score > bestScore) {
                        bestScore = score
                        bestAlert = alert
                    }
                }
                bestAlert?.let {
                    FaceMatch(
                        alertId = it.id,
                        confidence = bestScore,
                        boundingBox = face.boundingBox,
                        faceCrop = FacePreprocessor.cropForDisplay(frame, face.boundingBox),
                    )
                }
            }
        }
}
