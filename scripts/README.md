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
| Output | `[1,128]` (MobileFaceNet — what ships) **or** `[1,512]` (ArcFace upgrade) — length is read at runtime, never assumed |
| Alignment | 3-point similarity warp (left eye, right eye, nose) onto the ArcFace template — `scripts/face_align.py` ↔ `FaceGeometry` (Android) ↔ `TEMPLATE`/`solveSimilarity` (`functions/src/embedding.ts`) |
| Fallback (no landmarks) | square crop centred on the box, `FACE_CROP_MARGIN = 0.2` |
| Matching | cosine similarity, threshold **measured, not assumed** — see "Threshold" below |

---

> **Both artifacts must be generated in the same run.** They are compared to each
> other at runtime — the server embeds the parent's photo, the phone embeds the
> live face — so two different models mean two incompatible vector spaces. A
> width mismatch (a 512-d device model against 128-d server embeddings) is
> **silent**: nothing crashes, every alert falls out on a length check, and the
> scanner simply never matches anyone. `AlertIndex` on the device now detects and
> reports it, but the fix is always to regenerate both from one SavedModel.

---

## Option A — the shipping configuration: MobileFaceNet

Pretrained MobileFaceNet from **[sirius-ai/MobileFaceNet_TF]** (Apache-2.0):

```bash
pip install tensorflow pillow numpy

curl -L -o mobilefacenet.pb \
  https://raw.githubusercontent.com/sirius-ai/MobileFaceNet_TF/master/arch/pretrained_model/MobileFaceNet_9925_9680.pb

python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel --precision dynamic
python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel
python scripts/evaluate_model.py            # measure the threshold on your photos
```

Dynamic-range quantisation takes the phone's copy from 5.9 MB to **1.5 MB**.
`verify_parity.py` then runs one input through both the quantised `.tflite` and
the source SavedModel: measured **cosine 0.99967**, confirming the quantisation
did not damage the embedding.

[sirius-ai/MobileFaceNet_TF]: https://github.com/sirius-ai/MobileFaceNet_TF

## Option B — upgrade to a modern ArcFace model (not currently shipped)

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
  0.7142-0.9899, different-person 0.0864-0.3551 → threshold **0.55** (mid-band).
  The synopsis's 0.75 sat inside the same-person range and missed 5/15 genuine
  pairs. **This is the model and the threshold that ship**, with alignment and
  multi-frame fusion added on top — both of which move genuine pairs further from
  impostors, so 0.55 keeps at least the headroom it was measured with.
- A 512-d ArcFace (`w600k_mbf`) device model was trialled and **reverted**: the
  Cloud Function was still serving the 128-d MobileFaceNet, so alert embeddings
  and live embeddings had different widths and no comparison ever ran. If the
  upgrade is revisited, convert *both* artifacts in the same run and re-measure.
- After adding 3-point alignment / multi-frame fusion / an ArcFace model, the
  same-person band moves **down** (ArcFace cosine for genuine pairs is typically
  ~0.4–0.7, impostors ~0.0–0.3). Do not carry 0.55 over blindly — run the eval.

The asymmetry still favours the lower value: a missed child is the failure the
system exists to prevent; a false candidate costs one "Not a match" tap, and
every match is human-confirmed by design.

## Geometry must match on both sides — measured

A cosine score is only meaningful when both vectors were produced by the **same
geometry**. Measured on the shipped 128-d model, one real same-person pair:

| pairing | cosine |
|---|---|
| centred crop vs centred crop | **0.8855** |
| 3-point align vs 3-point align | **0.8467** |
| **centred crop vs 3-point align, on the _same photo_** | **0.38 - 0.49** |

Either geometry works on its own. Mixing them scores *below the 0.55 threshold on
an identical face*, so a server running a different geometry than the phone does
not degrade matching — it silently disables it. That is exactly what happened
when `ef3998f` gave the device 3-point alignment while the deployed Cloud
Function was still cropping: every live face scored ~0.2-0.4 against its own
alert and nothing ever matched.

Because of this the **mobile app now embeds alert photos on-device** (see
`ScanViewModel.prepare`), so both sides of every comparison come from one
implementation. The server embedding is kept only as a fallback for when the
photo cannot be fetched at all. Redeploy `functions` after any change to
`embedding.ts` so that fallback stays comparable.

## Verify the running system

- **Android**: `mobilefacenet.tflite` in assets; `TFLiteEmbeddingExtractor.backend`
  logs which accelerator was chosen; scanning a known face yields a high cosine.
- **Server**: deploy `functions`, create an alert with a photo, confirm the
  `onAlertCreated` log reports `dims: 128` (or `512`) on the written embedding.
