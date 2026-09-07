package com.rakshak.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rakshak.app.utils.Constants
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * MobileFaceNet / ArcFace embedding extractor backed by LiteRT (TensorFlow Lite).
 *
 * Loads `mobilefacenet.tflite` from assets and returns the model's face
 * embedding. The width is whatever the model outputs (128 for the original
 * MobileFaceNet, 512 for the ArcFace upgrade) — read from the output tensor at
 * load time, never assumed.
 *
 * Inference runs on XNNPACK (LiteRT's default: SIMD-accelerated CPU kernels)
 * across 4 threads. What ships is a dynamic-range quantised `.tflite` (see
 * `scripts/convert_models.py --precision dynamic`): 1.5 MB, int8 weights with
 * float activations, which XNNPACK runs on its hybrid kernels. LiteRT 2.x no
 * longer bundles the NNAPI / GPU delegates; if a device-specific accelerator is
 * needed later, add the Play Services LiteRT runtime rather than a standalone
 * delegate artifact.
 *
 * All interpreter work — creation, inference, close — runs on one dedicated
 * thread because the interpreter is not thread-safe and the scan loop calls in
 * from a coroutine dispatcher whose thread can change.
 */
class TFLiteEmbeddingExtractor(context: Context) : EmbeddingExtractor {

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, WORKER_THREAD_NAME).apply { isDaemon = true }
    }

    private class Engine(val interpreter: Interpreter, val outputSize: Int, val backend: String)

    /**
     * The interpreter, once built, kept for [close]. Read without forcing
     * [engineResult], so closing an extractor that was never used does not load a
     * model purely in order to throw it away.
     */
    @Volatile
    private var built: Engine? = null

    /**
     * Build result, computed once.
     *
     * **This must only ever be resolved from a thread that is not [worker].** The
     * build itself is submitted to [worker] and waited on, so resolving it from
     * inside a worker task deadlocks instantly: the task holds the executor's one
     * thread while waiting for a second task that cannot start until it returns.
     * That is not hypothetical — it is the bug that made the scanner appear to do
     * nothing at all, because `extract` used to touch this from inside its own
     * worker task and every embedding blocked until the timeout below fired.
     * [assertNotOnWorker] now makes a regression fail loudly instead of silently
     * costing 20 seconds per call.
     *
     * The timeout stays as a genuine safety net: a model the shipped LiteRT
     * runtime cannot parse can hang native `Interpreter` construction outright,
     * and this is reached from the scan loop. On failure the result is cached, so
     * later calls fail fast rather than re-hanging.
     */
    private val engineResult: Result<Engine> by lazy {
        assertNotOnWorker()
        runCatching { worker.submit<Engine> { buildEngine() }.get(BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .onFailure { Log.e(TAG, "face model load failed / timed out", it) }
    }

    private val engine: Engine
        get() = engineResult.getOrElse {
            throw IllegalStateException("face embedding model unavailable: ${it.message}", it)
        }

    override fun extract(face: Bitmap): FloatArray {
        // Resolved HERE, on the calling thread, while the worker is idle — never
        // inside the task below. See [engineResult].
        val e = engine
        val input = toNormalizedBuffer(face)
        return worker.submit<FloatArray> {
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
        val existing = built
        if (existing != null) {
            worker.submit { runCatching { existing.interpreter.close() } }.get()
        }
        worker.shutdown()
    }

    /**
     * Fail fast if the model build is being resolved from the interpreter thread.
     * Without this the mistake costs a full [BUILD_TIMEOUT_SECONDS] per call and
     * surfaces only as "the scanner matches nothing", which is very expensive to
     * trace back to its cause.
     */
    private fun assertNotOnWorker() = check(Thread.currentThread().name != WORKER_THREAD_NAME) {
        "The face model must not be initialised from the $WORKER_THREAD_NAME thread — " +
            "the build is submitted to that same single-threaded executor and would deadlock."
    }

    // --- setup (always on `worker`) ---

    private fun buildEngine(): Engine {
        val model = appContext.assets.open(Constants.MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(model.size).apply {
            order(ByteOrder.nativeOrder())
            put(model)
            rewind()
        }
        Log.i(TAG, "loading ${Constants.MODEL_ASSET} (${model.size} bytes)")

        // Try XNNPACK first (fp16 kernels). If delegate init rejects the model,
        // fall back to the plain reference kernels rather than failing outright.
        val (interp, backend) = try {
            val opts = Interpreter.Options().setNumThreads(CPU_THREADS).setUseXNNPACK(true)
            Interpreter(buffer, opts) to "cpu-xnnpack"
        } catch (e: Throwable) {
            Log.w(TAG, "XNNPACK path failed; retrying without it", e)
            buffer.rewind()
            val opts = Interpreter.Options().setNumThreads(CPU_THREADS).setUseXNNPACK(false)
            Interpreter(buffer, opts) to "cpu"
        }

        val size = outputSizeOf(interp)
        Log.i(TAG, "embedding backend: $backend, dim=$size")
        return Engine(interp, size, backend).also { built = it }
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
        const val WORKER_THREAD_NAME = "face-embed"
        const val CPU_THREADS = 4
        const val BUILD_TIMEOUT_SECONDS = 20L
    }
}
