#!/usr/bin/env python3
"""Emit BOTH Rakshak embedding artifacts from ONE MobileFaceNet SavedModel.

Why one source: the Android app (`mobilefacenet.tflite`) and the Firebase Cloud
Function (`functions/model/savedmodel/`) must use the *same* weights, or cosine
scores between a server-computed alert embedding and an on-device scan embedding
won't line up and matches are silently missed. The device gets a quantized
.tflite; the server loads the SavedModel itself via `tf.node.loadSavedModel`, so
there is no second conversion that could drift from the first.

Contract enforced (must match FacePreprocessor + TFLiteEmbeddingExtractor and
functions/src/embedding.ts):
  input : [1, 112, 112, 3], RGB, normalized (px - 127.5) / 127.5
  output: [1, 128] face embedding

Usage:
  pip install tensorflow
  python scripts/convert_models.py --saved-model ./mobilefacenet_savedmodel
  python scripts/verify_parity.py  --saved-model ./mobilefacenet_savedmodel

Get a MobileFaceNet SavedModel first (weights are NOT committed — size/licensing).
See scripts/README.md for a vetted source and the exact steps.
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ANDROID_ASSET = REPO / "nextgen-rakshak-mobile/app/src/main/assets/mobilefacenet.tflite"
FUNCTIONS_MODEL_DIR = REPO / "functions/model"
INPUT_SIZE = 112
SUPPORTED_EMBEDDING_SIZES = (128, 512)  # MobileFaceNet / ArcFace upgrade


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
    if not any(s[-1] in SUPPORTED_EMBEDDING_SIZES for s in out_shape):
        fail(f"Expected a {SUPPORTED_EMBEDDING_SIZES}-d output; got {out_shape}. "
             "Not a MobileFaceNet/ArcFace embedding model.")


def _representative_dataset(sample_dir: Path):
    """Yield ~100 normalized 112x112 face tiles for INT8 calibration."""
    import numpy as np
    from PIL import Image

    imgs = sorted(p for p in sample_dir.rglob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png"})
    if not imgs:
        fail(f"--precision int8 needs sample face images in {sample_dir} (100+ recommended).")
    for p in imgs[:200]:
        arr = np.asarray(Image.open(p).convert("RGB").resize((INPUT_SIZE, INPUT_SIZE)), np.float32)
        yield [((arr - 127.5) / 127.5)[None, ...]]


def export_tflite(saved_model_dir: Path, precision: str, sample_dir: Path) -> None:
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))

    if precision == "float16":
        # Best accuracy/size trade for GPU-delegate inference: weights in fp16,
        # compute in fp16/fp32. ~2x smaller than fp32, no measurable accuracy loss.
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    elif precision == "int8":
        # Full integer: smallest + fastest on NNAPI/DSP, needs a representative
        # dataset. Some accuracy cost — always re-run evaluate_model.py after.
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.representative_dataset = lambda: _representative_dataset(sample_dir)
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        converter.inference_input_type = tf.float32
        converter.inference_output_type = tf.float32
    elif precision == "dynamic":
        # Legacy behaviour: dynamic-range quant (int8 weights, float activations).
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    else:  # "fp32"
        converter.optimizations = []

    tflite = converter.convert()
    ANDROID_ASSET.parent.mkdir(parents=True, exist_ok=True)
    ANDROID_ASSET.write_bytes(tflite)
    print(f"  wrote {ANDROID_ASSET}  ({len(tflite) / 1024:.0f} KB, precision={precision})")


def export_server_model(saved_model_dir: Path) -> None:
    """Copy the SavedModel for the Cloud Function to load via tf.node.loadSavedModel.

    Deliberately NOT a tfjs GraphModel conversion: loading the SavedModel directly
    means the server runs the very same graph this script quantized into the
    Android .tflite, with no converter in between to introduce drift. It also
    avoids tensorflowjs_converter, which cannot run on Windows (it imports
    tensorflow_decision_forests, which has no Windows build).
    """
    dest = FUNCTIONS_MODEL_DIR / "savedmodel"
    if dest.exists():
        shutil.rmtree(dest)
    shutil.copytree(saved_model_dir, dest)
    print(f"  copied SavedModel -> {dest}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--saved-model", required=True, type=Path,
                    help="Path to a MobileFaceNet/ArcFace SavedModel directory.")
    ap.add_argument("--precision", default="float16",
                    choices=["float16", "int8", "dynamic", "fp32"],
                    help="TFLite weight precision (default: float16).")
    ap.add_argument("--sample-dir", type=Path, default=REPO / "data/eval",
                    help="Face images for INT8 calibration (only used with --precision int8).")
    args = ap.parse_args()

    if not args.saved_model.is_dir():
        fail(f"Not a directory: {args.saved_model}")

    print("[1/3] Checking model contract (112x112x3 -> 128d or 512d)...")
    check_contract(args.saved_model)
    print(f"[2/3] Exporting Android mobilefacenet.tflite (precision={args.precision})...")
    export_tflite(args.saved_model, args.precision, args.sample_dir)
    print("[3/3] Copying SavedModel for the Cloud Function...")
    export_server_model(args.saved_model)
    print("\nDone. Next: run scripts/verify_parity.py to confirm the two "
          "artifacts produce matching embeddings.")


if __name__ == "__main__":
    main()
