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
}
