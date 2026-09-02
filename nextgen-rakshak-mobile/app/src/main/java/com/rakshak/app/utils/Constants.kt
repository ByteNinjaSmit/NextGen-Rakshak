package com.rakshak.app.utils

/** App-wide constants. Keep magic numbers and collection names here. */
object Constants {
    // ML
    const val MODEL_ASSET = "mobilefacenet.tflite"
    const val FACE_INPUT_SIZE = 112       // MobileFaceNet / ArcFace input is 112x112

    /**
     * Expected embedding length. 128 for the original MobileFaceNet, 512 for the
     * ArcFace-trained upgrade (`w600k_mbf` / EdgeFace — see scripts/README.md).
     * This is a sanity bound only: [com.rakshak.app.ml.TFLiteEmbeddingExtractor]
     * reads the real length from the model's output tensor at load time, and the
     * comparator works off `FloatArray.size`, so nothing breaks if the shipped
     * model has a different width — the assertion just catches a wrong asset.
     */
    val SUPPORTED_EMBEDDING_SIZES = intArrayOf(128, 512)

    /**
     * Cosine similarity above which a face is treated as a candidate match.
     *
     * Set from measurement, not from the literature. With the original
     * MobileFaceNet, across 36 real photo pairs, same-person scores spanned
     * 0.7142–0.9899 and different-person scores 0.0864–0.3551; 0.55 sits in the
     * empty gap. After ANY change to the model, the alignment, or the precision,
     * this MUST be re-measured with `scripts/evaluate_model.py`, which prints the
     * separating band and a suggested threshold — the ArcFace upgrade shifts the
     * same-person band lower (~0.28–0.45 typical).
     *
     * Lower is the safer error here: a missed child is the failure the system
     * exists to prevent, while a false candidate costs only the moment a
     * volunteer takes to tap "Not a match" — every match is human-confirmed.
     */
    const val SIMILARITY_THRESHOLD = 0.55f

    /**
     * Frames of the same tracked face whose embeddings are averaged before a
     * match is surfaced. Averaging L2-normalised embeddings suppresses
     * per-frame detector jitter and motion blur, widening the gap between a true
     * and a false candidate. Kept small so a real match still appears within
     * about a second of the child entering view.
     */
    const val EMBEDDING_FUSION_FRAMES = 3

    /**
     * A single frame scoring at least this is surfaced immediately without
     * waiting for [EMBEDDING_FUSION_FRAMES] — a very confident hit should not be
     * delayed. Must sit comfortably above [SIMILARITY_THRESHOLD]; re-tune
     * alongside it.
     */
    const val STRONG_MATCH_THRESHOLD = 0.72f

    // --- Quality gate (com.rakshak.app.ml.ImageQuality) ---
    /** Minimum face box side in the camera frame, in pixels. */
    const val MIN_FACE_PX = 90
    /** Mean-luminance window (0..255) the aligned tile must fall inside. */
    const val MIN_FACE_LUMA = 40f
    const val MAX_FACE_LUMA = 235f
    /** Minimum variance-of-Laplacian on the aligned tile; below this it is blurred. */
    const val MIN_SHARPNESS_VAR = 60f

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

    /**
     * Seen-message-id entries older than this are evicted from [com.rakshak.app
     * .networking.mesh.MeshSeenCache]. Tied to the alert lifetime: once a packet's
     * parent alert can no longer be relayed, remembering its id serves no purpose.
     * This is what makes the duplicate-suppression set "short-lived" rather than a
     * set that grows for the length of a multi-day event.
     */
    const val MESH_SEEN_TTL_MILLIS = ALERT_EXPIRY_MILLIS

    /**
     * Face thumbnail carried inside an alert packet so an offline device can render
     * the parent's photo in the side-by-side match dialog (FR-07) with no internet.
     * A 96x96 JPEG at quality ~40 is 2-3 KB; the cap rejects anything that would
     * bloat the packet toward the 32 KB Nearby BytesPayload limit.
     */
    const val MESH_THUMBNAIL_SIZE_PX = 96
    const val MESH_THUMBNAIL_JPEG_QUALITY = 40
    const val MESH_THUMBNAIL_MAX_BYTES = 8 * 1024
}
