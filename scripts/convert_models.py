#!/usr/bin/env python3
"""Emit BOTH Rakshak embedding artifacts from ONE MobileFaceNet SavedModel.

Why one source: the Android app (`mobilefacenet.tflite`) and the Firebase
Cloud Function (tfjs GraphModel in `functions/model/`) must use the *same*
weights, or cosine scores between a server-computed alert embedding and an
on-device scan embedding won't line up and matches are silently missed.
This script derives both from a single SavedModel so they always agree.

Contract enforced (must match FacePreprocessor + TFLiteEmbeddingExtractor and
functions/src/embedding.ts):
  input : [1, 112, 112, 3], RGB, normalized (px - 127.5) / 127.5
  output: [1, 128] face embedding

Usage:
  pip install "tensorflow>=2.14,<2.16" tensorflowjs
  python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel

Get a MobileFaceNet SavedModel first (weights are NOT committed — size/licensing).
See scripts/README.md for vetted sources and how to produce a SavedModel.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ANDROID_ASSET = REPO / "nextgen-rakshak-mobile/app/src/main/assets/mobilefacenet.tflite"
FUNCTIONS_MODEL_DIR = REPO / "functions/model"
INPUT_SIZE = 112
EMBEDDING_SIZE = 128


def fail(msg: str) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def check_contract(saved_model_dir: Path) -> None:
    import tensorflow as tf

    model = tf.saved_model.load(str(saved_model_dir))
    sig = model.signatures.get("serving_default")
    if sig is None:
        fail("SavedModel has no 'serving_default' signature.")
    in_shape = [s.shape.as_list() for s in sig.inputs if s.dtype == tf.float32]
    out_shape = [s.shape.as_list() for s in sig.outputs]
    print(f"  input shapes : {in_shape}")
    print(f"  output shapes: {out_shape}")
    if not any(s[-3:] == [INPUT_SIZE, INPUT_SIZE, 3] for s in in_shape if len(s) >= 3):
        fail(f"Expected an input of [.,{INPUT_SIZE},{INPUT_SIZE},3]; got {in_shape}. "
             "Wrong model or wrong input layout.")
    if not any(s[-1] == EMBEDDING_SIZE for s in out_shape):
        fail(f"Expected a {EMBEDDING_SIZE}-d output; got {out_shape}. "
             "This is not the 128-d MobileFaceNet the app expects.")


def export_tflite(saved_model_dir: Path) -> None:
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))
    # Dynamic-range quantization: ~4x smaller, accuracy largely preserved.
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    ANDROID_ASSET.parent.mkdir(parents=True, exist_ok=True)
    ANDROID_ASSET.write_bytes(tflite)
    print(f"  wrote {ANDROID_ASSET}  ({len(tflite) / 1024:.0f} KB)")


def export_tfjs(saved_model_dir: Path) -> None:
    FUNCTIONS_MODEL_DIR.mkdir(parents=True, exist_ok=True)
    cmd = [
        sys.executable, "-m", "tensorflowjs.converters.converter",
        "--input_format=tf_saved_model",
        "--output_format=tfjs_graph_model",
        str(saved_model_dir),
        str(FUNCTIONS_MODEL_DIR),
    ]
    print("  running:", " ".join(cmd))
    result = subprocess.run(cmd)
    if result.returncode != 0:
        fail("tensorflowjs_converter failed. Is `tensorflowjs` installed?")
    print(f"  wrote {FUNCTIONS_MODEL_DIR / 'model.json'} (+ weight shards)")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--saved-model", required=True, type=Path,
                    help="Path to a MobileFaceNet SavedModel directory.")
    args = ap.parse_args()

    if not args.saved_model.is_dir():
        fail(f"Not a directory: {args.saved_model}")

    print("[1/3] Checking model contract (112x112x3 -> 128d)...")
    check_contract(args.saved_model)
    print("[2/3] Exporting Android mobilefacenet.tflite...")
    export_tflite(args.saved_model)
    print("[3/3] Exporting server tfjs GraphModel...")
    export_tfjs(args.saved_model)
    print("\nDone. Next: run scripts/verify_parity.py to confirm the two "
          "artifacts produce matching embeddings.")


if __name__ == "__main__":
    main()
