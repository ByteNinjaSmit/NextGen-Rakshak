package com.rakshak.app.domain.matching

import android.util.Log
import com.rakshak.app.data.model.Alert

/**
 * The set of active alerts a live face is scored against, prepared once when the
 * alert list changes instead of re-derived on every camera frame.
 *
 * The work it does up front is the width check. An alert whose embedding is a
 * different length than the device model's output cannot be compared to a live
 * face at all — and that is not a per-frame curiosity, it is a deployment fault:
 * the app's `mobilefacenet.tflite` and `functions/model/savedmodel` were built
 * from different weights. Left unhandled it disables matching **completely and
 * silently**, because every alert falls out on a size check buried in the scan
 * loop. Detecting it here means it is counted once, reported through
 * [ScanDiagnostics.alertsWrongWidth], and shown to the volunteer as a broken
 * build rather than as an ordinary "no match".
 *
 * Scoring itself is delegated to the injected [EmbeddingComparator], so cosine
 * similarity has exactly one definition in the app.
 */
class AlertIndex private constructor(
    private val comparator: EmbeddingComparator,
    private val entries: List<Alert>,
    /** Alerts held back because their embedding width does not match the model's. */
    val unusable: Int,
    /** Alerts held back because they carry no embedding yet. */
    val missing: Int,
) {
    /** Alerts that can actually be scored. */
    val size: Int get() = entries.size

    val isEmpty: Boolean get() = entries.isEmpty()

    /** The child names being scanned for, for the camera overlay. */
    fun names(): List<String> = entries.map { it.childName }

    fun alertById(id: String): Alert? = entries.firstOrNull { it.id == id }

    /**
     * Best-scoring alert for [embedding].
     *
     * Returns null only when the index is empty. Callers apply their own
     * threshold to [Scored.score], so a below-threshold top score is still
     * available to show the volunteer how close the scan is getting — which is
     * what makes a live "closest 48%" readout possible.
     */
    fun best(embedding: FloatArray): Scored? {
        var bestScore = Float.NEGATIVE_INFINITY
        var bestAlert: Alert? = null
        for (alert in entries) {
            if (alert.embedding.size != embedding.size) continue
            val score = comparator.similarity(embedding, alert.embedding)
            if (score > bestScore) {
                bestScore = score
                bestAlert = alert
            }
        }
        val alert = bestAlert ?: return null
        return Scored(alert, bestScore)
    }

    data class Scored(val alert: Alert, val score: Float)

    companion object {
        private const val TAG = "AlertIndex"

        fun empty(comparator: EmbeddingComparator) =
            AlertIndex(comparator, emptyList(), unusable = 0, missing = 0)

        /**
         * @param embeddingWidth the width the device model produces. Alerts of any
         *   other width are excluded rather than compared against a truncated
         *   prefix, which would produce a meaningless score.
         */
        fun build(
            alerts: List<Alert>,
            embeddingWidth: Int,
            comparator: EmbeddingComparator,
        ): AlertIndex {
            val usable = ArrayList<Alert>(alerts.size)
            var unusable = 0
            var missing = 0

            for (alert in alerts) {
                when {
                    alert.embedding.isEmpty() -> missing++
                    alert.embedding.size != embeddingWidth -> unusable++
                    else -> usable += alert
                }
            }

            if (unusable > 0) {
                Log.e(
                    TAG,
                    "$unusable alert(s) carry a ${alerts.firstOrNull { it.embedding.size != embeddingWidth }?.embedding?.size}-d " +
                        "embedding but the device model emits $embeddingWidth-d. " +
                        "app/src/main/assets/mobilefacenet.tflite and functions/model/savedmodel " +
                        "were built from different weights — regenerate both with " +
                        "scripts/convert_models.py and verify with scripts/verify_parity.py.",
                )
            }
            return AlertIndex(comparator, usable, unusable, missing)
        }
    }
}
