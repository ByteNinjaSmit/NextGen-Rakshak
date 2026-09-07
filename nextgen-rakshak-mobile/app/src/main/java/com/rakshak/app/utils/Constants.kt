package com.rakshak.app.utils

/** App-wide constants. Keep magic numbers and collection names here. */
object Constants {
    // ---------------------------------------------------------------------
    // Face model
    // ---------------------------------------------------------------------
    const val MODEL_ASSET = "mobilefacenet.tflite"
    const val FACE_INPUT_SIZE = 112       // MobileFaceNet input is 112x112

    /**
     * Expected embedding length. 128 for MobileFaceNet (what ships), 512 if an
     * ArcFace model is swapped in later.
     * [com.rakshak.app.ml.TFLiteEmbeddingExtractor] reads the real length from
     * the model's output tensor at load time and the comparator works off
     * `FloatArray.size`, so this is a sanity bound that catches a wrong asset —
     * nothing hard-codes a width.
     *
     * The shipped asset and `functions/model/savedmodel` MUST come from the same
     * weights. They currently do: both are the sirius-ai MobileFaceNet_TF
     * `MobileFaceNet_9925_9680` graph, and `scripts/verify_parity.py` measures
     * cosine 0.99967 between them. A device model of a different width than the
     * server's silently disables matching — every alert embedding is skipped on
     * the size check in [com.rakshak.app.domain.matching.AlertIndex].
     */
    val SUPPORTED_EMBEDDING_SIZES = intArrayOf(128, 512)

    // ---------------------------------------------------------------------
    // Matching thresholds
    // ---------------------------------------------------------------------

    /**
     * Cosine similarity a face must reach to be treated as a candidate.
     *
     * Set from measurement, not from the literature. With this MobileFaceNet,
     * across 36 real photo pairs: same-person 0.7142-0.9899, different-person
     * 0.0864-0.3551. The empty band runs 0.3551-0.7142 and 0.55 sits near its
     * middle with ~0.19 of headroom on each side. The synopsis's 0.75 sat inside
     * the same-person range and missed 5/15 genuine pairs.
     *
     * Re-measure with `scripts/evaluate_model.py` after ANY change to the model,
     * the alignment or the precision — an ArcFace model shifts the band down.
     *
     * Lower is the safer error here: a missed child is the failure the system
     * exists to prevent, while a false candidate costs only the moment a
     * volunteer takes to tap "Not a match" — every match is human-confirmed.
     */
    const val SIMILARITY_THRESHOLD = 0.55f

    /**
     * Score at which a match is surfaced from a **single** frame, with no
     * multi-frame confirmation. Deep inside the same-person band (whose measured
     * floor is 0.7142), so a face scoring this high is not a borderline call and
     * making the volunteer wait three frames for it only costs time.
     */
    const val STRONG_MATCH_THRESHOLD = 0.72f

    /**
     * Frames of the same tracked face that must **each** clear
     * [SIMILARITY_THRESHOLD] before a mid-band match is surfaced.
     *
     * This is the false-positive brake. A score of 0.58 on one frame can be
     * detector jitter; the same identity winning on two consecutive frames of the
     * same track is not. At ~10 fps this adds roughly 100 ms, which is why a
     * strong single frame is allowed to skip it entirely.
     */
    const val MATCH_CONFIRM_FRAMES = 2

    /**
     * Embeddings of the same tracked face averaged before comparison. Averaging
     * L2-normalised embeddings across frames cancels per-frame detector jitter,
     * motion blur and momentary expression, which widens the cosine gap between
     * the right child and everyone else.
     */
    const val EMBEDDING_FUSION_FRAMES = 3

    /**
     * A track not seen for this long is dropped from
     * [com.rakshak.app.domain.matching.TrackRegistry]. ML Kit reuses tracking ids
     * after a face leaves the frame, so without eviction a new child could
     * inherit the previous occupant's accumulated embedding — or its "rejected"
     * flag, which would silently make them unmatchable.
     */
    const val TRACK_IDLE_TIMEOUT_MILLIS = 3_000L

    /**
     * Minimum cosine between an incoming frame's embedding and the running mean
     * for that track before the frame is folded into it.
     *
     * Guards the premise multi-frame fusion rests on — that the frames are all of
     * one person. Sits between the two measured bands (same person > 0.7,
     * different people < 0.36), so a recycled tracking id or a badly blurred
     * frame restarts the average instead of dragging it toward a point between
     * two identities.
     */
    const val TRACK_COHERENCE_MIN = 0.5f

    // ---------------------------------------------------------------------
    // Quality gate (com.rakshak.app.ml.ImageQuality)
    // ---------------------------------------------------------------------
    /** Minimum face box side in the camera frame, in pixels. */
    const val MIN_FACE_PX = 48
    /** Mean-luminance window (0..255) the aligned tile must fall inside. */
    const val MIN_FACE_LUMA = 25f
    const val MAX_FACE_LUMA = 240f
    /** Minimum variance-of-Laplacian on the aligned tile; below this it is blurred. */
    const val MIN_SHARPNESS_VAR = 12f

    /**
     * Head-pose limits for discarding non-frontal faces before embedding.
     * MobileFaceNet is trained on roughly frontal faces: a profile view produces
     * an embedding that will not match even the correct child, and may weakly
     * match the wrong one.
     */
    const val MAX_FACE_YAW_DEGREES = 40f
    const val MAX_FACE_ROLL_DEGREES = 35f

    /**
     * Padding added around the detected face box before cropping to the model
     * input, as a fraction of the box's longest side. Only used on the
     * no-landmark fallback path. MUST match FACE_CROP_MARGIN in
     * `functions/src/embedding.ts`, or server and device embeddings of the same
     * child are framed differently and the cosine score falls.
     */
    const val FACE_CROP_MARGIN = 0.2f

    // ---------------------------------------------------------------------
    // Camera / scan loop
    // ---------------------------------------------------------------------
    /**
     * Analysis resolution requested from CameraX. 720p keeps a face 20 m away
     * above [MIN_FACE_PX] while costing ML Kit roughly a third of what 1080p
     * does; the scan loop is single-flight, so detector latency is the frame
     * rate.
     */
    const val ANALYSIS_WIDTH = 1280
    const val ANALYSIS_HEIGHT = 720

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
