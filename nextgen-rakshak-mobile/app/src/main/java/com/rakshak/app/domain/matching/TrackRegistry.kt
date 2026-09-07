package com.rakshak.app.domain.matching

import com.rakshak.app.utils.Constants

/**
 * Per-tracked-face memory for the scan loop.
 *
 * ML Kit gives every face it follows across frames a stable `trackingId`. That
 * id is the only thing that lets the scanner treat a stream of frames as
 * observations of *one child* rather than as a stream of unrelated faces, which
 * is what three separate behaviours need:
 *
 * - **Confirmation.** A mid-band score (just over the threshold) is acted on only
 *   after [Constants.MATCH_CONFIRM_FRAMES] consecutive frames of the same track
 *   agreeing on the same alert. One frame at 0.58 can be jitter; two in a row on
 *   one track is a signal. A run is broken by any frame that drops below the
 *   threshold or that lands on a different alert.
 * - **Rejection memory.** When a volunteer taps "Not a match", that verdict is
 *   about *this child*, so it is recorded against the track. Without it the very
 *   next frame re-scores the same face, clears the threshold again and re-opens
 *   the dialog the volunteer just dismissed — the scanner becomes unusable
 *   whenever a false positive is standing in front of the camera.
 * - **Suppression after a report.** A confirmed track is not re-reported while it
 *   stays in view.
 *
 * Eviction matters as much as the memory does: ML Kit **reuses** tracking ids
 * once a face leaves the frame. A track kept forever would hand a new child the
 * previous occupant's accumulated score history, or — far worse — their
 * "rejected" flag, making that child silently unmatchable. Anything not seen for
 * [Constants.TRACK_IDLE_TIMEOUT_MILLIS] is therefore dropped.
 *
 * Not thread-safe: the scan loop is single-flight, so all calls arrive in order
 * on one coroutine.
 */
class TrackRegistry(
    private val confirmFrames: Int = Constants.MATCH_CONFIRM_FRAMES,
    private val idleTimeoutMillis: Long = Constants.TRACK_IDLE_TIMEOUT_MILLIS,
) {
    private class State {
        var lastSeenAt: Long = 0L
        /** Consecutive frames this track has cleared the threshold on [runAlertId]. */
        var run: Int = 0
        var runAlertId: String? = null
        /** Highest score this track has ever reached, for the status readout. */
        var bestScore: Float = 0f
        /** The volunteer said this face is not the child. Never offer it again. */
        var rejected: Boolean = false
        /** A sighting has already been reported for this track. */
        var reported: Boolean = false
    }

    private val states = HashMap<Int, State>()

    /** What the scanner should do with a track this frame. */
    enum class Verdict {
        /** Below threshold, or the confirmation run is still building. */
        HOLD,

        /** Threshold cleared often enough (or strongly enough) — surface it. */
        SURFACE,

        /** Already rejected or already reported; do not score it again. */
        SUPPRESSED,
    }

    /** True when this track has been dismissed or reported and needs no embedding. */
    fun isSuppressed(trackId: Int?): Boolean {
        val state = states[trackId ?: return false] ?: return false
        return state.rejected || state.reported
    }

    /**
     * Fold one frame's result for [trackId] into its run.
     *
     * @param score the best cosine this track reached this frame.
     * @param alertId the alert that score belongs to, or null if nothing was scored.
     * @param threshold the mid-band threshold a run is counted against.
     * @param strongThreshold a score at or above which one frame is enough.
     */
    fun observe(
        trackId: Int?,
        score: Float,
        alertId: String?,
        threshold: Float,
        strongThreshold: Float,
        nowMillis: Long = System.currentTimeMillis(),
    ): Verdict {
        // A null tracking id means the detector is not following this face across
        // frames, so there is no run to build and no rejection to remember. Such a
        // face is only ever surfaced on a strong single-frame score — accepting a
        // mid-band one would be acting on exactly the single noisy frame that
        // confirmation exists to guard against.
        if (trackId == null) {
            return if (alertId != null && score >= strongThreshold) Verdict.SURFACE else Verdict.HOLD
        }

        val state = states.getOrPut(trackId) { State() }
        state.lastSeenAt = nowMillis
        if (state.rejected || state.reported) return Verdict.SUPPRESSED
        if (score > state.bestScore) state.bestScore = score

        if (alertId == null || score < threshold) {
            state.run = 0
            state.runAlertId = null
            return Verdict.HOLD
        }

        // A run only counts while it stays on the same alert. Two frames that each
        // clear the threshold against *different* children are not two pieces of
        // evidence for either of them.
        if (state.runAlertId != alertId) {
            state.runAlertId = alertId
            state.run = 0
        }
        state.run++

        return if (score >= strongThreshold || state.run >= confirmFrames) Verdict.SURFACE else Verdict.HOLD
    }

    /** Mark a track alive without scoring it (used for suppressed/gated faces). */
    fun touch(trackId: Int?, nowMillis: Long = System.currentTimeMillis()) {
        val id = trackId ?: return
        states.getOrPut(id) { State() }.lastSeenAt = nowMillis
    }

    /** The volunteer rejected this face. Never surface this track again. */
    fun reject(trackId: Int?) {
        val id = trackId ?: return
        states.getOrPut(id) { State() }.apply {
            rejected = true
            run = 0
            runAlertId = null
            lastSeenAt = System.currentTimeMillis()
        }
    }

    /** A sighting was reported for this track. Do not re-report it while in view. */
    fun markReported(trackId: Int?) {
        val id = trackId ?: return
        states.getOrPut(id) { State() }.apply {
            reported = true
            run = 0
            runAlertId = null
            lastSeenAt = System.currentTimeMillis()
        }
    }

    /** Highest score any live track has reached, for the on-screen readout. */
    fun bestScoreSoFar(): Float = states.values.maxOfOrNull { it.bestScore } ?: 0f

    /** Drop tracks not seen recently. Call once per frame, before scoring. */
    fun evictStale(nowMillis: Long = System.currentTimeMillis()) {
        val cutoff = nowMillis - idleTimeoutMillis
        val it = states.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.lastSeenAt < cutoff) it.remove()
        }
    }

    fun reset() = states.clear()
}
