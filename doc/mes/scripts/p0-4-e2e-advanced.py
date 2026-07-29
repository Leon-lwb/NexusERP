#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P0-4 NCR E2E 进阶: IQC autoCreate 触发 + 返工闭环(含关键工序IPQC)"""
import json, base64, subprocess, urllib.request, urllib.error, os, sys
from urllib.parse import quote
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.PublicKey import RSA
from Crypto.Util.Padding import pad

BASE = "http://localhost:8080"
CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e"
REDIS_PASS = "ruoyi123"
RSA_PUB = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdHnzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ=="
HEADER_FLAG = "encrypt-key"
ORIG_WO_ID = 1  # WO-TEST-001 (material 1001, routing 1, IN_PROGRESS)

results = []
def log(name, ok, detail=""):
    results.append((name, ok, detail))
    ds = f" :: {detail}" if detail != "" and detail is not None else ""
    print(f"[{'PASS' if ok else 'FAIL'}] {name}{ds}")

def http(method, path, body=None, headers=None, raw_body=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={quote(str(v))}" for k,v in params.items())
    h = {"Content-Type": "application/json;charset=UTF-8"}
    if headers: h.update(headers)
    data = None
    if raw_body is not None: data = raw_body.encode() if isinstance(raw_body,str) else raw_body
    elif body is not None: data = json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r: return r.status, r.read().decode()
    except urllib.error.HTTPError as e: return e.code, e.read().decode()
    except Exception as e: return -1, str(e)

def mysql_query(sql):
    try:
        out = subprocess.run(["mysql","-h127.0.0.1","-P3306","-uroot","-proot","--default-character-set=utf8mb4","-N","-e",sql],
                             capture_output=True, text=True, timeout=10)
        return out.stdout.strip()
    except Exception: return ""

def redis_get(key):
    try:
        out = subprocess.run(["redis-cli","-h","127.0.0.1","-p","6379","-a",REDIS_PASS,"GET",key],
                             capture_output=True, text=True, timeout=5)
        return out.stdout.strip().strip('"').strip("'")
    except Exception: return ""

def encrypt_body(body_str):
    aes_key = os.urandom(8).hex()
    ct = AES.new(aes_key.encode(), AES.MODE_ECB).encrypt(pad(body_str.encode(), 16))
    body_b64 = base64.b64encode(ct).decode()
    key_b64 = base64.b64encode(aes_key.encode()).decode()
    enc_key = PKCS1_v1_5.new(RSA.import_key(base64.b64decode(RSA_PUB))).encrypt(key_b64.encode())
    return body_b64, base64.b64encode(enc_key).decode()

def login():
    st, resp = http("GET", f"/auth/code?clientid={CLIENT_ID}")
    uuid = json.loads(resp)["data"]["uuid"]
    answer = redis_get(f"global:captcha_codes:{uuid}")
    body_str = json.dumps({"clientId":CLIENT_ID,"grantType":"password","username":"admin","password":"admin123","code":answer,"uuid":uuid,"tenantId":"000000"}, ensure_ascii=False)
    bb, hv = encrypt_body(body_str)
    st, resp = http("POST", "/auth/login", raw_body=bb, headers={HEADER_FLAG:hv,"clientid":CLIENT_ID})
    if st==200: return json.loads(resp)["data"]["access_token"]
    print(f"登录失败: {resp[:200]}"); return None

def H(token): return {"Authorization":f"Bearer {token}","clientid":CLIENT_ID}

def jget(resp, *path):
    d = json.loads(resp)
    for p in path:
        if d is None: return None
        d = d.get(p)
    return d

def main():
    print("=== 登录 ===")
    token = login()
    if not token: sys.exit(1)
    Hd = H(token)

    # ============ Part A: IQC autoCreate 触发 ============
    print("\n=== Part A: IQC 判定不合格 -> 自动建 NCR ===")
    iqc_no = f"E2E-IQC-{os.urandom(2).hex()}"
    st, resp = http("POST", "/mes/qc/iqc", body={"iqcNo":iqc_no,"materialId":1001,"materialCode":"M1001","receiveId":9001,"quantity":100,"sampleQty":10}, headers=Hd)
    iqc_id = jget(resp, "data", "iqcId") if st==200 else None
    log("创建IQC检验单", iqc_id is not None, f"iqcId={iqc_id} no={iqc_no}" if iqc_id else resp[:150])
    if not iqc_id: print("无法继续Part A");
    else:
        defect_info = '[{"code":"SCRATCH","qty":3,"desc":"表面划痕"},{"code":"DENT","qty":1,"desc":"凹痕"}]'
        st, resp = http("PUT", f"/mes/qc/iqc/inspect/{iqc_id}", params={"inspectResult":"UNQUALIFIED","defectInfo":defect_info,"sampleQty":"10","remark":"E2E不合格"}, headers=Hd)
        log("IQC inspect UNQUALIFIED", st==200 and jget(resp,"code")==200, resp[:120])
        # 查 NCR 是否自动创建(source=IQC, sourceId=iqcId)
        ncr_id_a = mysql_query(f"SELECT ncr_id FROM `ry-mes`.ncr_order WHERE source_type='IQC' AND source_id={iqc_id} LIMIT 1")
        log("IQC自动创建NCR", bool(ncr_id_a), f"ncrId={ncr_id_a}")
        if ncr_id_a:
            defect_cnt = mysql_query(f"SELECT COUNT(*) FROM `ry-mes`.ncr_defect WHERE ncr_id={ncr_id_a}")
            log("defectInfo解析为ncr_defect", defect_cnt=="2", f"缺陷明细数={defect_cnt}(预期2)")
            # 幂等: 再次inspect同iqc应跳过(但inspect本身会抛"已检验完成",所以测幂等用DB层 uk_source)
            dup_cnt = mysql_query(f"SELECT COUNT(*) FROM `ry-mes`.ncr_order WHERE source_type='IQC' AND source_id={iqc_id}")
            log("幂等:同来源NCR仅1条", dup_cnt=="1", f"实际={dup_cnt}")

    # ============ Part B: 返工闭环 ============
    print("\n=== Part B: 处置返工 -> 返工工单 -> 流转卡 -> 完工 -> NCR关闭 ===")
    # 1. 建NCR(关联原工单 woId=1)
    st, resp = http("POST", "/mes/ncr/order", body={"sourceType":"CUSTOMER","defectQty":5,"woId":ORIG_WO_ID,"woNo":"WO-TEST-001","materialId":1001,"remark":"返工闭环测试"}, headers=Hd)
    log("建NCR(关联原工单)", st==200 and jget(resp,"code")==200, resp[:120])
    ncr_id_b = mysql_query(f"SELECT ncr_id FROM `ry-mes`.ncr_order WHERE source_type='CUSTOMER' AND wo_id={ORIG_WO_ID} AND status='OPEN' ORDER BY create_time DESC LIMIT 1")
    if not ncr_id_b:
        log("获取NCR", False, "无OPEN的NCR"); print_summary(); return
    log("获取NCR", True, f"ncrId={ncr_id_b}")

    # 2. 评审
    st, resp = http("PUT", f"/mes/ncr/order/review/{ncr_id_b}", headers=Hd)
    log("评审 OPEN->REVIEW", st==200 and jget(resp,"code")==200, resp[:100])

    # 3. 处置 REWORK
    st, resp = http("PUT", f"/mes/ncr/order/dispose/{ncr_id_b}", params={"disposition":"REWORK","remark":"E2E返工"}, headers=Hd)
    log("处置 REWORK", st==200 and jget(resp,"code")==200, resp[:120])
    status_b = mysql_query(f"SELECT status FROM `ry-mes`.ncr_order WHERE ncr_id={ncr_id_b}")
    log("NCR状态=IN_REWORK", status_b=="IN_REWORK", f"实际={status_b}")

    # 4. 取返工工单ID
    rework_wo_id = mysql_query(f"SELECT rework_wo_id FROM `ry-mes`.ncr_rework_order WHERE ncr_id={ncr_id_b} LIMIT 1")
    rework_wo_source = mysql_query(f"SELECT source_type FROM `ry-mes`.pro_work_order WHERE mes_wo_id={rework_wo_id}") if rework_wo_id else ""
    log("派生返工工单", bool(rework_wo_id), f"reworkWoId={rework_wo_id} source={rework_wo_source}")
    log("返工工单source_type=REWORK", rework_wo_source=="REWORK", f"实际={rework_wo_source}")

    if not rework_wo_id or rework_wo_source!="REWORK":
        print("返工工单异常,终止"); print_summary(); return

    # 5. 下达返工工单(生成工序步骤)
    st, resp = http("PUT", f"/mes/pro/work/release/{rework_wo_id}", headers=Hd)
    step_cnt = mysql_query(f"SELECT COUNT(*) FROM `ry-mes`.pro_work_order_step WHERE mes_wo_id={rework_wo_id}")
    log("下达返工工单(生成步骤)", st==200 and jget(resp,"code")==200 and step_cnt!="0", f"工序步骤数={step_cnt}")

    # 6. 创建流转卡
    st, resp = http("POST", f"/mes/pro/flow-card", params={"mesWoId":rework_wo_id,"quantity":"5"}, headers=Hd)
    card_id = jget(resp,"data","cardId") if st==200 else None
    log("创建流转卡", card_id is not None, f"cardId={card_id}" if card_id else resp[:150])
    if not card_id: print_summary(); return

    # 7. 取流转卡工序步骤
    steps_raw = mysql_query(f"SELECT step_record_id, sequence_num, process_id, is_key_process FROM `ry-mes`.pro_flow_card_step WHERE card_id={card_id} ORDER BY sequence_num")
    steps = [s.split("\t") for s in steps_raw.splitlines()] if steps_raw else []
    log("流转卡工序步骤生成", len(steps)>0, f"步骤数={len(steps)}")
    if not steps: print_summary(); return

    # 8. 下达流转卡
    st, resp = http("PUT", f"/mes/pro/flow-card/release/{card_id}", headers=Hd)
    log("下达流转卡", st==200 and jget(resp,"code")==200, resp[:100])

    # 9. 逐工序 entry -> exit (关键工序后检验IPQC)
    for sr_id, seq, proc_id, is_key in steps:
        st, resp = http("POST", "/mes/pro/flow-card/entry", body={"cardId":int(card_id),"stepRecordId":int(sr_id),"operatorId":1}, headers=Hd)
        log(f"工序{seq} entry", st==200 and jget(resp,"code")==200, (jget(resp,"data","passed") if st==200 else resp[:80]))
        st, resp = http("POST", "/mes/pro/flow-card/exit", body={"cardId":int(card_id),"stepRecordId":int(sr_id),"goodQty":5,"defectQty":0,"scrapQty":0}, headers=Hd)
        log(f"工序{seq} exit", st==200 and jget(resp,"code")==200, (jget(resp,"data","passed") if st==200 else resp[:80]))
        if is_key == "1":
            # 关键工序: completeStep自动建IPQC, 查PENDING的IPQC并检验QUALIFIED
            ipqc_id = mysql_query(f"SELECT ipqc_id FROM `ry-mes`.qc_ipqc WHERE mes_wo_id={rework_wo_id} AND process_id={proc_id} AND status='PENDING' ORDER BY create_time DESC LIMIT 1")
            if ipqc_id:
                st, resp = http("PUT", f"/mes/qc/ipqc/inspect/{ipqc_id}", params={"inspectResult":"QUALIFIED","sampleQty":"5","defectQty":"0","remark":"E2E合格"}, headers=Hd)
                log(f"工序{seq} IPQC检验QUALIFIED", st==200 and jget(resp,"code")==200, f"ipqcId={ipqc_id}")
            else:
                log(f"工序{seq} IPQC检验QUALIFIED", False, "未找到PENDING的IPQC")

    # 10. 完工流转卡 -> 触发工单完工 -> 回写NCR
    st, resp = http("PUT", f"/mes/pro/flow-card/complete/{card_id}", headers=Hd)
    log("完工流转卡", st==200 and jget(resp,"code")==200, resp[:150])

    # 11. 验证 NCR 已关闭 + 返工工单已完工
    import time; time.sleep(1)
    status_final = mysql_query(f"SELECT status FROM `ry-mes`.ncr_order WHERE ncr_id={ncr_id_b}")
    rework_status = mysql_query(f"SELECT status FROM `ry-mes`.ncr_rework_order WHERE ncr_id={ncr_id_b}")
    wo_final = mysql_query(f"SELECT wo_status FROM `ry-mes`.pro_work_order WHERE mes_wo_id={rework_wo_id}")
    log("NCR状态=CLOSED(返工闭环关闭)", status_final=="CLOSED", f"实际={status_final}")
    log("返工记录status=COMPLETED", rework_status=="COMPLETED", f"实际={rework_status}")
    log("返工工单wo_status=COMPLETED", wo_final=="COMPLETED", f"实际={wo_final}")

    print_summary()

def print_summary():
    print("\n"+"="*60); print("E2E 进阶测试汇总"); print("="*60)
    passed = sum(1 for _,ok,_ in results if ok)
    for name, ok, detail in results:
        print(f"  [{'✓' if ok else '✗'}] {name}"+(f"  ({detail})" if detail else ""))
    print(f"\n通过 {passed}/{len(results)}")
    sys.exit(0 if passed==len(results) else 1)

if __name__ == "__main__":
    main()
