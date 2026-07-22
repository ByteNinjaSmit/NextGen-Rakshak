import * as path from "path";
import * as tf from "@tensorflow/tfjs-node";
import * as blazeface from "@tensorflow-models/blazeface";

// Must match the Android pipeline (FacePreprocessor + TFLiteEmbeddingExtractor)
// so server-computed alert embeddings are comparable to on-device scan embeddings.
const INPUT_SIZE = 112;
/** Must equal Constants.FACE_CROP_MARGIN in the Android app. */
const FACE_CROP_MARGIN = 0.2;
/**
 * The MobileFaceNet TensorFlow SavedModel, loaded directly rather than converted
 * to a tfjs GraphModel. Loading the SavedModel is what guarantees the server runs
 * the *identical* graph that `scripts/convert_models.py` quantized into the
 * Android `mobilefacenet.tflite`, with no conversion step in between to drift.
 */
const MODEL_DIR = path.join(__dirname, "..", "model", "savedmodel");

/** tfjs-node does not re-export the SavedModel handle type, so derive it. */
type LoadedSavedModel = Awaited<ReturnType<typeof tf.node.loadSavedModel>>;

let embedder: LoadedSavedModel | null = null;
let detector: blazeface.BlazeFaceModel | null = null;

async function ensureLoaded(): Promise<void> {
  if (!embedder) embedder = await tf.node.loadSavedModel(MODEL_DIR);
  if (!detector) detector = await blazeface.load();
}

/** The SavedModel signature has a single named output; accept either shape. */
function firstTensor(result: tf.Tensor | tf.Tensor[] | tf.NamedTensorMap): tf.Tensor {
  if (Array.isArray(result)) return result[0];
  if (result instanceof tf.Tensor) return result;
  return Object.values(result)[0];
}

/**
 * Compute a 128-d MobileFaceNet embedding for the largest face in an image.
 * Detects and crops the face first (mirroring the Android flow); falls back to
 * the whole image if no face is found.
 */
export async function computeEmbedding(imageBuffer: Buffer): Promise<number[]> {
  await ensureLoaded();

  const decoded = tf.node.decodeImage(imageBuffer, 3) as tf.Tensor3D;
  const face = await cropLargestFace(decoded);

  // Resize -> normalize to [-1, 1] -> add batch dim -> infer.
  const resized = tf.image.resizeBilinear(face, [INPUT_SIZE, INPUT_SIZE]);
  const normalized = resized.sub(127.5).div(127.5).expandDims(0);
  const output = firstTensor(embedder!.predict(normalized));

  const embedding = Array.from(await output.data());
  tf.dispose([decoded, face, resized, normalized, output]);
  return embedding;
}

/**
 * Return a tensor cropped to the largest detected face, or a clone if none.
 *
 * The crop is a **square centred on the detected box**, padded by
 * FACE_CROP_MARGIN — deliberately the same geometry as the Android
 * FacePreprocessor. The device detects faces with ML Kit and this function uses
 * BlazeFace; their raw boxes frame a face differently, so cropping each
 * detector's box directly would hand the same model two differently-framed
 * images of the same child and depress the cosine score. Deriving a square from
 * the box centre and size removes both that framing difference and the aspect
 * distortion of squashing a non-square box into a square input.
 *
 * Any change here must be mirrored in Android's `FacePreprocessor`.
 */
async function cropLargestFace(image: tf.Tensor3D): Promise<tf.Tensor3D> {
  const [height, width] = image.shape;
  const faces = await detector!.estimateFaces(image, false);
  if (faces.length === 0) return image.clone();

  let best: { cx: number; cy: number; size: number } | null = null;
  let bestArea = 0;
  for (const f of faces) {
    const [x1, y1] = f.topLeft as [number, number];
    const [x2, y2] = f.bottomRight as [number, number];
    const w = x2 - x1;
    const h = y2 - y1;
    const area = w * h;
    if (area > bestArea) {
      bestArea = area;
      best = { cx: x1 + w / 2, cy: y1 + h / 2, size: Math.max(w, h) };
    }
  }
  if (!best) return image.clone();

  const side = clamp(
    Math.floor(best.size * (1 + 2 * FACE_CROP_MARGIN)),
    1,
    Math.min(height, width),
  );
  const left = clamp(Math.floor(best.cx - side / 2), 0, width - side);
  const top = clamp(Math.floor(best.cy - side / 2), 0, height - side);
  return image.slice([top, left, 0], [side, side, 3]);
}

function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}
