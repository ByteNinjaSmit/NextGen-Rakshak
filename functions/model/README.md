# MobileFaceNet model (TensorFlow.js GraphModel)

The function loads the embedding model from `functions/model/model.json`
(+ its `*.bin` weight shards). It is **not committed** (size/licensing).

## Why a tfjs model here (not the Android .tflite)?
`@tensorflow/tfjs-node` loads TF.js GraphModels, not `.tflite`. Convert the SAME
MobileFaceNet weights used by the Android app (`mobilefacenet.tflite`) so the
server and on-device embeddings are comparable.

## Convert once (Python)
```bash
pip install tensorflowjs
# From a SavedModel / Keras / frozen graph of the SAME MobileFaceNet:
tensorflowjs_converter \
  --input_format=tf_saved_model \
  --output_format=tfjs_graph_model \
  ./mobilefacenet_savedmodel \
  ./functions/model
```
Result: `functions/model/model.json` + `group1-shard*.bin`.

## Contract (must match Android)
- Input: `[1, 112, 112, 3]`, RGB, normalized `(px - 127.5) / 127.5`.
- Output: `[1, 128]` face embedding.
- Matching: cosine similarity, threshold **0.75**.

If the server model differs from the Android one, cosine scores won't line up
and matches will be missed.
