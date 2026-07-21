package com.rakshak.app.ml

import android.graphics.Bitmap

/** Turns a preprocessed face bitmap into a face embedding vector. */
interface EmbeddingExtractor {
    /** @return 128-d embedding. Input must be [Constants.FACE_INPUT_SIZE] square. */
    fun extract(face: Bitmap): FloatArray
}
