package com.rakshak.app.domain.matching

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.rakshak.app.data.model.Alert
import com.rakshak.app.ml.EmbeddingExtractor
import com.rakshak.app.ml.FaceDetector
import com.rakshak.app.ml.FacePreprocessor
import com.rakshak.app.ml.ImageQuality
import com.rakshak.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The on-device scanning pipeline, one camera frame at a time:
 *
 * ```
 * detect + landmarks
 *   -> per face: suppression check -> frontality gate -> 3-point alignment
 *      -> quality gate -> embed -> fuse across frames of the track
 *      -> score against the alert index -> confirmation state machine
 * ```
 *
 * Three properties this is built around, in priority order:
 *
 * **1. The overlay never goes dark.** Detection results are turned into
 * [FaceBox]es before anything can fail, and every later stage is wrapped
 * per-face. A bad crop, a detector hiccup or a model error costs one face on one
 * frame; it can never blank the tracking boxes or stall the loop, because a
 * scanner that looks dead is one a volunteer walks away from.
 *
 * **2. Work is skipped, not repeated.** The expensive step is the model, so a
 * face is embedded only if it can produce a usable answer: tracks the volunteer
 * already judged are suppressed before alignment, non-frontal and low-quality
 * faces never reach the interpreter, and the alert side is normalised once into
 * an [AlertIndex] instead of once per (face x alert) pair per frame.
 *
 * **3. Evidence accumulates per identity, not per frame.** [EmbeddingAggregator]
 * averages a track's embeddings and [TrackRegistry] requires a mid-band score to
 * repeat before it is surfaced — while a strong score still fires from a single
 * frame. That is what makes the scanner both fast and quiet.
 *
 * Stateful across frames; call [reset] when a scan session ends.
 */
