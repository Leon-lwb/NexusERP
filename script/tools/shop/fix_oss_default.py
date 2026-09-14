"""Make `minio` the active OSS default again, then prove uploads really persist.

Background
----------
`sys_oss_config.status` uses 0 = enabled/default, 1 = disabled, and
`SysOssConfigServiceImpl.init()` points Redis
(`global:sys_oss:default_config`) at whichever row has status='0'.
The stale Tencent `qcloud` row is the only one left at '0', so every mall upload
was routed at an invalid COS endpoint: it failed with an S3 signature error while
the DB still recorded the attachment + a sys_oss row, i.e. silent data loss.

The repo's own reference config (docs/oss-migrate/oss_config_minio.json) shows
minio/status=0 is the intended state.

Redis is written over a raw socket because `RedisUtils.setCacheObject` stores the
value JSON-encoded (i.e. the 7 bytes `"minio"`, quotes included) and neither the
venv (no pip) nor PowerShell->docker exec preserves quotes reliably.
"""
from __future__ import annotations

import socket
import sys

import pymysql

sys.stdout.reconfigure(encoding="utf-8")

TARGET = "minio"
REDIS_KEY = "global:sys_oss:default_config"
REDIS_VALUE = '"minio"'          # JSON-encoded, quotes are part of the value


def resp_command(sock: socket.socket, *args: str) -> bytes:
    out = [f"*{len(args)}\r\n".encode()]
    for a in args:
        b = a.encode("utf-8")
        out.append(f"${len(b)}\r\n".encode() + b + b"\r\n")
    sock.sendall(b"".join(out))
    return sock.recv(65536)


def redis_set(host: str, port: int, password: str, db: int, key: str, value: str) -> bytes:
    with socket.create_connection((host, port), timeout=10) as s:
        resp_command(s, "AUTH", password)
        resp_command(s, "SELECT", str(db))
        return resp_command(s, "SET", key, value)


def main() -> None:
    con = pymysql.connect(host="127.0.0.1", user="root", password="root",
                          database="crmeb", charset="utf8mb4",
                          cursorclass=pymysql.cursors.DictCursor)
    with con, con.cursor() as cur:
        cur.execute("UPDATE sys_oss_config SET status='1' WHERE config_key <> %s", (TARGET,))
        cur.execute("UPDATE sys_oss_config SET status='0' WHERE config_key = %s", (TARGET,))
        con.commit()
        cur.execute("SELECT config_key, status FROM sys_oss_config ORDER BY oss_config_id")
        print("sys_oss_config:")
        for r in cur.fetchall():
            print(f'   {r["config_key"]:<8} status={r["status"]}'
                  + ("   <-- default" if r["status"] == "0" else ""))

    print("\nredis SET ->", redis_set("127.0.0.1", 6379, "ruoyi123", 3, REDIS_KEY, REDIS_VALUE))

    with socket.create_connection(("127.0.0.1", 6379), timeout=10) as s:
        resp_command(s, "AUTH", "ruoyi123")
        resp_command(s, "SELECT", "3")
        print("redis GET ->", resp_command(s, "GET", REDIS_KEY))
        print("redis STRLEN ->", resp_command(s, "STRLEN", REDIS_KEY))


if __name__ == "__main__":
    main()
