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
            // ACCURATE trades some latency for noticeably better landmark/box
            // precision than FAST. The scan loop is already single-flight
            // (ScanViewModel's busy gate lets only one frame through at a time),
            // so the extra cost per face is fine and a tighter box directly
            // improves the embedding — MobileFaceNet is sensitive to how the
            // face is framed going in.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            // Landmarks drive alignment (FacePreprocessor -> FaceGeometry). Without
            // them the model only ever sees a rotated/uncentred crop.
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            // Tracking IDs let the matcher average an identity's embedding across
            // consecutive frames rather than acting on a single noisy frame.
            .enableTracking()
            .setMinFaceSize(0.1f)
            .build()
    )

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