class FaceMatcher(
    private val detector: FaceDetector,
    private val extractor: EmbeddingExtractor,
    private val comparator: EmbeddingComparator,
    private val aggregator: EmbeddingAggregator = EmbeddingAggregator(Constants.EMBEDDING_FUSION_FRAMES),
    private val tracks: TrackRegistry = TrackRegistry(),
    private val threshold: Float = Constants.SIMILARITY_THRESHOLD,
    private val strongThreshold: Float = Constants.STRONG_MATCH_THRESHOLD,
    private val maxYaw: Float = Constants.MAX_FACE_YAW_DEGREES,
    private val maxRoll: Float = Constants.MAX_FACE_ROLL_DEGREES,
) {
    /** Drop all per-track history — accumulated embeddings, runs and verdicts. */
    fun reset() {
        aggregator.reset()
        tracks.reset()
    }

    /**
     * Record that the volunteer rejected the face on [trackingId], so the scanner
     * stops offering it. Scoped to the one track rather than resetting everything:
     * a "Not a match" on one child says nothing about the others in frame, and
     * discarding their accumulated evidence would make the scanner start over on
     * faces it was already close to confirming.
     */
    fun rejectTrack(trackingId: Int?) {
        tracks.reject(trackingId)
        aggregator.forget(trackingId)
    }

    /** Record that a sighting was reported for [trackingId]; stop re-offering it. */
    fun markReported(trackingId: Int?) {
        tracks.markReported(trackingId)
        aggregator.forget(trackingId)
    }

    /** Extract an embedding directly from an aligned model tile (alert photos). */
    fun extractTileEmbedding(tile: Bitmap): FloatArray = extractor.extract(tile)

    /**
     * The width the device model emits, probed once by running a blank tile.
     * Probing beats reading a TFLite-specific property off the extractor: it
     * works for any [EmbeddingExtractor] and cannot disagree with what the model
     * actually returns during a scan.
     */
    private val embeddingWidth: Int by lazy { extractor.extract(BLANK_TILE).size }

    /**
     * Prepare [alerts] for scanning. Built here rather than in the ViewModel so
     * the comparator injected into this matcher is the one that scores — the
     * index and the scan loop can never end up on different definitions of
     * similarity.
     */
    fun buildIndex(alerts: List<Alert>): AlertIndex =
        AlertIndex.build(alerts, embeddingWidth, comparator)

    /** An index that matches nothing, for before the alerts have loaded. */
    fun emptyIndex(): AlertIndex = AlertIndex.empty(comparator)

    /** Backward-compatible entry point: just the matches. */
    suspend fun match(frame: Bitmap, index: AlertIndex): List<FaceMatch> =
        scanFrame(frame, index).matches

    /**
     * Run the full pipeline on [frame] against a prepared [index].
     *
     * The index is built by the caller (once per alert-list change) rather than
     * here (once per frame) — normalising every alert embedding ten times a
     * second is pure waste, and the "wrong embedding width" fault it detects is a
     * deployment problem that deserves to be reported once, not logged per frame.
     */
    suspend fun scanFrame(frame: Bitmap, index: AlertIndex): ScanFrameResult =
        withContext(Dispatchers.Default) {
            val startedAt = SystemClock.elapsedRealtime()
            val now = System.currentTimeMillis()
            // Both per-track stores must expire together. TrackRegistry holds the
            // volunteer's verdicts, EmbeddingAggregator holds the accumulated
            // embedding; leaving either behind lets a recycled ML Kit tracking id
            // inherit the previous face's state.
            tracks.evictStale(now)
            aggregator.evictStale(now)

            val detected = runCatching { detector.detect(frame) }
                .onFailure { Log.w(TAG, "Face detection failed on this frame", it) }
                .getOrDefault(emptyList())

            val frameW = frame.width.toFloat().coerceAtLeast(1f)
            val frameH = frame.height.toFloat().coerceAtLeast(1f)

            // Boxes first, and unconditionally: whatever happens below, the
            // volunteer keeps seeing the scanner track faces in real time.
            val boxes = detected.map { face ->
                FaceBox(
                    left = (face.boundingBox.left / frameW).coerceIn(0f, 1f),
                    top = (face.boundingBox.top / frameH).coerceIn(0f, 1f),
                    right = (face.boundingBox.right / frameW).coerceIn(0f, 1f),
                    bottom = (face.boundingBox.bottom / frameH).coerceIn(0f, 1f),
                    isFrontal = face.isFrontal(maxYaw, maxRoll),
                    trackingId = face.trackingId,
                )
            }.toMutableList()

            var nonFrontal = 0
            var lowQuality = 0
            var suppressed = 0
            var embedded = 0
            var errors = 0
            var topScore: Float? = null
            val matches = mutableListOf<FaceMatch>()

            if (!index.isEmpty) {
                detected.forEachIndexed { i, face ->
                    if (tracks.isSuppressed(face.trackingId)) {
                        tracks.touch(face.trackingId, now)
                        suppressed++
                        return@forEachIndexed
                    }
                    if (!face.isFrontal(maxYaw, maxRoll)) {
                        tracks.touch(face.trackingId, now)
                        nonFrontal++
                        return@forEachIndexed
                    }

                    try {
                        val tile = FacePreprocessor.toModelInput(frame, face)
                        if (!ImageQuality.check(face.boundingBox, tile).ok) {
                            tracks.touch(face.trackingId, now)
                            lowQuality++
                            return@forEachIndexed
                        }

                        val fused = aggregator.fuse(face.trackingId, extractor.extract(tile), now)
                        embedded++

                        val scored = index.best(fused.embedding)
                        val score = scored?.score ?: -1f
                        if (scored != null && (topScore == null || score > topScore!!)) topScore = score

                        val verdict = tracks.observe(
                            trackId = face.trackingId,
                            score = score,
                            alertId = scored?.alert?.id,
                            threshold = threshold,
                            strongThreshold = strongThreshold,
                            nowMillis = now,
                        )
                        boxes[i] = boxes[i].copy(score = score.takeIf { scored != null })

                        if (verdict != TrackRegistry.Verdict.SURFACE || scored == null) return@forEachIndexed

                        boxes[i] = boxes[i].copy(isMatch = true)
                        matches += FaceMatch(
                            alertId = scored.alert.id,
                            confidence = score,
                            boundingBox = face.boundingBox,
                            faceCrop = FacePreprocessor.cropForDisplay(frame, face.boundingBox),
                            framesFused = fused.frames,
                            trackingId = face.trackingId,
                        )
                    } catch (e: Throwable) {
                        // One face's failure must not cost the other faces in the
                        // frame, nor the overlay, nor the loop. Count it and move on.
                        errors++
                        Log.w(TAG, "Embed/compare failed for a detected face; keeping its box", e)
                    }
                }
            }

            val diagnostics = ScanDiagnostics(
                detected = detected.size,
                nonFrontal = nonFrontal,
                lowQuality = lowQuality,
                suppressed = suppressed,
                embedded = embedded,
                errors = errors,
                alertsScored = index.size,
                alertsMissingEmbedding = index.missing,
                alertsWrongWidth = index.unusable,
                topScore = topScore,
                frameMillis = SystemClock.elapsedRealtime() - startedAt,
                frameWidth = frame.width,
                frameHeight = frame.height,
            )

            ScanFrameResult(
                detectedFaces = boxes,
                matches = matches,
                statusMessage = statusFor(diagnostics, matches.isNotEmpty()),
                diagnostics = diagnostics,
            )
        }

    /**
     * Turn the frame's diagnostics into one line the volunteer can act on.
     *
     * Ordered by what the volunteer should do about it, most urgent first — a
     * broken deployment outranks a blurred face, which outranks "keep looking".
     * The percentage is deliberately shown even below the threshold: seeing 48%
     * climb toward a match is what tells someone the scan is live and worth
     * holding steady for.
     */
    private fun statusFor(d: ScanDiagnostics, matched: Boolean): String = when {
        matched -> "Possible match — confirm below"

        d.alertsWrongWidth > 0 ->
            "Face model mismatch — alerts cannot be matched on this build"

        d.errors > 0 && d.errors == d.detected ->
            "Face model error — check ${Constants.MODEL_ASSET}"

        d.alertsScored == 0 && d.alertsMissingEmbedding > 0 ->
            "Preparing ${d.alertsMissingEmbedding} alert photo(s)..."

        d.alertsScored == 0 -> "No active alerts. Point the camera at faces."

        d.detected == 0 -> "Scanning for ${d.alertsScored} child alert(s)..."

        d.embedded == 0 && d.suppressed == d.detected ->
            "${d.detected} face(s) already reviewed"

        d.embedded == 0 && d.nonFrontal > 0 ->
            "${d.nonFrontal} face(s) turned away — ask them to look at the camera"

        d.embedded == 0 && d.lowQuality > 0 ->
            "Face too small or blurred — move closer and hold steady"

        d.topScore != null ->
            "${d.detected} face(s) · closest ${(d.topScore * 100).toInt()}%"

        else -> "${d.detected} face(s) detected"
    }

    private companion object {
        const val TAG = "FaceMatcher"

        /**
         * A throwaway tile used only to ask the model its output width. Cheaper
         * and far less fragile than reaching into the extractor for a
         * TFLite-specific property, which would tie this class to one backend.
         */
        val BLANK_TILE: Bitmap by lazy {
            Bitmap.createBitmap(
                Constants.FACE_INPUT_SIZE,
                Constants.FACE_INPUT_SIZE,
                Bitmap.Config.ARGB_8888,
            )
        }
    }
}
