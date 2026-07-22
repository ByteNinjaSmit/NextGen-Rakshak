package com.rakshak.app.domain.matching

import com.rakshak.app.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosineEmbeddingComparatorTest {

    private val comparator = CosineEmbeddingComparator()

    @Test
    fun identicalVectors_similarityIsOne() {
        val v = floatArrayOf(1f, 2f, 3f, 4f)
        assertEquals(1f, comparator.similarity(v, v), 1e-5f)
    }

    @Test
    fun orthogonalVectors_similarityIsZero() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, comparator.similarity(a, b), 1e-5f)
    }

    @Test
    fun oppositeVectors_similarityIsMinusOne() {
        val a = floatArrayOf(1f, 1f)
        val b = floatArrayOf(-1f, -1f)
        assertEquals(-1f, comparator.similarity(a, b), 1e-5f)
    }

    @Test
    fun similarVectors_clearThreshold() {
        val a = floatArrayOf(0.9f, 0.1f, 0.2f)
        val b = floatArrayOf(0.85f, 0.12f, 0.25f)
        // Compare against the real match threshold so this tracks it if it changes.
        assertTrue(comparator.similarity(a, b) > Constants.SIMILARITY_THRESHOLD)
    }

    @Test
    fun zeroVector_returnsZero() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(1f, 1f)
        assertEquals(0f, comparator.similarity(a, b), 1e-5f)
    }
}
