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

    // --- track hygiene: the bugs that made the scanner degrade over a session ---

    @Test
    fun `a track not seen recently is evicted so a recycled id starts clean`() {
        // ML Kit reuses tracking ids. Without eviction the next face to be handed
        // id 1 inherits this one's accumulated embedding and the fused vector
        // lands between two identities — the "only 18-20% after a while" failure.
        val agg = EmbeddingAggregator(maxFrames = 3, idleTimeoutMillis = 1_000L)
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        agg.fuse(1, a, nowMillis = 0L)
        assertEquals(2, agg.fuse(1, a, nowMillis = 100L).frames)

        agg.evictStale(nowMillis = 5_000L)

        val b = floatArrayOf(0f, 1f, 0f, 0f)
        val fresh = agg.fuse(1, b, nowMillis = 5_100L)
        assertEquals(1, fresh.frames)
        assertEquals(1f, fresh.embedding[1], 1e-5f)
        assertEquals(0f, fresh.embedding[0], 1e-5f)
    }

    @Test
    fun `a frame that disagrees with the track restarts it instead of polluting the mean`() {
        val agg = EmbeddingAggregator(maxFrames = 3, coherenceFloor = 0.5f)
        val person = floatArrayOf(1f, 0f, 0f, 0f)
        agg.fuse(7, person, nowMillis = 0L)
        agg.fuse(7, person, nowMillis = 10L)

        // Same tracking id, orthogonal embedding: a different face, or a frame so
        // bad it may as well be. Averaging the two would produce a vector matching
        // neither.
        val other = floatArrayOf(0f, 1f, 0f, 0f)
        val fused = agg.fuse(7, other, nowMillis = 20L)

        assertEquals(1, fused.frames)
        assertEquals(1f, fused.embedding[1], 1e-5f)
        assertEquals(0f, fused.embedding[0], 1e-5f)
    }

    @Test
    fun `a coherent frame still accumulates`() {
        val agg = EmbeddingAggregator(maxFrames = 3, coherenceFloor = 0.5f)
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val near = floatArrayOf(0.95f, 0.31f, 0f, 0f)  // cosine ~0.95, same person
        agg.fuse(2, a, nowMillis = 0L)
        assertEquals(2, agg.fuse(2, near, nowMillis = 10L).frames)
    }
}
