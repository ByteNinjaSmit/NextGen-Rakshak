import * as path from "path";
import * as tf from "@tensorflow/tfjs-node";
import * as blazeface from "@tensorflow-models/blazeface";

// Must mirror the Android pipeline (FacePreprocessor + FaceGeometry +
// TFLiteEmbeddingExtractor) so server-computed alert embeddings are comparable
// to on-device scan embeddings.
const INPUT_SIZE = 112;
/** Fallback-only: must equal Constants.FACE_CROP_MARGIN in the Android app. */
const FACE_CROP_MARGIN = 0.2;

/**
 * Canonical 3-point alignment template (left eye, right eye, nose) for a 112x112
 * tile — the first three rows of the standard ArcFace 5-point reference. MUST
 * stay identical to `FaceGeometry.TEMPLATE_112` (Android) and `ARCFACE_TEMPLATE`
 * in `scripts/face_align.py`. Ordered by ascending x.
 */
const TEMPLATE: Array<[number, number]> = [
  [38.2946, 51.6963],
  [73.5318, 51.5014],
  [56.0252, 71.7366],
];

/**
 * The MobileFaceNet / ArcFace TensorFlow SavedModel, loaded directly rather than
 * converted to a tfjs GraphModel — loading the SavedModel is what guarantees the
 * server runs the identical graph that `scripts/convert_models.py` quantized
 * into the Android `.tflite`.
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
 * Compute the face embedding (128-d MobileFaceNet or 512-d ArcFace, whichever
 * model is deployed) for the largest face in an image. Aligns the face to the
 * canonical template with the detected eye+nose landmarks (mirroring the phone);
 * falls back to a centred square crop, then to the whole image.
 */
export async function computeEmbedding(imageBuffer: Buffer): Promise<number[]> {
  await ensureLoaded();

  const rawImage = tf.node.decodeImage(imageBuffer, 3) as tf.Tensor3D;
  const decoded = rawImage.toFloat();
  rawImage.dispose();
  const face = await detectLargestFace(decoded);
  const tile = buildTile(decoded, face); // 112x112x3, float32

  const normalized = tile.sub(127.5).div(127.5).expandDims(0);
  const output = firstTensor(embedder!.predict(normalized));

  const embedding = Array.from(await output.data());
  tf.dispose([decoded, tile, normalized, output]);
  return embedding;
}

type Face = {
  box: { cx: number; cy: number; size: number };
  /** [leftEye, rightEye, nose] in image pixels, ordered by x; null if unavailable. */
  landmarks: Array<[number, number]> | null;
};

async function detectLargestFace(image: tf.Tensor3D): Promise<Face | null> {
  const raw = await detector!.estimateFaces(image, false);
  let best: Face | null = null;
  let bestArea = 0;
  for (const f of raw) {
    const [x1, y1] = f.topLeft as [number, number];
    const [x2, y2] = f.bottomRight as [number, number];
    const w = x2 - x1;
    const h = y2 - y1;
    const area = w * h;
    if (area <= bestArea) continue;
    bestArea = area;

    // blazeface landmark order: [rightEye, leftEye, nose, mouth, rightEar, leftEar].
    let landmarks: Array<[number, number]> | null = null;
    const lm = (f as unknown as { landmarks?: number[][] }).landmarks;
    if (lm && lm.length >= 3) {
      const eyes = [lm[1] as [number, number], lm[0] as [number, number]].sort(
        (a, b) => a[0] - b[0],
      );
      landmarks = [eyes[0], eyes[1], lm[2] as [number, number]];
    }
    best = { box: { cx: x1 + w / 2, cy: y1 + h / 2, size: Math.max(w, h) }, landmarks };
  }
  return best;
}

