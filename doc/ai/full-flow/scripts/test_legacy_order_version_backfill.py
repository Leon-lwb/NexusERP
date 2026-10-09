"""离线 TDD 夹具：使用合成身份标识，绝不使用原生私有快照。"""
import copy
import hashlib
import importlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

try:
    bf = importlib.import_module("legacy_order_version_backfill")
except ModuleNotFoundError as exc:
    if exc.name != "legacy_order_version_backfill":
        raise
    bf = None

IDS = tuple(str(1000000000000000100 + i) for i in range(7))
TENANT = "000000"
SQL = ("UPDATE pu_purchase_order SET certified_version=0 WHERE tenant_id=? "
       "AND order_id=? AND certified_version IS NULL AND status='DRAFT'")


def native_hash(*fields):
    h = hashlib.sha256()
    for field in fields:
        raw = field.encode("utf-8")
        h.update(struct.pack(">I", len(raw)))
        h.update(raw)
    return h.hexdigest()


def snapshot_bytes(snapshot):
    return json.dumps(snapshot, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def make_order(index):
    oid = IDS[index]
    req, line, reqline, detail, allocation = (str(2000 + index), str(3000 + index),
                                           str(4000 + index), str(5000 + index), str(6000 + index))
    material, vendor, actor, key = str(7000 + index), "8000", "9000", "birth-key-" + str(index)
    qty = "22.0000" if index % 2 == 0 else "44.0000"
    number = "22" if index % 2 == 0 else "44"
    start, approve, transfer = "2026-10-01 09:00:00", "2026-10-01 10:00:00", "2026-10-01 11:00:00"
    head = dict(tenant_id=TENANT, order_id=oid, order_no="PO" + str(index), requisition_id=req,
                vendor_id=vendor, status="DRAFT", del_flag="0", certified_terms_id=None,
                expected_arrival_date=None, select_flow_id=None, currency="CNY", exchange_rate="1.000000",
                payment_terms=None, delivery_terms=None, create_time=transfer, update_time=transfer)
    for name in ("total_amount", "amount_excluding_tax", "tax_amount", "discount_amount", "freight_amount", "discount_rate"):
        head[name] = None
    orderline = dict(tenant_id=TENANT, order_id=oid, line_id=line, requisition_line_id=reqline,
                     material_id=material, unit="26", order_quantity=qty, received_quantity="0.0000",
                     returned_quantity="0.0000", invoiced_qty="0.0000", receive_status="PENDING", del_flag="0")
    for name in ("unit_price", "unit_price_excluding_tax", "tax_rate", "line_amount", "line_amount_excluding_tax", "line_tax_amount"):
        orderline[name] = None
    rh = dict(tenant_id=TENANT, requisition_id=req, requisition_no="RQ" + str(index), source_type="AI_DECISION",
              status="ORDERED", version=2, del_flag="0", request_key=key, select_flow_id=None, create_time=start)
    rl = dict(tenant_id=TENANT, requisition_id=req, line_id=reqline, material_id=material, unit="26",
              apply_quantity=qty, approved_quantity=qty, source_type="MRP", source_id=detail,
              required_date="2026-10-20", del_flag="0")
    allocation_key = native_hash(TENANT, key, detail)
    ledger = dict(tenant_id=TENANT, allocation_id=allocation, material_id=material, detail_id=detail,
                  requisition_line_id=reqline, order_line_id=line, source_type="MRP_DETAIL", allocation_no=1,
                  base_uom_id="26", covered_demand_base_qty=qty, occupied_base_qty=qty,
                  fulfilled_base_qty="0.0000", released_base_qty="0.0000", version=2,
                  allocation_status="ORDER_COMMITTED_NOT_TIMELY_SUPPLY", allocation_key=allocation_key,
                  decision_idempotency_key=key, release_reason=None, create_time=start, update_time=transfer,
                  transfer_time=transfer)
    events = []
    for seq, kind, at, previous, after in (
        (0, "CREATE", start, None, "REQUEST_INTENT"),
        (1, "APPROVE", approve, "REQUEST_INTENT", "REQUEST_INTENT"),
        (2, "TRANSFER_TO_ORDER", transfer, "REQUEST_INTENT", "ORDER_COMMITTED_NOT_TIMELY_SUPPLY"),
    ):
        event_key = ("CREATE:" + allocation_key if seq == 0 else
                     "LIFECYCLE:" + native_hash(TENANT, allocation_key, kind, req if seq == 1 else line))
        events.append(dict(tenant_id=TENANT, event_id=str(10000 + index * 3 + seq), allocation_id=allocation,
                           event_sequence=seq, event_key=event_key, event_type=kind, from_status=previous,
                           to_status=after, occupied_base_before="0.0000" if seq == 0 else qty,
                           occupied_base_after=qty, requisition_line_id=reqline,
                           order_line_id=line if seq == 2 else None, actor_id=actor, occurred_at=at))
    # 合成 canonical 字符串用于替代独立导出的原始 Java canonical 前像。
    # 测试绝不生成原生私有证据。
    source_fields = ["S:" + str(i) + ":fixture-source;" for i in range(11)]
    catalog_fields = ["S:" + str(i) + ":fixture-catalog;" for i in range(10)]
    source_fp, catalog_fp = native_hash(*source_fields), native_hash(*catalog_fields)
    contract = dict(payloadFingerprint=hashlib.sha256(b"synthetic-payload").hexdigest(),
                    metadata={k: "1" for k in ("materialVersion", "vendorVersion", "supplierTermsId",
                        "supplierTermsVersion", "baseUomId", "baseUomVersion", "orderUomId", "orderUomVersion", "conversionVersion")},
                    rootWorkOrderId="11000")
    contract["metadata"].update(baseUomId="26", orderUomId="26")
    cfields = [contract["payloadFingerprint"]]
    for k, v in sorted(contract["metadata"].items()):
        cfields.extend([k, v])
    cfields.extend(["WORK_ORDER", contract["rootWorkOrderId"]])
    frozen = native_hash(native_hash(detail, "26", number, "26", number, "2026-10-20"))
    request_hash = native_hash(TENANT, actor, "AI_DECISION", key, material, vendor, "2026-10-20",
                               "," + detail, source_fp, "12000", "13000", frozen, native_hash(*cfields))
    command = dict(tenant_id=TENANT, request_key=key, command_status="COMMITTED", actor_id=actor,
                   entry_type="AI_DECISION", material_id=material, vendor_id=vendor, window_end="2026-10-20",
                   requisition_id=req, requisition_no=rh["requisition_no"], request_hash=request_hash,
                   source_fingerprint=source_fp, catalog_fingerprint=catalog_fp,
                   result_fingerprint=native_hash(native_hash(reqline, detail, material, "26", number, "2026-10-20")))
    native_head = dict(head, certified_version=None)
    for field in "requisition_no vendor_code vendor_name order_date remark attr1 attr2 attr3 attr4 create_dept create_by update_by contract_id contract_no dept_id dept_name buyer_id buyer_name".split():
        native_head[field] = None
    native_head.update(requisition_no=rh["requisition_no"], create_by=actor, order_date="2026-10-01")
    native_line = copy.deepcopy(orderline)
    for field in "material_code material_name material_spec sort_order remark create_dept create_by create_time update_by update_time".split():
        native_line[field] = None
    native_line.update(create_by=actor, create_time=transfer)
    return dict(nativeVersion=None, head=head, lines=[orderline], requisition=dict(head=rh, lines=[rl]),
                command=command, ledger=[ledger], events=events,
                mrp=[dict(tenant_id=TENANT, detail_id=detail, material_id=material, base_uom_id="26",
                          covered_demand_base_qty=qty, del_flag="0")],
                rootUnit=dict(tenant_id=TENANT, uom_id="26", is_base=1, primary_id=None, quantity_scale=4,
                              conversion_rate="1.000000", conversion_version="1",
                              conversion_verified_at=start, del_flag="0"),
                absence={name: [] for name in ("terms", "quoteConfirmation", "orderOperation", "arrival", "receive",
                      "return", "stockTransaction", "birth", "invoice", "flowable", "extraOrderLines", "extraAllocations")},
                fingerprintEvidence=dict(expectedSourceFingerprint=source_fp, runId="12000", actionId="13000",
                      contract=contract, sourceCanonicalFields=source_fields, catalogCanonicalFields=catalog_fields,
                      immutableVersionBindings=[]),
                nativeRows=dict(head=native_head, lines=[native_line]))


def valid_snapshot():
    orders = [make_order(i) for i in range(7)]
    # 合同元数据不是布尔认证标志：完整列规格和完整唯一键组成部分
    # 会在后续测试中接受校验／变异检查。
    metadata = dict(tables={name: "InnoDB" for name in bf.REQUIRED_TABLES},
                    columns={k: dict(dataType=v[0], precision=v[1], scale=v[2], maxLength=v[3],
                                    nullable="NO" if v[4] else "YES") for k, v in bf.COLUMN_CONTRACT.items()},
                    uniqueKeys={t: [dict(indexName="uk_" + str(i), nonUnique=0,
                      parts=[dict(columnName=c, sequence=j + 1, subPart=None) for j, c in enumerate(parts)])
                      for i, parts in enumerate(keys)] for t, keys in bf.UNIQUE_KEYS.items()},
                    nativeColumns={"pu_purchase_order": sorted(orders[0]["nativeRows"]["head"]),
                                   "pu_purchase_order_line": sorted(orders[0]["nativeRows"]["lines"][0])},
                    orderUpdateTriggers=[], orderAutomaticUpdates=[])
    return dict(environment=dict(host="localhost", transport="TCP", port=3306, schema="ry-erp",
                                 profile="dev", tenant=TENANT),
                authorization=dict(scope="LOCAL_SOFTWARE_BUSINESS_DATA_COMPLETION", allowedOrderIds=list(IDS),
                                   allowedChange="certified_version:NULL->0", production=False),
                metadata=metadata, orders=orders)


class MemoryPort:
    """真实的内存事务实现；校验回滚会恢复全部七条记录。"""
    def __init__(self, snapshot):
        self.snapshot = copy.deepcopy(snapshot)
        self.before = None
        self.calls = []
        self.drift = None
        self.rowcounts = []
        self.commit_error = False
        self.rollback_error = False
        self.extra_field_write = False
        self.begin_error = False
        self.read_error = False

    def begin(self):
        self.calls.append("begin")
        self.before = copy.deepcopy(self.snapshot)
        if self.begin_error:
            raise RuntimeError("PRIVATE begin payload")

    def read_locked(self, expected_order_ids):
        self.calls.append(("read_locked", expected_order_ids))
        if self.read_error:
            raise RuntimeError("PRIVATE read payload")
        result = copy.deepcopy(self.snapshot)
        if self.drift is not None and all(o["nativeVersion"] is None for o in self.snapshot["orders"]):
            self.drift(result)
        return result

    def cas_set_birth_zero(self, sql, bindings):
        self.calls.append(("cas", sql, bindings))
        oid = bindings[1]
        row = next(o for o in self.snapshot["orders"] if o["head"]["order_id"] == oid)
        row["nativeVersion"] = 0
        row["nativeRows"]["head"]["certified_version"] = 0
        if self.extra_field_write:
            row["nativeRows"]["head"]["update_time"] = "2026-10-09 00:00:00"
        return self.rowcounts.pop(0) if self.rowcounts else 1

    def commit(self):
        self.calls.append("commit")
        if self.commit_error:
            raise RuntimeError("PRIVATE uncertain COMMIT payload")
        self.before = None

    def rollback(self):
        self.calls.append("rollback")
        if self.rollback_error:
            raise RuntimeError("PRIVATE rollback payload")
        self.snapshot = self.before
        self.before = None


class BackfillTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(bf, "Task4 offline planner/executor is not implemented")
        self.snapshot = valid_snapshot()

    def refuse(self, mutate, code=None):
        mutate(self.snapshot)
        with self.assertRaises(bf.BackfillRefused) as caught:
            bf.plan_backfill(self.snapshot, IDS)
        if code:
            self.assertEqual(code, caught.exception.code)
        self.assertNotIn("PRIVATE", str(caught.exception))

    def backup(self, directory):
        path = Path(directory) / "original.json"
        bf.write_backup(self.snapshot, IDS, path)
        return path

    def test_plan_seven_exact_fixed_sql_and_opaque_proofs(self):
        before = copy.deepcopy(self.snapshot)
        with patch("socket.socket", side_effect=AssertionError("network forbidden")):
            plan = bf.plan_backfill(self.snapshot, IDS)
        self.assertEqual(before, self.snapshot)
        self.assertEqual("PLANNED", plan["status"])
        self.assertEqual(hashlib.sha256(snapshot_bytes(self.snapshot)).hexdigest(), plan["snapshotSha256"])
        self.assertEqual(7, len(plan["statements"]))
        for oid, statement in zip(IDS, plan["statements"]):
            self.assertEqual(SQL, statement["sql"])
            self.assertEqual([TENANT, oid], statement["bindings"])
            self.assertRegex(statement["proofSha256"], r"^[a-f0-9]{64}$")
        output = json.dumps(plan)
        for private in ("birth-key-", "requisition_no", "actor_id", "vendor_id", "sourceCanonicalFields"):
            self.assertNotIn(private, output)

    def test_native_zero_or_normal_version_never_backfilled(self):
        for version in (0, 1, 2, -1, False, "0"):
            with self.subTest(version=version):
                self.snapshot = valid_snapshot()
                self.refuse(lambda s: s["orders"][0].update(nativeVersion=version))

    def test_exact_scope_rejects_wrong_duplicate_extra_or_missing_id(self):
        for ids in (IDS[:-1], IDS + ("999",), IDS[:-1] + (IDS[0],), IDS[:-1] + ("999",)):
            with self.subTest(count=len(ids)):
                with self.assertRaises(bf.BackfillRefused):
                    bf.plan_backfill(self.snapshot, ids)

    def test_orders_must_be_exact_seven(self):
        self.refuse(lambda s: s["orders"].pop())
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"].append(copy.deepcopy(s["orders"][0])))

    def test_environment_only_exact_local_dev_tcp_schema_tenant(self):
        for field, value in (("host", "localhost.evil"), ("host", "::1"), ("host", "db.internal"),
                ("port", "3306"), ("port", 3307), ("transport", "SOCKET"), ("profile", "prod"),
                ("schema", "ry-vue"), ("tenant", "000001")):
            with self.subTest(field=field, value=value):
                self.snapshot = valid_snapshot()
                self.refuse(lambda s: s["environment"].update({field: value}))
        self.snapshot = valid_snapshot()
        self.snapshot["environment"]["host"] = "127.0.0.1"
        self.assertEqual("PLANNED", bf.plan_backfill(self.snapshot, IDS)["status"])

    def test_explicit_authorization_not_boolean_or_generic_permission(self):
        for field, value in (("scope", "approved"), ("allowedChange", "all"), ("production", True),
                             ("allowedOrderIds", list(IDS[:-1]))):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["authorization"].update({field: value}))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s.update(authorization=True))

    def test_sourceproof_boolean_and_unknown_fields_not_a_proof(self):
        self.refuse(lambda s: s["orders"][0].update(sourceProof=True))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0].pop("fingerprintEvidence"))

    def test_non_draft_flow_deleted_and_terms_rejected(self):
        for field, value in (("status", "APPROVED"), ("del_flag", "1"), ("select_flow_id", "1"),
                             ("certified_terms_id", "1"), ("payment_terms", "NET30"),
                             ("delivery_terms", "EXW"), ("expected_arrival_date", "2026-10-10")):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["orders"][0]["head"].update({field: value}))

    def test_all_downstream_absence_domains_complete_empty_not_unknown(self):
        for field in self.snapshot["orders"][0]["absence"]:
            for value in ([{"opaque": "PRIVATE"}], None, False):
                self.snapshot = valid_snapshot()
                self.refuse(lambda s: s["orders"][0]["absence"].update({field: value}))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0]["absence"].pop("birth"))

    def test_all_head_and_line_money_nonzero_rejected(self):
        for group, fields in (("head", ("total_amount", "amount_excluding_tax", "tax_amount", "discount_amount", "freight_amount", "discount_rate")),
                              ("line", ("unit_price", "unit_price_excluding_tax", "tax_rate", "line_amount", "line_amount_excluding_tax", "line_tax_amount"))):
            for field in fields:
                self.snapshot = valid_snapshot()
                self.refuse(lambda s: (s["orders"][0]["head"] if group == "head" else s["orders"][0]["lines"][0]).update({field: "0.01"}))

    def test_genuine_zero_money_accepted_but_float_bool_nan_missing_rejected(self):
        self.snapshot["orders"][0]["head"]["total_amount"] = "0.00"
        self.snapshot["orders"][0]["nativeRows"]["head"]["total_amount"] = "0.00"
        self.assertEqual("PLANNED", bf.plan_backfill(self.snapshot, IDS)["status"])
        for value in (0.0, False, "NaN", "Infinity", "-0", "0e0", " 0"):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["orders"][0]["head"].update(total_amount=value))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0]["head"].pop("total_amount"))

    def test_receive_return_and_invoice_counters_require_true_zero(self):
        for field in ("received_quantity", "returned_quantity", "invoiced_qty"):
            for value in (None, "1.0000", 0, False):
                self.snapshot = valid_snapshot()
                self.refuse(lambda s: s["orders"][0]["lines"][0].update({field: value}))

    def test_quantity_drift_each_identity_link_rejected(self):
        mutations = [lambda o: o["lines"][0].update(order_quantity="23.0000"),
                     lambda o: o["requisition"]["lines"][0].update(approved_quantity="23.0000"),
                     lambda o: o["ledger"][0].update(occupied_base_qty="23.0000"),
                     lambda o: o["mrp"][0].update(covered_demand_base_qty="23.0000"),
                     lambda o: o["events"][1].update(occupied_base_after="23.0000")]
        for mutate in mutations:
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: mutate(s["orders"][0]))

    def test_tenant_material_vendor_requestkey_mrp_root_link_rejected(self):
        mutations = [lambda o: o["command"].update(tenant_id="000001"),
                     lambda o: o["command"].update(material_id="999"),
                     lambda o: o["command"].update(vendor_id="999"),
                     lambda o: o["requisition"]["head"].update(request_key="wrong-key"),
                     lambda o: o["lines"][0].update(requisition_line_id="999"),
                     lambda o: o["ledger"][0].update(detail_id="999"),
                     lambda o: o["mrp"][0].update(material_id="999"),
                     lambda o: o["rootUnit"].update(is_base=0)]
        for mutate in mutations:
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: mutate(s["orders"][0]))

    def test_requisition_ordered_v2_and_command_committed_required(self):
        for mutation in (lambda o: o["requisition"]["head"].update(version=1),
                         lambda o: o["requisition"]["head"].update(status="APPROVED"),
                         lambda o: o["command"].update(command_status="CLAIMED")):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: mutation(s["orders"][0]))

    def test_all_four_digest_formats_and_actual_preimages_checked(self):
        for field in ("request_hash", "result_fingerprint", "source_fingerprint", "catalog_fingerprint"):
            for value in (None, "invalid", "f" * 64):
                self.snapshot = valid_snapshot()
                self.refuse(lambda s: s["orders"][0]["command"].update({field: value}))
        for field in ("sourceCanonicalFields", "catalogCanonicalFields"):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"][field].append("changed"))

    def test_original_request_contract_payload_and_root_not_rewritten(self):
        self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["contract"].update(rootWorkOrderId="999"))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["contract"]["metadata"].update(materialVersion="2"))

    def test_immutable_null_version_binding_is_a_hard_refusal(self):
        self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["immutableVersionBindings"].append(
            dict(kind="source", table="pu_purchase_order", rowId=IDS[0], column="certified_version", value=None)),
            "IMMUTABLE_VERSION_CONFLICT")

    def test_three_causal_events_not_just_names(self):
        for field, value in (("event_sequence", 7), ("event_key", "wrong"), ("actor_id", None),
                             ("from_status", "OTHER"), ("requisition_line_id", "999"),
                             ("occurred_at", "2026-09-01 00:00:00"), ("order_line_id", "999")):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["orders"][0]["events"][2].update({field: value}))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0]["events"].pop())
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0]["events"].append(copy.deepcopy(s["orders"][0]["events"][2])))

    def test_extra_ledger_or_order_line_not_accepted(self):
        for field in ("ledger", "lines"):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["orders"][0][field].append(copy.deepcopy(s["orders"][0][field][0])))

    def test_metadata_unknown_non_innodb_bad_precision_key_trigger_rejected(self):
        for mutation in (lambda m: m["tables"].pop("wm_material_receive"),
                         lambda m: m["tables"].update(pu_purchase_order="MyISAM"),
                         lambda m: m["columns"]["pu_purchase_order.certified_version"].update(dataType="bigint"),
                         lambda m: m["columns"]["pu_purchase_order_line.order_quantity"].update(scale=2),
                         lambda m: m["uniqueKeys"]["pu_requirement_allocation_event"].pop(),
                         lambda m: m.update(orderUpdateTriggers=[{"trigger": "unknown"}])):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: mutation(s["metadata"]))

    def test_complete_native_rows_match_projections_and_schema(self):
        self.refuse(lambda s: s["orders"][0]["nativeRows"]["head"].update(certified_version=0))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0]["nativeRows"]["lines"][0].pop("unit_price"))
        self.snapshot = valid_snapshot()
        self.refuse(lambda s: s["orders"][0]["nativeRows"]["head"].update(status="APPROVED"))

    def test_invalid_seventh_order_makes_entire_plan_refuse(self):
        self.refuse(lambda s: s["orders"][6]["head"].update(payment_terms="PRIVATE"))

    def test_backup_new_identical_and_conflicting_file_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.backup(directory)
            raw = path.read_bytes()
            bf.write_backup(self.snapshot, IDS, path)
            self.assertEqual(raw, path.read_bytes())
            changed = copy.deepcopy(self.snapshot)
            changed["orders"][0]["nativeRows"]["head"]["update_time"] = "2026-10-09 00:00:00"
            with self.assertRaises(bf.BackfillRefused):
                bf.write_backup(changed, IDS, path)
            self.assertEqual(raw, path.read_bytes())
            path.write_bytes(b"PRIVATE conflicting backup")
            with self.assertRaises(bf.BackfillRefused) as caught:
                bf.write_backup(self.snapshot, IDS, path)
            self.assertEqual("BACKUP_CONFLICT", caught.exception.code)
            self.assertEqual(b"PRIVATE conflicting backup", path.read_bytes())

    def test_executor_requires_backup_before_begin(self):
        port = MemoryPort(self.snapshot)
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(bf.BackfillRefused):
                bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, Path(directory) / "absent.json")
        self.assertEqual([], port.calls)

    def test_executor_checks_backup_sha_not_presence_boolean(self):
        port = MemoryPort(self.snapshot)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "backup.json"
            path.write_bytes(b"PRIVATE conflicting backup")
            with self.assertRaises(bf.BackfillRefused):
                bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, path)
        self.assertEqual([], port.calls)

    def test_executor_all_seven_one_transaction_and_postcheck_only_version(self):
        port = MemoryPort(self.snapshot)
        with tempfile.TemporaryDirectory() as directory:
            receipt = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
        self.assertEqual("COMMITTED", receipt["status"])
        self.assertEqual("begin", port.calls[0])
        self.assertEqual("commit", port.calls[-1])
        self.assertEqual(2, len([c for c in port.calls if isinstance(c, tuple) and c[0] == "read_locked"]))
        updates = [c for c in port.calls if isinstance(c, tuple) and c[0] == "cas"]
        self.assertEqual([(SQL, (TENANT, oid)) for oid in IDS], [(c[1], c[2]) for c in updates])
        before = copy.deepcopy(port.snapshot)
        for o in before["orders"]:
            o["nativeVersion"] = None
            o["nativeRows"]["head"]["certified_version"] = None
        self.assertEqual(self.snapshot, before)

    def test_lock_reread_any_drift_refuses_before_first_update(self):
        for mutate in (lambda s: s["orders"][6]["head"].update(payment_terms="NET30"),
                       lambda s: s["orders"][0]["nativeRows"]["head"].update(update_time="2026-10-09 00:00:00"),
                       lambda s: s["orders"][0].update(nativeVersion=0)):
            port = MemoryPort(self.snapshot)
            port.drift = mutate
            with tempfile.TemporaryDirectory() as directory:
                result = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
            self.assertEqual("ROLLED_BACK", result["status"])
            self.assertEqual("rollback", port.calls[-1])
            self.assertFalse(any(isinstance(c, tuple) and c[0] == "cas" for c in port.calls))
            self.assertEqual(self.snapshot, port.snapshot)

    def test_rowcount_not_exact_integer_one_rolls_back_every_partial_write(self):
        for value in (0, 2, -1, True, None):
            port = MemoryPort(self.snapshot)
            port.rowcounts = [1, 1, 1, value]
            with tempfile.TemporaryDirectory() as directory:
                result = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
            self.assertEqual("ROLLED_BACK", result["status"])
            self.assertEqual("ROWCOUNT_NOT_ONE", result["reason"])
            self.assertNotIn("commit", port.calls)
            self.assertEqual(self.snapshot, port.snapshot)

    def test_postwrite_business_field_change_rolls_back(self):
        port = MemoryPort(self.snapshot)
        port.extra_field_write = True
        with tempfile.TemporaryDirectory() as directory:
            result = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
        self.assertEqual("ROLLED_BACK", result["status"])
        self.assertEqual("POSTWRITE_DRIFT", result["reason"])
        self.assertEqual(self.snapshot, port.snapshot)

    def test_commit_exception_unknown_no_rollback_or_retry(self):
        port = MemoryPort(self.snapshot)
        port.commit_error = True
        executor = bf.BackfillExecutor(port, prior_outcome="NEW")
        with tempfile.TemporaryDirectory() as directory:
            path = self.backup(directory)
            result = executor.execute(self.snapshot, IDS, path)
            self.assertEqual("UNKNOWN", result["status"])
            self.assertFalse(result["retryAllowed"])
            calls = list(port.calls)
            with self.assertRaises(bf.BackfillRefused):
                executor.execute(self.snapshot, IDS, path)
            self.assertEqual(calls, port.calls)
        self.assertNotIn("rollback", port.calls)
        self.assertNotIn("PRIVATE", json.dumps(result))

    def test_persisted_unknown_blocks_new_executor_without_port_call(self):
        port = MemoryPort(self.snapshot)
        with self.assertRaises(bf.BackfillRefused):
            bf.BackfillExecutor(port, prior_outcome="UNKNOWN")
        self.assertEqual([], port.calls)

    def test_rollback_exception_also_unknown_and_no_retry(self):
        port = MemoryPort(self.snapshot)
        port.rowcounts = [0]
        port.rollback_error = True
        with tempfile.TemporaryDirectory() as directory:
            result = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
        self.assertEqual("UNKNOWN", result["status"])
        self.assertFalse(result["retryAllowed"])

    def test_begin_and_read_exceptions_are_sanitized_and_rolled_back(self):
        for failure in ("begin_error", "read_error"):
            port = MemoryPort(self.snapshot)
            setattr(port, failure, True)
            with tempfile.TemporaryDirectory() as directory:
                result = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
            self.assertEqual("ROLLED_BACK", result["status"])
            self.assertNotIn("PRIVATE", json.dumps(result))
            self.assertNotIn("commit", port.calls)

    def test_main_is_offline_plan_and_backup_not_an_executor(self):
        with tempfile.TemporaryDirectory() as directory:
            input_path, backup_path = Path(directory) / "input.json", Path(directory) / "backup.json"
            input_path.write_bytes(snapshot_bytes(self.snapshot))
            args = ["--snapshot", str(input_path), "--backup", str(backup_path)]
            for oid in IDS:
                args.extend(["--expected-order-id", oid])
            with patch("socket.socket", side_effect=AssertionError("network forbidden")), patch("builtins.print") as output:
                self.assertEqual(0, bf.main(args))
            self.assertEqual(snapshot_bytes(self.snapshot), backup_path.read_bytes())
            self.assertIn('"PLANNED"', output.call_args.args[0])

    def test_native_root_version_is_varchar_not_an_integer_default(self):
        for order in self.snapshot["orders"]:
            order["rootUnit"]["conversion_version"] = "1"
        self.assertEqual("PLANNED", bf.plan_backfill(self.snapshot, IDS)["status"])

    def test_native_four_scale_decimal_with_zero_fraction_obeys_root_integer_scale(self):
        for order in self.snapshot["orders"]:
            order["rootUnit"]["quantity_scale"] = 0
        self.assertEqual("PLANNED", bf.plan_backfill(self.snapshot, IDS)["status"])

    def test_native_unique_index_rows_including_nonunique_sequence_and_prefix(self):
        self.assertEqual("PLANNED", bf.plan_backfill(self.snapshot, IDS)["status"])
        for mutate in (lambda index: index.update(nonUnique=1),
                       lambda index: index["parts"][0].update(sequence=2),
                       lambda index: index["parts"][0].update(subPart=10)):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: mutate(s["metadata"]["uniqueKeys"]["pu_certified_purchase_command"][0]))

    def test_unknown_order_automatic_updates_not_inferred_empty(self):
        self.snapshot["metadata"]["orderAutomaticUpdates"] = []
        self.assertEqual("PLANNED", bf.plan_backfill(self.snapshot, IDS)["status"])

    def test_non_none_commit_return_is_unknown_not_committed(self):
        port = MemoryPort(self.snapshot)
        port.commit = lambda: False
        with tempfile.TemporaryDirectory() as directory:
            receipt = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
        self.assertEqual("UNKNOWN", receipt["status"])
        self.assertFalse(receipt["retryAllowed"])

    def test_commit_cancellation_is_unknown_and_still_one_shot(self):
        port = MemoryPort(self.snapshot)
        def interrupt():
            raise KeyboardInterrupt("PRIVATE cancelled commit")
        port.commit = interrupt
        executor = bf.BackfillExecutor(port, prior_outcome="NEW")
        with tempfile.TemporaryDirectory() as directory:
            try:
                receipt = executor.execute(self.snapshot, IDS, self.backup(directory))
            except KeyboardInterrupt:
                self.fail("COMMIT cancellation not classified UNKNOWN")
            self.assertEqual("UNKNOWN", receipt["status"])
            with self.assertRaises(bf.BackfillRefused):
                executor.execute(self.snapshot, IDS, self.backup(directory))

    def test_untrusted_port_refusal_reason_never_copied_into_receipt(self):
        port = MemoryPort(self.snapshot)
        def fail(sql, bindings):
            raise bf.BackfillRefused("PRIVATE unsafe driver credentials")
        port.cas_set_birth_zero = fail
        with tempfile.TemporaryDirectory() as directory:
            receipt = bf.BackfillExecutor(port, prior_outcome="NEW").execute(self.snapshot, IDS, self.backup(directory))
        self.assertEqual("ROLLED_BACK", receipt["status"])
        self.assertNotIn("PRIVATE", json.dumps(receipt))

    def test_malformed_native_column_names_yield_fixed_refusal_not_typeerror(self):
        self.snapshot["metadata"]["nativeColumns"]["pu_purchase_order"] = [{}]
        with self.assertRaises(bf.BackfillRefused):
            bf.plan_backfill(self.snapshot, IDS)

    def test_native_full_row_contract_cannot_omit_a_known_business_column(self):
        self.snapshot["metadata"]["nativeColumns"]["pu_purchase_order"].remove("contract_id")
        for order in self.snapshot["orders"]:
            order["nativeRows"]["head"].pop("contract_id")
        with self.assertRaises(bf.BackfillRefused):
            bf.plan_backfill(self.snapshot, IDS)

    def test_native_contract_and_opaque_attrs_are_unknown_extra_business_sources(self):
        for field in ("contract_id", "contract_no", "attr1", "attr2", "attr3", "attr4"):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["orders"][0]["nativeRows"]["head"].update({field: "PRIVATE unknown source"}))

    def test_native_requisition_no_and_transfer_creator_identity_checked(self):
        for field in ("requisition_no", "create_by"):
            self.snapshot = valid_snapshot()
            self.refuse(lambda s: s["orders"][0]["nativeRows"]["head"].update({field: "999"}))

    def test_fractional_root_quantity_and_native_integer_overflow_rejected(self):
        for value in ("22.5000", "100000000000000.0000"):
            self.snapshot = valid_snapshot()
            self.snapshot["orders"][0]["rootUnit"]["quantity_scale"] = 0
            self.refuse(lambda s: s["orders"][0]["lines"][0].update(order_quantity=value))

    def test_surrogate_preimage_and_malformed_shapes_have_safe_refusals(self):
        self.snapshot["orders"][0]["fingerprintEvidence"]["sourceCanonicalFields"][0] = "\ud800"
        with self.assertRaises(bf.BackfillRefused):
            bf.plan_backfill(self.snapshot, IDS)

    def test_execution_history_cannot_implicitly_default_to_new(self):
        port = MemoryPort(self.snapshot)
        with self.assertRaises(bf.BackfillRefused):
            bf.BackfillExecutor(port)
        self.assertEqual([], port.calls)

    def test_main_duplicate_json_keys_are_refused_no_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            input_path, backup_path = Path(directory) / "input.json", Path(directory) / "backup.json"
            input_path.write_text('{"environment":{},"environment":{}}', encoding="utf-8")
            with patch("builtins.print"):
                self.assertEqual(2, bf.main(["--snapshot", str(input_path), "--backup", str(backup_path)]))
            self.assertFalse(backup_path.exists())


if __name__ == "__main__":
    unittest.main()