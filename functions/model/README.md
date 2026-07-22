# MobileFaceNet model (TensorFlow SavedModel)

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
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel
python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel
```
Result: `functions/model/savedmodel/{saved_model.pb,variables/}`.

See `scripts/README.md` for where to get the source SavedModel.

## Contract (must match Android)
- Input: `[1, 112, 112, 3]`, RGB, normalized `(px - 127.5) / 127.5`.
- Output: `[1, 128]`, already L2-normalized by the graph.
- Face crop: square centred on the detected box, padded by `FACE_CROP_MARGIN`
  (0.2) — mirrored in Android's `FacePreprocessor`.
- Matching: cosine similarity, threshold **0.55** (set from measurement — see
  `scripts/README.md`).

If the server model differs from the Android one, cosine scores won't line up
and matches will be missed.
