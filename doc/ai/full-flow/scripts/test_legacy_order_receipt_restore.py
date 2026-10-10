"""原提交回执恢复的合成 TDD 证据；不读取任何私有业务值、数据库或网络。"""
import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

import legacy_order_version_backfill as old
import legacy_order_receipt_restore as restore
import test_legacy_order_version_backfill as fixture

IDS = fixture.IDS
TENANT = fixture.TENANT
POLICY = "ORIGINAL_COMMITTED_RECEIPT_BIRTH_ONLY"
SQL = ("UPDATE pu_purchase_order SET certified_version=0,update_time=update_time WHERE tenant_id=? "
       "AND order_id=? AND certified_version IS NULL AND status='DRAFT'")
h = fixture.native_hash


def payload_hash(action, payload, actor):
    """独立对齐 Java Codec：身份前缀、排序标量、原来源顺序、规范 DECIMAL。"""
    fields = ["certified-purchase:v2", TENANT, actor, action["id"], action["idempotency_key"],
              action["target_biz_type"], action["target_biz_id"]]
    for key in sorted(set(payload) - {"frozenLines", "requirement_detail_ids"}):
        fields.extend((key, str(payload[key])))
    for line in payload["frozenLines"]:
        fields.extend((line["detailId"], line["baseUomId"], old._number(line["coveredDemandBase"]),
                       line["orderUomId"], old._number(line["requestedOrderQty"]), line["requiredDate"]))
    return h(*fields)


def rebind(order):
    """仅在测试构造原始持久回执时绑定所有摘要；变异测试默认绝不重签。"""
    proof = order["fingerprintEvidence"]
    action, command = proof["nativeAction"], order["command"]
    payload = json.loads(action["payload_json"])
    key = "AI_PURCHASE_V2:" + h(TENANT, action["run_id"], action["action_type"],
        "WORK_ORDER", action["target_biz_id"], payload["materialId"], payload["vendorId"])
    action["idempotency_key"] = key
    command["request_key"] = order["requisition"]["head"]["request_key"] = key
    proof["nativeRequisition"]["decision_idempotency_key"] = key
    contract = proof["contract"]
    contract["payloadFingerprint"] = payload_hash(action, payload, command["actor_id"])
    cfields = [contract["payloadFingerprint"]]
    for k, v in sorted(contract["metadata"].items()):
        cfields.extend((k, v))
    cfields.extend(("WORK_ORDER", contract["rootWorkOrderId"]))
    frozen, result = [], []
    rows = sorted(order["requisition"]["lines"], key=lambda r: int(r["source_id"]))
    result_lines = []
    for row in rows:
        qty = old._number(row["apply_quantity"])
        detail, rid, day = row["source_id"], row["line_id"], row["required_date"]
        frozen.append(h(detail, "26", qty, "26", qty, day))
        result.append(h(rid, detail, row["material_id"], "26", qty, day))
        result_lines.append(dict(lineId=rid, requirementDetailId=detail, materialId=row["material_id"],
            baseUomId="26", orderUomId="26", coveredDemandBase=qty, requestedOrderQty=qty, requiredDate=day))
        ledger = next(l for l in order["ledger"] if l["requisition_line_id"] == rid)
        ledger["decision_idempotency_key"] = key
        ledger["allocation_key"] = h(TENANT, key, detail)
        for event in order["events"]:
            if event["allocation_id"] == ledger["allocation_id"]:
                event["event_key"] = ("CREATE:" + ledger["allocation_key"] if event["event_sequence"] == 0 else
                    "LIFECYCLE:" + h(TENANT, ledger["allocation_key"], event["event_type"],
                        order["head"]["requisition_id"] if event["event_sequence"] == 1 else ledger["order_line_id"]))
    command["request_hash"] = h(TENANT, command["actor_id"], "AI_DECISION", key, command["material_id"],
        command["vendor_id"], command["window_end"], "".join("," + r["source_id"] for r in rows),
        payload["expectedSourceFingerprint"], action["run_id"], action["id"], h(*frozen), h(*cfields))
    command["result_fingerprint"] = h(*result)
    details = dict(purchase_payload_version=2, runId=action["run_id"], actionId=action["id"], tenantId=TENANT,
        requestKey=key, materialId=command["material_id"], vendorId=command["vendor_id"], sourceType="AI_DECISION",
        sourceFingerprint=command["source_fingerprint"], payloadFingerprint=contract["payloadFingerprint"],
        status="DRAFT", versionMarker="v0", beforeState="ABSENT", afterState="DRAFT", replayed=False, lines=result_lines)
    action["result_json"] = json.dumps(dict(success=True, retryable=False, errorCode=None, errorMessage=None,
        resultBizType=action["result_biz_type"], resultBizId=action["result_biz_id"],
        resultBizCode=action["result_biz_code"], result=details), ensure_ascii=False)


