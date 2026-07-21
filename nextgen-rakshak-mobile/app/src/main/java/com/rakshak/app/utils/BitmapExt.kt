package com.rakshak.app.utils

import android.graphics.Bitmap
import android.graphics.Matrix

/** Returns a copy rotated by [degrees]; the original when no rotation is needed. */
fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
