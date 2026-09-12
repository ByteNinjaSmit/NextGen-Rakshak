# Face model (TensorFlow SavedModel)

The function loads the embedding model from `functions/model/savedmodel/` via
`tf.node.loadSavedModel`. It is **not committed** (size/licensing) — generate it
with `scripts/convert_models.py`.

## Why a SavedModel here (not a tfjs GraphModel)?
`tfjs-node` can load a TensorFlow SavedModel directly, so the server runs the
*same graph* that `convert_models.py` quantized into the Android
`mobilefacenet.tflite`. Converting to a tfjs GraphModel would add a second
conversion that could drift from the first — and `tensorflowjs_converter` cannot
run on Windows anyway (it imports `tensorflow_decision_forests`, which has no
Windows build).

## Generate it
```bash
pip install tensorflow
python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel --precision dynamic
python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel
```
Result: `functions/model/savedmodel/{saved_model.pb,variables/}`.

See `scripts/README.md` for where to get the source SavedModel. Measured parity
between the exported `.tflite` and this SavedModel: **cosine 0.99967**.

## Contract (must match Android)
- Input: `[1, 112, 112, 3]`, RGB, normalized `(px - 127.5) / 127.5`.
- Output: `[1, 128]` for the shipping MobileFaceNet, `[1, 512]` for an ArcFace
  upgrade — already L2-normalized by the graph. The width is **read from the
  graph at runtime** on both sides; nothing hard-codes it.
- Face geometry: **3-point similarity alignment** (left eye, right eye, nose)
  onto the ArcFace 112×112 template — `TEMPLATE` in `functions/src/embedding.ts`
  must stay identical to `FaceGeometry.TEMPLATE_112` (Android) and
  `ARCFACE_TEMPLATE` (`scripts/face_align.py`). The centred square crop with
  `FACE_CROP_MARGIN` (0.2) is only the no-landmark fallback, mirrored in Android's
  `FacePreprocessor`.
- Matching: cosine similarity, threshold **0.55**, set from measurement — see
  `scripts/README.md`.

## A geometry or width mismatch disables matching silently

Not "degrades" — disables. Measured on the shipped model, same-person photo:
aligned-vs-aligned scores 0.8467, crop-vs-crop 0.8855, but **crop-vs-aligned
scores 0.38–0.49**, i.e. below the 0.55 threshold on an identical face. A width
mismatch is worse: every alert embedding falls out on a length check and nothing
ever matches, with no error anywhere.

This is why the mobile app now re-embeds alert photos **on-device** and treats
this function's embedding as a fallback only. Redeploy `functions` after any
change to `embedding.ts` so the fallback stays comparable, and regenerate both
artifacts from one SavedModel in one run.