def valid_snapshot():
    """复用纯结构夹具，另建真正匹配 Codec/writer 的原 v2 action/command。"""
    snapshot = fixture.valid_snapshot()
    snapshot["metadata"]["orderAutomaticUpdates"] = [dict(tableName=t, columnName="update_time",
        dataType="datetime", extra="on update CURRENT_TIMESTAMP")
        for t in ("pu_purchase_order", "pu_purchase_order_line")]
    for i, order in enumerate(snapshot["orders"]):
        command = order["command"]
        root, run, aid, requirement, plan = (str(11000 + i % 3), str(12000 + i % 3),
                                             str(13000 + i), str(14000 + i % 3), str(15000 + i % 3))
        metadata = copy.deepcopy(order["fingerprintEvidence"]["contract"]["metadata"])
        payload = dict(purchase_payload_version=2, runId=run, materialId=command["material_id"],
            vendorId=command["vendor_id"], windowEnd=command["window_end"], **metadata,
            requirement_detail_ids=[r["source_id"] for r in order["requisition"]["lines"]],
            frozenLines=[dict(detailId=r["source_id"], baseUomId="26", coveredDemandBase=r["apply_quantity"],
                orderUomId="26", requestedOrderQty=r["apply_quantity"], requiredDate=r["required_date"])
                for r in order["requisition"]["lines"]], expectedSourceFingerprint=command["source_fingerprint"])
        action = dict(id=aid, tenant_id=TENANT, run_id=run, action_type="CREATE_PURCHASE_REQUISITION_DRAFT",
            target_biz_type="WORK_ORDER", target_biz_id=root, target_biz_code="WO-SYNTHETIC-" + root,
            purchase_payload_version=2, payload_json=json.dumps(payload, ensure_ascii=False), approval_required="Y",
            approval_status="APPROVED", execution_status="SUCCEEDED", permission_code="pu:requisition:add",
            executor_code="purchaseRequisitionDraft", idempotency_key="", approved_by="9001",
            approved_at="2026-10-01 08:00:00", started_at="2026-10-01 08:59:00", finished_at="2026-10-01 09:01:00",
            result_biz_type="PU_PURCHASE_REQUISITION", result_biz_id=command["requisition_id"],
            result_biz_code=command["requisition_no"], result_json="", last_error_code=None, last_error_message=None,
            del_flag="0")
        native_run = dict(id=run, tenant_id=TENANT, biz_type="WORK_ORDER", biz_id=root,
            biz_code=action["target_biz_code"], status="SUCCEEDED", decision_contract_version=2,
            started_at="2026-10-01 07:00:00", finished_at="2026-10-01 09:02:00",
            error_code=None, error_message=None, del_flag="0")
        order["fingerprintEvidence"] = dict(policy=POLICY, nativeAction=action, nativeRun=native_run,
            nativeRequisition=dict(tenant_id=TENANT, requisition_id=command["requisition_id"], decision_run_id=run,
                decision_action_id=aid, decision_idempotency_key="", create_by=command["actor_id"], del_flag="0"),
            rootWorkOrder=dict(tenant_id=TENANT, wo_id=root, wo_no=action["target_biz_code"], plan_id=plan,
                source_type="MATERIAL_REQUIREMENT", source_id=requirement, del_flag="0"),
            requirements=[dict(tenant_id=TENANT, requirement_id=requirement, plan_id=plan, del_flag="0")],
            mrpDetails=[dict(tenant_id=TENANT, detail_id=r["source_id"], requirement_id=requirement,
                material_id=r["material_id"], unit="26", required_date=r["required_date"], del_flag="0")
                for r in order["requisition"]["lines"]],
            contract=dict(payloadFingerprint="", metadata=metadata, rootWorkOrderId=root))
        # 没有 source/catalog 历史动态前像；本测试不合成或补造这两类证据。
        rebind(order)
    return snapshot


