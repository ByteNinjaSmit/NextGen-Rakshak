package com.rakshak.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.rakshak.app.utils.Constants
import kotlin.math.abs

/**
 * Cheap gate that rejects face crops too poor to embed usefully.
 *
 * A blurred, tiny or badly-exposed face still produces a 128-d vector, but one
 * that is closer to noise than to the child's true embedding — which both hides
 * real matches and occasionally lights up a wrong alert. Screening here costs a
 * single pass over a 112x112 tile and skips the far more expensive interpreter
 * run when it would be wasted.
 */
object ImageQuality {

    data class Report(val ok: Boolean, val reason: String? = null) {
        companion object {
            val PASS = Report(true)
        }
    }

    /**
     * @param frameFace the face box in the full camera frame — used for the
     *   minimum-resolution check (a face only 40 px wide carries almost no
     *   identity signal however sharp it is).
     * @param modelTile the aligned/cropped 112x112 tile about to be embedded.
     */
    fun check(frameFace: Rect, modelTile: Bitmap): Report {
        val faceSide = minOf(frameFace.width(), frameFace.height())
        if (faceSide < Constants.MIN_FACE_PX) {
            return Report(false, "face too small (${faceSide}px)")
        }

        val stats = lumaStats(modelTile)
        if (stats.mean < Constants.MIN_FACE_LUMA || stats.mean > Constants.MAX_FACE_LUMA) {
            return Report(false, "poor exposure (luma ${stats.mean.toInt()})")
        }
        if (stats.laplacianVariance < Constants.MIN_SHARPNESS_VAR) {
            return Report(false, "too blurred (var ${stats.laplacianVariance.toInt()})")
        }
        return Report.PASS
    }

    private class LumaStats(val mean: Float, val laplacianVariance: Float)

    /**
     * Mean luminance and the variance of a 4-neighbour Laplacian over the tile.
     * Variance-of-Laplacian is the standard no-reference sharpness measure: a
     * sharp image has strong high-frequency edges (high variance), a blurred one
     * does not.
     */
    private fun lumaStats(bmp: Bitmap): LumaStats {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        val luma = FloatArray(w * h)
        var sum = 0.0
        for (i in px.indices) {
            val p = px[i]
            val y = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
            luma[i] = y
            sum += y
        }
        val mean = (sum / px.size).toFloat()

        var lapSum = 0.0
        var lapSqSum = 0.0
        var n = 0
        for (yy in 1 until h - 1) {
            for (xx in 1 until w - 1) {
                val idx = yy * w + xx
                val lap = 4f * luma[idx] - luma[idx - 1] - luma[idx + 1] - luma[idx - w] - luma[idx + w]
                val a = abs(lap).toDouble()
                lapSum += a
                lapSqSum += a * a
                n++
            }
        }
        val lapMean = lapSum / n
        val variance = (lapSqSum / n - lapMean * lapMean).toFloat()
        return LumaStats(mean, variance)
    }
}
