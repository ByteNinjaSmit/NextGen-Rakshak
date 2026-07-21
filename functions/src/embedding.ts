import * as path from "path";
import * as tf from "@tensorflow/tfjs-node";
import * as blazeface from "@tensorflow-models/blazeface";

// Must match the Android pipeline (FacePreprocessor + TFLiteEmbeddingExtractor)
// so server-computed alert embeddings are comparable to on-device scan embeddings.
const INPUT_SIZE = 112;
const MODEL_URL = `file://${path.join(__dirname, "..", "model", "model.json")}`;

let embedder: tf.GraphModel | null = null;
let detector: blazeface.BlazeFaceModel | null = null;

async function ensureLoaded(): Promise<void> {
  if (!embedder) embedder = await tf.loadGraphModel(MODEL_URL);
  if (!detector) detector = await blazeface.load();
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
  const output = embedder!.predict(normalized) as tf.Tensor;

  const embedding = Array.from(await output.data());
  tf.dispose([decoded, face, resized, normalized, output]);
  return embedding;
}

/** Return a tensor cropped to the largest detected face, or a clone if none. */
async function cropLargestFace(image: tf.Tensor3D): Promise<tf.Tensor3D> {
  const [height, width] = image.shape;
  const faces = await detector!.estimateFaces(image, false);
  if (faces.length === 0) return image.clone();

  let best: { top: number; left: number; h: number; w: number } | null = null;
  let bestArea = 0;
  for (const f of faces) {
    const [x1, y1] = f.topLeft as [number, number];
    const [x2, y2] = f.bottomRight as [number, number];
    const area = (x2 - x1) * (y2 - y1);
    if (area > bestArea) {
      bestArea = area;
      best = { top: y1, left: x1, h: y2 - y1, w: x2 - x1 };
    }
  }
  if (!best) return image.clone();

  const top = clamp(Math.floor(best.top), 0, height - 1);
  const left = clamp(Math.floor(best.left), 0, width - 1);
  const h = clamp(Math.floor(best.h), 1, height - top);
  const w = clamp(Math.floor(best.w), 1, width - left);
  return image.slice([top, left, 0], [h, w, 3]);
}

function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}
