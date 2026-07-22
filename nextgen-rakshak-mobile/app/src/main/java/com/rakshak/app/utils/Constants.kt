package com.rakshak.app.utils

/** App-wide constants. Keep magic numbers and collection names here. */
object Constants {
    // ML
    const val MODEL_ASSET = "mobilefacenet.tflite"
    const val FACE_INPUT_SIZE = 112       // MobileFaceNet input is 112x112
    const val EMBEDDING_SIZE = 128
    /**
     * Cosine similarity above which a face is treated as a candidate match.
     *
     * Set from measurement, not from the literature. Across 36 real photo pairs
     * run through this exact model, same-person scores spanned 0.7142–0.9899 and
     * different-person scores spanned 0.0864–0.3551. The original 0.75 sat inside
     * the same-person range and missed 5 of 15 genuine pairs; 0.55 sits in the
     * empty gap between the two groups and classifies all 36 correctly.
     *
     * Lower is the safer error here: a missed child is the failure the system
     * exists to prevent, while a false candidate costs only the moment a
     * volunteer takes to tap "Not a match" — every match is human-confirmed.
     * See scripts/README.md for the measurements.
     */
    const val SIMILARITY_THRESHOLD = 0.55f

    /**
     * Head-pose limits for discarding non-frontal faces before embedding.
     * MobileFaceNet expects roughly frontal input, so a profile view yields an
     * embedding that matches nothing useful. Yaw (left/right turn) is the
     * dominant signal; roll (head tilt) is tolerated more generously because
     * the crop stays recognisable.
     */
    const val MAX_FACE_YAW_DEGREES = 30f
    const val MAX_FACE_ROLL_DEGREES = 40f

    /**
     * Padding added around the detected face box before cropping to the model
     * input, as a fraction of the box's longest side. MUST match FACE_CROP_MARGIN
     * in `functions/src/embedding.ts`, or server and device embeddings of the same
     * child will be framed differently and the cosine score will fall.
     */
    const val FACE_CROP_MARGIN = 0.2f

    // Firestore
    const val COLLECTION_ALERTS = "alerts"
    const val COLLECTION_MATCHES = "matches"

    // Nearby Connections
    const val MESH_SERVICE_ID = "com.rakshak.alert"

    // Mesh store-and-forward routing
    /** Initial hop-count/TTL stamped on a packet; decremented at each relay. */
    const val MESH_INITIAL_TTL = 6
    /** Alerts older than this are considered expired and are neither matched nor relayed. */
    const val ALERT_EXPIRY_MILLIS = 8L * 60 * 60 * 1000 // 8 hours (matches FR-12)
}
