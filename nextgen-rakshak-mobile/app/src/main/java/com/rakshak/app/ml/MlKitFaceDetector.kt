package com.rakshak.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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
            .setMinFaceSize(0.1f)
            .build()
    )

    override suspend fun detect(frame: Bitmap): List<DetectedFace> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(frame, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    cont.resume(
                        faces.map {
                            DetectedFace(
                                boundingBox = it.boundingBox,
                                headEulerAngleY = it.headEulerAngleY,
                                headEulerAngleZ = it.headEulerAngleZ,
                            )
                        }
                    )
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
