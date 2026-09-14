"""Collect the generated category icons: map -> de-watermark -> resize -> save.

Matches `~/.dsh/dsh-imagegen/index.json` entries to categories by EXACT prompt
string (the prompt is built from the manifest, so this is unambiguous), keeps the
newest entry per category, strips the fixed SenseNova watermark, and writes a
200x200 PNG (admin tip: 分类图标 建议 100x100px; 200 keeps it crisp on retina).

Reports anything missing so only the gaps need regenerating.

Run: python collect_category_icons.py [--report-only]
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import cv2
import numpy as np

sys.stdout.reconfigure(encoding="utf-8")

DSH_IMAGEGEN = Path(r"C:\Users\admin\.dsh\dsh-imagegen")
WORK = Path(r"D:\workspace\nexus-erp\doc\assets\_t1_work")
MANIFEST = WORK / "category_manifest.json"
OUT_DIR = Path(r"D:\workspace\nexus-erp\doc\assets\category-icons-2026-09-14")

SIZE = 200
WM_FLOOR_X, WM_FLOOR_Y, PAD = 0.790, 0.898, 8


def wm_window(img):
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
    report_only = "--report-only" in sys.argv
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entries = json.loads((DSH_IMAGEGEN / "index.json").read_text(encoding="utf-8"))["entries"]

    # newest entry per exact prompt
    best: dict[str, dict] = {}
    for e in entries:
        p = e.get("prompt")
        if not p or not e.get("images"):
            continue
        f = DSH_IMAGEGEN / "images" / e["images"][0]["file"]
        if not f.exists():
            continue
        if p not in best or e["createdAt"] > best[p]["createdAt"]:
            best[p] = {"createdAt": e["createdAt"], "file": f}

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    missing, done = [], 0
    for m in manifest:
        hit = best.get(m["prompt"])
        if not hit:
            missing.append(m)
            continue
        if report_only:
            done += 1
            continue

        img = cv2.imread(str(hit["file"]), cv2.IMREAD_COLOR)
        if img is None:
            missing.append(m)
            continue
        before = int((wm_window(img) < 200).sum())
        out = strip(img)
        after = int((wm_window(out) < 200).sum())
        icon = cv2.resize(out, (SIZE, SIZE), interpolation=cv2.INTER_AREA)

        dst = OUT_DIR / f'{m["id"]}_{m["new_name"]}.png'
        ok, buf = cv2.imencode(".png", icon, [int(cv2.IMWRITE_PNG_COMPRESSION), 9])
        if not ok:
            raise RuntimeError(f"encode failed for {dst.name}")
        dst.write_bytes(buf.tobytes())

        done += 1
        flag = "CLEAN" if after == 0 else f"RESIDUE {after}px"
        print(f'{m["id"]} {m["new_name"]:<6} {SIZE}x{SIZE} {len(buf.tobytes()):>7}B  wm {before:>5}->{after:<4} {flag}')

    print(f"\nicons ready {done}/{len(manifest)}")
    if missing:
        print(f"MISSING ({len(missing)}):")
        for m in missing:
            print(f'   id={m["id"]} {m["new_name"]}  <- {m["subject"]}')


if __name__ == "__main__":
    main()
