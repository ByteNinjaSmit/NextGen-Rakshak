package com.rakshak.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure-JVM checks on the alignment solver. The transform math must match the
 * TypeScript (`functions/src/embedding.ts`) and Python (`scripts/face_align.py`)
 * mirrors bit-for-bit given the same inputs.
 */
class FaceGeometryTest {

    private fun apply(p: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val (a, b, tx, ty) = arrayOf(p[0], p[1], p[2], p[3])
        return (a * x - b * y + tx) to (b * x + a * y + ty)
    }

    @Test
    fun `recovers known scale rotation translation`() {
        val template = FaceGeometry.TEMPLATE_112
        val scale = 2.5f
        val theta = Math.toRadians(18.0)
        val ox = 60f
        val oy = 40f

        // Build "detected" points by transforming the template with a known S/R/T.
        val detected = FloatArray(template.size)
        var i = 0
        while (i < template.size) {
            val x = template[i]
            val y = template[i + 1]
            detected[i] = (scale * (cos(theta) * x - sin(theta) * y)).toFloat() + ox
            detected[i + 1] = (scale * (sin(theta) * x + cos(theta) * y)).toFloat() + oy
            i += 2
        }

        val p = FaceGeometry.solveSimilarity(template, detected)
        assertEquals(4, p.size)

        // Recovered scale = hypot(a, b); recovered angle = atan2(b, a).
        assertEquals(scale.toDouble(), hypot(p[0], p[1]).toDouble(), 1e-4)
        assertEquals(Math.toDegrees(theta), Math.toDegrees(Math.atan2(p[1].toDouble(), p[0].toDouble())), 1e-3)

        // Forward-applying the solution reproduces the detected points.
        var j = 0
        while (j < template.size) {
            val (px, py) = apply(p, template[j], template[j + 1])
            assertEquals(detected[j].toDouble(), px.toDouble(), 1e-2)
            assertEquals(detected[j + 1].toDouble(), py.toDouble(), 1e-2)
            j += 2
        }
    }

    @Test
    fun `identity maps a set onto itself`() {
        val pts = floatArrayOf(10f, 10f, 90f, 12f, 50f, 70f)
        val p = FaceGeometry.solveSimilarity(pts, pts)
        assertEquals(1.0, hypot(p[0], p[1]).toDouble(), 1e-6)
        assertEquals(0.0, p[2].toDouble(), 1e-4)
        assertEquals(0.0, p[3].toDouble(), 1e-4)
    }

    @Test
    fun `degenerate source points return empty`() {
        // All source landmarks at the same spot -> no scale/rotation is defined.
        val from = floatArrayOf(5f, 5f, 5f, 5f, 5f, 5f)
        val to = floatArrayOf(0f, 0f, 10f, 0f, 5f, 9f)
        assertTrue(FaceGeometry.solveSimilarity(from, to).isEmpty())
    }

    @Test
    fun `template scales linearly with output size`() {
        val at224 = FaceGeometry.template(224)
        for (k in FaceGeometry.TEMPLATE_112.indices) {
            assertEquals(FaceGeometry.TEMPLATE_112[k] * 2f, at224[k], 1e-4f)
        }
    }
}
