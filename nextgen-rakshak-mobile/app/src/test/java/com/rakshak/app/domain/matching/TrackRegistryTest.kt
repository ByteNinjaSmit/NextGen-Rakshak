package com.rakshak.app.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The confirmation and suppression rules the scanner's false-positive behaviour
 * rests on. These are pure logic — no Android, no model — so they are the cheap
 * place to pin down the decisions that are expensive to test on a phone.
 */
class TrackRegistryTest {

    private val threshold = 0.55f
    private val strong = 0.72f

    private fun registry(confirmFrames: Int = 2, idleMillis: Long = 3_000L) =
        TrackRegistry(confirmFrames = confirmFrames, idleTimeoutMillis = idleMillis)

    @Test
    fun `a single mid-band frame is not enough`() {
        val verdict = registry().observe(1, 0.60f, "alert-a", threshold, strong, nowMillis = 0)
        assertEquals(TrackRegistry.Verdict.HOLD, verdict)
    }

    @Test
    fun `two consecutive mid-band frames on the same alert surface a match`() {
        val tracks = registry()
        assertEquals(
            TrackRegistry.Verdict.HOLD,
            tracks.observe(1, 0.60f, "alert-a", threshold, strong, nowMillis = 0),
        )
        assertEquals(
            TrackRegistry.Verdict.SURFACE,
            tracks.observe(1, 0.58f, "alert-a", threshold, strong, nowMillis = 100),
        )
    }

    @Test
    fun `a strong single frame surfaces immediately`() {
        val verdict = registry().observe(1, 0.80f, "alert-a", threshold, strong, nowMillis = 0)
        assertEquals(TrackRegistry.Verdict.SURFACE, verdict)
    }

    @Test
    fun `a run is broken by a frame that drops below the threshold`() {
        val tracks = registry()
        tracks.observe(1, 0.60f, "alert-a", threshold, strong, nowMillis = 0)
        tracks.observe(1, 0.40f, "alert-a", threshold, strong, nowMillis = 100)
        assertEquals(
            TrackRegistry.Verdict.HOLD,
            tracks.observe(1, 0.60f, "alert-a", threshold, strong, nowMillis = 200),
        )
    }

    @Test
    fun `frames that clear the threshold against different alerts are not evidence for either`() {
        val tracks = registry()
        tracks.observe(1, 0.60f, "alert-a", threshold, strong, nowMillis = 0)
        // Switching alerts restarts the run rather than completing it.
        assertEquals(
            TrackRegistry.Verdict.HOLD,
            tracks.observe(1, 0.60f, "alert-b", threshold, strong, nowMillis = 100),
        )
    }

    @Test
    fun `a rejected track is never surfaced again however high it scores`() {
        val tracks = registry()
        tracks.reject(1)
        assertTrue(tracks.isSuppressed(1))
        assertEquals(
            TrackRegistry.Verdict.SUPPRESSED,
            tracks.observe(1, 0.99f, "alert-a", threshold, strong, nowMillis = 0),
        )
    }

    @Test
    fun `a reported track is not re-reported while it stays in view`() {
        val tracks = registry()
        tracks.markReported(7)
        assertEquals(
            TrackRegistry.Verdict.SUPPRESSED,
            tracks.observe(7, 0.90f, "alert-a", threshold, strong, nowMillis = 0),
        )
    }

    @Test
    fun `an untracked face is only surfaced on a strong score`() {
        val tracks = registry()
        assertEquals(
            TrackRegistry.Verdict.HOLD,
            tracks.observe(null, 0.60f, "alert-a", threshold, strong, nowMillis = 0),
        )
        assertEquals(
            TrackRegistry.Verdict.SURFACE,
            tracks.observe(null, 0.80f, "alert-a", threshold, strong, nowMillis = 0),
        )
    }

    @Test
    fun `an evicted id does not inherit the previous face's rejection`() {
        // ML Kit reuses tracking ids. A new child given a recycled id must not
        // silently inherit "rejected" and become unmatchable.
        val tracks = registry(idleMillis = 1_000L)
        tracks.reject(1)
        assertTrue(tracks.isSuppressed(1))

        tracks.evictStale(nowMillis = System.currentTimeMillis() + 5_000L)
        assertFalse(tracks.isSuppressed(1))
    }

    @Test
    fun `reset clears every verdict`() {
        val tracks = registry()
        tracks.reject(1)
        tracks.markReported(2)
        tracks.reset()
        assertFalse(tracks.isSuppressed(1))
        assertFalse(tracks.isSuppressed(2))
    }
}
