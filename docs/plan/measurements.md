# Measurement Log

**Owner:** Bankar Smitraj (09) · **Task:** DOC-08

Every number the final report claims must appear here first, with the method and
the date it was taken. Fill a row the day you measure it — reconstructing
measurements in Week 8 is how projects end up quoting figures they cannot defend
at the viva.

Rules:
- Record the **device and conditions**, not just the number.
- Record failures too. "Measured 240 ms, target 200 ms, not met" is a result.
- Link to the raw data (script output, screenshot, log) wherever one exists.

---

## Non-Functional Requirements

| ID | Requirement | Target | Measured | Method | Device / conditions | Date | Verdict |
|---|---|---|---|---|---|---|---|
| NFR-01 | Face detection accuracy | ≥95% frontal | — | VER-06 | — | — | pending |
| NFR-02 | Face recognition accuracy | ≥90% under festival lighting | 100% on 36 adult pairs (0 FP/21, 0 FN/15) | 36-pair study, quantized `.tflite`, app crop geometry | Adult faces, public sample, good lighting | Week 3 | ⚠️ **proxy only — not children, not festival lighting** |
| NFR-03 | Inference time per face | <200 ms | — | VER-02 | — | — | pending |
| NFR-04 | Alert delivery (internet) | <5 s | — | VER-03 | — | — | pending |
| NFR-05 | Alert delivery (mesh) | <30 s | — | VER-08 | — | — | pending |
| NFR-06 | Scan throughput | 200+ faces/hour | — | VER-14 | — | — | pending |
| NFR-07 | Concurrent active alerts | 50, no degradation | — | VER-09 | — | — | pending |
| NFR-08 | Zero biometric upload | on-device only | — | VER-12 | — | — | pending |
| NFR-09 | Battery impact | camera only during an alert | — | VER-13 | — | — | pending |
| NFR-10 | Device compatibility | Android 5.0+ (API 21) | `minSdk 24` | build config | — | Week 3 | ⚠️ **synopsis correction required (GAP-09)** |

## Model Pipeline — completed in Week 3

The pipeline was split across all four tracks. The owner column is the member who
produced each number, so a question at the viva goes to the person who ran it.

| Metric | Result | Method | Owner | Date |
|---|---|---|---|---|
| Model size after quantisation | 5.9 MB → **1.5 MB** | `convert_models.py` | SB (09) | Week 3 |
| Both artefacts derived from one SavedModel | yes — no second conversion | `freeze_to_savedmodel.py` | SB (09) | Week 3 |
| Server runs the same graph as the device | yes — direct SavedModel load, not a tfjs `GraphModel` | `functions/src/embedding.ts` | TB (11) | Week 3 |
| Device/server crop geometry | square crop, shared 0.2 margin both sides | code review + shared constant | TB (11) + SB (09) | Week 3 |
| SavedModel output shape | `(1, 128)`, L2 norm 1.000000 | direct inference | VD (34) | Week 3 |
| Quantized `.tflite` vs SavedModel parity | cosine **0.99967** (threshold 0.99) | `verify_parity.py` | VD (34) | Week 3 |
| Same-person cosine range | 0.7142 – 0.9899 (15 pairs) | 36-pair study | AN (94) | Week 3 |
| Different-person cosine range | 0.0864 – 0.3551 (21 pairs) | 36-pair study | AN (94) | Week 3 |
| Separation gap | **0.3591** | derived | AN (94) | Week 3 |
| Errors at threshold 0.55 | 0 false / 21, 0 missed / 15 | 36-pair study | AN (94) | Week 3 |
| Errors at threshold 0.75 | 0 false / 21, **5 missed / 15** | 36-pair study | AN (94) | Week 3 |
| Threshold adopted in code | 0.75 → **0.55** in `Constants.SIMILARITY_THRESHOLD` | measurement above | SB (09) | Week 3 |

⚠️ The accuracy rows above were measured on **9 photographs of 4 adults in good
lighting**. They set the threshold on evidence; they do **not** answer NFR-02
(≥90% on children's faces under festival lighting). See VER-05.

## Mesh Trial — VER-08, Week 5

| Metric | Result | Notes |
|---|---|---|
| Devices in the trial | — | model, Android version each |
| Hops achieved | — | target ≥2 relays (A→B→C) |
| TTL observed at each hop | — | from logs; expect 6 → 5 → 4 |
| Duplicate suppression | — | packet arriving twice relayed once? |
| Alert delivery time, 1 hop | — | vs NFR-05 30 s |
| Alert delivery time, 2 hops | — | |
| Match relayed back to an online peer | — | |
| Failures observed | — | record everything, including what did not work |

## Build & Test

| Check | Result | Date |
|---|---|---|
| `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL — 13 tests, 0 failures | Week 3 |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL | Week 3 |
| `functions` `tsc --noEmit` | clean | Week 3 |
| `webportal` `tsc --noEmit` | clean | Week 3 |
| 16 KB alignment, arm64-v8a | all 8 libraries `p_align 16384` | Week 3 |
| 16 KB alignment, x86_64 | all 9 libraries `p_align 16384` | Week 3 |
| Unit test count after QA-04 | — target ≥25 | Week 5 |
| Test cases executed (VER-16) | — of ≥60 | Week 7 |
