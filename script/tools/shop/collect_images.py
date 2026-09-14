"""Collect this session's generated images, de-watermark them, and name them by material code.

Maps `~/.dsh/dsh-imagegen/index.json` entries to products by matching the
material code embedded in each prompt (`（型号 IF20260914xxx）`), then writes:

    doc/assets/_t1_work/raw/<code>.png      original API output (watermarked)
    doc/assets/product-images-2026-09-15/<code>_<name>.png   cleaned

Prints a per-image watermark report so removal is verifiable without eyeballing.
"""
from __future__ import annotations

import json
import re
import shutil
import sys
from pathlib import Path

import cv2
import numpy as np

sys.stdout.reconfigure(encoding="utf-8")

DSH_HOME = Path(r"C:\Users\admin\.dsh")
IMAGEGEN = DSH_HOME / "dsh-imagegen"
INDEX = IMAGEGEN / "index.json"

ERP = Path(r"D:\workspace\nexus-erp")
WORK = ERP / "doc" / "assets" / "_t1_work"
RAW = WORK / "raw"
CLEAN = ERP / "doc" / "assets" / "product-images-2026-09-15"
MANIFEST = WORK / "manifest.json"

WM_FLOOR_X, WM_FLOOR_Y, PAD = 0.790, 0.898, 8
CODE_RE = re.compile(r"IF20260914\d{3}|IF\d{9}")


def save_window(img):
    """The watermark rect; after inpainting it should be pure white."""
    h, w = img.shape[:2]
    x0 = max(0, int(w * WM_FLOOR_X) - PAD)
    y0 = max(0, int(h * WM_FLOOR_Y) - PAD)
    return cv2.cvtColor(img[y0:h, x0:w], cv2.COLOR_BGR2GRAY)


def strip(img):
    h, w = img.shape[:2]
    x0 = max(0, int(w * WM_FLOOR_X) - PAD)
    y0 = max(0, int(h * WM_FLOOR_Y) - PAD)
    mask = np.zeros((h, w), dtype=np.uint8)
    mask[y0:h, x0:w] = 255
    return cv2.inpaint(img, mask, 5, cv2.INPAINT_TELEA)


def main() -> None:
    manifest = {m["code"]: m for m in json.loads(MANIFEST.read_text(encoding="utf-8"))}
    entries = json.loads(INDEX.read_text(encoding="utf-8"))["entries"]

    # newest entry per code wins (a product regenerated later supersedes earlier)
    latest: dict[str, dict] = {}
    for e in entries:
        m = CODE_RE.search(e.get("prompt") or "")
        if not m or not e.get("images"):
            continue
        code = m.group(0)
        if code not in manifest:
            continue
        f = IMAGEGEN / "images" / e["images"][0]["file"]
        if not f.exists():
            continue
        if code not in latest or e["createdAt"] > latest[code]["createdAt"]:
            latest[code] = {"createdAt": e["createdAt"], "file": f}

    RAW.mkdir(parents=True, exist_ok=True)
    CLEAN.mkdir(parents=True, exist_ok=True)

    missing = [c for c in manifest if c not in latest]
    ok = 0
    for code, meta in sorted(latest.items(), key=lambda kv: int(manifest[kv[0]]["id"])):
        p = manifest[code]
        dst_raw = RAW / f"{code}.png"
        shutil.copy2(meta["file"], dst_raw)

        img = cv2.imread(str(dst_raw), cv2.IMREAD_COLOR)
        before = int((save_window(img) < 200).sum())
        out = strip(img)
        after = int((save_window(out) < 200).sum())

        safe = re.sub(r'[\\/:*?"<>|]', "_", p["name"])
        dst = CLEAN / f'{p["id"]:02d}_{code}_{safe}.png'
        # cv2.imwrite() silently fails on non-ASCII Windows paths (it opens the
        # file with the ANSI codepage), so encode in memory and write via pathlib.
        ok_enc, buf = cv2.imencode(".png", out, [int(cv2.IMWRITE_PNG_COMPRESSION), 3])
        if not ok_enc:
            raise RuntimeError(f"png encode failed for {dst.name}")
        dst.write_bytes(buf.tobytes())
        ok += 1
        flag = "CLEAN" if after == 0 else f"RESIDUE {after}px"
        print(f'{p["id"]:>3} {code} {p["name"]:<24} wm {before:>5}px -> {after:>4}px  {flag}')

    print(f"\ncleaned {ok}/{len(manifest)}")
    if missing:
        print(f"MISSING ({len(missing)}): {missing}")


if __name__ == "__main__":
    main()
