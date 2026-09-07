package com.rakshak.app.domain.matching

import com.rakshak.app.utils.Constants
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
class EmbeddingAggregator(
    private val maxFrames: Int,
    private val idleTimeoutMillis: Long = Constants.TRACK_IDLE_TIMEOUT_MILLIS,
    private val coherenceFloor: Float = Constants.TRACK_COHERENCE_MIN,
) {

    private class Track(val sum: FloatArray, var count: Int) {
        var lastSeenAt: Long = 0L
        /** Current running mean, L2-normalised — the track's identity so far. */
        var mean: FloatArray = FloatArray(sum.size)
    }

    private val tracks = HashMap<Int, Track>()

    /**
     * Fold [embedding] into the running mean for [trackId] and return the
     * current fused, L2-normalised embedding together with how many frames it
     * now represents. A null [trackId] (detector reported no tracking) is
     * treated as a fresh single-frame observation every time.
     */
    fun fuse(trackId: Int?, embedding: FloatArray, nowMillis: Long = System.currentTimeMillis()): Fused {
        val unit = l2normalize(embedding)
        if (trackId == null) return Fused(unit, 1)

        val track = tracks.getOrPut(trackId) { Track(FloatArray(unit.size), 0) }
        track.lastSeenAt = nowMillis

        // Averaging is only valid while the frames really are the same face. Two
        // things break that premise, and both used to silently poison the mean:
        //
        //  - ML Kit RECYCLES tracking ids. A child who walks into frame can be
        //    handed the id of whoever just left, inheriting their accumulated sum.
        //  - A single bad frame (motion blur, half-turn, an arm across the face)
        //    embeds to something closer to noise than to the person.
        //
        // Either way the mean drifts to a point between two identities and the
        // score collapses — the "it matched instantly at first, then only 18-20%"
        // failure. So each frame is checked against the identity built so far, and
        // a frame that disagrees restarts the track instead of contaminating it.
        if (track.count > 0 && dot(track.mean, unit) < coherenceFloor) {
            track.sum.fill(0f)
            track.count = 0
        }

        if (track.count >= maxFrames) {
            // Slide the window: drop the oldest weight's worth so a face that
            // stays in view keeps adapting instead of freezing on its first N frames.
            val scale = (maxFrames - 1f) / maxFrames
            for (i in track.sum.indices) track.sum[i] *= scale
            track.count = maxFrames - 1
        }
        for (i in track.sum.indices) track.sum[i] += unit[i]
        track.count++
        track.mean = l2normalize(track.sum)

        return Fused(track.mean, track.count)
    }

    /** Forget a track once its match has been handled or it has left the frame. */
    fun forget(trackId: Int?) {
        if (trackId != null) tracks.remove(trackId)
    }

    /**
     * Drop tracks not seen for [idleTimeoutMillis]. Must be called every frame,
     * in step with [TrackRegistry.evictStale].
     *
     * Without this the map only ever shrank on an explicit reject/report, so it
     * grew for the whole session and — because tracking ids are reused — handed
     * new faces the embeddings of old ones.
     */
    fun evictStale(nowMillis: Long = System.currentTimeMillis()) {
        val cutoff = nowMillis - idleTimeoutMillis
        val it = tracks.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.lastSeenAt < cutoff) it.remove()
        }
    }

    fun reset() = tracks.clear()

    data class Fused(val embedding: FloatArray, val frames: Int) {
        override fun equals(other: Any?): Boolean =
            other is Fused && frames == other.frames && embedding.contentEquals(other.embedding)

        override fun hashCode(): Int = 31 * frames + embedding.contentHashCode()
    }

    private companion object {
        /** Both vectors are unit length, so this is their cosine similarity. */
        fun dot(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var acc = 0f
            for (i in a.indices) acc += a[i] * b[i]
            return acc
        }

        fun l2normalize(v: FloatArray): FloatArray {
            var norm = 0f
            for (x in v) norm += x * x
            norm = sqrt(norm)
            if (norm == 0f) return v.copyOf()
            return FloatArray(v.size) { v[it] / norm }
        }
    }
}
