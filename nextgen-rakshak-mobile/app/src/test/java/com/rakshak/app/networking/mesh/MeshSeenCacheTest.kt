package com.rakshak.app.networking.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshSeenCacheTest {

    @Test
    fun markIfNew_isTrueOnceThenFalse() {
        val cache = MeshSeenCache(ttlMillis = 1000, clock = { 0L })
        assertTrue(cache.markIfNew("m1"))
        assertFalse(cache.markIfNew("m1"))
    }

    @Test
    fun entriesEvictAfterTheWindow() {
        var now = 0L
        val cache = MeshSeenCache(ttlMillis = 1000, clock = { now })
        cache.markIfNew("old")
        now = 500
        cache.markIfNew("mid")
        now = 1200 // "old" is now 1200ms → past the 1000ms window; "mid" is 700ms

        assertFalse(cache.contains("old"))
        assertTrue(cache.contains("mid"))
        assertEquals(1, cache.size())
    }

    @Test
    fun aReEvictedIdIsAcceptedAgain() {
        var now = 0L
        val cache = MeshSeenCache(ttlMillis = 100, clock = { now })
        cache.markIfNew("m")
        now = 200
        assertTrue(cache.markIfNew("m")) // window passed, treated as new
    }

    @Test
    fun restoreDropsStaleEntriesImmediately() {
        val cache = MeshSeenCache(ttlMillis = 1000, clock = { 5000L })
        cache.restore(mapOf("fresh" to 4500L, "stale" to 1000L))
        assertTrue(cache.contains("fresh"))
        assertFalse(cache.contains("stale"))
    }
}
