package com.rakshak.app.domain.matching

import kotlin.math.sqrt

/**
 * Averages a tracked face's embedding over consecutive frames.
 *
 * A single frame's embedding carries detector jitter, motion blur and momentary
 * expression. Averaging the L2-normalised embeddings of the same tracked face
 * across a few frames pulls the estimate toward the identity's true direction,
 * which widens the cosine gap between the correct child and everyone else and
 * cuts spurious single-frame candidates.
 *
 * Not thread-safe: the scan loop is single-flight, so all calls arrive in order
 * on one coroutine.
 */
class EmbeddingAggregator(private val maxFrames: Int) {

    private class Track(val sum: FloatArray, var count: Int)

    private val tracks = HashMap<Int, Track>()

    /**
     * Fold [embedding] into the running mean for [trackId] and return the
     * current fused, L2-normalised embedding together with how many frames it
     * now represents. A null [trackId] (detector reported no tracking) is
     * treated as a fresh single-frame observation every time.
     */
    fun fuse(trackId: Int?, embedding: FloatArray): Fused {
        val unit = l2normalize(embedding)
        if (trackId == null) return Fused(unit, 1)

        val track = tracks.getOrPut(trackId) { Track(FloatArray(unit.size), 0) }
        if (track.count >= maxFrames) {
            // Slide the window: drop the oldest weight's worth so a face that
            // stays in view keeps adapting instead of freezing on its first N frames.
            val scale = (maxFrames - 1f) / maxFrames
            for (i in track.sum.indices) track.sum[i] *= scale
            track.count = maxFrames - 1
        }
        for (i in track.sum.indices) track.sum[i] += unit[i]
        track.count++

        return Fused(l2normalize(track.sum), track.count)
    }

    /** Forget a track once its match has been handled or it has left the frame. */
    fun forget(trackId: Int?) {
        if (trackId != null) tracks.remove(trackId)
    }

    fun reset() = tracks.clear()

    data class Fused(val embedding: FloatArray, val frames: Int) {
        override fun equals(other: Any?): Boolean =
            other is Fused && frames == other.frames && embedding.contentEquals(other.embedding)

        override fun hashCode(): Int = 31 * frames + embedding.contentHashCode()
    }

    private companion object {
        fun l2normalize(v: FloatArray): FloatArray {
            var norm = 0f
            for (x in v) norm += x * x
            norm = sqrt(norm)
            if (norm == 0f) return v.copyOf()
            return FloatArray(v.size) { v[it] / norm }
        }
    }
}
