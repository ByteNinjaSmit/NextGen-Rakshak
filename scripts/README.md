# Model setup — MobileFaceNet (one source → two artifacts)

The embedding model is **not committed** (size + licensing). Two artifacts are
generated from the **same** MobileFaceNet weights, so embeddings computed on the
server and on the phone are directly comparable:

| Artifact | Consumer | Path |
|----------|----------|------|
| `mobilefacenet.tflite` (quantized) | Android app | `nextgen-rakshak-mobile/app/src/main/assets/` |
| `savedmodel/` (TensorFlow SavedModel) | Firebase Cloud Function | `functions/model/` |

**Shared contract:** input `[1,112,112,3]` RGB, normalized `(px - 127.5)/127.5`;
output `[1,128]`, already L2-normalized by the graph; face crop = square centred
on the detector box padded by `FACE_CROP_MARGIN` (0.2); matching = cosine
similarity, threshold `0.55` (see the measurements below).

## Source weights

Pretrained MobileFaceNet from **[sirius-ai/MobileFaceNet_TF]**
(Apache-2.0 — attribute it in your report):

- File: `arch/pretrained_model/MobileFaceNet_9925_9680.pb` (~5.9 MB)
- TF1 frozen graph; input `img_inputs` `[-1,112,112,3]`, output `embeddings` `[-1,128]`
- The filename encodes its reported LFW accuracy (99.25%)

[sirius-ai/MobileFaceNet_TF]: https://github.com/sirius-ai/MobileFaceNet_TF

## Steps

```bash
pip install tensorflow

# 1. Download the frozen graph
curl -L -o mobilefacenet.pb \
  https://raw.githubusercontent.com/sirius-ai/MobileFaceNet_TF/master/arch/pretrained_model/MobileFaceNet_9925_9680.pb

# 2. Convert the frozen graph to a SavedModel
python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel

# 3. Emit both artifacts from it
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel

# 4. Prove the two agree before shipping
python scripts/verify_parity.py --saved-model ./mobilefacenet_savedmodel
```

`convert_models.py` shape-checks the model, writes the quantized `.tflite` into
Android assets, and copies the SavedModel for the Cloud Function.
`verify_parity.py` runs one input through both and asserts cosine ≥ 0.99
(measured on this model: **0.99967**).

> TensorFlow 2.21 works on Python 3.13. `tensorflowjs` is **not** needed — the
> server loads the SavedModel directly.

## Verify the running system

- **Android**: `mobilefacenet.tflite` present in assets; scanning a known face
  yields a high cosine score.
- **Server**: deploy `functions`, create an alert with a photo, and confirm the
  `onAlertCreated` log reports `dims: 128` on the written embedding.

## Measured threshold behaviour

The 0.75 figure from the synopsis was checked against this model using real
photographs (9 images of 4 people, run through the quantized `.tflite` with the
app's own crop geometry — 36 pairs total):

| | cosine range |
|---|---|
| Same person (15 pairs) | 0.7142 – 0.9899 |
| Different people (21 pairs) | 0.0864 – 0.3551 |
| **Separation gap** | **0.3591** |

The model separates identities very cleanly — no different-person pair came
close to any same-person pair.

### Why the threshold is 0.55, not the synopsis's 0.75

The two groups are separated by an empty band from **0.3551 to 0.7142**. Any
threshold inside that band classifies all 36 pairs correctly.

`0.75` sits *above* the band — inside the same-person range — and so misses
genuine matches:

| Threshold | False matches | Missed matches |
|---|---|---|
| 0.75 (synopsis) | 0 / 21 | **5 / 15** — same-person pairs at 0.71–0.75 fall below the cut |
| **0.55 (configured)** | **0 / 21** | **0 / 15** |

0.55 sits near the middle of the empty band, leaving roughly 0.19 of headroom on
each side, so it tolerates harder real-world pairs than the sample contains.

The asymmetry of the errors justifies favouring the lower value: a missed child
is the failure the system exists to prevent, whereas a false candidate costs only
the moment a volunteer takes to tap "Not a match" — the design already requires
every match to be human-confirmed, so false positives are cheap by construction.

Reproduce these numbers before changing the value again.
