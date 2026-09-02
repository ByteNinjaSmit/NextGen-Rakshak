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
        if (denom == 0f) return 0f
        // Clamp: floating-point rounding can push an identical-vector score a hair
        // past 1.0, and the score is shown to the volunteer as a percentage.
        return (dot / denom).coerceIn(-1f, 1f)
    }
}
