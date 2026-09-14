"""Batch: strip the SenseNova watermark from generated product images.

The '日日新 sensenova' watermark is a fixed-position overlay applied by the API
gateway AFTER generation, so it always sits at the same offset from the
bottom-right corner. Measured on 1024x1024 output:
    x 0.797 -> 0.971 ,  y 0.908 -> 0.971   (normalised)

We therefore mask a FIXED rectangle hugging the bottom-right corner plus a
safety pad, and run OpenCV Telea inpainting. Product shots sit on plain white
backgrounds, so inpainting reconstructs the background seamlessly.

NOTE: do NOT try to auto-detect the watermark by "non-white pixels in the
bottom-right corner" -- the product itself (e.g. a bicycle wheel) often extends
into that region and would be masked and erased along with the watermark.

Usage:
    python strip_watermark_batch.py <src_dir> <dst_dir> [name_mapping.json]
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import cv2
import numpy as np

# Fixed watermark footprint: mask everything right of / below this point.
# Derived from the measured watermark bbox (0.797, 0.908) minus a small margin.
WM_FLOOR_X = 0.790
WM_FLOOR_Y = 0.898

SAFETY_PAD = 8


def process(src: Path, dst: Path) -> str:
    img = cv2.imread(str(src), cv2.IMREAD_COLOR)
    if img is None:
        return f"SKIP (unreadable) {src.name}"
    h, w = img.shape[:2]

    x0 = max(0, int(w * WM_FLOOR_X) - SAFETY_PAD)
    y0 = max(0, int(h * WM_FLOOR_Y) - SAFETY_PAD)

    mask = np.zeros((h, w), dtype=np.uint8)
    mask[y0:h, x0:w] = 255

    out = cv2.inpaint(img, mask, 5, cv2.INPAINT_TELEA)
    dst.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(dst), out, [int(cv2.IMWRITE_PNG_COMPRESSION), 3])

    return (f"OK   {src.name} -> {dst.name}  ({w}x{h})  "
            f"mask x>={x0} y>={y0}  ({100*(w-x0)/w:.1f}% x {100*(h-y0)/h:.1f}%)")


def main() -> None:
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    src_dir, dst_dir = Path(sys.argv[1]), Path(sys.argv[2])
    mapping: dict[str, str] = {}
    if len(sys.argv) > 3:
        mapping = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))

    files = sorted(
        (p for p in src_dir.glob("*.png")),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    if not files:
        raise SystemExit(f"no PNG in {src_dir}")

    for src in files:
        name = mapping.get(src.name, src.name)
        print(process(src, dst_dir / name))


if __name__ == "__main__":
    main()
