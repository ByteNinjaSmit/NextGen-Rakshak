package com.rakshak.app.domain.matching

import android.graphics.Rect

/** Result of matching a detected face against the active alerts. */
data class FaceMatch(
    val alertId: String,
    val confidence: Float,
    val boundingBox: Rect,
)
