#!/usr/bin/env python3
"""视觉识别 CV 端到端测试: 登录(RSA+AES+redis验证码) -> cv 底库/健康/打卡链路/FAIL 复查"""
import json, os, subprocess, urllib.request, urllib.error, base64
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.PublicKey import RSA
from Crypto.Util.Padding import pad

BASE = "http://localhost:8080"
CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e"
REDIS_PASS = "ruoyi123"
RSA_PUB = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdHnzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ=="
HEADER_FLAG = "encrypt-key"
FACE_PHOTO = "C:/Users/admin/AppData/Local/Temp/emp20002.jpg"  # 已从 vision 底库导出的真实人脸照片


def log(name, ok, detail=""):
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" - {detail}" if detail else ""))


def http(method, path, body=None, headers=None, raw_body=None, files=None, timeout=30):
    import uuid as _uuid
    url = BASE + path
    h = {"clientid": CLIENT_ID}
    if headers:
        h.update(headers)
    data = None
    if files:
        boundary = "----cv" + _uuid.uuid4().hex
        parts = []
        for k, v in files.items():
            if isinstance(v, tuple):
                fn, content, ctype = v
                parts.append(
                    f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"; filename="{fn}"\r\n'
                    f"Content-Type: {ctype}\r\n\r\n".encode() + content + b"\r\n"
                )
            else:
                parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        parts.append(f"--{boundary}--\r\n".encode())
        data = b"".join(parts)
        h["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    elif raw_body is not None:
        data = raw_body.encode() if isinstance(raw_body, str) else raw_body
        h.setdefault("Content-Type", "application/json;charset=UTF-8")
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        h.setdefault("Content-Type", "application/json;charset=UTF-8")
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return -1, str(e)


def redis_get(key):
    try:
        out = subprocess.run(
            ["redis-cli", "-h", "127.0.0.1", "-p", "6379", "-a", REDIS_PASS, "GET", key],
            capture_output=True, text=True, timeout=5,
        )
        return out.stdout.strip().strip('"').strip("'")
    except Exception:
        return ""


def encrypt_body(body_str):
    aes_key = os.urandom(8).hex()
    cipher = AES.new(aes_key.encode(), AES.MODE_ECB)
    ct = cipher.encrypt(pad(body_str.encode(), 16))
    body_b64 = base64.b64encode(ct).decode()
    key_b64 = base64.b64encode(aes_key.encode()).decode()
    rsa_key = RSA.import_key(base64.b64decode(RSA_PUB))
    rsa_cipher = PKCS1_v1_5.new(rsa_key)
    enc_key = rsa_cipher.encrypt(key_b64.encode())
    return body_b64, base64.b64encode(enc_key).decode()


def login():
    st, resp = http("GET", f"/auth/code?clientid={CLIENT_ID}")
    cap = json.loads(resp)["data"]
    uuid = cap["uuid"]
    answer = redis_get(f"global:captcha_codes:{uuid}")
    login_body = {
        "clientId": CLIENT_ID, "grantType": "password", "username": "admin",
        "password": "admin123", "code": answer, "uuid": uuid, "tenantId": "000000",
    }
    body_b64, header_val = encrypt_body(json.dumps(login_body, ensure_ascii=False))
    st, resp = http("POST", "/auth/login", raw_body=body_b64, headers={HEADER_FLAG: header_val})
    if st == 200:
        return json.loads(resp)["data"]["access_token"]
    else:
        print(f"  登录失败 st={st} resp={resp[:200]}")
        return None


def auth(token):
    return {"Authorization": f"Bearer {token}", "clientid": CLIENT_ID}


def main():
    print("=== 1. 登录 ===")
    token = login()
    if not token:
        return
    log("登录获取 token", True, token[:20] + "...")

    print("=== 2. cv 底库列表 ===")
    st, resp = http("GET", "/cv/face/list?page=1&pageSize=5", headers=auth(token))
    if st == 200:
        data = json.loads(resp)["data"]
        log("底库列表", True, f"total={data.get('total')} items={[i['subject'] for i in data.get('items', [])]}")
    else:
        log("底库列表", False, f"st={st} {resp[:150]}")

    print("=== 3. cv 健康探活 ===")
    st, resp = http("GET", "/cv/face/health", headers=auth(token))
    if st == 200:
        data = json.loads(resp)["data"]
        log("健康探活", data.get("available"), f"status={data.get('status')} models={data.get('models')}")
    else:
        log("健康探活", False, f"st={st} {resp[:150]}")

    print("=== 4. 打卡 + 异步人脸验证 ===")
    with open(FACE_PHOTO, "rb") as f:
        face_b64 = base64.b64encode(f.read()).decode()
    punch_body = {
        "deviceId": "E2E-CV-TEST",
        "deviceName": "E2E 视觉验证",
        "faceImage": face_b64,
    }
    st, resp = http("POST", "/app/sch/attendance/clock-in", body=punch_body, headers=auth(token))
    log("打卡请求", st == 200, f"st={st} {resp[:200]}")
    if st == 200:
        data = json.loads(resp)["data"]
        att_id = data.get("attendanceId")
        log("打卡成功", att_id is not None, f"attendanceId={att_id}")
        import time

        print("  等待异步人脸验证(3s)...")
        time.sleep(3)
        st2, resp2 = http("GET", f"/erp/sch/attendance/{att_id}", headers=auth(token))
        if st2 == 200:
            att = json.loads(resp2)["data"]
            log(
                "人脸状态回写",
                att.get("faceVerifyStatus") is not None,
                f"status={att.get('faceVerifyStatus')} sim={att.get('faceSimilarity')} ossId={att.get('faceImageOssId')}",
            )
        else:
            log("查询考勤", False, f"st={st2} {resp2[:150]}")

    print("=== 5. FAIL 复查列表(faceVerifyStatus=FAIL) ===")
    st, resp = http("GET", "/erp/sch/attendance/list?page=1&pageSize=5&faceVerifyStatus=FAIL", headers=auth(token))
    if st == 200:
        data = json.loads(resp)
        rows = data.get("rows", [])
        log("FAIL 复查列表", True, f"total={data.get('total')} rows={len(rows)}")
    else:
        log("FAIL 复查列表", False, f"st={st} {resp[:150]}")

    print("=== 6. 标记误判(FAIL → PASS) ===")
    # 用第 4 步的考勤记录做复查
    if "att_id" in dir():
        st, resp = http(
            "PUT", f"/erp/sch/attendance/{att_id}/face/mark-pass?remark=E2E误判验证",
            headers=auth(token), body={},
        )
        log("标记误判", st == 200, f"st={st} {resp[:150]}")


if __name__ == "__main__":
    main()
