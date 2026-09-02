package com.rakshak.app.domain.matching

import android.graphics.Bitmap
import com.rakshak.app.data.model.Alert
import com.rakshak.app.ml.EmbeddingExtractor
import com.rakshak.app.ml.FaceDetector
import com.rakshak.app.ml.FacePreprocessor
import com.rakshak.app.ml.ImageQuality
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the on-device pipeline:
 *   detect + landmarks -> frontality gate -> align to template -> quality gate
 *   -> embed -> fuse across frames of the same track -> compare to active alerts.
 *
 * (SOLID: Single Responsibility — it only coordinates; each step lives in its
 * own class.) Stateful across frames because of the multi-frame [aggregator];
 * call [reset] when a scan session ends or a pending match is dismissed.
 */
class FaceMatcher(
    private val detector: FaceDetector,
    private val extractor: EmbeddingExtractor,
    private val comparator: EmbeddingComparator,
    private val aggregator: EmbeddingAggregator = EmbeddingAggregator(Constants.EMBEDDING_FUSION_FRAMES),
    private val threshold: Float = Constants.SIMILARITY_THRESHOLD,
    private val strongThreshold: Float = Constants.STRONG_MATCH_THRESHOLD,
    private val fusionFrames: Int = Constants.EMBEDDING_FUSION_FRAMES,
    private val maxYaw: Float = Constants.MAX_FACE_YAW_DEGREES,
    private val maxRoll: Float = Constants.MAX_FACE_ROLL_DEGREES,
) {
    /** Drop all per-track embedding history. */
    fun reset() = aggregator.reset()

    /** Returns the best match per detected face that clears the threshold. */
    suspend fun match(frame: Bitmap, activeAlerts: List<Alert>): List<FaceMatch> =
        withContext(Dispatchers.Default) {
            if (activeAlerts.isEmpty()) return@withContext emptyList()
            val alertsWithEmbedding = activeAlerts.filter { it.embedding.isNotEmpty() }
            if (alertsWithEmbedding.isEmpty()) return@withContext emptyList()

            // Discard non-frontal faces before the expensive steps: they cannot
            // match reliably and alignment on a profile is meaningless.
            val faces = detector.detect(frame).filter { it.isFrontal(maxYaw, maxRoll) }

            faces.mapNotNull { face ->
                val tile = FacePreprocessor.toModelInput(frame, face)
                if (!ImageQuality.check(face.boundingBox, tile).ok) {
                    // A bad crop for a tracked face should not poison its running
                    // mean, but also should not reset it — just skip this frame.
                    return@mapNotNull null
                }

                val raw = extractor.extract(tile)
                val fused = aggregator.fuse(face.trackingId, raw)

                var bestAlert: Alert? = null
                var bestScore = threshold
                for (alert in alertsWithEmbedding) {
                    val score = comparator.similarity(fused.embedding, alert.embedding)
                    if (score > bestScore) {
                        bestScore = score
                        bestAlert = alert
                    }
                }

                val alert = bestAlert ?: return@mapNotNull null
                // Surface once the evidence is either deep (enough fused frames)
                // or overwhelming (one very strong frame).
                val ready = fused.frames >= fusionFrames || bestScore >= strongThreshold
                if (!ready) return@mapNotNull null

                aggregator.forget(face.trackingId)
                FaceMatch(
                    alertId = alert.id,
                    confidence = bestScore,
                    boundingBox = face.boundingBox,
                    faceCrop = FacePreprocessor.cropForDisplay(frame, face.boundingBox),
                    framesFused = fused.frames,
                )
            }
        }
}
