package com.rakshak.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.rakshak.app.utils.Constants
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MobileFaceNet embedding extractor backed by TensorFlow Lite.
 * Loads `mobilefacenet.tflite` from assets and outputs a 128-d embedding.
 */
class TFLiteEmbeddingExtractor(context: Context) : EmbeddingExtractor {

    private val interpreter: Interpreter by lazy {
        val model = context.assets.open(Constants.MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(model.size).apply {
            order(ByteOrder.nativeOrder())
            put(model)
            rewind()
        }
        // Multi-threaded inference to hold the <200 ms per-face budget on mid-range devices.
        Interpreter(buffer, Interpreter.Options().setNumThreads(NUM_THREADS))
    }

    /**
     * TFLite interpreters are not thread-safe, so serialise access. In practice the
     * scan loop is single-flight, but a stray concurrent call would corrupt the
     * interpreter's internal state rather than fail loudly.
     */
    override fun extract(face: Bitmap): FloatArray = synchronized(this) {
        val input = toNormalizedBuffer(face)
        val output = Array(1) { FloatArray(Constants.EMBEDDING_SIZE) }
        interpreter.run(input, output)
        output[0]
    }

    /** Convert pixels to a [-1, 1] normalized RGB float buffer. */
    private fun toNormalizedBuffer(bitmap: Bitmap): ByteBuffer {
        val size = Constants.FACE_INPUT_SIZE
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3).apply {
            order(ByteOrder.nativeOrder())
        }
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (pixel in pixels) {
            buffer.putFloat((((pixel shr 16) and 0xFF) - 127.5f) / 127.5f) // R
            buffer.putFloat((((pixel shr 8) and 0xFF) - 127.5f) / 127.5f)  // G
            buffer.putFloat(((pixel and 0xFF) - 127.5f) / 127.5f)          // B
        }
        buffer.rewind()
        return buffer
    }

    private companion object {
        const val NUM_THREADS = 4
    }
}
