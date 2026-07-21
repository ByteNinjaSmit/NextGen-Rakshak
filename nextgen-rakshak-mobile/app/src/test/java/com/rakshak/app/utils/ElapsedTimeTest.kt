package com.rakshak.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ElapsedTimeTest {

    private val now = 1_753_000_000_000L // epoch MILLISECONDS

    private fun agoMinutes(n: Long) = now - TimeUnit.MINUTES.toMillis(n)

    @Test
    fun formats_the_golden_hour_range() {
        assertEquals("just now", ElapsedTime.since(now, now))
        assertEquals("1 min ago", ElapsedTime.since(agoMinutes(1), now))
        assertEquals("12 min ago", ElapsedTime.since(agoMinutes(12), now))
        assertEquals("59 min ago", ElapsedTime.since(agoMinutes(59), now))
        assertEquals("1 hr ago", ElapsedTime.since(agoMinutes(60), now))
        assertEquals("3 hrs ago", ElapsedTime.since(agoMinutes(180), now))
    }

    @Test
    fun missing_timestamp_does_not_read_as_ancient() {
        assertEquals("just now", ElapsedTime.since(0L, now))
    }

    @Test
    fun clock_skew_does_not_produce_negative_ages() {
        // A server timestamp slightly ahead of the device clock must not underflow.
        assertEquals("just now", ElapsedTime.since(now + 5_000, now))
    }

    /**
     * Regression: alert timestamps were once read from Firestore in SECONDS while
     * every consumer treated them as MILLISECONDS. A seconds value is ~1000x too
     * small, so every alert looked ancient — which silently made the mesh drop
     * every packet as expired. Pin the unit here.
     */
    @Test
    fun timestamps_are_interpreted_as_milliseconds() {
        val secondsValue = now / 1000 // what the old code produced
        assertEquals("over a day ago", ElapsedTime.since(secondsValue, now))
        assertEquals("12 min ago", ElapsedTime.since(agoMinutes(12), now))
    }
}
