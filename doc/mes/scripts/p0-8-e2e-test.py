#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P0-8 IoT 打通收尾 E2E: 登录(RSA+AES+redis验证码) -> F8-2 采集点-IoT 绑定 CRUD + F8-5 OFFLINE 字典可查。
F8-5 任务触发(SnailJob)/F8-6 归档回补依赖真实 IoT 数据+调度,逻辑由单测覆盖,此处验证 HTTP 可达面。
"""
import json, base64, subprocess, urllib.request, urllib.error, os
from urllib.parse import quote
import time
SUF = str(int(time.time()))[-6:]  # 每次运行唯一后缀, 避开软删除+uk_point_code 唯一索引陷阱

BASE = "http://localhost:8080"
CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e"
REDIS_PASS = "ruoyi123"
RSA_PUB = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB"
HEADER_FLAG = "encrypt-key"

from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.PublicKey import RSA
from Crypto.Util.Padding import pad

results = []
def log(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}{' :: '+detail if detail else ''}")

def http(method, path, body=None, headers=None, raw_body=None):
    h = {"Content-Type": "application/json;charset=UTF-8"}
    if headers: h.update(headers)
    data = None
    if raw_body is not None:
        data = raw_body.encode() if isinstance(raw_body, str) else raw_body
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return -1, str(e)

def redis_get(key):
    try:
        out = subprocess.run(["redis-cli", "-h","127.0.0.1","-p","6379","-a",REDIS_PASS,"GET",key],
                             capture_output=True, text=True, timeout=5)
        return out.stdout.strip().strip('"').strip("'")
    except Exception:
        return ""

def encrypt_body(body_str):
    aes_key = os.urandom(8).hex()  # 16 hex ASCII
    cipher = AES.new(aes_key.encode(), AES.MODE_ECB)
    ct = cipher.encrypt(pad(body_str.encode(), 16))
    body_b64 = base64.b64encode(ct).decode()
    rsa_cipher = PKCS1_v1_5.new(RSA.import_key(base64.b64decode(RSA_PUB)))
    header_val = base64.b64encode(rsa_cipher.encrypt(base64.b64encode(aes_key.encode()))).decode()
    return body_b64, header_val

def login():
    st, resp = http("GET", f"/auth/code?clientid={CLIENT_ID}")
    if st != 200:
        print(f"  /auth/code 失败 st={st} resp={(resp or '')[:120]}")
        return None
    cap = json.loads(resp)["data"]
    uuid = cap["uuid"]
    answer = redis_get(f"global:captcha_codes:{uuid}") or redis_get(f"captcha_codes:{uuid}")
    print(f"  captcha uuid={uuid[:8]}.. answer={answer}")
    login_body = {"clientId":CLIENT_ID,"grantType":"password","username":"admin",
                  "password":"admin123","code":answer,"uuid":uuid,"tenantId":"000000"}
    body_b64, header_val = encrypt_body(json.dumps(login_body, ensure_ascii=False))
    st, resp = http("POST", "/auth/login", raw_body=body_b64,
                    headers={HEADER_FLAG: header_val, "clientid": CLIENT_ID})
    if st == 200:
        token = json.loads(resp)["data"]["access_token"]
        print(f"  登录成功 token={token[:20]}...")
        return token
    print(f"  登录失败 st={st} resp={resp[:200]}")
    return None

def auth(token):
    return {"Authorization": f"Bearer {token}", "clientid": CLIENT_ID}

def main():
    print("=== 1. 登录 ===")
    token = login()
    if not token:
        log("登录", False, "服务可能未启动")
        return
    log("登录", True)
    H = auth(token)
    created = []

    # --- F8-5: OFFLINE 字典可查(前端 dict-tag 渲染依赖) ---
    print("=== 2. F8-5 OFFLINE 字典 ===")
    st, resp = http("GET", "/system/dict/data/type/mes_equipment_status", headers=H)
    vals = [d.get("dictValue") for d in json.loads(resp).get("data", [])] if st == 200 else []
    log("mes_equipment_status 含 OFFLINE", "OFFLINE" in vals, f"vals={vals}")

    # --- F8-2: 取一个 IoT 设备用于绑定 ---
    print("=== 3. F8-2 取 IoT 设备 ===")
    st, resp = http("GET", "/iot/device/list?pageNum=1&pageSize=10", headers=H)
    iot_devices = json.loads(resp).get("rows", []) if st == 200 else []
    iot_id = iot_devices[0]["deviceId"] if iot_devices else None
    print(f"  IoT 设备数={len(iot_devices)} 选用 iotId={iot_id}")

    # --- F8-2-A: AUTO 采集点绑定 IoT 设备 ---
    print("=== 4. F8-2 新建 AUTO 采集点(绑定 IoT) ===")
    if iot_id:
        body = {"pointCode":f"E2E-AUTO-IOT-{SUF}","pointName":"E2E自动采集点","pointType":"AUTO",
                "iotDeviceId":iot_id,"iotPropertyCode":"temperature",
                "dataType":"NUMERIC","unit":"℃","frequency":10,"status":"ACTIVE","remark":"p0-8 e2e"}
        st, resp = http("POST", "/mes/dc/collection-point", body=body, headers=H)
        pid = (json.loads(resp).get("data") or {}).get("pointId") if st == 200 else None
        log("新建 AUTO 采集点(绑 IoT)", st == 200 and pid is not None, f"st={st} pid={pid}")
        if pid:
            created.append(pid)
            # 回显校验
            st, resp = http("GET", f"/mes/dc/collection-point/{pid}", headers=H)
            d = json.loads(resp).get("data", {})
            log("回显 iotDeviceId", str(d.get("iotDeviceId")) == str(iot_id), f"got={d.get('iotDeviceId')}")
            log("回显 iotPropertyCode", d.get("iotPropertyCode") == "temperature", f"got={d.get('iotPropertyCode')}")
    else:
        log("新建 AUTO 采集点(绑 IoT)", False, "无可用 IoT 设备,跳过(需先在 IoT 模块注册设备)")

    # --- F8-2-B: MANUAL 采集点(无 IoT) ---
    print("=== 5. F8-2 新建 MANUAL 采集点(无 IoT) ===")
    body = {"pointCode":f"E2E-MANUAL-{SUF}","pointName":"E2E手动采集点","pointType":"MANUAL",
            "dataType":"TEXT","status":"ACTIVE","remark":"p0-8 e2e"}
    st, resp = http("POST", "/mes/dc/collection-point", body=body, headers=H)
    pid = (json.loads(resp).get("data") or {}).get("pointId") if st == 200 else None
    log("新建 MANUAL 采集点", st == 200 and pid is not None, f"st={st} pid={pid}")
    if pid: created.append(pid)
    # MANUAL 点 iotDeviceId 应为空(Service 规整)
    if pid:
        st, resp = http("GET", f"/mes/dc/collection-point/{pid}", headers=H)
        d = json.loads(resp).get("data", {})
        log("MANUAL 点 iotDeviceId 为空", d.get("iotDeviceId") in (None, "", 0), f"got={d.get('iotDeviceId')}")

    # --- F8-2-C: AUTO 未绑 IoT 应被拒 ---
    print("=== 6. F8-2 AUTO 未绑 IoT 应被拒 ===")
    body = {"pointCode":f"E2E-AUTO-NOIOT-{SUF}","pointName":"E2E无IoT自动点","pointType":"AUTO",
            "dataType":"NUMERIC","status":"ACTIVE"}
    st, resp = http("POST", "/mes/dc/collection-point", body=body, headers=H)
    # RuoYi 业务错误返回 HTTP 200 + body {code:500, msg:...}, 按 msg 判定
    ok = "必须关联 IoT" in (resp or "")
    log("AUTO 未绑 IoT 被拒", ok, f"st={st} resp={(resp or '')[:120]}")

    # --- F8-2-D: 列表回显 IoT 绑定 ---
    print("=== 7. F8-2 列表回显 ===")
    st, resp = http("GET", "/mes/dc/collection-point/list?pageNum=1&pageSize=50&pointCode=E2E", headers=H)
    rows = json.loads(resp).get("rows", []) if st == 200 else []
    auto_row = next((r for r in rows if str(r.get("pointCode","")).startswith("E2E-AUTO-IOT")), None) if iot_id else None
    if auto_row:
        log("列表回显 AUTO iotDeviceId", str(auto_row.get("iotDeviceId")) == str(iot_id), f"got={auto_row.get('iotDeviceId')}")
    else:
        log("列表回显 AUTO iotDeviceId", False, "未找到 E2E-AUTO-IOT 行")

    # --- 清理 ---
    print("=== 8. 清理 ===")
    for pid in created:
        http("DELETE", f"/mes/dc/collection-point/{pid}", headers=H)
    log("清理创建的采集点", len(created) > 0, f"删除 {len(created)} 条")

    passed = sum(1 for _,ok,_ in results if ok)
    print(f"\n=== 结果: {passed}/{len(results)} 通过 ===")
    if passed != len(results):
        print("失败项:", [n for n,ok,_ in results if not ok])
        exit(1)

if __name__ == "__main__":
    main()
