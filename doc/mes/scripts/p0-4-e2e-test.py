#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P0-4 NCR E2E 自动化测试: 登录(RSA+AES+redis验证码) -> NCR 缺陷代码/单据/状态机/8D 全链路"""
import json, base64, subprocess, urllib.request, urllib.error, ssl, os, sys
from urllib.parse import quote

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
    url = BASE + path
    data = None
    h = {"Content-Type": "application/json;charset=UTF-8"}
    if headers: h.update(headers)
    if raw_body is not None:
        data = raw_body.encode() if isinstance(raw_body, str) else raw_body
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=data, headers=h, method=method)
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
        val = out.stdout.strip().strip('"').strip("'")
        return val
    except Exception as e:
        return ""

def encrypt_body(body_str):
    # AES 密钥须为 16 位 ASCII(hutool decryptByBase64 会 String 往返,随机字节会损坏)
    aes_key = os.urandom(8).hex()  # 16 hex chars
    cipher = AES.new(aes_key.encode(), AES.MODE_ECB)
    ct = cipher.encrypt(pad(body_str.encode(), 16))
    body_b64 = base64.b64encode(ct).decode()
    key_b64 = base64.b64encode(aes_key.encode()).decode()
    rsa_key = RSA.import_key(base64.b64decode(RSA_PUB))
    rsa_cipher = PKCS1_v1_5.new(rsa_key)
    enc_key = rsa_cipher.encrypt(key_b64.encode())
    header_val = base64.b64encode(enc_key).decode()
    return body_b64, header_val

def login():
    st, resp = http("GET", f"/auth/code?clientid={CLIENT_ID}")
    cap = json.loads(resp)["data"]
    uuid = cap["uuid"]
    answer = redis_get(f"global:captcha_codes:{uuid}")
    if not answer:
        answer = redis_get(f"captcha_codes:{uuid}")
    print(f"  captcha uuid={uuid[:8]}.. answer={answer}")
    login_body = {"clientId":CLIENT_ID,"grantType":"password","username":"admin",
                  "password":"admin123","code":answer,"uuid":uuid,"tenantId":"000000"}
    body_str = json.dumps(login_body, ensure_ascii=False)
    body_b64, header_val = encrypt_body(body_str)
    st, resp = http("POST", "/auth/login", raw_body=body_b64,
                    headers={HEADER_FLAG: header_val, "clientid": CLIENT_ID})
    if st == 200:
        token = json.loads(resp)["data"]["access_token"]
        print(f"  登录成功 token={token[:20]}...")
        return token
    else:
        print(f"  登录失败 st={st} resp={resp[:200]}")
        return None

def auth_headers(token):
    return {"Authorization": f"Bearer {token}", "clientid": CLIENT_ID}

