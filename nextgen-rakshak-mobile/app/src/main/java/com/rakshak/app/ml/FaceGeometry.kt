package com.rakshak.app.ml

import android.graphics.Matrix

/**
 * Face-alignment geometry shared by the whole pipeline.
 *
 * MobileFaceNet (and every ArcFace-family model) is trained on faces warped so
 * that key landmarks land on fixed pixel positions inside a 112x112 tile.
 * Feeding the model a face that is merely *cropped* instead of *aligned* leaves
 * in-plane rotation and scale variation that the model was never asked to be
 * invariant to, which flattens the cosine gap between "same child" and
 * "different child".
 *
 * We align on **left eye, right eye, nose** — the 3-point subset the server's
 * BlazeFace detector can also produce. The template below is the first three
 * rows of the canonical ArcFace 5-point reference for a 112x112 input. It MUST
 * stay identical to `ARCFACE_TEMPLATE` in `scripts/face_align.py` and `TEMPLATE`
 * in `functions/src/embedding.ts`.
 *
 * Coordinates are packed as flat `[x0, y0, x1, y1, ...]` float arrays so the
 * core solver has no Android dependency and is unit-testable on a plain JVM.
 */
object FaceGeometry {

    /** [leftEye, rightEye, noseBase] for a 112x112 tile, ordered by ascending x. */
    val TEMPLATE_112: FloatArray = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
    )

    /** The template scaled to an arbitrary square output size, same packing. */
    fun template(outputSize: Int): FloatArray {
        val k = outputSize / 112f
        return FloatArray(TEMPLATE_112.size) { TEMPLATE_112[it] * k }
    }

    /**
     * Least-squares 2D **similarity** transform (uniform scale + rotation +
     * translation, no shear) mapping [fromXY] onto [toXY]. Both are flat
     * `[x,y,...]` arrays of equal, even length holding >= 2 points.
     *
     * Model per point:  x' = a*x - b*y + tx ;  y' = b*x + a*y + ty
     *
     * @return `[a, b, tx, ty]`, or an empty array if the system is singular
     *   (degenerate landmarks) — callers fall back to a plain crop.
     */
    fun solveSimilarity(fromXY: FloatArray, toXY: FloatArray): FloatArray {
        require(fromXY.size == toXY.size && fromXY.size >= 4 && fromXY.size % 2 == 0) {
            "need matching flat point arrays of even length >= 4, got ${fromXY.size} / ${toXY.size}"
        }

        val ata = Array(4) { DoubleArray(4) }
        val aty = DoubleArray(4)

        fun addRow(r: DoubleArray, target: Double) {
            for (i in 0 until 4) {
                aty[i] += r[i] * target
                for (j in 0 until 4) ata[i][j] += r[i] * r[j]
            }
        }

        var i = 0
        while (i < fromXY.size) {
            val x = fromXY[i].toDouble()
            val y = fromXY[i + 1].toDouble()
            addRow(doubleArrayOf(x, -y, 1.0, 0.0), toXY[i].toDouble())       // x'
            addRow(doubleArrayOf(y, x, 0.0, 1.0), toXY[i + 1].toDouble())    // y'
            i += 2
        }

        val p = solve4x4(ata, aty) ?: return FloatArray(0)
        return floatArrayOf(p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
    }

    /**
     * Android convenience: the similarity transform from [fromXY] to [toXY] as a
     * [Matrix] ready for `Canvas.drawBitmap(src, matrix, paint)`. Returns null
     * when the solve is singular.
     */
    fun similarityMatrix(fromXY: FloatArray, toXY: FloatArray): Matrix? {
        val p = solveSimilarity(fromXY, toXY)
        if (p.isEmpty()) return null
        val a = p[0]; val b = p[1]; val tx = p[2]; val ty = p[3]
        return Matrix().apply {
            setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f))
        }
    }

    /**
     * Gaussian elimination with partial pivoting + back-substitution for a 4x4
     * system `m x = rhs`. Returns `x`, or null if the matrix is singular.
     */
    private fun solve4x4(m: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
        val n = 4
        // Augmented matrix [m | rhs], deep-copied so the caller's arrays are untouched.
        val a = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) m[r][c] else rhs[r] } }

        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (kotlin.math.abs(a[r][col]) > kotlin.math.abs(a[pivot][col])) pivot = r
            }
            if (kotlin.math.abs(a[pivot][col]) < 1e-12) return null
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp

            // Eliminate `col` from the rows below only (forward elimination).
            for (r in col + 1 until n) {
                val f = a[r][col] / a[col][col]
                for (c in col..n) a[r][c] -= f * a[col][c]
            }
        }

        // Back-substitution.
        val x = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var acc = a[row][n]
            for (c in row + 1 until n) acc -= a[row][c] * x[c]
            x[row] = acc / a[row][row]
        }
        return x
    }
}
