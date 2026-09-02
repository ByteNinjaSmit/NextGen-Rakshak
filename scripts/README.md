# Face model setup & evaluation

The embedding model is **not committed** (size + licensing). Everything the apps
need is generated from **one source model** so that an embedding computed on the
server and one computed on a phone are directly comparable.

| Artifact | Consumer | Path |
|----------|----------|------|
| `mobilefacenet.tflite` | Android app | `nextgen-rakshak-mobile/app/src/main/assets/` |
| `savedmodel/` (TF SavedModel) | Firebase Cloud Function | `functions/model/` |

**Shared contract** (do not change one side only):

| | value |
|---|---|
| Input | `[1,112,112,3]` RGB, normalized `(px - 127.5) / 127.5` |
| Output | `[1,128]` (MobileFaceNet) **or** `[1,512]` (ArcFace upgrade) — length is read at runtime, never assumed |
| Alignment | 3-point similarity warp (left eye, right eye, nose) onto the ArcFace template — `scripts/face_align.py` ↔ `FaceGeometry` (Android) ↔ `TEMPLATE`/`solveSimilarity` (`functions/src/embedding.ts`) |
| Fallback (no landmarks) | square crop centred on the box, `FACE_CROP_MARGIN = 0.2` |
| Matching | cosine similarity, threshold **measured, not assumed** — see "Threshold" below |

---

## Option A — keep the current MobileFaceNet weights

Pretrained MobileFaceNet from **[sirius-ai/MobileFaceNet_TF]** (Apache-2.0):

```bash
pip install tensorflow pillow numpy

curl -L -o mobilefacenet.pb \
  https://raw.githubusercontent.com/sirius-ai/MobileFaceNet_TF/master/arch/pretrained_model/MobileFaceNet_9925_9680.pb

python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel --precision float16
python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel
python scripts/evaluate_model.py            # measure the threshold on your photos
```

[sirius-ai/MobileFaceNet_TF]: https://github.com/sirius-ai/MobileFaceNet_TF

## Option B — upgrade to a modern ArcFace model (recommended)

Same backbone / same on-device latency, much better real-world accuracy. Get
`w600k_mbf.onnx` from the InsightFace `buffalo_s` pack (or an EdgeFace ONNX):

```bash
pip install onnx2tf onnx onnx-graphsurgeon sng4onnx tensorflow pillow numpy

python scripts/onnx_to_savedmodel.py --onnx w600k_mbf.onnx --out ./arcface_savedmodel \
    --source-layout nchw --source-bgr
python scripts/convert_models.py --saved-model ./arcface_savedmodel --precision float16
python scripts/verify_parity.py  --saved-model ./arcface_savedmodel
python scripts/evaluate_model.py            # RE-MEASURE — ArcFace shifts the band
```

`w600k_mbf` outputs a **512-d** embedding. Nothing hard-codes 128 any more, but if
you pick a model with some other width, add it to
`Constants.SUPPORTED_EMBEDDING_SIZES` (Android) and `SUPPORTED_EMBEDDING_SIZES`
(the Python scripts).

---

## `convert_models.py` precision flag

| `--precision` | size vs fp32 | notes |
|---------------|-------------|-------|
| `float16` *(default)* | ~0.5x | best with the GPU delegate; no measurable accuracy loss |
| `int8` | ~0.25x | fastest on NNAPI/DSP; needs `--sample-dir` with 100+ face images; **re-run `evaluate_model.py`** |
| `dynamic` | ~0.25x | old behaviour (int8 weights, float activations) |
| `fp32` | 1x | reference / debugging |

The Android `TFLiteEmbeddingExtractor` runs on XNNPACK (SIMD CPU kernels, fp16
paths) across 4 threads — LiteRT 2.x no longer bundles NNAPI/GPU delegates.
`float16` is smaller, loads faster and hits XNNPACK's half-precision kernels, so
it is the right default here.

## Parity check

`verify_parity.py` runs one synthetic input through the exported `.tflite` and
the source SavedModel and asserts cosine ≥ 0.99. The server loads the same
SavedModel directly, so tflite-vs-SavedModel parity is a valid proxy for
device-vs-server parity.

## Threshold — measure it, every time

`Constants.SIMILARITY_THRESHOLD` is only valid for the exact model + alignment +
precision it was measured against. `evaluate_model.py` takes a folder of labelled
photos:

```
data/eval/
  personA/  a1.jpg a2.jpg a3.jpg
  personB/  b1.jpg b2.jpg
  ...
```

and reports, over every same-person and different-person pair: the two cosine
ranges, the empty band between them (if any), the operating point at a target
FAR, Youden's-J optimum, and ROC AUC. It aligns each photo with the same
`face_align` warp the app uses (needs `pip install mtcnn` for landmarks;
otherwise it centre-crops and warns).

### History

- Original MobileFaceNet, **unaligned** square crop, 36 pairs: same-person
  0.7142–0.9899, different-person 0.0864–0.3551 → threshold **0.55** (mid-band).
  The synopsis's 0.75 sat inside the same-person range and missed 5/15 genuine
  pairs.
- After adding 3-point alignment / multi-frame fusion / an ArcFace model, the
  same-person band moves **down** (ArcFace cosine for genuine pairs is typically
  ~0.4–0.7, impostors ~0.0–0.3). Do not carry 0.55 over blindly — run the eval.

The asymmetry still favours the lower value: a missed child is the failure the
system exists to prevent; a false candidate costs one "Not a match" tap, and
every match is human-confirmed by design.

## Verify the running system

- **Android**: `mobilefacenet.tflite` in assets; `TFLiteEmbeddingExtractor.backend`
  logs which accelerator was chosen; scanning a known face yields a high cosine.
- **Server**: deploy `functions`, create an alert with a photo, confirm the
  `onAlertCreated` log reports `dims: 128` (or `512`) on the written embedding.
