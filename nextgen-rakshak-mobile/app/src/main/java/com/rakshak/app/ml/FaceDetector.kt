package com.rakshak.app.ml

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect

/**
 * Landmarks in image-pixel coordinates. All nullable: ML Kit omits a landmark it
 * cannot locate (e.g. an eye turned too far away).
 *
 * Alignment ([FacePreprocessor] -> [FaceGeometry]) uses the **two eye centres and
 * the nose base** only. That 3-point subset is what the server's BlazeFace
 * detector can also give (it reports a single mouth *centre*, not corners), so
 * pinning both pipelines to eyes+nose keeps their alignments identical. The mouth
 * corners are kept here for potential display/QA use, not for the transform.
 *
 * "left" / "right" follow ML Kit's convention — the subject's own — which
 * [FacePreprocessor] reconciles by ordering the eye points by x.
 */
data class FaceLandmarks(
    val leftEye: PointF?,
    val rightEye: PointF?,
    val noseBase: PointF?,
    val mouthLeft: PointF? = null,
    val mouthRight: PointF? = null,
) {
    /** The three points alignment needs are all present. */
    val canAlign: Boolean
        get() = leftEye != null && rightEye != null && noseBase != null
}

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
 * @param landmarks five-point landmarks for alignment, or null landmarks when the
 *   detector could not locate them.
 * @param trackingId stable across consecutive frames for the same physical face
 *   when the detector has tracking enabled; null otherwise. Used to average an
 *   identity's embedding over several frames instead of trusting one.
 */
data class DetectedFace(
    val boundingBox: Rect,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val landmarks: FaceLandmarks = FaceLandmarks(null, null, null, null, null),
    val trackingId: Int? = null,
) {
    /** True when the face is square-on enough to be worth embedding. */
    fun isFrontal(maxYaw: Float, maxRoll: Float): Boolean =
        kotlin.math.abs(headEulerAngleY) <= maxYaw && kotlin.math.abs(headEulerAngleZ) <= maxRoll
}

/** Detects faces in a frame. (SOLID: Interface Segregation — lean, one job.) */
interface FaceDetector {
    suspend fun detect(frame: Bitmap): List<DetectedFace>
}
