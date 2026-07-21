package com.rakshak.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.scale
import com.rakshak.app.utils.Constants

/** Crops a detected face out of a frame and resizes it to the model input size. */
object FacePreprocessor {

    fun cropAndResize(frame: Bitmap, box: Rect): Bitmap {
        // Clamp the bounding box to the frame bounds to avoid out-of-range crops.
        val left = box.left.coerceIn(0, frame.width - 1)
        val top = box.top.coerceIn(0, frame.height - 1)
        val width = box.width().coerceAtMost(frame.width - left)
        val height = box.height().coerceAtMost(frame.height - top)

        val cropped = Bitmap.createBitmap(frame, left, top, width, height)
        return cropped.scale(Constants.FACE_INPUT_SIZE, Constants.FACE_INPUT_SIZE)
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
