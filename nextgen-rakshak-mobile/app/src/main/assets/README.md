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
| Output | `[1,128]`, already L2-normalized by the graph |
| Matching | cosine similarity, `Constants.SIMILARITY_THRESHOLD` = 0.55 |

## This file and `functions/model/savedmodel/` must come from the same weights

They are compared to each other at runtime: the server embeds the parent's photo,
the phone embeds the live face, and the two vectors are scored against one
another. Two models means two incompatible vector spaces.

The width mismatch is the failure mode to watch for, because it is **silent**.
A 512-d device model against 128-d server embeddings does not crash and does not
warn — every alert falls out on a length check and the scanner simply never
matches anyone. `AlertIndex` now detects it, counts it, and the scanner says
"Face model mismatch" instead of "no match", but the fix is always the same:
regenerate both artifacts from one SavedModel with `scripts/convert_models.py`
and confirm with `scripts/verify_parity.py` (currently **cosine 0.99967**).
