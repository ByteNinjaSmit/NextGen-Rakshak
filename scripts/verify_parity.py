#!/usr/bin/env python3
"""Confirm the Android .tflite and the source SavedModel agree.

Runs the SAME synthetic 112x112 face through the exported
`mobilefacenet.tflite` and the source SavedModel, then reports cosine
similarity between the two 128-d embeddings. If quantization or the export
mangled the model, the score drops and matches would fail in production.

  python scripts/verify_parity.py --saved-model ./mobilefacenet_savedmodel

Pass if cosine >= 0.99 (quantization noise only). The tfjs server model is a
faithful conversion of the same SavedModel, so SavedModel-vs-tflite parity is a
valid proxy for server-vs-device parity.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np

REPO = Path(__file__).resolve().parent.parent
ANDROID_ASSET = REPO / "nextgen-rakshak-mobile/app/src/main/assets/mobilefacenet.tflite"
INPUT_SIZE = 112
THRESHOLD = 0.99


def normalized_input(seed: int = 0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    px = rng.integers(0, 256, size=(1, INPUT_SIZE, INPUT_SIZE, 3)).astype(np.float32)
    return (px - 127.5) / 127.5  # same contract as app + cloud function


def tflite_embedding(x: np.ndarray) -> np.ndarray:
    import tensorflow as tf

    interp = tf.lite.Interpreter(model_path=str(ANDROID_ASSET))
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    interp.set_tensor(inp["index"], x)
    interp.invoke()
    return interp.get_tensor(out["index"]).reshape(-1)


def savedmodel_embedding(saved_model_dir: Path, x: np.ndarray) -> np.ndarray:
    import tensorflow as tf

    model = tf.saved_model.load(str(saved_model_dir))
    fn = model.signatures["serving_default"]
    result = fn(tf.constant(x))
    tensor = next(iter(result.values()))
    return tensor.numpy().reshape(-1)


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-9))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--saved-model", required=True, type=Path)
    args = ap.parse_args()

    if not ANDROID_ASSET.exists():
        print(f"ERROR: {ANDROID_ASSET} missing. Run convert_models.py first.", file=sys.stderr)
        sys.exit(1)

    x = normalized_input()
    a = tflite_embedding(x)
    b = savedmodel_embedding(args.saved_model, x)
    score = cosine(a, b)
    print(f"tflite vs SavedModel cosine = {score:.5f}  (threshold {THRESHOLD})")
    if score < THRESHOLD:
        print("FAIL: exports diverge. Do NOT ship — matches would be missed.", file=sys.stderr)
        sys.exit(1)
    print("PASS: embeddings agree. Safe to ship.")


if __name__ == "__main__":
    main()
