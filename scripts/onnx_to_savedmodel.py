#!/usr/bin/env python3
"""Convert an ONNX face-recognition model to the SavedModel this repo expects.

Use this for the **accuracy upgrade** path — replacing the 2018 MobileFaceNet
weights with a modern ArcFace-trained model of the same speed class:

| Model         | Backbone      | Dim | Source                                            |
|---------------|---------------|-----|---------------------------------------------------|
| `w600k_mbf`   | MobileFaceNet | 512 | InsightFace `buffalo_s` pack (`w600k_mbf.onnx`)   |
| EdgeFace-S/XS | EdgeNeXt      | 512 | https://github.com/otroshi/edgeface (ONNX assets) |

`w600k_mbf` is the closest drop-in: same MobileFaceNet backbone (=> same on-device
latency) but trained on WebFace600K with ArcFace, which lifts real-world accuracy
well above the current `MobileFaceNet_9925_9680` weights.

Output contract enforced here (matches `convert_models.py`,
`functions/src/embedding.ts` and the Android pipeline):
  input : [1, 112, 112, 3] float32, RGB, normalized (px - 127.5) / 127.5
  output: {"embedding": [1, 512]}   (128 also accepted)

Steps:
  pip install onnx2tf onnx onnx-graphsurgeon sng4onnx tensorflow pillow
  python scripts/onnx_to_savedmodel.py --onnx w600k_mbf.onnx --out ./arcface_savedmodel \
      --source-layout nchw --source-bgr
  python scripts/convert_models.py --saved-model ./arcface_savedmodel --precision float16
  python scripts/verify_parity.py  --saved-model ./arcface_savedmodel
  python scripts/evaluate_model.py           # RE-MEASURE THE THRESHOLD — mandatory

InsightFace models typically want NCHW BGR input scaled to [-1, 1]; EdgeFace wants
NHWC RGB. Set --source-layout / --source-bgr to match yours. The wrapper this
script saves always exposes the repo's NHWC normalized-RGB contract regardless.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

INPUT_SIZE = 112
SUPPORTED_EMBEDDING_SIZES = (128, 512)


def fail(msg: str) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--onnx", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path, help="SavedModel output dir.")
    ap.add_argument("--source-layout", choices=["nchw", "nhwc"], default="nchw",
                    help="Channel layout the ONNX model's input expects.")
    ap.add_argument("--source-bgr", action="store_true",
                    help="ONNX model expects BGR channel order (most InsightFace models).")
    args = ap.parse_args()

    if not args.onnx.is_file():
        fail(f"no such file: {args.onnx}")

    import numpy as np
    import tensorflow as tf

    raw_dir = args.out.with_name(args.out.name + "_onnx2tf")
    print(f"[1/3] onnx2tf  {args.onnx}  ->  {raw_dir}")
    try:
        subprocess.run(["onnx2tf", "-i", str(args.onnx), "-o", str(raw_dir), "-osd"], check=True)
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        fail(f"onnx2tf failed ({e}). Install: pip install onnx2tf onnx onnx-graphsurgeon sng4onnx")

    print("[2/3] Wrapping to the repo input contract")
    base = tf.saved_model.load(str(raw_dir))
    base_fn = base.signatures["serving_default"]
    in_key = next(iter(base_fn.structured_input_signature[1]))
    out_key = next(iter(base_fn.structured_outputs))

    class Wrapper(tf.Module):
        def __init__(self):
            super().__init__()
            self._base = base  # keep a ref so variables are tracked

        @tf.function(input_signature=[
            tf.TensorSpec([1, INPUT_SIZE, INPUT_SIZE, 3], tf.float32, name="input")
        ])
        def __call__(self, x):
            t = tf.reverse(x, axis=[-1]) if args.source_bgr else x
            if args.source_layout == "nchw":
                t = tf.transpose(t, [0, 3, 1, 2])
            emb = base_fn(**{in_key: t})[out_key]
            return {"embedding": emb}

    module = Wrapper()
    probe = module(tf.constant(np.zeros((1, INPUT_SIZE, INPUT_SIZE, 3), np.float32)))["embedding"]
    dim = int(probe.shape[-1])
    print(f"  embedding dim {dim}   L2 norm {float(np.linalg.norm(probe)):.4f}")
    if dim not in SUPPORTED_EMBEDDING_SIZES:
        fail(f"embedding dim {dim} not in {SUPPORTED_EMBEDDING_SIZES}. If deliberate, widen "
             "Constants.SUPPORTED_EMBEDDING_SIZES on both apps and re-run.")

    print(f"[3/3] Saving -> {args.out}")
    tf.saved_model.save(module, str(args.out), signatures={"serving_default": module.__call__})
    print("\nDone. Next:")
    print(f"  python scripts/convert_models.py --saved-model {args.out} --precision float16")
    print(f"  python scripts/verify_parity.py  --saved-model {args.out}")
    print("  python scripts/evaluate_model.py           # reset SIMILARITY_THRESHOLD")


if __name__ == "__main__":
    main()
