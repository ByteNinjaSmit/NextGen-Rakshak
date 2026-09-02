#!/usr/bin/env python3
"""Convert a MobileFaceNet TF1 frozen graph (.pb) into a TF2 SavedModel.

The pretrained MobileFaceNet weights ship as a TF1 frozen GraphDef, but both
downstream steps (TFLite conversion for Android, and tf.node.loadSavedModel on
the server) want a SavedModel. This wraps the frozen graph in a batch-1
`serving_default` signature.

Usage:
  python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel

Defaults match sirius-ai/MobileFaceNet_TF: input `img_inputs`, output
`embeddings` (already L2-normalized by the graph).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

INPUT_SIZE = 112
SUPPORTED_EMBEDDING_SIZES = (128, 512)  # MobileFaceNet / ArcFace


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pb", required=True, type=Path, help="Frozen graph (.pb).")
    ap.add_argument("--out", required=True, type=Path, help="SavedModel output directory.")
    ap.add_argument("--input-tensor", default="img_inputs:0")
    ap.add_argument("--output-tensor", default="embeddings:0")
    args = ap.parse_args()

    if not args.pb.is_file():
        print(f"ERROR: no such file: {args.pb}", file=sys.stderr)
        sys.exit(1)

    import numpy as np
    import tensorflow as tf

    graph_def = tf.compat.v1.GraphDef()
    graph_def.ParseFromString(args.pb.read_bytes())

    # Wrap the frozen graph in a callable, pruned to the tensors we care about.
    wrapped = tf.compat.v1.wrap_function(
        lambda: tf.compat.v1.import_graph_def(graph_def, name=""), []
    )
    fn = wrapped.prune(args.input_tensor, args.output_tensor)

    probe = fn(tf.constant(np.zeros((1, INPUT_SIZE, INPUT_SIZE, 3), np.float32)))
    print(f"  output shape: {tuple(probe.shape)}")
    print(f"  output L2 norm: {float(np.linalg.norm(probe.numpy())):.6f}")
    if probe.shape[-1] not in SUPPORTED_EMBEDDING_SIZES:
        print(
            f"ERROR: expected a {SUPPORTED_EMBEDDING_SIZES}-d embedding, got {probe.shape[-1]}.",
            file=sys.stderr,
        )
        sys.exit(1)

    class Wrapper(tf.Module):
        def __init__(self, fn):
            super().__init__()
            self.fn = fn

        @tf.function(
            input_signature=[
                tf.TensorSpec([1, INPUT_SIZE, INPUT_SIZE, 3], tf.float32, name="input")
            ]
        )
        def __call__(self, x):
            return {"embedding": self.fn(x)}

    module = Wrapper(fn)
    tf.saved_model.save(module, str(args.out), signatures={"serving_default": module.__call__})
    print(f"  wrote SavedModel -> {args.out}")
    print("\nNext: python scripts/convert_models.py --saved-model", args.out)


if __name__ == "__main__":
    main()
