package com.rakshak.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.scale
import com.rakshak.app.utils.Constants

/** Crops a detected face out of a frame and resizes it to the model input size. */
object FacePreprocessor {

    /**
     * Crops the face to the model input.
     *
     * The crop is a **square centred on the detector box**, not the raw box. Two
     * reasons, both of which affect whether a true match clears the 0.75 threshold:
     *
     * 1. Squashing a non-square box into a square 112x112 input distorts the face
     *    by a factor that depends on the box's aspect ratio.
     * 2. The server computes the alert embedding with a different detector
     *    (BlazeFace) whose boxes frame faces differently from ML Kit's. Deriving a
     *    square from the box centre and size makes the two pipelines agree
     *    geometrically instead of inheriting each detector's framing convention.
     *
     * Any change here must be mirrored in `functions/src/embedding.ts`.
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
     * Unlike [cropAndResize] this keeps the native resolution (so it isn't a
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
