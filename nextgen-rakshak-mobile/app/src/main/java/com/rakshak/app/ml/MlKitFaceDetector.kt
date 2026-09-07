package com.rakshak.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google ML Kit implementation of [FaceDetector]. Runs fully offline. */
class MlKitFaceDetector : FaceDetector {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // FAST, not ACCURATE. Detection is the slowest stage of the whole scan
            // loop — several times the cost of the embedding — and the loop is
            // single-flight, so its latency *is* the frame rate (roughly 4 fps on
            // ACCURATE against 10-15 on FAST). What ACCURATE buys is a tighter
            // box, and the box is no longer what frames the face: the 3-point
            // similarity warp in FacePreprocessor -> FaceGeometry positions the
            // tile from the landmarks. Landmark precision is close between the two
            // modes, so spending 3x the time for a box the pipeline barely uses
            // would trade the thing that matters most in a crowd — how many faces
            // get scanned per second — for one that barely moves the cosine.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            // Landmarks drive alignment. Without them the model only ever sees a
            // rotated, uncentred crop and the score gap collapses.
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            // Head-pose angles feed the frontality gate; without classification
            // ML Kit still fills the Euler angles, so no extra mode is needed.
            // Tracking IDs are what let the matcher accumulate evidence per
            // identity (EmbeddingAggregator) and remember the volunteer's verdicts
            // (TrackRegistry) instead of judging each frame in isolation.
            .enableTracking()
            // 0.1 of the frame width. Below this a face carries too few pixels to
            // embed usefully anyway (ImageQuality rejects it on MIN_FACE_PX), so
            // detecting it only costs time.
            .setMinFaceSize(0.1f)
            .build()
    )

    /**
     * Release the detector's native resources. ML Kit holds a native pipeline per
     * client, and the app builds detectors outside the scan loop too (alert photos
     * are embedded through one), so leaving them open leaks that pipeline.
     */
    fun close() = detector.close()

    override suspend fun detect(frame: Bitmap): List<DetectedFace> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(frame, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    cont.resume(
                        faces.map { face ->
                            DetectedFace(
                                boundingBox = face.boundingBox,
                                headEulerAngleY = face.headEulerAngleY,
                                headEulerAngleZ = face.headEulerAngleZ,
                                landmarks = FaceLandmarks(
                                    leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
                                    rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
                                    noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position,
                                    mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position,
                                    mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position,
                                ),
                                trackingId = face.trackingId,
                            )
                        }
                    )
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
