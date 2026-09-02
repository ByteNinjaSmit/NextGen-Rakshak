#!/usr/bin/env python3
"""Reference 3-point face alignment — the Python mirror of the pipeline.

Every consumer of the embedding model must feed it a face warped the *same* way,
or embeddings of the same person taken by different code paths won't line up.
This module is the single source of truth for that warp on the Python side; it
MUST stay identical to:

  - `FaceGeometry.TEMPLATE_112` + `similarityTransform` (Android, Kotlin)
  - `TEMPLATE` + `solveSimilarity` in `functions/src/embedding.ts` (server, TS)

Alignment uses **left eye, right eye, nose** only — the three landmarks the
server's BlazeFace detector can report. The transform is a 2D similarity
(uniform scale + rotation + translation, no shear).
"""
from __future__ import annotations

import numpy as np
from PIL import Image

INPUT_SIZE = 112

# First three rows of the standard ArcFace 5-point reference, for a 112x112 tile.
# Order: [left eye, right eye, nose], left = smaller x.
ARCFACE_TEMPLATE = np.array(
    [
        [38.2946, 51.6963],
        [73.5318, 51.5014],
        [56.0252, 71.7366],
    ],
    dtype=np.float64,
)


def template(output_size: int = INPUT_SIZE) -> np.ndarray:
    """The 3-point template scaled to an arbitrary square output size."""
    return ARCFACE_TEMPLATE * (output_size / 112.0)


def similarity_transform(src: np.ndarray, dst: np.ndarray) -> np.ndarray:
    """Least-squares 2D similarity mapping ``src`` onto ``dst``.

    Model per point:  x' = a*x - b*y + tx ;  y' = b*x + a*y + ty
    Returns the 2x3 affine matrix [[a, -b, tx], [b, a, ty]].

    Same normal-equation solve as the Kotlin and TypeScript implementations, so
    all three produce bit-comparable transforms for the same inputs.
    """
    src = np.asarray(src, dtype=np.float64)
    dst = np.asarray(dst, dtype=np.float64)
    assert src.shape == dst.shape and src.shape[0] >= 2

    ata = np.zeros((4, 4))
    aty = np.zeros(4)
    for (x, y), (xp, yp) in zip(src, dst):
        for row, target in (
            (np.array([x, -y, 1.0, 0.0]), xp),
            (np.array([y, x, 0.0, 1.0]), yp),
        ):
            ata += np.outer(row, row)
            aty += row * target

    a, b, tx, ty = np.linalg.solve(ata, aty)
    return np.array([[a, -b, tx], [b, a, ty]], dtype=np.float64)


def align_face(image: Image.Image, landmarks: np.ndarray, output_size: int = INPUT_SIZE) -> Image.Image:
    """Warp ``image`` so ``landmarks`` (3x2: left eye, right eye, nose) land on
    the template. ``landmarks`` are in image pixels; eyes are ordered by x here
    to match the detectors' handling.
    """
    landmarks = np.asarray(landmarks, dtype=np.float64)
    eyes = landmarks[:2][np.argsort(landmarks[:2, 0])]
    src = np.vstack([eyes, landmarks[2]])

    # Solve template -> image (PIL's Image.transform wants the output->input map).
    m = similarity_transform(template(output_size), src)
    a, nb, tx = m[0]
    b, aa, ty = m[1]
    return image.transform(
        (output_size, output_size),
        Image.AFFINE,
        (a, nb, tx, b, aa, ty),
        resample=Image.BILINEAR,
    )


def center_square_crop(image: Image.Image, box: tuple[float, float, float, float],
                       margin: float = 0.2, output_size: int = INPUT_SIZE) -> Image.Image:
    """Fallback used when landmarks are unavailable — must match the apps'
    ``cropAndResize`` / square-crop fallback geometry."""
    x1, y1, x2, y2 = box
    cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
    side = max(x2 - x1, y2 - y1) * (1 + 2 * margin)
    side = min(side, image.width, image.height)
    left = min(max(cx - side / 2, 0), image.width - side)
    top = min(max(cy - side / 2, 0), image.height - side)
    crop = image.crop((left, top, left + side, top + side))
    return crop.resize((output_size, output_size), Image.BILINEAR)
