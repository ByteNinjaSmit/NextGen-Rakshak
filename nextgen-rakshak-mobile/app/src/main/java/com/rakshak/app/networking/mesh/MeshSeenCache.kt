package com.rakshak.app.networking.mesh

import com.rakshak.app.utils.Constants

/**
 * Time-windowed set of message ids this device has already processed — the
 * "short-lived seen-IDs set" that stops a flooded packet from being relayed
 * forever, and a mesh-relayed sighting from being uploaded twice.
 *
 * Entries older than [ttlMillis] are evicted, so the set cannot grow unbounded
 * across a multi-day event. Pure and clock-injectable so the eviction logic is
 * unit-testable without waiting 8 hours.
 */
class MeshSeenCache(
    private val ttlMillis: Long = Constants.MESH_SEEN_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // Insertion order == time order (clock is monotonic across a session), so
    // eviction can stop at the first entry still inside the window.
    private val seen = LinkedHashMap<String, Long>()

    /** Record [id] and return true if it was not already present. */
    @Synchronized
    fun markIfNew(id: String): Boolean {
        evict()
        if (seen.containsKey(id)) return false
        seen[id] = clock()
        return true
    }

    @Synchronized
    fun contains(id: String): Boolean {
        evict()
        return seen.containsKey(id)
    }

    @Synchronized
    fun size(): Int {
        evict()
        return seen.size
    }

    /** For persistence: current entries, id -> firstSeenMillis. */
    @Synchronized
    fun snapshot(): Map<String, Long> = LinkedHashMap(seen)

    /** Reload persisted entries (older-than-window ones are dropped immediately). */
    @Synchronized
    fun restore(entries: Map<String, Long>) {
        entries.entries
            .sortedBy { it.value }
            .forEach { seen[it.key] = it.value }
        evict()
    }

    private fun evict() {
        val cutoff = clock() - ttlMillis
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value < cutoff) iterator.remove() else break
        }
    }
}