class ReceiptPlanTests(unittest.TestCase):
    def setUp(self):
        self.snapshot = valid_snapshot()

    def test_complete_original_v2_receipt_birth_only_plan(self):
        plan = restore.plan_restore(self.snapshot, IDS)
        self.assertEqual("PLANNED", plan["status"])
        self.assertEqual(POLICY, plan["policy"])
        self.assertFalse(plan["historicalSourcePreimageCertified"])
        self.assertFalse(plan["currentFreshnessCertified"])
        self.assertFalse(plan["actorPermissionCertified"])
        self.assertEqual(7, len(plan["statements"]))
        self.assertEqual(21, sum(len(o["events"]) for o in self.snapshot["orders"]))
        self.assertEqual([SQL] * 7, [s["sql"] for s in plan["statements"]])
        self.assertEqual([[TENANT, oid] for oid in IDS], [s["bindings"] for s in plan["statements"]])
        self.assertEqual(hashlib.sha256(fixture.snapshot_bytes(self.snapshot)).hexdigest(), plan["snapshotSha256"])
        self.assertFalse(plan["retryAllowed"])

    def refuse(self, mutate):
        snapshot = copy.deepcopy(self.snapshot)
        mutate(snapshot)
        with self.assertRaises(old.BackfillRefused) as caught:
            restore.plan_restore(snapshot, IDS)
        self.assertNotIn("PRIVATE", str(caught.exception))

    def mutate_payload(self, snapshot, mutate):
        action = snapshot["orders"][0]["fingerprintEvidence"]["nativeAction"]
        payload = json.loads(action["payload_json"])
        mutate(payload)
        action["payload_json"] = json.dumps(payload)

    def mutate_result(self, snapshot, mutate):
        action = snapshot["orders"][0]["fingerprintEvidence"]["nativeAction"]
        result = json.loads(action["result_json"])
        mutate(result)
        action["result_json"] = json.dumps(result)

    def test_original_entry_still_refuses_onupdate_and_missing_preimage(self):
        with self.assertRaises(old.BackfillRefused):
            old.plan_backfill(self.snapshot, IDS)
        self.snapshot["metadata"]["orderAutomaticUpdates"] = []
        with self.assertRaises(old.BackfillRefused):
            old.plan_backfill(self.snapshot, IDS)
        legacy = fixture.valid_snapshot()
        legacy["orders"][0]["fingerprintEvidence"]["sourceCanonicalFields"] = []
        with self.assertRaises(old.BackfillRefused):
            old.plan_backfill(legacy, IDS)

    def test_input_unmodified_and_plan_opaque(self):
        before = copy.deepcopy(self.snapshot)
        plan = restore.plan_restore(self.snapshot, IDS)
        self.assertEqual(before, self.snapshot)
        text = json.dumps(plan)
        for forbidden in ("payload_json", "result_json", "nativeAction", "WO-SYNTHETIC", "source_fingerprint"):
            self.assertNotIn(forbidden, text)
        self.assertEqual(plan, restore.plan_restore(copy.deepcopy(self.snapshot), tuple(reversed(IDS))))

    def test_policy_extra_verified_or_fullpreimage_proof_refused(self):
        for change in (lambda p: p.update(policy="FULL_PREIMAGE"), lambda p: p.update(verified=True),
                       lambda p: p.update(sourceCanonicalFields=["PRIVATE"] * 11),
                       lambda p: p.pop("nativeAction")):
            with self.subTest(change=change):
                self.refuse(lambda s: change(s["orders"][0]["fingerprintEvidence"]))

    def test_exact_seven_explicit_ids_and_authorization(self):
        for ids in (IDS[:6], IDS + ("99",), (IDS[0],) * 7, [int(x) for x in IDS], ("01",) + IDS[1:]):
            with self.subTest(ids_count=len(ids)):
                with self.assertRaises(old.BackfillRefused):
                    restore.plan_restore(self.snapshot, ids)
        for field, value in (("production", True), ("allowedChange", "ANY"), ("scope", "PRIVATE"),
                             ("allowedOrderIds", ["99"] + list(IDS[1:]))):
            self.refuse(lambda s: s["authorization"].update({field: value}))
        self.refuse(lambda s: s["orders"].pop())
        self.refuse(lambda s: s["orders"].__setitem__(6, copy.deepcopy(s["orders"][0])))

    def test_local_environment_is_exact(self):
        for field, value in (("host", "remote"), ("transport", "PIPE"), ("port", "3306"), ("port", 3307),
                             ("schema", "ry-vue"), ("profile", "prod"), ("tenant", "123456")):
            self.refuse(lambda s: s["environment"].update({field: value}))
        self.snapshot["environment"]["host"] = "127.0.0.1"
        self.assertEqual("PLANNED", restore.plan_restore(self.snapshot, IDS)["status"])

    def test_action_identity_approval_execution_and_fixed_metadata(self):
        wrong = dict(id="99", tenant_id="123456", run_id="99", action_type="SEND_MANAGER_NOTIFICATION",
            target_biz_type="SHORTAGE_HORIZON", target_biz_id="99", target_biz_code="PRIVATE",
            purchase_payload_version="2", approval_required=True, approval_status="PENDING",
            execution_status="FAILED", permission_code="*:*:*", executor_code="PRIVATE", idempotency_key="PRIVATE",
            approved_by=None, approved_at=None, started_at=None, finished_at=None,
            result_biz_type="PU_PURCHASE_ORDER", result_biz_id="99", result_biz_code="PRIVATE",
            last_error_code="PRIVATE", last_error_message="PRIVATE", del_flag="1")
        for field, value in wrong.items():
            with self.subTest(field=field):
                self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["nativeAction"].update({field: value}))

    def test_native_run_identity_terminal_v2_root_and_errors(self):
        for field, value in dict(id="99", tenant_id="123456", biz_type="SALES_ORDER", biz_id="99",
            biz_code="PRIVATE", status="PARTIAL_SUCCESS", decision_contract_version=True,
            started_at=None, finished_at=None, error_code="PRIVATE", error_message="PRIVATE", del_flag="1").items():
            with self.subTest(field=field):
                self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["nativeRun"].update({field: value}))

    def test_native_requisition_run_action_actor_and_key_binding(self):
        for field, value in dict(tenant_id="123456", requisition_id="99", decision_run_id="99", decision_action_id="99",
                                decision_idempotency_key="PRIVATE", create_by="99", del_flag="1").items():
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["nativeRequisition"].update({field: value}))

    def test_original_actor_not_current_client_verified(self):
        self.refuse(lambda s: s["orders"][0]["command"].update(actor_id="99"))
        self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"].update(actorVerified=True))
        self.refuse(lambda s: s.update(currentActor={"verified": True}))

    def test_all_seventeen_payload_fields_missing_extra_or_wrong_type(self):
        action = self.snapshot["orders"][0]["fingerprintEvidence"]["nativeAction"]
        for field in json.loads(action["payload_json"]):
            with self.subTest(field=field):
                self.refuse(lambda s: self.mutate_payload(s, lambda p: p.pop(field)))
                self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update({field: None})))
        self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update(verified=True)))
        self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update(purchase_payload_version=True)))

    def test_payload_identity_source_and_original_request_hash(self):
        for field in ("runId", "materialId", "vendorId", "supplierTermsId", "baseUomId", "orderUomId"):
            self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update({field: "99"})))
        for value in (None, "g" * 64, "0" * 64):
            self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update(expectedSourceFingerprint=value)))
        self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update(windowEnd="2026-10-21")))

    def test_all_nine_metadata_bound_to_original_payload(self):
        keys = self.snapshot["orders"][0]["fingerprintEvidence"]["contract"]["metadata"]
        for field in keys:
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["contract"]["metadata"].update({field: "different"}))
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["contract"]["metadata"].pop(field))
        for field in ("materialVersion", "vendorVersion", "supplierTermsVersion", "baseUomVersion", "orderUomVersion", "conversionVersion"):
            self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update({field: "different"})))
        self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["contract"].update(payloadFingerprint="0" * 64))
        self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["contract"].update(rootWorkOrderId="99"))

    def test_original_hash_formats_and_mismatch(self):
        for field in ("request_hash", "result_fingerprint", "source_fingerprint", "catalog_fingerprint"):
            self.refuse(lambda s: s["orders"][0]["command"].update({field: "invalid"}))
        for field in ("request_hash", "result_fingerprint", "source_fingerprint"):
            self.refuse(lambda s: s["orders"][0]["command"].update({field: "0" * 64}))
        # catalog 摘要只保留/格式校验，不能将没有前像的它冒称重算通过。
        self.snapshot["orders"][0]["command"]["catalog_fingerprint"] = "0" * 64
        plan = restore.plan_restore(self.snapshot, IDS)
        self.assertFalse(plan["historicalSourcePreimageCertified"])

    def test_frozen_lines_exact_identity_unit_quantity_date_and_no_rounding(self):
        wrong = dict(detailId="99", baseUomId="99", orderUomId="99", coveredDemandBase="23",
                     requestedOrderQty="21", requiredDate="2026-10-19")
        for field, value in wrong.items():
            self.refuse(lambda s: self.mutate_payload(s, lambda p: p["frozenLines"][0].update({field: value})))
        for qty in (None, 22, 22.0, True, "22e0", "022", "22.00001", "-22", "0", "100000000000000.0000"):
            self.refuse(lambda s: self.mutate_payload(s, lambda p: p["frozenLines"][0].update(coveredDemandBase=qty)))
        for value in ([], ["99"], ["5000", "5000"], [5000]):
            self.refuse(lambda s: self.mutate_payload(s, lambda p: p.update(requirement_detail_ids=value)))
        self.refuse(lambda s: self.mutate_payload(s, lambda p: p["frozenLines"][0].update(verified=True)))

    def test_codec_canonical_decimal_equivalence_preserves_raw_backup(self):
        self.mutate_payload(self.snapshot, lambda p: p["frozenLines"][0].update(coveredDemandBase="22", requestedOrderQty="22.00"))
        plan = restore.plan_restore(self.snapshot, IDS)
        self.assertEqual("PLANNED", plan["status"])
        self.assertNotEqual(plan["snapshotSha256"], restore.plan_restore(valid_snapshot(), IDS)["snapshotSha256"])

    def test_java_codec_rejects_c1_control_in_original_version_even_with_matching_hashes(self):
        for field in ("materialVersion", "vendorVersion", "supplierTermsVersion"):
            def mutate(s):
                o = s["orders"][0]
                self.mutate_payload(s, lambda p: p.update({field: "v" + chr(128) + "x"}))
                o["fingerprintEvidence"]["contract"]["metadata"][field] = "v" + chr(128) + "x"
                rebind(o)
            self.refuse(mutate)

    def test_adapter_result_code_contract_not_just_matching_arbitrary_text(self):
        def mutate(s):
            o = s["orders"][0]
            code = "INVALID:CODE"
            o["requisition"]["head"]["requisition_no"] = code
            o["nativeRows"]["head"]["requisition_no"] = code
            o["command"]["requisition_no"] = code
            o["fingerprintEvidence"]["nativeAction"]["result_biz_code"] = code
            rebind(o)
        self.refuse(mutate)

    def test_utf8_version_hash_uses_byte_length_not_character_length(self):
        o = self.snapshot["orders"][0]
        version = "version-" + chr(0x4e2d)
        self.mutate_payload(self.snapshot, lambda p: p.update(materialVersion=version))
        o["fingerprintEvidence"]["contract"]["metadata"]["materialVersion"] = version
        rebind(o)
        self.assertEqual("PLANNED", restore.plan_restore(self.snapshot, IDS)["status"])

    def test_original_success_result_envelope_and_all_details(self):
        wrong = dict(success=False, retryable=True, resultBizType="PU_PURCHASE_ORDER", resultBizId="99",
                     resultBizCode="PRIVATE", errorCode="PRIVATE", errorMessage="PRIVATE")
        for field, value in wrong.items():
            self.refuse(lambda s: self.mutate_result(s, lambda r: r.update({field: value})))
        details = json.loads(self.snapshot["orders"][0]["fingerprintEvidence"]["nativeAction"]["result_json"])["result"]
        for field in details:
            self.refuse(lambda s: self.mutate_result(s, lambda r: r["result"].pop(field)))
        for field, value in dict(runId="99", actionId="99", tenantId="123456", requestKey="PRIVATE", materialId="99",
            vendorId="99", sourceType="MANUAL", sourceFingerprint="0" * 64, payloadFingerprint="0" * 64,
            status="ORDERED", versionMarker="v2", beforeState="COMMITTED", afterState="ORDERED",
            replayed=1, purchase_payload_version=True).items():
            self.refuse(lambda s: self.mutate_result(s, lambda r: r["result"].update({field: value})))
        self.refuse(lambda s: self.mutate_result(s, lambda r: r.update(verified=True)))
        self.refuse(lambda s: self.mutate_result(s, lambda r: r["result"].update(verified=True)))

    def test_original_replayed_receipt_has_exact_before_state(self):
        self.mutate_result(self.snapshot, lambda r: r["result"].update(replayed=True, beforeState="COMMITTED"))
        self.assertEqual("PLANNED", restore.plan_restore(self.snapshot, IDS)["status"])

    def test_result_line_is_original_requisition_line_not_order_line(self):
        for field, value in dict(lineId="3000", requirementDetailId="99", materialId="99", baseUomId="99",
            orderUomId="99", coveredDemandBase="23", requestedOrderQty="23", requiredDate="2026-10-19").items():
            self.refuse(lambda s: self.mutate_result(s, lambda r: r["result"]["lines"][0].update({field: value})))
        self.refuse(lambda s: self.mutate_result(s, lambda r: r["result"].update(lines=[])))
        self.refuse(lambda s: self.mutate_result(s, lambda r: r["result"]["lines"][0].update(verified=True)))

    def test_duplicate_json_keys_and_non_json_proof_refused(self):
        for field in ("payload_json", "result_json"):
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["nativeAction"].update({field: '{"runId":"1","runId":"2"}'}))
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["nativeAction"].update({field: {"verified": True}}))
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["nativeAction"].update({field: "PRIVATE"}))

    def test_root_work_order_and_native_mrp_requirement_plan_source_chain(self):
        for field, value in dict(tenant_id="123456", wo_id="99", wo_no="PRIVATE", plan_id="99",
                                source_type="UNKNOWN", source_id="99", del_flag="1").items():
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["rootWorkOrder"].update({field: value}))
        for field, value in dict(tenant_id="123456", requirement_id="99", plan_id="99", del_flag="1").items():
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["requirements"][0].update({field: value}))
        for field, value in dict(tenant_id="123456", detail_id="99", requirement_id="99", material_id="99",
                                unit="99", required_date="2026-10-19", del_flag="1").items():
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["mrpDetails"][0].update({field: value}))
        for name in ("requirements", "mrpDetails"):
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"].update({name: []}))
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"][name].append(copy.deepcopy(s["orders"][0]["fingerprintEvidence"][name][0])))

    def test_plan_source_root_supported_without_claiming_freshness(self):
        for order in self.snapshot["orders"]:
            root = order["fingerprintEvidence"]["rootWorkOrder"]
            root.update(source_type="PLAN", source_id=root["plan_id"])
        self.assertEqual("PLANNED", restore.plan_restore(self.snapshot, IDS)["status"])

    def test_repeated_native_run_or_root_projection_must_be_identical(self):
        self.refuse(lambda s: s["orders"][3]["fingerprintEvidence"]["nativeRun"].update(started_at="2026-10-01 06:00:00"))

    def test_terminal_approval_and_execution_times_are_causal(self):
        for field, value in (("approved_at", "2026-10-01 10:00:00"), ("started_at", "2026-10-01 10:00:00"),
                             ("finished_at", "2026-10-01 08:00:00")):
            self.refuse(lambda s: s["orders"][0]["fingerprintEvidence"]["nativeAction"].update({field: value}))

    def test_native_metadata_exact_103_contracts_and_full_rows(self):
        self.assertEqual(103, len(self.snapshot["metadata"]["columns"]))
        self.assertEqual(41, len(self.snapshot["orders"][0]["nativeRows"]["head"]))
        self.assertEqual(28, len(self.snapshot["orders"][0]["nativeRows"]["lines"][0]))
        for table in ("pu_purchase_order", "pu_purchase_order_line"):
            self.refuse(lambda s: s["metadata"]["nativeColumns"][table].pop())
        self.refuse(lambda s: s["metadata"]["tables"].update(pu_purchase_order="MyISAM"))
        self.refuse(lambda s: s["metadata"]["columns"].pop(next(iter(s["metadata"]["columns"]))))
        self.refuse(lambda s: s["orders"][0]["nativeRows"]["head"].pop("buyer_name"))
        self.refuse(lambda s: s["orders"][0]["nativeRows"]["lines"][0].pop("remark"))
        self.refuse(lambda s: s["metadata"]["uniqueKeys"]["pu_certified_purchase_command"][0]["parts"][0].update(subPart=8))

    def test_null_birth_version_requires_physically_nullable_column(self):
        self.refuse(lambda s: s["metadata"]["columns"]["pu_purchase_order.certified_version"].update(nullable="NO"))

    def test_native_full_rows_are_sql_scalars_not_nested_json_or_boolean(self):
        for part in ("head", "lines"):
            for value in ({"verified": True}, ["PRIVATE"], True):
                def mutate(s):
                    row = s["orders"][0]["nativeRows"][part]
                    row = row if part == "head" else row[0]
                    row["remark"] = value
                self.refuse(mutate)

    def test_only_exact_reviewed_two_datetime_automatic_columns(self):
        for field, value in dict(tableName="OTHER", columnName="status", dataType="timestamp",
                                extra="DEFAULT_GENERATED on update CURRENT_TIMESTAMP").items():
            self.refuse(lambda s: s["metadata"]["orderAutomaticUpdates"][0].update({field: value}))
        self.refuse(lambda s: s["metadata"]["orderAutomaticUpdates"].pop())
        self.refuse(lambda s: s["metadata"].update(orderAutomaticUpdates=[]))
        self.refuse(lambda s: s["metadata"]["orderAutomaticUpdates"].append(copy.deepcopy(s["metadata"]["orderAutomaticUpdates"][0])))
        self.refuse(lambda s: s["metadata"]["orderAutomaticUpdates"][0].update(verified=True))
        self.refuse(lambda s: s["metadata"]["orderUpdateTriggers"].append("PRIVATE"))

    def test_all_unpriced_amount_fields_null_or_exact_real_zero_only(self):
        for scope, fields in (("head", old.HEAD_MONEY), ("line", old.LINE_MONEY)):
            for field in fields:
                for value in ("0.01", 0, False, "1e-4", "NaN", "-0.01"):
                    def change(s):
                        o = s["orders"][0]
                        projection = o["head"] if scope == "head" else o["lines"][0]
                        native = o["nativeRows"]["head"] if scope == "head" else o["nativeRows"]["lines"][0]
                        projection[field] = native[field] = value
                    self.refuse(change)
        for o in self.snapshot["orders"]:
            for field in old.HEAD_MONEY:
                o["head"][field] = o["nativeRows"]["head"][field] = "0.00"
            for field in old.LINE_MONEY:
                o["lines"][0][field] = o["nativeRows"]["lines"][0][field] = "0.00"
        self.assertEqual("PLANNED", restore.plan_restore(self.snapshot, IDS)["status"])

    def test_absence_complete_history_not_unknown_or_partial(self):
        for field in old.ABSENCE:
            self.refuse(lambda s: s["orders"][0]["absence"].pop(field))
            for value in (None, True, [{}]):
                self.refuse(lambda s: s["orders"][0]["absence"].update({field: value}))

    def test_nonnull_version_priced_approved_received_or_deleted_refused(self):
        for version in (0, 1, "0", False):
            self.refuse(lambda s: s["orders"][0].update(nativeVersion=version))
            self.refuse(lambda s: s["orders"][0]["nativeRows"]["head"].update(certified_version=version))
        for field, value in dict(status="APPROVED", del_flag="1", certified_terms_id="99",
            select_flow_id="99", payment_terms="PRIVATE", expected_arrival_date="2026-10-20").items():
            self.refuse(lambda s: s["orders"][0]["head"].update({field: value}))
        for field in ("received_quantity", "returned_quantity", "invoiced_qty"):
            for value in (None, "1", 0):
                self.refuse(lambda s: s["orders"][0]["lines"][0].update({field: value}))

    def test_quantities_and_ledger_full_chain_never_use_current_net(self):
        for name, field in (("lines", "order_quantity"), ("ledger", "covered_demand_base_qty"),
                            ("ledger", "occupied_base_qty"), ("ledger", "fulfilled_base_qty"),
                            ("ledger", "released_base_qty"), ("mrp", "covered_demand_base_qty")):
            self.refuse(lambda s: s["orders"][0][name][0].update({field: "1"}))
        self.refuse(lambda s: s["orders"][0]["requisition"]["lines"][0].update(apply_quantity="1"))
        for field, value in dict(allocation_key="0" * 64, allocation_status="CONSUMED", version=3,
            detail_id="99", order_line_id="99", requisition_line_id="99", release_reason="PRIVATE").items():
            self.refuse(lambda s: s["orders"][0]["ledger"][0].update({field: value}))

    def test_exact_21_causal_events_identity_amount_actor_and_time(self):
        for seq in range(3):
            for field, value in dict(event_key="PRIVATE", event_sequence=9, event_type="RELEASE",
                from_status="WRONG", to_status="WRONG", occupied_base_before="1", occupied_base_after="1",
                requisition_line_id="99", order_line_id="99", occurred_at="2026-09-01 00:00:00").items():
                self.refuse(lambda s: s["orders"][0]["events"][seq].update({field: value}))
        self.refuse(lambda s: s["orders"][0]["events"].pop())
        self.refuse(lambda s: s["orders"][0]["events"][0].update(actor_id="99"))
        self.refuse(lambda s: s["orders"][0]["events"].append(copy.deepcopy(s["orders"][0]["events"][0])))

    def test_create_event_must_have_occurred_within_original_action_execution(self):
        self.refuse(lambda s: s["orders"][0]["events"][0].update(occurred_at="2026-10-01 09:30:00"))

    def test_root_unit_native_projection_consistency(self):
        self.refuse(lambda s: s["orders"][1]["rootUnit"].update(conversion_verified_at="2026-09-01 00:00:00"))

    def test_same_requirement_cannot_bind_multiple_plans(self):
        def conflict(s):
            # Same native requirement identity cannot belong to two different plans.
            for i in (1, 4):
                p = s["orders"][i]["fingerprintEvidence"]
                p["requirements"][0]["requirement_id"] = "14000"
                p["mrpDetails"][0]["requirement_id"] = "14000"
                p["rootWorkOrder"]["source_id"] = "14000"
        self.refuse(conflict)


