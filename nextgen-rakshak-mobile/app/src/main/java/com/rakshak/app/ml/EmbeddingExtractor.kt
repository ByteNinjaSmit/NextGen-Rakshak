package com.rakshak.app.ml

import android.graphics.Bitmap

/** Turns a preprocessed face bitmap into a face embedding vector. */
interface EmbeddingExtractor {
    /**
     * @return the model's face embedding (128-d for MobileFaceNet, 512-d for the
     *   ArcFace upgrade — whatever the shipped `.tflite` outputs). Input must be a
     *   [com.rakshak.app.utils.Constants.FACE_INPUT_SIZE]-square aligned tile.
     */
    fun extract(face: Bitmap): FloatArray
}
