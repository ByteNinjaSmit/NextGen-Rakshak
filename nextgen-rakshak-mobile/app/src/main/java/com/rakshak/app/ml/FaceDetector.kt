package com.rakshak.app.ml

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * A face located in a frame.
 *
 * Head-pose (Euler) angles are used to discard non-frontal faces before the
 * expensive embedding step: MobileFaceNet is trained on roughly frontal faces,
 * so a profile view produces an embedding that will not match even the correct
 * child — and may weakly match the wrong one.
 *
 * @param headEulerAngleY yaw — head turned left/right. The dominant frontality signal.
 * @param headEulerAngleZ roll — head tilted toward a shoulder.
 */
data class DetectedFace(
    val boundingBox: Rect,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
) {
    /** True when the face is square-on enough to be worth embedding. */
    fun isFrontal(maxYaw: Float, maxRoll: Float): Boolean =
        kotlin.math.abs(headEulerAngleY) <= maxYaw && kotlin.math.abs(headEulerAngleZ) <= maxRoll
}

/** Detects faces in a frame. (SOLID: Interface Segregation — lean, one job.) */
interface FaceDetector {
    suspend fun detect(frame: Bitmap): List<DetectedFace>
}
