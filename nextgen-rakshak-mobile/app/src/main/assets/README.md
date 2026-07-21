# Assets

Place the MobileFaceNet model here as **`mobilefacenet.tflite`**.

The app loads it at runtime via `TFLiteEmbeddingExtractor` (see
`Constants.MODEL_ASSET`). It is not committed because of its size and licensing.

Model input: 112×112 RGB, normalized to [-1, 1]. Output: 128-d embedding.
