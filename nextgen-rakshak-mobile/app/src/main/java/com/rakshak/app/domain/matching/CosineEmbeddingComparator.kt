package com.rakshak.app.domain.matching

import kotlin.math.sqrt

/**
 * Cosine similarity comparator. Robust to lighting/pose since it measures the
 * angle between embedding vectors rather than their magnitude.
 */
class CosineEmbeddingComparator : EmbeddingComparator {
    override fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding size mismatch: ${a.size} vs ${b.size}" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}
