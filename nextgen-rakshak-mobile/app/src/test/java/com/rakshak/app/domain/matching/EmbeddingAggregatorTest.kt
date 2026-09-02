package com.rakshak.app.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EmbeddingAggregatorTest {

    private fun norm(v: FloatArray) = sqrt(v.fold(0f) { s, x -> s + x * x })

    @Test
    fun `output is always L2 normalised`() {
        val agg = EmbeddingAggregator(maxFrames = 3)
        val f = agg.fuse(1, floatArrayOf(3f, 4f, 0f)) // norm 5
        assertEquals(1.0, norm(f.embedding).toDouble(), 1e-5)
    }

    @Test
    fun `fusing consistent frames converges to that direction`() {
        val agg = EmbeddingAggregator(maxFrames = 5)
        val target = floatArrayOf(1f, 0f, 0f)
        var last = FloatArray(3)
        repeat(4) { last = agg.fuse(7, floatArrayOf(1f, 0.02f, -0.01f)).embedding }
        // cosine with the clean direction should be very close to 1.
        val cos = last[0] * target[0] + last[1] * target[1] + last[2] * target[2]
        assertTrue("cos=$cos", cos > 0.999)
    }

    @Test
    fun `frame count caps at maxFrames via sliding window`() {
        val agg = EmbeddingAggregator(maxFrames = 3)
        var frames = 0
        repeat(10) { frames = agg.fuse(1, floatArrayOf(0f, 1f, 0f)).frames }
        assertEquals(3, frames)
    }

    @Test
    fun `null track id is treated as a single fresh frame`() {
        val agg = EmbeddingAggregator(maxFrames = 3)
        val a = agg.fuse(null, floatArrayOf(0f, 3f, 4f))
        val b = agg.fuse(null, floatArrayOf(4f, 0f, 3f))
        assertEquals(1, a.frames)
        assertEquals(1, b.frames)
        // Second call not influenced by the first.
        assertEquals(0.8, b.embedding[0].toDouble(), 1e-5)
    }

    @Test
    fun `forget and reset drop history`() {
        val agg = EmbeddingAggregator(maxFrames = 3)
        agg.fuse(1, floatArrayOf(1f, 0f, 0f))
        agg.fuse(1, floatArrayOf(1f, 0f, 0f))
        agg.forget(1)
        assertEquals(1, agg.fuse(1, floatArrayOf(1f, 0f, 0f)).frames)

        agg.fuse(2, floatArrayOf(0f, 1f, 0f))
        agg.reset()
        assertEquals(1, agg.fuse(2, floatArrayOf(0f, 1f, 0f)).frames)
    }
}
