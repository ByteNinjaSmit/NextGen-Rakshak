package com.rakshak.app.utils

/** App-wide constants. Keep magic numbers and collection names here. */
object Constants {
    // ML
    const val MODEL_ASSET = "mobilefacenet.tflite"
    const val FACE_INPUT_SIZE = 112       // MobileFaceNet input is 112x112
    const val EMBEDDING_SIZE = 128
    const val SIMILARITY_THRESHOLD = 0.75f

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
