"""Clear the mall's Redis business cache, preserving what must survive.

Why not FLUSHDB -- this mirrors the nexus-erp project's own ops rule
("清一次 Redis 业务缓存，保留登录令牌"):

  KEEP  Authorization:login:*   Sa-Token tokens / sessions / last-active.  There are
                                live admin logins; wiping them just logs the operator out.
  KEEP  online_tokens:*         online-user registry (same family).
  KEEP  global:sys_oss*         OSS default pointer + config cache.  These are
                                LOAD-BEARING: NexusFrontImagePathFilterConfig:85 calls
                                getCdnUrl() -> OssFactory.instance(), which THROWS when
                                the pointer is missing -- every C-end product image would
                                then break until the app is restarted.
  KEEP  *redisson*              Redisson internal bookkeeping.

  DELETE everything else (framework/system caches, rate-limit counters, ...);
  they re-read from MySQL on next use.

Run: python clear_mall_cache.py [--dry-run]
"""
from __future__ import annotations

import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")

CONTAINER = "redis-7.2.8"
PASSWORD = "ruoyi123"
DB = 3

KEEP_PREFIXES = ("Authorization:login:", "online_tokens:", "global:sys_oss")
KEEP_CONTAINS = ("redisson",)


def cli(*args: str) -> list[str]:
    p = subprocess.run(["docker", "exec", CONTAINER, "redis-cli", "-a", PASSWORD, "-n", str(DB), *args],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return [ln for ln in (p.stdout or "").splitlines() if ln and "Warning" not in ln]


def keep(k: str) -> bool:
    if any(k.startswith(p) for p in KEEP_PREFIXES):
        return True
    return any(c in k for c in KEEP_CONTAINS)


def main() -> None:
    dry = "--dry-run" in sys.argv
    keys = cli("--scan")
    kept = [k for k in keys if keep(k)]
    drop = [k for k in keys if not keep(k)]

    print(f"db{DB}: {len(keys)} keys -> keep {len(kept)}, {'would delete' if dry else 'delete'} {len(drop)}")

    groups: dict[str, int] = {}
    for k in kept:
        g = next((p for p in KEEP_PREFIXES if k.startswith(p)), "redisson/internal")
        groups[g] = groups.get(g, 0) + 1
    print("\n--- KEPT ---")
    for g, n in sorted(groups.items()):
        print(f"  {g:<24} {n}")

    print("\n--- " + ("WOULD DELETE" if dry else "DELETED") + " ---")
    for k in sorted(drop):
        print(f"  {k}")
    if not dry and drop:
        for i in range(0, len(drop), 50):
            print("  DEL ->", cli("DEL", *drop[i:i + 50]))

    print(f"\ndb{DB} size after: {cli('DBSIZE')}")


if __name__ == "__main__":
    main()