/** 112x112x3 float32 tile: aligned if landmarks exist, else cropped, else whole. */
function buildTile(image: tf.Tensor3D, face: Face | null): tf.Tensor3D {
  const [height, width] = image.shape;

  if (face?.landmarks) {
    const aligned = alignFace(image, face.landmarks);
    if (aligned) return aligned;
  }

  if (face) {
    const { cx, cy, size } = face.box;
    const side = clamp(
      Math.floor(size * (1 + 2 * FACE_CROP_MARGIN)),
      1,
      Math.min(height, width),
    );
    const left = clamp(Math.floor(cx - side / 2), 0, width - side);
    const top = clamp(Math.floor(cy - side / 2), 0, height - side);
    const crop = image.slice([top, left, 0], [side, side, 3]) as tf.Tensor3D;
    const resized = tf.image.resizeBilinear(crop, [INPUT_SIZE, INPUT_SIZE]);
    crop.dispose();
    return resized;
  }

  return tf.image.resizeBilinear(image, [INPUT_SIZE, INPUT_SIZE]);
}

/**
 * Warp [image] so the detected eye+nose land on [TEMPLATE], producing a
 * 112x112x3 tile. tf.image.transform's 8-param projective vector maps OUTPUT
 * (template) coordinates back to INPUT (image) coordinates, so the similarity
 * transform is solved in the template -> image direction.
 */
function alignFace(image: tf.Tensor3D, landmarks: Array<[number, number]>): tf.Tensor3D | null {
  const st = solveSimilarity(TEMPLATE, landmarks); // template -> image
  if (!st) return null;
  const { a, b, tx, ty } = st;
  const transform = tf.tensor2d([[a, -b, tx, b, a, ty, 0, 0]], [1, 8]);
  const batched = image.expandDims(0) as tf.Tensor4D;
  const out = tf.image.transform(
    batched,
    transform,
    "bilinear",
    "constant",
    0,
    [INPUT_SIZE, INPUT_SIZE],
  ) as tf.Tensor4D;
  const tile = out.squeeze([0]) as tf.Tensor3D;
  tf.dispose([transform, batched, out]);
  return tile;
}

/**
 * Least-squares 2D similarity transform (uniform scale + rotation + translation)
 * mapping [from] onto [to]. Identical model to Android's
 * FaceGeometry.similarityTransform:
 *   x' =  a*x - b*y + tx
 *   y' =  b*x + a*y + ty
 */
function solveSimilarity(
  from: Array<[number, number]>,
  to: Array<[number, number]>,
): { a: number; b: number; tx: number; ty: number } | null {
  if (from.length !== to.length || from.length < 2) return null;

  const ata = [
    [0, 0, 0, 0],
    [0, 0, 0, 0],
    [0, 0, 0, 0],
    [0, 0, 0, 0],
  ];
  const aty = [0, 0, 0, 0];
  const addRow = (r: number[], target: number) => {
    for (let i = 0; i < 4; i++) {
      aty[i] += r[i] * target;
      for (let j = 0; j < 4; j++) ata[i][j] += r[i] * r[j];
    }
  };
  for (let i = 0; i < from.length; i++) {
    const [x, y] = from[i];
    addRow([x, -y, 1, 0], to[i][0]);
    addRow([y, x, 0, 1], to[i][1]);
  }

  return solve4x4(ata, aty);
}

function solve4x4(
  m: number[][],
  rhs: number[],
): { a: number; b: number; tx: number; ty: number } | null {
  const n = 4;
  const a = m.map((row, r) => [...row, rhs[r]]); // augmented [m | rhs]

  for (let col = 0; col < n; col++) {
    let pivot = col;
    for (let r = col + 1; r < n; r++) {
      if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
    }
    if (Math.abs(a[pivot][col]) < 1e-12) return null;
    [a[col], a[pivot]] = [a[pivot], a[col]];
    for (let r = col + 1; r < n; r++) {
      const f = a[r][col] / a[col][col];
      for (let c = col; c <= n; c++) a[r][c] -= f * a[col][c];
    }
  }

  const x = [0, 0, 0, 0];
  for (let row = n - 1; row >= 0; row--) {
    let acc = a[row][n];
    for (let c = row + 1; c < n; c++) acc -= a[row][c] * x[c];
    x[row] = acc / a[row][row];
  }
  return { a: x[0], b: x[1], tx: x[2], ty: x[3] };
}

function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}
