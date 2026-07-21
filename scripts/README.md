# Model setup — MobileFaceNet (one source → two artifacts)

The embedding model is **not committed** (size + licensing). Two artifacts must
be generated from the **same** MobileFaceNet weights, or embeddings won't align
and matches are silently missed:

| Artifact | Consumer | Path |
|----------|----------|------|
| `mobilefacenet.tflite` | Android app | `nextgen-rakshak-mobile/app/src/main/assets/` |
| tfjs GraphModel (`model.json` + `*.bin`) | Firebase Cloud Function | `functions/model/` |

**Shared contract:** input `[1,112,112,3]` RGB, normalized `(px - 127.5)/127.5`;
output `[1,128]`; matching = cosine similarity, threshold `0.75`.

## Steps

```bash
pip install "tensorflow>=2.14,<2.16" tensorflowjs

# 1. Obtain a MobileFaceNet SavedModel (see sources below) -> ./mobilefacenet_savedmodel
# 2. Emit BOTH artifacts from it:
python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel
# 3. Prove they agree before shipping:
python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel
```

`convert_models.py` shape-checks the model, writes the quantized `.tflite` to
Android assets, and runs `tensorflowjs_converter` for the server model.
`verify_parity.py` runs one input through both and asserts cosine ≥ 0.99.

> Python 3.13 is not yet supported by TensorFlow. Use a **Python 3.10–3.12**
> venv for these scripts.

## Where to get a MobileFaceNet SavedModel

Pick one, confirm its license permits your use, and export a SavedModel with the
112×112×3 → 128-d signature above:

- **sirius-ai/MobileFaceNet_TF** — TF frozen graph; wrap/convert to SavedModel.
- **Keras/TF2 MobileFaceNet reproductions** (e.g. `zye1996/Mobilefacenet-TF2`) —
  load the `.h5`, `model.save('mobilefacenet_savedmodel')`.
- Any MobileFaceNet you train yourself on your dataset.

If a source outputs 192-d instead of 128-d, either use its 128-d variant or
update `EMBEDDING_SIZE` in both `Constants.kt` and this pipeline to match.

## Verify the running system after conversion

- Android: `mobilefacenet.tflite` present in assets; scan screen produces
  non-zero similarity on a known face.
- Server: deploy `functions`, create an alert with a photo, confirm the
  `onAlertCreated` function writes a 128-length `embedding` to the Firestore doc.
