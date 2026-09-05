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
)

/** Aggregated result of running face detection and alert matching on a single frame. */
data class ScanFrameResult(
    val detectedFaces: List<FaceBox> = emptyList(),
    val matches: List<FaceMatch> = emptyList(),
    val statusMessage: String? = null,
)