def main():
    print("=== 1. 登录 ===")
    token = login()
    if not token:
        print("登录失败,终止"); sys.exit(1)
    H = auth_headers(token)

    print("\n=== 2. 缺陷代码库 CRUD ===")
    dc_code = f"SURF{os.urandom(2).hex()}"
    st, resp = http("POST", "/mes/ncr/defect-code", body={"code":dc_code,"name":"表面缺陷","level":1,"parentId":0,"enabled":"1"}, headers=H)
    dc_id = None
    if st == 200 and json.loads(resp).get("code") == 200:
        dc_id = json.loads(resp).get("data")
        log("创建缺陷代码 "+dc_code, True, f"id={dc_id}")
    else:
        log("创建缺陷代码 "+dc_code, False, resp[:150])
    # 查询树
    st, resp = http("GET", "/mes/ncr/defect-code/tree", headers=H)
    ok = st==200 and json.loads(resp).get("code")==200
    log("查询缺陷代码树", ok, f"数量={len(json.loads(resp).get('data') or [])}" if ok else resp[:150])

    print("\n=== 3. 手工建 NCR(CUSTOMER) ===")
    ncr_body = {"sourceType":"CUSTOMER","defectQty":10,"materialId":99002,
                "materialCode":"QC-RM-A","materialName":"测试物料",
                "woNo":"WO-TEST-001","remark":"E2E自动化测试建单"}
    st, resp = http("POST", "/mes/ncr/order", body=ncr_body, headers=H)
    ncr_id = None
    if st == 200 and json.loads(resp).get("code") == 200:
        log("手工建NCR", True)
    else:
        log("手工建NCR", False, resp[:200])
    # 查列表拿 ncrId
    st, resp = http("GET", "/mes/ncr/order/list?pageNum=1&pageSize=5", headers=H)
    if st == 200:
        rows = json.loads(resp).get("rows") or json.loads(resp).get("data",{}).get("rows") or []
        if rows:
            ncr_id = rows[0].get("ncrId")
            ncr_code = rows[0].get("ncrCode")
            log("查询NCR列表", True, f"首条 ncrId={ncr_id} code={ncr_code} status={rows[0].get('status')}")
        else:
            log("查询NCR列表", False, "无数据")
    else:
        log("查询NCR列表", False, resp[:150])

    if not ncr_id:
        print("无NCR可继续,终止后续"); print_summary(); return

    # 详情
    st, resp = http("GET", f"/mes/ncr/order/{ncr_id}", headers=H)
    ok = st==200 and json.loads(resp).get("code")==200
    log("查询NCR详情", ok, f"status={json.loads(resp).get('data',{}).get('status')}" if ok else resp[:150])

    print("\n=== 4. 状态机: 评审 -> 处置(让步) ===")
    st, resp = http("PUT", f"/mes/ncr/order/review/{ncr_id}", headers=H)
    log("评审 OPEN->REVIEW", st==200 and json.loads(resp).get("code")==200, resp[:120])
    st, resp = http("GET", f"/mes/ncr/order/{ncr_id}", headers=H)
    status = json.loads(resp).get("data",{}).get("status") if st==200 else None
    log("评审后状态=REVIEW", status=="REVIEW", f"实际={status}")

    st, resp = http("PUT", f"/mes/ncr/order/dispose/{ncr_id}?disposition=ACCEPT_AS_IS&remark={quote('让步接收')}", headers=H)
    log("处置 ACCEPT_AS_IS", st==200 and json.loads(resp).get("code")==200, resp[:120])
    st, resp = http("GET", f"/mes/ncr/order/{ncr_id}", headers=H)
    status = json.loads(resp).get("data",{}).get("status") if st==200 else None
    log("处置后状态=CLOSED", status=="CLOSED", f"实际={status}")

    print("\n=== 5. 删除守卫(已CLOSED可删) / 非 CLOSED 不可删 ===")
    # 再建一个 OPEN 的测删除守卫
    st, resp = http("POST", "/mes/ncr/order", body={"sourceType":"CUSTOMER","defectQty":3,"materialId":99002,"remark":"删除守卫测试"}, headers=H)
    st, resp = http("GET", "/mes/ncr/order/list?pageNum=1&pageSize=5", headers=H)
    rows = json.loads(resp).get("rows") or [] if st==200 else []
    open_id = None
    for r in rows:
        if r.get("status")=="OPEN":
            open_id = r.get("ncrId"); break
    if open_id:
        st, resp = http("DELETE", f"/mes/ncr/order/{open_id}", headers=H)
        log("删除OPEN的NCR(应拒绝)", st!=200 or json.loads(resp).get("code")!=200, resp[:120])

    print("\n=== 6. 8D 报告 ===")
    st, resp = http("GET", f"/mes/ncr/8d/{ncr_id}", headers=H)
    report_id = None
    if st==200 and json.loads(resp).get("code")==200:
        report_id = json.loads(resp).get("data",{}).get("reportId")
        log("获取/懒创建8D", True, f"reportId={report_id}")
    else:
        log("获取/懒创建8D", False, resp[:150])
    if report_id:
        st, resp = http("PUT", "/mes/ncr/8d", body={"reportId":report_id,"ncrId":ncr_id,
            "d1Team":"E2E测试团队","d2Problem":"自动化测试发现问题","d3Interim":"临时措施",
            "d4RootCause":"根因","d5Permanent":"永久措施","d6Implement":"已实施",
            "d7Prevent":"预防","d8Recognize":"认可"}, headers=H)
        log("编辑8D(D1-D8)", st==200 and json.loads(resp).get("code")==200, resp[:120])
        st, resp = http("PUT", f"/mes/ncr/8d/complete/{report_id}", headers=H)
        log("完成8D", st==200 and json.loads(resp).get("code")==200, resp[:120])

    print_summary()

def print_summary():
    print("\n" + "="*60)
    print("E2E 测试汇总")
    print("="*60)
    passed = sum(1 for _,ok,_ in results if ok)
    total = len(results)
    for name, ok, detail in results:
        print(f"  [{'✓' if ok else '✗'}] {name}" + (f"  ({detail})" if detail else ""))
    print(f"\n通过 {passed}/{total}")
    sys.exit(0 if passed==total else 1)

if __name__ == "__main__":
    main()
