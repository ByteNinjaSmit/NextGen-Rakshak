package com.rakshak.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.scale
import com.rakshak.app.utils.Constants

/** Turns a detected face into the model's 112x112 input tile. */
object FacePreprocessor {

    private val alignPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * Produce the model input tile for [face].
     *
     * Preferred path: a **5-point similarity alignment** that warps the eyes,
     * nose and mouth corners onto MobileFaceNet's canonical template
     * ([FaceGeometry.TEMPLATE_112]). This removes in-plane rotation and scale
     * variation the model was never trained to ignore, and — critically — makes
     * the phone and the server (`functions/src/embedding.ts`) frame the same
     * child identically despite using different detectors.
     *
     * Fallback path (landmarks missing): the old behaviour — a square centred on
     * the detector box, padded by [Constants.FACE_CROP_MARGIN], resized to the
     * input size. Still mirrored by the server's fallback.
     */
    fun toModelInput(frame: Bitmap, face: DetectedFace): Bitmap {
        val lm = face.landmarks
        if (lm.canAlign) {
            aligned(frame, lm)?.let { return it }
        }
        return cropAndResize(frame, face.boundingBox)
    }

    /** 112x112 tile warped so eyes+nose hit the canonical template. */
    private fun aligned(frame: Bitmap, lm: FaceLandmarks): Bitmap? {
        val size = Constants.FACE_INPUT_SIZE
        // FaceGeometry's template is ordered [leftEye, rightEye, nose] purely by
        // ascending x. ML Kit's LEFT_EYE / RIGHT_EYE are the *subject's* eyes,
        // which land on either image side depending on whether the frame is
        // mirrored — so ignore that label and order the eye pair by x. Head roll
        // is already gated below 40 deg, well short of an x-order flip.
        val eyes = listOf(lm.leftEye!!, lm.rightEye!!).sortedBy { it.x }
        val n = lm.noseBase!!
        val srcXY = floatArrayOf(eyes[0].x, eyes[0].y, eyes[1].x, eyes[1].y, n.x, n.y)

        // Warp maps image -> template, for Canvas.drawBitmap(src, matrix).
        val matrix = FaceGeometry.similarityMatrix(srcXY, FaceGeometry.template(size)) ?: return null

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(frame, matrix, alignPaint)
        return out
    }

    /**
     * Square crop centred on [box], padded by [Constants.FACE_CROP_MARGIN],
     * resized to the model input. Kept for the no-landmark fallback and for
     * tests. MUST stay geometrically identical to the server's fallback crop.
     */
    fun cropAndResize(frame: Bitmap, box: Rect): Bitmap {
        val square = squareAround(frame, box, Constants.FACE_CROP_MARGIN)
        val cropped = Bitmap.createBitmap(frame, square.left, square.top, square.width(), square.height())
        return cropped.scale(Constants.FACE_INPUT_SIZE, Constants.FACE_INPUT_SIZE)
    }

    /** Largest in-bounds square centred on [box], padded by [margin] on each side. */
    private fun squareAround(frame: Bitmap, box: Rect, margin: Float): Rect {
        val side = (maxOf(box.width(), box.height()) * (1f + 2f * margin)).toInt()
            .coerceAtMost(minOf(frame.width, frame.height))
            .coerceAtLeast(1)

        val left = (box.centerX() - side / 2).coerceIn(0, frame.width - side)
        val top = (box.centerY() - side / 2).coerceIn(0, frame.height - side)
        return Rect(left, top, left + side, top + side)
    }

    /**
     * Crops the face for on-screen display next to the parent-submitted photo.
     * Unlike [toModelInput] this keeps the native resolution (so it isn't a
     * pixelated 112x112) and adds margin around the box so the volunteer sees
     * hair and chin, not just the bare face rectangle.
     */
    fun cropForDisplay(frame: Bitmap, box: Rect, marginRatio: Float = 0.3f): Bitmap {
        val padX = (box.width() * marginRatio).toInt()
        val padY = (box.height() * marginRatio).toInt()

        val left = (box.left - padX).coerceIn(0, frame.width - 1)
        val top = (box.top - padY).coerceIn(0, frame.height - 1)
        val right = (box.right + padX).coerceIn(left + 1, frame.width)
        val bottom = (box.bottom + padY).coerceIn(top + 1, frame.height)

        return Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
    }
}
