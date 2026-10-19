"""对调用方提供的离线一致性导出进行审计；绝不按同码或同物料自动关联。"""
from collections import defaultdict
from decimal import Decimal
import hashlib
from core import REQUIRED_TABLES, digest, find, number, row_key, validate_artifact, validate_snapshot

ZERO=Decimal("0")

def audit(snapshot):
    validate_snapshot(snapshot)
    tables=snapshot["tables"]
    findings=[]
    complete=REQUIRED_TABLES <= set(snapshot.get("complete_tables",[])) & set(tables)

    def emit(code, table=None, row=None, detail="", *, severity="ERROR", material_id=None):
        item={"code":code,"severity":severity,"detail":detail}
        if table:item["table"]=table
        if row is not None:
            item["key"]=row_key(table,row)
            item["tenant_id"]=row.get("tenant_id")
            item["material_id"]=material_id or row.get("material_id") or row.get("component_material_id")
        findings.append(item)

    missing=sorted(REQUIRED_TABLES-(set(snapshot.get("complete_tables",[])) & set(tables)))
    if missing:emit("INCOMPLETE_EXPORT",detail="缺少完整导出声明或表："+",".join(missing))
    if not snapshot.get("captured_at") or set(snapshot.get("watermarks",{}))!={"erp","mes"}:
        emit("MISSING_WATERMARK",detail="必须保留两数据源导出边界；跨库不是天然一致事务")
        complete=False

    def rows(table): return tables.get(table,[])
    def index(table, key):return {(r["tenant_id"],r.get(key)):r for r in rows(table)}
    materials=index("bd_material","material_id")
    identities=index("wm_sn","sn_id")
    batches=index("wm_batch","batch_id")
    lots=index("lt_lot","lot_id")
    local_sns=index("pro_sn","sn_id")
    active=defaultdict(list)
    stock_qty=defaultdict(lambda:ZERO);stock_amount=defaultdict(lambda:ZERO)
    tx_qty=defaultdict(lambda:ZERO);tx_amount=defaultdict(lambda:ZERO)
    tx_by_stock=defaultdict(list)
    valuation_rows=defaultdict(list);posting_rows=defaultdict(list)
    identity_codes=defaultdict(list)

    for r in rows("wm_sn"):
        identity_codes[(r["tenant_id"],r.get("sn_code"))].append(r)
        if not r.get("sn_code"):emit("MISSING_CANONICAL_CODE","wm_sn",r,"正式原码为空，不得补造")
    for group in identity_codes.values():
        if len(group)>1:
            for r in group:emit("DUPLICATE_CANONICAL_CODE","wm_sn",r,"同租户正式码重复")

    for barcode in rows("wm_barcode"):
        if barcode.get("primary_object_type") is None:
            emit("LEGACY_BARCODE_OBJECT_UNKNOWN","wm_barcode",barcode,"历史条码主对象未typed，保留原码及当前stock引用；须核实原证据，不猜SN/BATCH/MATERIAL/DOCUMENT")

    aliases=defaultdict(list)
    proven_aliases=set()
    alias_commands=defaultdict(set)
    for r in rows("wm_sn_alias"):
        aliases[(r["tenant_id"],r.get("alias_code"),r.get("material_id"),r.get("source_type"),r.get("source_id"))].append(r)
        n=identities.get((r["tenant_id"],r.get("identity_id")))
        if not n or n.get("material_id")!=r.get("material_id"):
            emit("INVALID_EXTERNAL_ALIAS","wm_sn_alias",r,"别名没有有效同租户同物料正式身份")
        else:proven_aliases.add((r["tenant_id"],r["identity_id"],r.get("alias_code")))
        alias_commands[(r["tenant_id"],r.get("command_id"))].add((r.get("identity_id"),r.get("alias_code"),r.get("source_type"),r.get("source_id")))
    for group in aliases.values():
        if len({r.get("identity_id") for r in group})>1:
            for r in group:emit("AMBIGUOUS_EXTERNAL_ALIAS","wm_sn_alias",r,"相同上下文下外码对应多实物；保留候选，不自动选择",severity="AUDIT")
    for r in rows("wm_sn_alias"):
        if len(alias_commands[(r["tenant_id"],r.get("command_id"))])>1:
            emit("ALIAS_COMMAND_REUSED","wm_sn_alias",r,"同一别名命令对应不同参数")

    for r in rows("wm_material_stock"):
        k=(r["tenant_id"],r["material_id"])
        q=number(r,"stock_quantity");amount=number(r,"total_cost")
        stock_qty[k]+=q;stock_amount[k]+=amount
        if r.get("valuation_group_id"):valuation_rows[(r["tenant_id"],r["valuation_group_id"])].append(r)
        if q==0 and amount!=0:emit("ZERO_QUANTITY_COST_RESIDUE","wm_material_stock",r,"零库存仍有金额，须按原凭据核实成本残留，不能进入STRICT")
        if q<0:emit("NEGATIVE_STOCK","wm_material_stock",r,"负库存需按原业务规则复核")
        if number(r,"reserved_quantity")<0 or number(r,"reserved_quantity")>q:
            emit("INVALID_RESERVED_QUANTITY","wm_material_stock",r,"预留数量超出库存范围")
        if q<=0:continue
        if r.get("del_flag")=="1":emit("DELETED_POSITIVE_STOCK","wm_material_stock",r,"软删除不释放新版正库存身份")
        policy=materials.get(k)
        if policy is None:
            emit("MISSING_MATERIAL","wm_material_stock",r,"找不到同租户物料策略")
            if r.get("sn_id") and (r["tenant_id"],r["sn_id"]) not in identities:
                emit("MISSING_ERP_IDENTITY","wm_material_stock",r,"显式SN引用没有同租户正式身份")
            continue
        is_serial=policy.get("is_serial_manage")=="1"
        if r.get("sn_id"):
            active[(r["tenant_id"],r["sn_id"])].append(r)
        if is_serial:
            n=identities.get((r["tenant_id"],r.get("sn_id")))
            if not n or n.get("material_id")!=r["material_id"] or n.get("del_flag")=="1":
                emit("MISSING_ERP_IDENTITY","wm_material_stock",r,"SN物料的正库存没有有效同租户正式身份")
            elif r.get("sn_code")!=n.get("sn_code"):
                emit("CANONICAL_CODE_MISMATCH","wm_material_stock",r,"原码与明确身份不一致，不自动改写")
            if q!=1:emit("INVALID_SERIAL_QUANTITY","wm_material_stock",r,"SN正库存必须一件；多件旧记录只能凭单件证据另行拆分")
            if int(r.get("tracking_version","1"))<2:emit("LEGACY_TRACKING_VERSION","wm_material_stock",r,"旧版记录仍为AUDIT范围",severity="AUDIT")
        if policy.get("is_batch_manage")=="1":
            b=batches.get((r["tenant_id"],r.get("batch_id")))
            if not b or b.get("material_id")!=r["material_id"]:emit("MISSING_ERP_BATCH","wm_material_stock",r,"批次物料缺少同租户同物料批次")
            elif is_serial and n and n.get("batch_id")!=r.get("batch_id"):
                emit("IDENTITY_BATCH_MISMATCH","wm_material_stock",r,"正式身份批次与库存批次不一致")
    for group in active.values():
        if len(group)>1:
            for r in group:emit("DUPLICATE_ACTIVE_IDENTITY","wm_material_stock",r,"同租户同实物同时占用多条正库存，含历史版本")

    for identity in rows("wm_sn"):
        if identity.get("status") != "IN_STOCK" or identity.get("del_flag") == "1":continue
        current=[r for r in active[(identity["tenant_id"],identity["sn_id"])] if r.get("del_flag") == "0" and r["material_id"] == identity["material_id"]]
        if len(current)!=1:emit("ORPHAN_IN_STOCK_IDENTITY","wm_sn",identity,"在库声明必须有且仅一条同物料有效正库存，不能以当前转换掩盖")
        elif identity.get("stock_id") != current[0]["stock_id"]:emit("IN_STOCK_LOCATION_MISMATCH","wm_sn",identity,"在库身份当前位置必须指向唯一有效正库存")

    for r in rows("wm_material_stock_tx"):
        k=(r["tenant_id"],r["material_id"])
        q=number(r,"tx_quantity");flag=number(r,"tx_flag");amount=number(r,"tx_amount")
        if flag not in (Decimal(-1),ZERO,Decimal(1)):
            emit("INVALID_TX_DIRECTION","wm_material_stock_tx",r,"方向只允许-1、0、1");continue
        if q<0:emit("INVALID_TX_MAGNITUDE","wm_material_stock_tx",r,"事务数量须为绝对值，不能再次按方向翻转负数")
        if flag:
            tx_qty[k]+=flag*q;tx_amount[k]+=flag*amount
            if number(r,"before_quantity")+flag*q!=number(r,"after_quantity"):
                emit("TX_QUANTITY_MISMATCH","wm_material_stock_tx",r,"期初+方向×数量不等于期末")
            tx_by_stock[(r["tenant_id"],r.get("material_stock_id"))].append(r)
        if r.get("posting_group_id"):posting_rows[(r["tenant_id"],r["posting_group_id"])].append(r)
    from stock_converter import bridge_effects
    conversion_effects,conversion_findings=bridge_effects(snapshot)
    findings.extend(conversion_findings)
    for group in tx_by_stock.values():
        ordered=sorted(group,key=lambda r:(r.get("tx_date") or "",int(r["tx_id"])))
        stock=next((s for s in rows("wm_material_stock") if s["tenant_id"]==ordered[-1]["tenant_id"] and s["stock_id"]==ordered[-1].get("material_stock_id")),None)
        effect=conversion_effects.get((ordered[-1]["tenant_id"],ordered[-1].get("material_stock_id")))
        cutoff_delta=effect["quantity"] if effect and (ordered[-1].get("tx_date") or "") <= effect["cutover_at"] else ZERO
        if stock and number(ordered[-1],"after_quantity")-cutoff_delta!=number(stock,"stock_quantity"):
            emit("STOCK_TX_END_BALANCE_MISMATCH","wm_material_stock",stock,"最后一笔实物事务余额不等于当前库存")
        for prev,current in zip(ordered,ordered[1:]):
            if number(prev,"after_quantity")!=number(current,"before_quantity"):
                emit("TX_CHAIN_GAP","wm_material_stock_tx",current,"同库存事务前后数量链断裂，必须核实完整历史")

    opening_qty=defaultdict(lambda:ZERO);opening_amount=defaultdict(lambda:ZERO)
    for r in snapshot.get("opening_balances",[]):
        try:
            candidates=[e for e in snapshot.get("evidence",[]) if e.get("evidence_id")==r.get("evidence_id")]
            if len(candidates)!=1:raise ValueError("期初证据不唯一")
            e=validate_artifact(candidates[0])
            assertion={"opening_balance":{k:r[k] for k in ("tenant_id","material_id","quantity","amount")}}
            if e["tenant_id"]!=r["tenant_id"] or sum(a==assertion for a in e.get("assertions",[]))!=1:
                raise ValueError("凭据未明确授权该期初余额")
            if not e.get("source_rows"):raise ValueError("缺少来源行")
            for source in e["source_rows"]:
                if source["table"] not in snapshot.get("complete_tables",[]):raise ValueError("期初来源表不完整")
                source_row=find(snapshot,source["table"],source["key"])
                if source_row["tenant_id"]!=r["tenant_id"] or digest(source_row)!=source.get("row_hash"):raise ValueError("期初来源摘要或租户不一致")
            q=number(r,"quantity");amount=number(r,"amount")
        except (ValueError,KeyError,TypeError):
            emit("UNVERIFIED_OPENING_BALANCE",detail="期初余额缺少精确原凭据、冻结断言或来源摘要，不能用差额倒推")
            continue
        k=(r["tenant_id"],r["material_id"])
        opening_qty[k]+=q;opening_amount[k]+=amount
    reconciliation=[]
    for k in sorted(set(stock_qty)|set(tx_qty)|set(opening_qty)):
        qdelta=stock_qty[k]-tx_qty[k]-opening_qty[k]
        adelta=stock_amount[k]-tx_amount[k]-opening_amount[k]
        reconciliation.append({"tenant_id":k[0],"material_id":k[1],"stock_quantity":f"{stock_qty[k]:.4f}","tx_net_quantity":f"{tx_qty[k]:.4f}","opening_quantity":f"{opening_qty[k]:.4f}","quantity_delta":f"{qdelta:.4f}","stock_amount":f"{stock_amount[k]:.4f}","tx_net_amount":f"{tx_amount[k]:.4f}","opening_amount":f"{opening_amount[k]:.4f}","amount_delta":f"{adelta:.4f}"})
        for code,delta in (("QUANTITY_RECONCILIATION_MISMATCH",qdelta),("AMOUNT_RECONCILIATION_MISMATCH",adelta)):
            if delta:
                findings.append({"code":code,"severity":"ERROR","tenant_id":k[0],"material_id":k[1],"detail":"余额与完整事务/有凭据期初不平："+str(delta)})

    mapped=defaultdict(list)
    for r in rows("pro_sn"):
        if not r.get("mes_wo_id") and r.get("source")!="SUBCONTRACT":emit("MISSING_MES_WORK_ORDER","pro_sn",r,"仅来源已核实为SUBCONTRACT才允许空工单")
        if not r.get("identity_id"):
            emit("UNMAPPED_MES_IDENTITY","pro_sn",r,"没有正式身份映射；同码同物料不是证据")
        else:
            mapped[(r["tenant_id"],r["identity_id"])].append(r)
            n=identities.get((r["tenant_id"],r["identity_id"]))
            if not n or n.get("material_id")!=r["material_id"]:emit("INVALID_MES_IDENTITY","pro_sn",r,"映射身份缺失或跨物料")
            elif n.get("sn_code")!=r.get("sn_code") and (r["tenant_id"],r["identity_id"],r.get("sn_code")) not in proven_aliases:emit("MES_CANONICAL_CODE_MISMATCH","pro_sn",r,"保留原标签凭据，由别名与正式码共同解释差异")
        if r.get("product_lot_id"):
            lot=lots.get((r["tenant_id"],r["product_lot_id"]))
            if not lot or lot.get("material_id")!=r["material_id"]:emit("INVALID_PRODUCT_LOT","pro_sn",r,"成品批次不存在或跨物料")
    for group in mapped.values():
        if len(group)>1:
            for r in group:emit("DUPLICATE_MES_IDENTITY","pro_sn",r,"同租户正式身份映射多个MES本地SN")
    allocated=defaultdict(lambda:ZERO)
    for r in rows("lt_lot_allocation"):
        k=(r["tenant_id"],r["lot_id"]);lot=lots.get(k);batch=batches.get((r["tenant_id"],r.get("erp_batch_id")))
        if not lot or not batch or batch.get("material_id")!=r.get("material_id") or lot.get("material_id")!=r.get("material_id"):
            emit("INVALID_LOT_ALLOCATION","lt_lot_allocation",r,"分配跨租户、物料或缺少原批次")
        if not r.get("evidence_json") or not r.get("evidence_hash"):emit("MISSING_ALLOCATION_EVIDENCE","lt_lot_allocation",r,"不能仅按同批次码分配")
        allocated[k]+=number(r,"quantity")
    for r in rows("lt_lot"):
        if r.get("erp_batch_id"):
            b=batches.get((r["tenant_id"],r["erp_batch_id"]))
            if not b or b.get("material_id")!=r["material_id"]:emit("INVALID_LOT_MAPPING","lt_lot",r,"直接ERP批次映射无效")
        elif not allocated[(r["tenant_id"],r["lot_id"])]:emit("UNMAPPED_LOT","lt_lot",r,"历史批次缺少有证据的ERP批次映射")
        if allocated[(r["tenant_id"],r["lot_id"])]>number(r,"quantity"):emit("LOT_OVERALLOCATION","lt_lot",r,"分配合计超过原批次数量")

    wo_consumptions=index("lt_wo_consumption","consumption_id")
    entries=index("lt_sn_consumption","entry_id")
    reversed_qty=defaultdict(lambda:ZERO);consumed=defaultdict(lambda:ZERO);mixed=defaultdict(lambda:defaultdict(lambda:ZERO))
    commands=set()
    for r in rows("lt_sn_consumption"):
        q=number(r,"quantity");key=(r["tenant_id"],r.get("command_id"),r.get("line_no"))
        formal=identities.get((r["tenant_id"],r.get("identity_id")))
        local=local_sns.get((r["tenant_id"],r.get("local_sn_id")))
        component_lot=lots.get((r["tenant_id"],r.get("component_lot_id")))
        wo_source=wo_consumptions.get((r["tenant_id"],r.get("wo_consumption_id")))
        if not formal or not local or local.get("identity_id")!=r.get("identity_id") or not component_lot or component_lot.get("material_id")!=r.get("component_material_id") or not wo_source or wo_source.get("lot_id")!=r.get("component_lot_id") or wo_source.get("material_id")!=r.get("component_material_id"):
            emit("INVALID_COMPONENT_SOURCE","lt_sn_consumption",r,"组件账缺少明确成品身份、本地SN、组件批次或原工单消耗凭据")
        if key in commands:emit("DUPLICATE_CONSUMPTION_COMMAND","lt_sn_consumption",r,"同命令行重复")
        commands.add(key)
        sign=1
        if q<=0:emit("INVALID_CONSUMPTION_QUANTITY","lt_sn_consumption",r,"分录绝对数量必须为正")
        if r.get("entry_type")=="REVERSE":
            sign=-1;original=entries.get((r["tenant_id"],r.get("original_entry_id")))
            fields=("identity_id","component_material_id","component_lot_id","wo_consumption_id")
            if not original or original.get("entry_type")!="CONSUME" or any(original.get(f)!=r.get(f) for f in fields):
                emit("INVALID_CONSUMPTION_REVERSAL","lt_sn_consumption",r,"冲正缺原消耗分录或引用其他实物/组件/批次/工单")
            else:reversed_qty[(r["tenant_id"],r["original_entry_id"])]+=q
        elif r.get("entry_type")!="CONSUME":emit("INVALID_CONSUMPTION_TYPE","lt_sn_consumption",r,"只允许CONSUME/REVERSE")
        consumed[(r["tenant_id"],r.get("wo_consumption_id"))]+=sign*q
        mixed[(r["tenant_id"],r["identity_id"],r["component_material_id"])][r["component_lot_id"]]+=sign*q
    for k,q in reversed_qty.items():
        if q>number(entries[k],"quantity"):emit("OVER_REVERSED_CONSUMPTION","lt_sn_consumption",entries[k],"累计冲正超过原消耗")
    for r in rows("lt_wo_consumption"):
        if consumed[(r["tenant_id"],r["consumption_id"])]!=number(r,"consumed_qty"):
            emit("UNALLOCATED_COMPONENT_CONSUMPTION","lt_wo_consumption",r,"原工单消耗与单件净消耗不平，不按产量均分补造")
    guards={(r["tenant_id"],r["identity_id"],r["component_material_id"]):r for r in rows("lt_sn_material_guard")}
    for k,by_lot in mixed.items():
        guard=guards.get(k);positive={lot for lot,q in by_lot.items() if q>0}
        if guard and guard.get("allow_mixed_lot")=="0" and (len(positive)>1 or (positive and guard.get("selected_lot_id") not in positive)):
            emit("MIXED_LOT_POLICY_VIOLATION","lt_sn_material_guard",guard,"禁止混批但存在多个净消耗批次或守卫批次不一致")
        if any(q<0 for q in by_lot.values()):emit("NEGATIVE_COMPONENT_CONSUMPTION",detail="单件组件批次净消耗为负")

    for r in rows("wm_valuation_group"):
        group=valuation_rows[(r["tenant_id"],r["valuation_group_id"])]
        stock_ids={x["stock_id"] for x in group}
        subsequent=[tx for tx in rows("wm_material_stock_tx") if tx["tenant_id"] == r["tenant_id"] and tx.get("material_stock_id") in stock_ids and tx.get("tracking_version") == "2" and tx.get("del_flag") == "0"]
        qty_effect=sum((number(tx,"tx_flag")*number(tx,"tx_quantity") for tx in subsequent),ZERO)
        amount_effect=sum((number(tx,"tx_flag")*number(tx,"tx_amount") for tx in subsequent),ZERO)
        if sum((number(x,"stock_quantity") for x in group),ZERO)!=number(r,"quantity")+qty_effect or sum((number(x,"total_cost") for x in group),ZERO)!=number(r,"amount")+amount_effect:
            emit("VALUATION_GROUP_MISMATCH","wm_valuation_group",r,"切换冻结范围的拆分数量或金额不守恒；后续业务须另核补偿")
    for r in rows("wm_trace_posting_group"):
        group=posting_rows[(r["tenant_id"],r["posting_group_id"])]
        if sum((number(x,"tx_quantity") for x in group),ZERO)!=number(r,"quantity") or sum((number(x,"tx_amount") for x in group),ZERO)!=number(r,"amount"):
            emit("POSTING_GROUP_MISMATCH","wm_trace_posting_group",r,"冻结过账数量或金额不守恒")

    manifest_lines=defaultdict(list)
    for r in rows("pro_completion_manifest_line"):manifest_lines[(r["tenant_id"],r["manifest_id"],r["revision"])].append(r)
    for r in rows("pro_completion_manifest"):
        group=manifest_lines[(r["tenant_id"],r["manifest_id"],r.get("revision","1"))]
        good=sum((number(x,"quantity") for x in group if x.get("result")=="GOOD"),ZERO)
        scrap=sum((number(x,"quantity") for x in group if x.get("result")=="SCRAP"),ZERO)
        if len(group)!=int(r["line_count"]) or good!=number(r,"good_qty") or scrap!=number(r,"scrap_qty"):
            emit("MANIFEST_RECONCILIATION_MISMATCH","pro_completion_manifest",r,"清单件数、合格及报废数量不平")
        ids=set()
        for line in group:
            if line.get("identity_id"):
                if number(line,"quantity")!=1 or line["identity_id"] in ids:emit("INVALID_MANIFEST_SN_LINE","pro_completion_manifest_line",line,"SN行须一件且身份不重复")
                ids.add(line["identity_id"])
            if line.get("result") not in ("GOOD","SCRAP"):emit("INVALID_MANIFEST_RESULT","pro_completion_manifest_line",line,"清单结果不在已协调值域")

    print_logs=index("mes_label_print_log","log_id")
    for r in rows("mes_label_print_log"):
        if r.get("snapshot_mode")!="FROZEN":
            emit("LEGACY_LABEL_WITHOUT_SNAPSHOT","mes_label_print_log",r,"历史发送留痕不具备完整冻结来源，不补造快照且不认定实际出纸",severity="INFO")
        template_raw=r.get("template_snapshot")
        if template_raw is not None and hashlib.sha256(template_raw.encode("utf-8")).hexdigest()!=r.get("content_hash"):
            emit("LABEL_CONTENT_HASH_MISMATCH","mes_label_print_log",r,"冻结模板原始UTF8字节摘要不一致")
        raw=r.get("rendered_payload")
        if raw is not None:
            try:
                actual=hashlib.sha256(raw.encode(r.get("payload_charset") or "")).hexdigest()
            except (LookupError,UnicodeError):
                emit("UNVERIFIED_LABEL_CHARSET","mes_label_print_log",r,"字符集无法离线编码，不能声明发送载荷已核验")
            else:
                if actual!=r.get("payload_hash"):emit("LABEL_PAYLOAD_HASH_MISMATCH","mes_label_print_log",r,"按原字符集编码的发送字节摘要不一致")
        if r.get("snapshot_mode")=="FROZEN" and r.get("result")=="SENT" and (not template_raw or raw is None or not r.get("field_snapshot")):
            emit("INCOMPLETE_FROZEN_LABEL","mes_label_print_log",r,"已发送的新冻结任务缺模板、字段或指令快照")
        if r.get("original_print_log_id") and (r["tenant_id"],r["original_print_log_id"]) not in print_logs:
            emit("MISSING_ORIGINAL_LABEL_TASK","mes_label_print_log",r,"补打任务缺同租户原打印任务，不能按同SN代替")

    for table in ("wm_trace_outbox","wm_trace_inbox","mes_trace_outbox","mes_trace_inbox"):
        streams=defaultdict(list)
        for r in rows(table):
            raw=r.get("payload")
            if not isinstance(raw,str) or hashlib.sha256(raw.encode("utf-8")).hexdigest()!=r.get("content_hash"):
                emit("EVENT_CONTENT_HASH_MISMATCH",table,r,"只校验冻结载荷原始UTF8字节；禁止重新序列化后比较")
            stream=(r["tenant_id"],r.get("source_module"),r.get("aggregate_type"),r.get("aggregate_id"))
            streams[stream].append(r)
            if number(r,"aggregate_version")<1 or number(r,"schema_version")!=1 or number(r,"attempts")<0:
                emit("INVALID_EVENT_VERSION",table,r,"事件聚合版本、契约版本或尝试次数无效")
            if bool(r.get("lease_owner"))!=bool(r.get("lease_until")):
                emit("INCOMPLETE_EVENT_LEASE",table,r,"租约持有者与期限必须同时记录")
        for group in streams.values():
            ordered=sorted(group,key=lambda r:int(r["aggregate_version"]))
            for previous,current in zip(ordered,ordered[1:]):
                if int(current["aggregate_version"])==int(previous["aggregate_version"]):
                    emit("DUPLICATE_EVENT_STREAM_VERSION",table,current,"同来源聚合版本重复")
                elif int(current["aggregate_version"])!=int(previous["aggregate_version"])+1:
                    emit("EVENT_VERSION_GAP",table,current,"已导出流中出现版本缺口；需核对保留边界")
    for source,destination in (("wm_trace_outbox","mes_trace_inbox"),("mes_trace_outbox","wm_trace_inbox")):
        sent={(r["tenant_id"],r["event_id"]):r for r in rows(source)}
        for received in rows(destination):
            original=sent.get((received["tenant_id"],received["event_id"]))
            if not original:emit("ORPHAN_INBOX_EVENT",destination,received,"未找到来源发件箱，需核对归档保留边界及来源凭据")
            elif any(original.get(k)!=received.get(k) for k in ("source_module","aggregate_type","aggregate_id","aggregate_version","event_type","schema_version","payload","content_hash")):
                emit("CROSS_DATABASE_EVENT_MISMATCH",destination,received,"收发件箱同事件载荷或身份不一致")

    ready=[]
    for k,r in sorted(materials.items()):
        scoped=[f for f in findings if (f.get("tenant_id"),f.get("material_id"))==k]
        blockers=sorted({f["code"] for f in scoped if f["severity"]!="INFO"}|{f["code"] for f in findings if not f.get("material_id") and f["severity"]=="ERROR"})
        if not complete:blockers.append("INCOMPLETE_EXPORT")
        ready.append({"tenant_id":k[0],"material_id":k[1],"current_stage":r.get("tracking_stage","AUDIT"),"ready":not blockers,"blockers":sorted(set(blockers))})
    return {"schema_version":1,"snapshot_hash":digest(snapshot),"complete":complete,"scope":"离线导出与声明边界；不代表现场实物、生产部署或原始凭据真实性验收", "counts":{t:len(v) for t,v in sorted(tables.items())},"totals":{"stock_quantity":f"{sum(stock_qty.values(),ZERO):.4f}","stock_amount":f"{sum(stock_amount.values(),ZERO):.4f}","tx_net_quantity":f"{sum(tx_qty.values(),ZERO):.4f}","tx_amount":f"{sum(tx_amount.values(),ZERO):.4f}"},"reconciliation":reconciliation,"strict_ready":ready,"findings":findings}
