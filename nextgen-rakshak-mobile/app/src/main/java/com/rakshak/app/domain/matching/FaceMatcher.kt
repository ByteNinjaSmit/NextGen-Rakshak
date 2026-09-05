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

    /** Extract a 128-d vector directly from an aligned model tile. */
    fun extractTileEmbedding(tile: Bitmap): FloatArray = extractor.extract(tile)

    /** Returns the best match per detected face that clears the threshold (backward compatible). */
    suspend fun match(frame: Bitmap, activeAlerts: List<Alert>): List<FaceMatch> =
        scanFrame(frame, activeAlerts).matches

    /**
     * Executes the full scanning pipeline on [frame]:
     * 1. Detects all faces (so the UI always has real-time face tracking boxes).
     * 2. If active alerts with embeddings exist, filters frontal/quality faces and performs embedding & matching.
     * 3. Returns a [ScanFrameResult] with detected face boxes, status guidance, and any candidate matches.
     */
    suspend fun scanFrame(frame: Bitmap, activeAlerts: List<Alert>): ScanFrameResult =
        withContext(Dispatchers.Default) {
            val allDetected = detector.detect(frame)
            if (allDetected.isEmpty()) {
                return@withContext ScanFrameResult(
                    detectedFaces = emptyList(),
                    matches = emptyList(),
                    statusMessage = if (activeAlerts.isEmpty()) "No active alerts. Point camera at faces." else "Looking for faces...",
                )
            }

            val alertsWithEmbedding = activeAlerts.filter { it.embedding.isNotEmpty() }
            val frameW = frame.width.toFloat().coerceAtLeast(1f)
            val frameH = frame.height.toFloat().coerceAtLeast(1f)

            // If no alerts exist with embeddings, return the detected faces so the user sees live tracking!
            if (alertsWithEmbedding.isEmpty()) {
                val faceBoxes = allDetected.map { face ->
                    FaceBox(
                        left = (face.boundingBox.left / frameW).coerceIn(0f, 1f),
                        top = (face.boundingBox.top / frameH).coerceIn(0f, 1f),
                        right = (face.boundingBox.right / frameW).coerceIn(0f, 1f),
                        bottom = (face.boundingBox.bottom / frameH).coerceIn(0f, 1f),
                        isFrontal = face.isFrontal(maxYaw, maxRoll),
                        trackingId = face.trackingId,
                        isMatch = false,
                    )
                }
                val msg = if (activeAlerts.isEmpty()) {
                    "${allDetected.size} face(s) detected. No active alerts."
                } else {
                    "${allDetected.size} face(s) detected. Alerts missing embeddings."
                }
                return@withContext ScanFrameResult(
                    detectedFaces = faceBoxes,
                    matches = emptyList(),
                    statusMessage = msg,
                )
            }

            val matches = mutableListOf<FaceMatch>()
            val matchedBoxes = mutableSetOf<Int>() // indices in allDetected

            allDetected.forEachIndexed { index, face ->
                if (!face.isFrontal(maxYaw, maxRoll)) return@forEachIndexed

                val tile = FacePreprocessor.toModelInput(frame, face)
                if (!ImageQuality.check(face.boundingBox, tile).ok) return@forEachIndexed

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

                val alert = bestAlert ?: return@forEachIndexed
                aggregator.forget(face.trackingId)
                matchedBoxes.add(index)
                matches.add(
                    FaceMatch(
                        alertId = alert.id,
                        confidence = bestScore,
                        boundingBox = face.boundingBox,
                        faceCrop = FacePreprocessor.cropForDisplay(frame, face.boundingBox),
                        framesFused = fused.frames,
                    )
                )
            }

            val faceBoxes = allDetected.mapIndexed { index, face ->
                FaceBox(
                    left = (face.boundingBox.left / frameW).coerceIn(0f, 1f),
                    top = (face.boundingBox.top / frameH).coerceIn(0f, 1f),
                    right = (face.boundingBox.right / frameW).coerceIn(0f, 1f),
                    bottom = (face.boundingBox.bottom / frameH).coerceIn(0f, 1f),
                    isFrontal = face.isFrontal(maxYaw, maxRoll),
                    trackingId = face.trackingId,
                    isMatch = index in matchedBoxes,
                )
            }

            val statusMsg = when {
                matches.isNotEmpty() -> "Potential match detected!"
                allDetected.isNotEmpty() -> "Scanning ${allDetected.size} face(s) against ${alertsWithEmbedding.size} alert(s)..."
                else -> "Point camera at faces"
            }

            ScanFrameResult(
                detectedFaces = faceBoxes,
                matches = matches,
                statusMessage = statusMsg,
            )
        }
}
