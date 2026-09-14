"""List every eb_system_config value that embeds a local port, plus the Redis cache layout.

Run before touching config rows so the change set is explicit and reviewable.
"""
import socket
import sys

import pymysql

sys.stdout.reconfigure(encoding="utf-8")


def resp(sock, *args):
    out = [f"*{len(args)}\r\n".encode()]
    for a in args:
        b = a.encode()
        out.append(f"${len(b)}\r\n".encode() + b + b"\r\n")
    sock.sendall(b"".join(out))
    return sock.recv(65536)


con = pymysql.connect(host="127.0.0.1", user="root", password="root", database="crmeb",
                      charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=True)
with con, con.cursor() as cur:
    print("=== eb_system_config rows containing 8080 / 8090 / 9090 / 20400 ===")
    cur.execute("""SELECT id, name, value FROM eb_system_config
                   WHERE value LIKE '%8080%' OR value LIKE '%8090%'
                      OR value LIKE '%9090%' OR value LIKE '%20400%'
                   ORDER BY name""")
    for r in cur.fetchall():
        print(f'  {r["id"]:>6}  {r["name"]:<32} {r["value"]}')

    print("\n=== eb_system_config rows mentioning localhost ===")
    cur.execute("SELECT id, name, value FROM eb_system_config WHERE value LIKE '%localhost%' ORDER BY name")
    for r in cur.fetchall():
        print(f'  {r["id"]:>6}  {r["name"]:<32} {r["value"]}')

print("\n=== mall redis DB sizes ===")
with socket.create_connection(("127.0.0.1", 6379), timeout=10) as s:
    resp(s, "AUTH", "ruoyi123")
    for db in (3, 10, 13):
        resp(s, "SELECT", str(db))
        n = resp(s, "DBSIZE")
        print(f"  db{db}: {n!r}")
