# Assets

Place the face-embedding model here as **`mobilefacenet.tflite`**.

The app loads it at runtime via `TFLiteEmbeddingExtractor` (see
`Constants.MODEL_ASSET`). It is not committed because of its size and licensing.

## What ships

**MobileFaceNet** from [sirius-ai/MobileFaceNet_TF] (`MobileFaceNet_9925_9680`),
dynamic-range quantised: **1.5 MB**, 128-d output.

```bash
python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel
python scripts/convert_models.py       --saved-model ./mobilefacenet_savedmodel --precision dynamic
python scripts/verify_parity.py        --saved-model ./mobilefacenet_savedmodel
```

[sirius-ai/MobileFaceNet_TF]: https://github.com/sirius-ai/MobileFaceNet_TF

## Contract

| | value |
|---|---|
| Input | `[1,112,112,3]` RGB, normalized `(px - 127.5) / 127.5` |
| Output | `[1,128]` (or `[1,512]` for an ArcFace model), already L2-normalized by the graph. `TFLiteEmbeddingExtractor` reads the width from the output tensor at load time — nothing hard-codes it |
| Geometry | 3-point similarity alignment (eyes + nose) onto the ArcFace template (`FaceGeometry`); centred crop with `FACE_CROP_MARGIN` = 0.2 only when landmarks are missing |
| Matching | cosine similarity, `Constants.SIMILARITY_THRESHOLD` = 0.55 (strong single-frame match at 0.72) |

## This file and `functions/model/savedmodel/` must come from the same weights

They can still end up compared to each other at runtime. The app's primary path
now re-embeds each alert photo **on this device** (`ScanViewModel.prepare`), so
both sides of a normal comparison come from this one file — but the server's
embedding is still used as a fallback when the photo cannot be fetched, and two
different models mean two incompatible vector spaces.

The width mismatch is the failure mode to watch for, because it is **silent**.
A 512-d device model against 128-d server embeddings does not crash and does not
warn — every alert falls out on a length check and the scanner simply never
matches anyone. `AlertIndex` now detects it, counts it, and the scanner says
"Face model mismatch" instead of "no match", but the fix is always the same:
regenerate both artifacts from one SavedModel with `scripts/convert_models.py`
and confirm with `scripts/verify_parity.py` (currently **cosine 0.99967**).
