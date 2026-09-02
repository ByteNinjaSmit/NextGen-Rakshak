package com.rakshak.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rakshak.app.utils.Constants
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * MobileFaceNet / ArcFace embedding extractor backed by LiteRT (TensorFlow Lite).
 *
 * Loads `mobilefacenet.tflite` from assets and returns the model's face
 * embedding. The width is whatever the model outputs (128 for the original
 * MobileFaceNet, 512 for the ArcFace upgrade) — read from the output tensor at
 * load time, never assumed.
 *
 * Inference runs on XNNPACK (LiteRT's default: SIMD-accelerated CPU kernels,
 * including fp16 paths) across 4 threads. Ship a `float16` `.tflite` (see
 * `scripts/convert_models.py --precision float16`) — it is smaller, loads faster
 * and hits XNNPACK's half-precision kernels. LiteRT 2.x no longer bundles the
 * NNAPI / GPU delegates; if a device-specific accelerator is needed later, add
 * the Play Services LiteRT runtime rather than a standalone delegate artifact.
 *
 * All interpreter work — creation, inference, close — runs on one dedicated
 * thread because the interpreter is not thread-safe and the scan loop calls in
 * from a coroutine dispatcher whose thread can change.
 */
class TFLiteEmbeddingExtractor(context: Context) : EmbeddingExtractor {

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "face-embed").apply { isDaemon = true }
    }

    private class Engine(val interpreter: Interpreter, val outputSize: Int, val backend: String)

    private val engine: Engine by lazy { worker.submit<Engine> { buildEngine() }.get() }

    override fun extract(face: Bitmap): FloatArray {
        val input = toNormalizedBuffer(face)
        return worker.submit<FloatArray> {
            val e = engine
            val output = Array(1) { FloatArray(e.outputSize) }
            e.interpreter.run(input, output)
            output[0]
        }.get()
    }

    /** The model's embedding width, forced by touching [engine]. */
    val embeddingSize: Int get() = engine.outputSize

    /** "cpu-xnnpack" — kept as a field so a future accelerator path can report itself. */
    val backend: String get() = engine.backend

    fun close() {
        worker.submit { runCatching { engine.interpreter.close() } }.get()
        worker.shutdown()
    }

    // --- setup (always on `worker`) ---

    private fun buildEngine(): Engine {
        val model = appContext.assets.open(Constants.MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(model.size).apply {
            order(ByteOrder.nativeOrder())
            put(model)
            rewind()
        }
        val interp = Interpreter(buffer, Interpreter.Options().setNumThreads(CPU_THREADS))
        val size = outputSizeOf(interp)
        Log.i(TAG, "embedding backend: cpu-xnnpack, dim=$size")
        return Engine(interp, size, "cpu-xnnpack")
    }

    private fun outputSizeOf(interp: Interpreter): Int {
        val size = interp.getOutputTensor(0).shape().last()  // e.g. [1, 512] -> 512
        require(size in Constants.SUPPORTED_EMBEDDING_SIZES) {
            "mobilefacenet.tflite outputs a ${size}-d embedding; expected one of " +
                Constants.SUPPORTED_EMBEDDING_SIZES.joinToString() + ". Wrong asset?"
        }
        return size
    }

    /** Convert pixels to a [-1, 1] normalized RGB float buffer. */
    private fun toNormalizedBuffer(bitmap: Bitmap): ByteBuffer {
        val size = Constants.FACE_INPUT_SIZE
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3).apply { order(ByteOrder.nativeOrder()) }
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
        const val TAG = "TFLiteEmbeddingExtractor"
        const val CPU_THREADS = 4
    }
}
