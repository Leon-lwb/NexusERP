"""Analyze watermark region of a raw SenseNova output, then strip it and re-verify.

Prints, for both raw and stripped:
  - whether the bottom-right watermark rectangle is uniform white (255) or has dark ink
  - min/max/mean of that region
A properly stripped image should show a clean white rectangle.
"""
from __future__ import annotations
import sys
from pathlib import Path
import cv2
import numpy as np

WM_FLOOR_X = 0.790
WM_FLOOR_Y = 0.898
SAFETY_PAD = 8


def region(img):
    h, w = img.shape[:2]
    x0 = max(0, int(w * WM_FLOOR_X) - SAFETY_PAD)
    y0 = max(0, int(h * WM_FLOOR_Y) - SAFETY_PAD)
    return img[y0:h, x0:w], x0, y0, h, w


def report(tag, img):
    r, x0, y0, h, w = region(img)
    g = cv2.cvtColor(r, cv2.COLOR_BGR2GRAY) if r.ndim == 3 else r
    dark = int((g < 200).sum())
    print(f"{tag}: {w}x{h} region=({x0},{y0})-({w},{h}) size={r.shape[1]}x{r.shape[0]} "
          f"min={g.min()} max={g.max()} mean={g.mean():.2f} dark_px={dark} "
          f"({100.0*dark/g.size:.2f}%)")
    return dark


def strip(img):
    h, w = img.shape[:2]
    x0 = max(0, int(w * WM_FLOOR_X) - SAFETY_PAD)
    y0 = max(0, int(h * WM_FLOOR_Y) - SAFETY_PAD)
    mask = np.zeros((h, w), dtype=np.uint8)
    mask[y0:h, x0:w] = 255
    return cv2.inpaint(img, mask, 5, cv2.INPAINT_TELEA)


if __name__ == "__main__":
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2])
    raw = cv2.imread(str(src), cv2.IMREAD_COLOR)
    if raw is None:
        raise SystemExit(f"unreadable {src}")
    d1 = report("RAW     ", raw)
    out = strip(raw)
    dst.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(dst), out, [int(cv2.IMWRITE_PNG_COMPRESSION), 3])
    d2 = report("STRIPPED", out)
    print(f"\nverdict: watermark ink {d1}px -> {d2}px  "
          f"({'CLEAN' if d2 == 0 else 'RESIDUE REMAINS'})")
