package com.rakshak.app.ml

import android.graphics.Bitmap
import android.graphics.Rect

/** A face located in a frame. */
data class DetectedFace(val boundingBox: Rect, val headEulerAngleZ: Float)

/** Detects faces in a frame. (SOLID: Interface Segregation — lean, one job.) */
interface FaceDetector {
    suspend fun detect(frame: Bitmap): List<DetectedFace>
}