class MemoryPort(fixture.MemoryPort):
    """沿用真实内存事务模型，失败注入仅用于验证安全边界，不伪造证明。"""
    def __init__(self, snapshot):
        super().__init__(snapshot)
        self.post_drift = None
        self.commit_return = None
        self.begin_return = None
        self.rollback_return = None
        self.commit_interrupt = False
        self.cas_error_after = None

    def begin(self):
        super().begin()
        return self.begin_return

    def read_locked(self, expected_order_ids):
        result = super().read_locked(expected_order_ids)
        if self.post_drift and any(o["nativeVersion"] is not None for o in self.snapshot["orders"]):
            self.post_drift(result)
        return result

    def cas_set_birth_zero(self, sql, bindings):
        count = sum(isinstance(c, tuple) and c[0] == "cas" for c in self.calls)
        if self.cas_error_after is not None and count == self.cas_error_after:
            raise RuntimeError("PRIVATE CAS exception")
        return super().cas_set_birth_zero(sql, bindings)

    def commit(self):
        if self.commit_interrupt:
            raise KeyboardInterrupt("PRIVATE commit cancelled")
        super().commit()
        return self.commit_return

    def rollback(self):
        super().rollback()
        return self.rollback_return


class ReceiptExecutionTests(unittest.TestCase):
    def setUp(self):
        self.snapshot = valid_snapshot()
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.backup = self.directory / "backup.json"
        self.history = self.directory / "history"
        self.port = MemoryPort(self.snapshot)

    def executor(self, port=None):
        return restore.RestoreExecutor(port or self.port, history_dir=self.history)

    def execute(self, port=None):
        restore.write_backup(self.snapshot, IDS, self.backup)
        return self.executor(port).execute(self.snapshot, IDS, self.backup)

    def test_backup_exclusive_canonical_bytes_and_sha_fsynced(self):
        with patch.object(restore.os, "fsync", wraps=restore.os.fsync) as sync:
            result = restore.write_backup(self.snapshot, IDS, self.backup)
            self.assertTrue(self.backup.exists(), "完整证据备份必须在任何事务前真正排他持久化")
            self.assertGreaterEqual(sync.call_count, 1)
        self.assertEqual(fixture.snapshot_bytes(self.snapshot), self.backup.read_bytes())
        self.assertEqual(hashlib.sha256(self.backup.read_bytes()).hexdigest(), result["sha256"])
        self.assertEqual(result, restore.write_backup(self.snapshot, IDS, self.backup))

    def test_backup_conflict_never_overwrites_existing(self):
        self.backup.write_bytes(b"PRIVATE-original")
        with self.assertRaises(old.BackfillRefused):
            restore.write_backup(self.snapshot, IDS, self.backup)
        self.assertEqual(b"PRIVATE-original", self.backup.read_bytes())

    def test_backup_symlink_is_refused_without_following(self):
        with patch.object(Path, "is_symlink", return_value=True):
            with self.assertRaises(old.BackfillRefused):
                restore.write_backup(self.snapshot, IDS, self.backup)
        self.assertFalse(self.backup.exists())

    def test_backup_fsync_failure_refuses_no_port(self):
        with patch.object(restore.os, "fsync", side_effect=OSError("PRIVATE fsync")):
            with self.assertRaises(old.BackfillRefused) as caught:
                restore.write_backup(self.snapshot, IDS, self.backup)
        self.assertNotIn("PRIVATE", str(caught.exception))
        self.assertEqual([], self.port.calls)

    def test_all_seven_cas_one_transaction_preserves_every_other_byte(self):
        result = self.execute()
        self.assertEqual("COMMITTED", result["status"])
        self.assertEqual(7, result["changedRows"])
        self.assertEqual(POLICY, result["policy"])
        self.assertFalse(result["retryAllowed"])
        self.assertEqual(1, self.port.calls.count("begin"))
        self.assertEqual(1, self.port.calls.count("commit"))
        self.assertNotIn("rollback", self.port.calls)
        calls = [c for c in self.port.calls if isinstance(c, tuple) and c[0] == "cas"]
        self.assertEqual([("cas", SQL, (TENANT, oid)) for oid in IDS], calls)
        expected = copy.deepcopy(self.snapshot)
        for o in expected["orders"]:
            o["nativeVersion"] = o["nativeRows"]["head"]["certified_version"] = 0
        self.assertEqual(fixture.snapshot_bytes(expected), fixture.snapshot_bytes(self.port.snapshot))
        self.assertEqual(2, sum(isinstance(c, tuple) and c[0] == "read_locked" for c in self.port.calls))
        records = [json.loads(p.read_bytes()) for p in self.history.iterdir()]
        self.assertEqual({"PENDING", "COMMITTED"}, {r["status"] for r in records})

    def test_execution_requires_exact_backup_before_any_port(self):
        for data in (None, b"{}", fixture.snapshot_bytes(self.snapshot) + b" "):
            if data is not None:
                self.backup.write_bytes(data)
            with self.assertRaises(old.BackfillRefused):
                self.executor().execute(self.snapshot, IDS, self.backup)
            self.assertEqual([], self.port.calls)

    def test_invalid_evidence_no_history_no_port(self):
        self.snapshot["orders"][6]["head"]["status"] = "APPROVED"
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(self.snapshot, IDS, self.backup)
        self.assertEqual([], self.port.calls)
        self.assertFalse(self.history.exists())

    def test_history_reservation_fsync_before_begin_and_outcome(self):
        original_begin = self.port.begin
        def begin():
            pending = list(self.history.glob("*.attempt.json"))
            self.assertEqual(1, len(pending))
            record = json.loads(pending[0].read_bytes())
            self.assertEqual("PENDING", record["status"])
            self.assertEqual(restore.plan_restore(self.snapshot, IDS)["snapshotSha256"], record["snapshotSha256"])
            return original_begin()
        self.port.begin = begin
        with patch.object(restore.os, "fsync", wraps=restore.os.fsync) as sync:
            self.assertEqual("COMMITTED", self.execute()["status"])
            self.assertGreaterEqual(sync.call_count, 3)

    def test_persisted_history_refuses_fresh_executor_after_commit_or_unknown(self):
        self.port.commit_error = True
        result = self.execute()
        self.assertEqual("UNKNOWN", result["status"])
        calls = copy.deepcopy(self.port.calls)
        with self.assertRaises(old.BackfillRefused) as caught:
            self.executor().execute(self.snapshot, IDS, self.backup)
        self.assertEqual("HISTORY_NO_RETRY", caught.exception.code)
        self.assertEqual(calls, self.port.calls)

    def test_history_gate_cannot_be_bypassed_by_local_host_alias_or_backup_path(self):
        self.execute()
        other = copy.deepcopy(self.snapshot)
        other["environment"]["host"] = "127.0.0.1"
        path = self.directory / "other-backup.json"
        restore.write_backup(other, IDS, path)
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(other, tuple(reversed(IDS)), path)
        self.assertEqual(1, self.port.calls.count("begin"))

    def test_process_crash_pending_file_blocks_retry_without_trusting_contents(self):
        self.execute()
        outcome = next(self.history.glob("*.outcome.json"))
        outcome.unlink()  # 仅模拟崩溃丢失末次结果；意图仍在，不允许重试。
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(self.snapshot, IDS, self.backup)
        attempt = next(self.history.glob("*.attempt.json"))
        attempt.write_bytes(b"PRIVATE-corrupt-history")
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(self.snapshot, IDS, self.backup)
        self.assertEqual(1, self.port.calls.count("begin"))

    def test_history_fsync_failure_is_refused_before_port(self):
        restore.write_backup(self.snapshot, IDS, self.backup)
        with patch.object(restore.os, "fsync", side_effect=OSError("PRIVATE history")):
            with self.assertRaises(old.BackfillRefused):
                self.executor().execute(self.snapshot, IDS, self.backup)
        self.assertEqual([], self.port.calls)
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(self.snapshot, IDS, self.backup)

    def test_one_executor_instance_never_retries_even_after_rejected_backup(self):
        executor = self.executor()
        with self.assertRaises(old.BackfillRefused):
            executor.execute(self.snapshot, IDS, self.backup)
        restore.write_backup(self.snapshot, IDS, self.backup)
        with self.assertRaises(old.BackfillRefused):
            executor.execute(self.snapshot, IDS, self.backup)
        self.assertEqual([], self.port.calls)

    def test_lock_reread_full_receipt_native_metadata_absence_drift_rolls_back(self):
        changes = [lambda s: s["orders"][0]["nativeRows"]["head"].update(buyer_name="PRIVATE"),
            lambda s: s["orders"][0]["fingerprintEvidence"]["nativeAction"].update(approved_by="99"),
            lambda s: s["orders"][0]["fingerprintEvidence"]["nativeRun"].update(started_at="2026-10-01 06:00:00"),
            lambda s: s["orders"][0]["absence"]["arrival"].append({}),
            lambda s: s["metadata"]["orderAutomaticUpdates"].pop()]
        restore.write_backup(self.snapshot, IDS, self.backup)
        for i, change in enumerate(changes):
            port = MemoryPort(self.snapshot)
            port.drift = change
            executor = restore.RestoreExecutor(port, history_dir=self.directory / ("drift-history-" + str(i)))
            result = executor.execute(self.snapshot, IDS, self.backup)
            self.assertEqual("ROLLED_BACK", result["status"])
            self.assertFalse(any(isinstance(c, tuple) and c[0] == "cas" for c in port.calls))
            self.assertNotIn("commit", port.calls)
            self.assertEqual(self.snapshot, port.snapshot)

    def test_all_rowcounts_except_exact_int_one_roll_back_entire_batch(self):
        restore.write_backup(self.snapshot, IDS, self.backup)
        for i, count in enumerate((0, 2, -1, True, "1", 1.0, None)):
            port = MemoryPort(self.snapshot)
            port.rowcounts = [1, 1, count]
            result = restore.RestoreExecutor(port, history_dir=self.directory / ("rows-" + str(i))).execute(self.snapshot, IDS, self.backup)
            self.assertEqual("ROLLED_BACK", result["status"])
            self.assertEqual("ROWCOUNT_NOT_ONE", result["reason"])
            self.assertEqual(self.snapshot, port.snapshot)
            self.assertNotIn("commit", port.calls)

    def test_partial_cas_exception_rolls_back_and_persists_no_retry(self):
        self.port.cas_error_after = 3
        result = self.execute()
        self.assertEqual("ROLLED_BACK", result["status"])
        self.assertEqual("PORT_EXCEPTION", result["reason"])
        self.assertEqual(self.snapshot, self.port.snapshot)
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(self.snapshot, IDS, self.backup)

    def test_postwrite_every_native_field_chain_and_timestamp_drift_rolls_back(self):
        restore.write_backup(self.snapshot, IDS, self.backup)
        changes = []
        for part in ("head", "lines"):
            row = self.snapshot["orders"][0]["nativeRows"][part]
            row = row if part == "head" else row[0]
            for key in row:
                if key != "certified_version":
                    def change(s, part=part, key=key):
                        native = s["orders"][0]["nativeRows"][part]
                        native = native if part == "head" else native[0]
                        native[key] = "PRIVATE-drift"
                    changes.append(change)
        changes.extend([lambda s: s["orders"][0]["ledger"][0].update(version=3),
            lambda s: s["orders"][0]["fingerprintEvidence"]["nativeAction"].update(result_json="{}"),
            lambda s: s["orders"][0]["absence"]["birth"].append({})])
        for i, change in enumerate(changes):
            port = MemoryPort(self.snapshot)
            port.post_drift = change
            result = restore.RestoreExecutor(port, history_dir=self.directory / ("post-" + str(i))).execute(self.snapshot, IDS, self.backup)
            self.assertEqual("ROLLED_BACK", result["status"])
            self.assertEqual("POSTWRITE_DRIFT", result["reason"])
            self.assertEqual(self.snapshot, port.snapshot)
            self.assertNotIn("commit", port.calls)

    def test_commit_exception_unknown_never_rollback_or_retry(self):
        self.port.commit_error = True
        result = self.execute()
        self.assertEqual("UNKNOWN", result["status"])
        self.assertEqual("COMMIT_OUTCOME_UNKNOWN", result["reason"])
        self.assertNotIn("rollback", self.port.calls)
        self.assertNotIn("PRIVATE", json.dumps(result))
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(self.snapshot, IDS, self.backup)

    def test_commit_non_none_or_cancellation_also_unknown(self):
        restore.write_backup(self.snapshot, IDS, self.backup)
        for i, value in enumerate((True, False, "COMMITTED", 7)):
            port = MemoryPort(self.snapshot)
            port.commit_return = value
            result = restore.RestoreExecutor(port, history_dir=self.directory / ("commit-" + str(i))).execute(self.snapshot, IDS, self.backup)
            self.assertEqual("UNKNOWN", result["status"])
            self.assertNotIn("rollback", port.calls)
        port = MemoryPort(self.snapshot)
        port.commit_interrupt = True
        result = restore.RestoreExecutor(port, history_dir=self.directory / "interrupt").execute(self.snapshot, IDS, self.backup)
        self.assertEqual("UNKNOWN", result["status"])
        self.assertNotIn("rollback", port.calls)

    def test_begin_read_rollback_failures_sanitized(self):
        restore.write_backup(self.snapshot, IDS, self.backup)
        for i, field in enumerate(("begin_error", "read_error", "begin_return", "rollback_error", "rollback_return")):
            port = MemoryPort(self.snapshot)
            setattr(port, field, True)
            if field.startswith("rollback"):
                port.rowcounts = [1, 0]
            result = restore.RestoreExecutor(port, history_dir=self.directory / ("port-" + str(i))).execute(self.snapshot, IDS, self.backup)
            self.assertEqual("UNKNOWN" if field.startswith("rollback") else "ROLLED_BACK", result["status"])
            self.assertNotIn("PRIVATE", json.dumps(result))

    def test_outcome_fsync_failure_after_commit_is_unknown_and_attempt_blocks_retry(self):
        restore.write_backup(self.snapshot, IDS, self.backup)
        original = restore.os.fsync
        calls = 0
        def fsync(fd):
            nonlocal calls
            calls += 1
            if calls == 2:
                raise OSError("PRIVATE outcome fsync")
            return original(fd)
        with patch.object(restore.os, "fsync", side_effect=fsync):
            result = self.executor().execute(self.snapshot, IDS, self.backup)
        self.assertEqual("UNKNOWN", result["status"])
        self.assertNotIn("rollback", self.port.calls)
        with self.assertRaises(old.BackfillRefused):
            self.executor().execute(self.snapshot, IDS, self.backup)

    def test_untrusted_port_exception_reason_cannot_escape_or_skip_rollback(self):
        class UnsafeReason(old.BackfillRefused):
            def __init__(self):
                self.code = ["PRIVATE-unhashable"]
        def bad_read(ids):
            raise UnsafeReason()
        self.port.read_locked = bad_read
        result = self.execute()
        self.assertEqual("ROLLED_BACK", result["status"])
        self.assertEqual("PORT_EXCEPTION", result["reason"])
        self.assertNotIn("PRIVATE", json.dumps(result))

    def test_postwrite_bool_zero_is_not_integer_birth_zero(self):
        self.port.post_drift = lambda s: s["orders"][0].update(nativeVersion=False)
        result = self.execute()
        self.assertEqual("ROLLED_BACK", result["status"])
        self.assertEqual("POSTWRITE_DRIFT", result["reason"])

    def test_cli_only_offline_plan_exclusive_backup_no_execute_flag(self):
        snapshot_path = self.directory / "snapshot.json"
        snapshot_path.write_bytes(fixture.snapshot_bytes(self.snapshot))
        argv = ["--snapshot", str(snapshot_path), "--backup", str(self.backup)]
        for oid in IDS:
            argv.extend(("--expected-order-id", oid))
        out = io.StringIO()
        with redirect_stdout(out):
            code = restore.main(argv)
        self.assertEqual(0, code)
        self.assertEqual("PLANNED", json.loads(out.getvalue())["status"])
        self.assertEqual(fixture.snapshot_bytes(self.snapshot), self.backup.read_bytes())
        self.assertFalse(self.history.exists())
        with redirect_stdout(io.StringIO()), patch("sys.stderr", new=io.StringIO()):
            with self.assertRaises(SystemExit):
                restore.main(argv + ["--execute"])

    def test_cli_duplicate_keys_invalid_json_and_unknown_scope_refused(self):
        snapshot_path = self.directory / "snapshot.json"
        for raw in (b'{"orders":[],"orders":[]}', b"PRIVATE", b"{}"):
            snapshot_path.write_bytes(raw)
            out = io.StringIO()
            with redirect_stdout(out):
                code = restore.main(["--snapshot", str(snapshot_path), "--backup", str(self.backup)])
            self.assertEqual(2, code)
            self.assertEqual("REFUSED", json.loads(out.getvalue())["status"])
            self.assertNotIn("PRIVATE", out.getvalue())
            self.assertFalse(self.backup.exists())


if __name__ == "__main__":
    unittest.main()
