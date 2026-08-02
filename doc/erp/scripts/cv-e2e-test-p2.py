#!/usr/bin/env python3
"""视觉识别 CV Phase 2 端到端测试: 登录 -> 物料 OCR 检测/解析 -> 操作台 1:N 识别 -> 1:1 确认
用法: python cv-e2e-test-p2.py
前置: nexus-vision-service :5001 运行, nexus-erp :8080 运行, 底库已含测试人脸
"""
import json, os, subprocess, urllib.request, urllib.error, urllib.parse, base64
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.PublicKey import RSA
from Crypto.Util.Padding import pad

BASE = "http://localhost:8080"
CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e"
REDIS_PASS = "ruoyi123"
RSA_PUB = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdHnzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ=="
HEADER_FLAG = "encrypt-key"
FACE_PHOTO = "C:/Users/admin/AppData/Local/Temp/emp20002.jpg"  # 底库 EMP-20002 同款人脸照片
LABEL_PHOTO = "C:/Users/admin/AppData/Local/Temp/material_label.jpg"  # 料件标签照片(OCR)


def log(name, ok, detail=""):
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" - {detail}" if detail else ""))


def http(method, path, body=None, headers=None, raw_body=None, files=None, timeout=60):
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

    print("=== 2. 物料 OCR 检测(enableLabelParse=true) ===")
    if os.path.exists(LABEL_PHOTO):
        with open(LABEL_PHOTO, "rb") as f:
            img_bytes = f.read()
        st, resp = http("POST", "/cv/detect/product", headers=auth(token),
                        files={"file": ("label.jpg", img_bytes, "image/jpeg"), "enableOcr": "true", "enableLabelParse": "true"})
        if st == 200:
            data = json.loads(resp)["data"]
            ocr = data.get("ocrResults") or []
            label = data.get("label") or {}
            log("OCR 检测接口", data.get("success"), f"count={data.get('count')} ocr_lines={len(ocr)}")
            if ocr:
                log("OCR 文本", True, "; ".join(o["text"] for o in ocr[:3]))
            if label:
                log("标签解析料号", bool(label.get("materialCode")), f"materialCode={label.get('materialCode')} matched={label.get('matched')}")
                log("标签解析批次", bool(label.get("batchCode")), f"batchCode={label.get('batchCode')}")
                log("物料主数据校验", bool(label.get("matchedMaterial")),
                    label.get("matchedMaterial", {}).get("materialName") if label.get("matchedMaterial") else "主数据未命中(测试环境可能无此料)")
            else:
                log("标签解析", False, "label 为空(需 enableLabelParse=true)")
        else:
            log("OCR 检测接口", False, f"st={st} {resp[:200]}")
    else:
        print(f"  [SKIP] 标签照片不存在 {LABEL_PHOTO} - 请放一张料件标签照片后重跑")
        log("OCR 检测", False, "缺照片跳过")

    print("=== 3. 操作台 1:N 识别 ===")
    if os.path.exists(FACE_PHOTO):
        with open(FACE_PHOTO, "rb") as f:
            face_bytes = f.read()
        st, resp = http("POST", "/cv/ops/identify", headers=auth(token),
                        files={"file": ("face.jpg", face_bytes, "image/jpeg"), "limit": "3"})
        if st == 200:
            cands = json.loads(resp)["data"]
            log("1:N 识别", True, f"candidates={len(cands)} subjects={[c.get('subject') for c in cands]}")
            if cands:
                c = cands[0]
                log("候选含 worker 信息", "workerId" in c, f"subject={c.get('subject')} sim={c.get('similarity')} worker={c.get('userName')}")
        else:
            log("1:N 识别", False, f"st={st} {resp[:200]}")

    print("=== 4. 1:1 确认(操作台安全闭环) ===")
    if os.path.exists(FACE_PHOTO):
        st, resp = http("POST", "/cv/face/verify", headers=auth(token),
                        files={"file": ("face.jpg", face_bytes, "image/jpeg"), "subject": "EMP-20002"})
        if st == 200:
            v = json.loads(resp)["data"]
            log("1:1 确认", v.get("matched") and v.get("similarity", 0) >= 0.5,
                f"matched={v.get('matched')} sim={v.get('similarity')}")
        else:
            log("1:1 确认", False, f"st={st} {resp[:200]}")


if __name__ == "__main__":
    main()
