package com.rakshak.app.domain.matching

import android.graphics.Bitmap
import android.graphics.Rect

/** Result of matching a detected face against the active alerts. */
data class FaceMatch(
    val alertId: String,
    val confidence: Float,
    val boundingBox: Rect,
    /** The matched face cropped from the live frame, shown beside the parent's photo. */
    val faceCrop: Bitmap,
    val framesFused: Int = 1,
    /**
     * The ML Kit track this match came from, carried through so a rejection or a
     * report can be recorded against the right face rather than against the whole
     * scan session. See [TrackRegistry].
     */
    val trackingId: Int? = null,
)

/** Bounding box in normalized coordinates (0..1 relative to camera frame width and height). */
data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isFrontal: Boolean = true,
    val trackingId: Int? = null,
    val isMatch: Boolean = false,
    /**
     * Best cosine this face reached on this frame, or null when it was never
     * scored (gated out, suppressed, or no alerts to score against). Drives the
     * live per-face readout so the volunteer can see the scanner working and aim
     * the camera better, instead of staring at a box that never changes.
     */
    val score: Float? = null,
)

/**
 * Why faces did not reach the model on a frame.
 *
 * This is the difference between "the scanner is working and nobody here is the
 * child" and "the scanner has silently done nothing for two minutes". Every
 * count here is a distinct, actionable cause with its own fix — step closer,
 * ask them to look up, find better light — so the status line can say which one
 * is happening instead of an unchanging "Scanning...".
 */
data class ScanDiagnostics(
    val detected: Int = 0,
    /** Faces turned too far away to embed. */
    val nonFrontal: Int = 0,
    /** Faces that failed the blur / size / exposure gate. */
    val lowQuality: Int = 0,
    /** Faces already rejected or reported by the volunteer, so not re-scored. */
    val suppressed: Int = 0,
    /** Faces actually run through the model this frame. */
    val embedded: Int = 0,
    /** Per-face failures inside align/embed/compare. */
    val errors: Int = 0,
    /** Alerts available to score against. */
    val alertsScored: Int = 0,
    /** Alerts skipped for a missing embedding. */
    val alertsMissingEmbedding: Int = 0,
    /** Alerts skipped for an embedding of the wrong width — a deployment fault. */
    val alertsWrongWidth: Int = 0,
    /** Best cosine seen this frame, or null if nothing was scored. */
    val topScore: Float? = null,
    /** Wall-clock cost of the whole frame, for the debug readout. */
    val frameMillis: Long = 0L,
    /**
     * Size of the analysed frame. [FaceBox] coordinates are normalised against
     * it, and the preview crops that frame to fill the screen, so the overlay
     * cannot place a box correctly without knowing the frame's aspect ratio —
     * normalised coordinates stretched straight onto the canvas land off the
     * face by tens of pixels on any phone whose screen is not 16:9.
     */
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
)

/** Aggregated result of running face detection and alert matching on a single frame. */
data class ScanFrameResult(
    val detectedFaces: List<FaceBox> = emptyList(),
    val matches: List<FaceMatch> = emptyList(),
    val statusMessage: String? = null,
    val diagnostics: ScanDiagnostics = ScanDiagnostics(),
)
