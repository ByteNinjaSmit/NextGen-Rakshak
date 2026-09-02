#!/usr/bin/env python3
"""Measure the shipped face model on real photos: ROC, TAR@FAR, threshold.

The face-match threshold in the app (`Constants.SIMILARITY_THRESHOLD`) is only
valid for the exact model + alignment + precision it was measured against. After
any change to those, re-run this.

Layout of the evaluation set (not committed — your own photos):

    data/eval/
      alice/  img1.jpg  img2.jpg  img3.jpg
      bob/    img1.jpg  img2.jpg
      ...

Every image is aligned with `face_align` (same 3-point warp as the app), embedded
through `nextgen-rakshak-mobile/app/src/main/assets/mobilefacenet.tflite`, then
every within-person pair (label "same") and every across-person pair (label
"different") is scored by cosine similarity.

Detection/landmarks use MTCNN if installed (`pip install mtcnn`); otherwise the
script falls back to a centre square crop and prints a warning — usable, but the
numbers will be pessimistic versus the aligned pipeline the app actually runs.

    python scripts/evaluate_model.py
    python scripts/evaluate_model.py --data data/eval --far 0.001
"""
from __future__ import annotations

import argparse
import itertools
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from face_align import INPUT_SIZE, align_face, center_square_crop  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
ANDROID_ASSET = REPO / "nextgen-rakshak-mobile/app/src/main/assets/mobilefacenet.tflite"
IMG_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def load_detector():
    try:
        from mtcnn import MTCNN
    except ImportError:
        print("WARNING: `mtcnn` not installed — falling back to centre-crop "
              "(numbers will understate the aligned pipeline). `pip install mtcnn`.")
        return None
    return MTCNN()


def tile_for(path: Path, detector) -> np.ndarray | None:
    img = Image.open(path).convert("RGB")
    if detector is None:
        return np.asarray(center_square_crop(img, (0, 0, img.width, img.height)), np.float32)

    faces = detector.detect_faces(np.asarray(img))
    if not faces:
        print(f"  no face detected in {path.name}; skipping")
        return None
    face = max(faces, key=lambda f: f["box"][2] * f["box"][3])
    kp = face["keypoints"]
    landmarks = np.array([kp["left_eye"], kp["right_eye"], kp["nose"]], dtype=np.float64)
    return np.asarray(align_face(img, landmarks), np.float32)


def make_embedder():
    import tensorflow as tf

    interp = tf.lite.Interpreter(model_path=str(ANDROID_ASSET))
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]

    def embed(tile: np.ndarray) -> np.ndarray:
        x = ((tile - 127.5) / 127.5)[None, ...].astype(np.float32)
        interp.set_tensor(inp["index"], x)
        interp.invoke()
        v = interp.get_tensor(out["index"]).reshape(-1).astype(np.float64)
        return v / (np.linalg.norm(v) + 1e-9)

    return embed, out["shape"][-1]


def roc(same: np.ndarray, diff: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    thr = np.unique(np.concatenate([same, diff]))
    tar = np.array([(same >= t).mean() for t in thr])   # true accept rate
    far = np.array([(diff >= t).mean() for t in thr])   # false accept rate
    return thr, tar, far


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=Path, default=REPO / "data/eval")
    ap.add_argument("--far", type=float, default=1e-3,
                    help="Target false-accept rate for the reported operating point.")
    args = ap.parse_args()

    if not ANDROID_ASSET.exists():
        sys.exit(f"ERROR: {ANDROID_ASSET} missing — run convert_models.py first.")
    people = [d for d in sorted(args.data.iterdir()) if d.is_dir()] if args.data.is_dir() else []
    if len(people) < 2:
        sys.exit(f"ERROR: need >= 2 person folders under {args.data}. See the module docstring.")

    detector = load_detector()
    embed, dim = make_embedder()
    print(f"model: {ANDROID_ASSET.name}  embedding dim: {dim}\n")

    embeddings: dict[str, list[np.ndarray]] = {}
    for person in people:
        vs = []
        for path in sorted(person.iterdir()):
            if path.suffix.lower() not in IMG_EXT:
                continue
            tile = tile_for(path, detector)
            if tile is not None:
                vs.append(embed(tile))
        if len(vs) >= 1:
            embeddings[person.name] = vs
        print(f"  {person.name}: {len(vs)} embeddings")

    same, diff = [], []
    for name, vs in embeddings.items():
        for a, b in itertools.combinations(vs, 2):
            same.append(float(np.dot(a, b)))
    for (n1, v1), (n2, v2) in itertools.combinations(embeddings.items(), 2):
        for a in v1:
            for b in v2:
                diff.append(float(np.dot(a, b)))
    same, diff = np.array(same), np.array(diff)
    if not len(same) or not len(diff):
        sys.exit("ERROR: not enough pairs — add more images / more people.")

    print(f"\npairs: {len(same)} same-person, {len(diff)} different-person")
    print(f"same-person cosine : {same.min():.4f} – {same.max():.4f}  (mean {same.mean():.4f})")
    print(f"different-person   : {diff.min():.4f} – {diff.max():.4f}  (mean {diff.mean():.4f})")

    gap_lo, gap_hi = diff.max(), same.min()
    if gap_hi > gap_lo:
        mid = (gap_lo + gap_hi) / 2
        print(f"\nCLEAN SEPARATION: empty band {gap_lo:.4f} – {gap_hi:.4f}")
        print(f"  -> suggested SIMILARITY_THRESHOLD = {mid:.2f}  "
              f"(midpoint, ~{(gap_hi - gap_lo) / 2:.2f} headroom each side)")
    else:
        print(f"\nOVERLAP: different-person max {gap_lo:.4f} exceeds same-person min {gap_hi:.4f}")

    thr, tar, far = roc(same, diff)
    # Operating point at the target FAR.
    ok = np.where(far <= args.far)[0]
    if len(ok):
        i = ok[np.argmax(tar[ok])]
        print(f"\n@ FAR <= {args.far:g}:  threshold {thr[i]:.4f}  ->  TAR {tar[i]:.4f}  (FAR {far[i]:.4f})")
    # Youden's J (max TAR - FAR).
    j = int(np.argmax(tar - far))
    print(f"Youden's J optimum:  threshold {thr[j]:.4f}  ->  TAR {tar[j]:.4f}  FAR {far[j]:.4f}")

    # Trapezoidal AUC over FAR ascending.
    order = np.argsort(far)
    auc = float(np.trapz(tar[order], far[order]))
    print(f"ROC AUC: {auc:.4f}")

    print("\nReminder: update Constants.SIMILARITY_THRESHOLD (and the mirror in "
          "scripts/README.md / CLAUDE.md) if this run moved the band.")


if __name__ == "__main__":
    main()
