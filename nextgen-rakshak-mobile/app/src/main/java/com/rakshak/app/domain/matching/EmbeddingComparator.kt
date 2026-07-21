package com.rakshak.app.domain.matching

/**
 * Compares face embeddings. (SOLID: Dependency Inversion — high-level matching
 * depends on this abstraction, not a concrete similarity implementation.)
 */
interface EmbeddingComparator {
    /** Similarity in [-1, 1]; higher means more similar. */
    fun similarity(a: FloatArray, b: FloatArray): Float
}
