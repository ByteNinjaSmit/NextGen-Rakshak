package com.rakshak.app.domain.matching

import com.rakshak.app.data.model.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the failure that silently disabled matching in production: the device
 * model and the server model were built from different weights, so every alert
 * embedding was a different width than the live one and fell out on a size check
 * inside the scan loop. Nothing crashed, nothing logged loudly, and the scanner
 * simply never matched anybody.
 */
class AlertIndexTest {

    private val comparator = CosineEmbeddingComparator()

    private fun alert(id: String, embedding: FloatArray) =
        Alert(id = id, childName = id, embedding = embedding)

    private fun vector(width: Int, seed: Float) = FloatArray(width) { seed + it * 0.001f }

    @Test
    fun `alerts of the model's width are scoreable`() {
        val index = AlertIndex.build(listOf(alert("a", vector(128, 0.5f))), 128, comparator)
        assertEquals(1, index.size)
        assertEquals(0, index.unusable)
        assertEquals(0, index.missing)
    }

    @Test
    fun `an alert of the wrong width is excluded and counted, not silently skipped`() {
        val index = AlertIndex.build(
            listOf(alert("server-512", vector(512, 0.5f))),
            embeddingWidth = 128,
            comparator = comparator,
        )
        assertTrue(index.isEmpty)
        assertEquals(1, index.unusable)
    }

    @Test
    fun `an alert with no embedding yet is counted separately from a wrong width`() {
        // These need different messages: one is "wait, the photo is still being
        // prepared", the other is "this build cannot match anybody".
        val index = AlertIndex.build(
            listOf(alert("no-embedding", FloatArray(0)), alert("wrong", vector(512, 0.2f))),
            embeddingWidth = 128,
            comparator = comparator,
        )
        assertEquals(1, index.missing)
        assertEquals(1, index.unusable)
    }

    @Test
    fun `best returns the closest alert`() {
        val target = vector(128, 0.9f)
        val index = AlertIndex.build(
            listOf(alert("far", vector(128, -0.4f)), alert("near", target)),
            128,
            comparator,
        )
        val scored = index.best(target)
        assertNotNull(scored)
        assertEquals("near", scored!!.alert.id)
        assertEquals(1f, scored.score, 1e-4f)
    }

    @Test
    fun `best on an empty index returns null rather than a bogus score`() {
        assertNull(AlertIndex.empty(comparator).best(vector(128, 0.5f)))
    }

    @Test
    fun `a below-threshold top score is still returned so the UI can show progress`() {
        val index = AlertIndex.build(listOf(alert("a", vector(128, 0.5f))), 128, comparator)
        val opposite = FloatArray(128) { -(0.5f + it * 0.001f) }
        val scored = index.best(opposite)
        assertNotNull(scored)
        assertEquals(-1f, scored!!.score, 1e-4f)
    }
}
