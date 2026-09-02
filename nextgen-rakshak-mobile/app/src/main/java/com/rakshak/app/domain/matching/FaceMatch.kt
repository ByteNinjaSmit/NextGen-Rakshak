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
    /** How many frames of this track were averaged into the matched embedding. */
    val framesFused: Int = 1,
)
