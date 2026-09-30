"""离线合成测试；按用户报告形状构造的桩仅复制用户事实，并非 API 读回结果。

任何测试值都不是独立核验的实时系统事实，也不是业务数据夹具。
"""
from copy import deepcopy
import ast
import json
from pathlib import Path
import re
import unittest
from unittest.mock import patch

try:
    import formal_manufacturing_fixture as factory
except ModuleNotFoundError as exc:
    if exc.name != "formal_manufacturing_fixture":
        raise
    factory = None

ROOT = Path(__file__).resolve().parents[4]
DAY = "2026-09-29"
COMPONENTS = ("bolt", "nut", "washer", "box")


def readings():
    units = {
        "finished": {"uomId": "41", "uomName": "套", "isBase": 0, "primaryId": "26",
                     "primaryName": "件", "conversionRate": "1", "category": "数量", "status": 1},
        "piece": {"uomId": "26", "uomName": "件", "isBase": 1, "primaryId": None,
                  "conversionRate": None, "category": "数量", "status": 1},
    }
    # 角色选择不同，但使用同一条合成的 /measure/all VO 行。
    units["box"] = dict(units["piece"])
    categories = {
        role: {"categoryId": str(61 + i), "categoryName": name, "status": 1}
        for i, (role, name) in enumerate((("finished", "安装套件"), ("piece", "紧固件"), ("box", "包装材料")))
    }
    processes = {
        role: {"processId": str(9007199254741001 + i), "processCode": code,
               "processName": name, "processCategory": category, "status": 1,
               "workstationId": None, "standardHours": "0.0125", "setupHours": "0.25",
               "laborCount": 1, "machineCount": 0, "isOutsource": 0,
               "qualityRequired": int(role == "quality"), "isKeyProcess": 0}
        for i, (role, code, name, category) in enumerate((
            ("assembly", "ASM", "套件装配", "装配"),
            ("packaging", "PKG", "独立包装", "通用"),
            ("quality", "QC", "成品质检", "通用")))
    }
    workstations = {
        role: {"workstationId": str(81 + i), "workstationName": name,
               "workshopId": str(91 + i), "status": 1}
        for i, (role, name) in enumerate((
            ("assembly", "装配线A"), ("packaging", "包装线A"), ("quality", "质检台A")))
    }
    workshops = {
        role: {"workshopId": str(91 + i), "workshopName": name, "status": 1}
        for i, (role, name) in enumerate((
            ("assembly", "装配车间"), ("packaging", "包装车间"), ("quality", "质检车间")))
    }
    rules = [{"ruleId": "91", "ruleCode": "MATERIAL_CODE", "enableFlag": "Y"},
             {"ruleId": "92", "ruleCode": "ROUTING_CODE", "enableFlag": "Y"}]
    return {"units": units, "unit_catalog": [units["finished"], units["piece"]], "categories": categories,
            "processes": processes, "workstations": workstations, "workshops": workshops,
            "numbering_rules": rules, "dates": {event: DAY for event in
             ("order", "requisition", "receipt", "issue", "completion")}, "as_of_date": DAY}


def reported_shape_stub():
    """仅重放用户报告的数据形状；此代码不查询 API 或数据库。"""
    data = readings()
    piece = {"uomId": "26", "uomName": "件", "isBase": 1, "primaryId": None,
             "conversionRate": None, "category": "数量", "status": 1}
    finished = {"uomId": "27", "uomName": "套", "isBase": 0, "primaryId": "26",
                "primaryName": "件", "conversionRate": "1", "category": "数量", "status": 1}
    incomplete_box = {"uomId": "56", "uomName": "个", "isBase": 0, "primaryId": None,
                      "conversionRate": "1", "category": None, "status": 1}
    data["units"] = {"finished": finished, "piece": piece, "box": incomplete_box}
    data.pop("workstations")
    data.pop("workshops")
    data["unit_catalog"] = [finished, piece, incomplete_box]
    data["numbering_rules"] = [{"ruleId": "236", "ruleCode": "MATERIAL_CODE", "enableFlag": "Y"},
                               {"ruleId": "301", "ruleCode": "ROUTING_CODE", "enableFlag": "Y"}]
    for role, code, name, category, process_id, hours in (
        ("assembly", "ASM", "装配", "装配", "44", "0.6"),
        ("packaging", "PKG", "包装", "通用", "55", "0.15"),
        ("quality", "QC", "质检", "通用", "54", "0.2"),
    ):
        data["processes"][role].update(processCode=code, processName=name, processCategory=category,
                                        processId=process_id, workstationId=None, standardHours=hours,
                                        setupHours="0.1")
    return data


def created_records(plan):
    rows = {}
    for i, request in enumerate(plan["requests"][:5]):
        row = deepcopy(request["body"])
        row.update(materialId=str(9007199254742001 + i), materialCode=f"readback-material-{i}")
        rows[request["key"]] = row
    finished_id = rows["material.finished"]["materialId"]
    rows["routing"] = {"routingId": "9007199254742101", "routingCode": "readback-routing",
                       "routingName": plan["requests"][5]["body"]["routingName"],
                       "materialId": finished_id, "status": 1}
    rows["bom"] = {"bomId": "9007199254742201", "bomName": plan["requests"][6]["body"]["bomName"],
                   "parentMaterialId": finished_id, "routingId": rows["routing"]["routingId"],
                   "sourceType": "PROCESS", "status": 1}
    return rows


def baseline():
    return {"material": ("1", "2"), "routing": ("1",), "bom": ("1",)}


def moq_input(plan):
    new = created_records(plan)
    materials = {}
    reads = {}
    for component in COMPONENTS:
        row = deepcopy(new[f"material.{component}"])
        row["baseUomName"] = "件"
        materials[component] = row
        reads[component] = {
            "readDate": DAY, "quantityUomId": row["purchaseUomId"], "unitBasisConfirmed": True,
            "row": {"relationId": str(301 + len(reads)), "materialId": row["materialId"],
                    "vendorId": "401", "minOrderQty": "400.0000", "status": 1,
                    "effectiveDate": "2026-09-01", "expiryDate": None,
                    "price": "12.3456", "leadTime": 30},
        }
    return {"moq_reads": reads, "component_readbacks": materials,
            "baseline_material_ids": baseline()["material"], "as_of_date": DAY}


class FormalFixtureContractTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(factory, "offline formal_manufacturing_fixture factory is not implemented")
        self.reads = readings()
        self.plan = factory.build_fixture(**self.reads)

    def test_synthetic_measure_all_unit_26_can_bind_piece_and_box(self):
        data = reported_shape_stub()  # 合成快照，绝非 API 或数据库查询结果。
        data["units"]["box"] = dict(data["units"]["piece"])
        plan = factory.build_fixture(**data)
        bodies = {request["key"]: request["body"] for request in plan["requests"]}
        for component in ("bolt", "nut", "washer", "box"):
            self.assertEqual(bodies[f"material.{component}"]["baseUomId"], "26")
        self.assertIn("独立纸盒1件", bodies["material.finished"]["materialSpec"])
        self.assertIn("每套1件", bodies["material.box"]["materialSpec"])

    def test_unit_26_requires_exact_measure_all_row_before_any_request(self):
        for mutate in (
            lambda d: d["unit_catalog"].pop(1),
            lambda d: d["units"]["box"].update(status=0),
            lambda d: d["units"]["box"].update(primaryId="999"),
            lambda d: d["unit_catalog"].append(dict(d["unit_catalog"][1])),
        ):
            data = readings()
            mutate(data)
            with self.subTest(mutate=mutate), patch.object(
                factory, "_request", side_effect=AssertionError("must not build requests")
            ), self.assertRaises(factory.FixtureContractError):
                factory.build_fixture(**data)

    def test_other_consistent_piece_unit_or_wrong_quantity_category_is_refused(self):
        for changed_id, category in (("57", "数量"), ("26", "重量")):
            data = readings()
            data["units"]["finished"]["primaryId"] = changed_id
            data["units"]["piece"].update(uomId=changed_id, category=category)
            data["units"]["box"].update(uomId=changed_id, category=category)
            with self.subTest(changed_id=changed_id, category=category):
                with self.assertRaises(factory.FixtureContractError):
                    factory.build_fixture(**data)

    def test_legacy_broken_56_in_catalog_does_not_block_correct_26_selection(self):
        data = readings()
        data["unit_catalog"].append({"uomId": "56", "uomName": "个", "isBase": 0,
                                     "primaryId": None, "category": None, "status": 1})
        plan = factory.build_fixture(**data)
        self.assertEqual(plan["requests"][4]["body"]["baseUomId"], "26")

    def test_reported_structure_with_unlinked_individual_unit_fails_before_request(self):
        # 仅为用户报告的数据形状，不是新取得的证据，也不是 API 或请求模板。
        data = reported_shape_stub()
        with patch.object(factory, "_request", side_effect=AssertionError("must not build requests")):
            with self.assertRaisesRegex(factory.FixtureContractError, r"unit\.box"):
                factory.build_fixture(**data)
        self.assertIsNone(data["units"]["box"]["primaryId"])
        self.assertEqual(data["units"]["box"]["uomId"], "56")

    def test_incomplete_uom_56_is_blocked_until_primary_is_known(self):
        data = reported_shape_stub()
        with self.assertRaises(factory.FixtureContractError):
            factory.build_fixture(**data)

    def test_derived_unit_primary_must_resolve_to_single_active_same_category_base(self):
        for mutation in (lambda d: d["units"]["finished"].update(primaryId=None),
                         lambda d: d["units"]["finished"].update(primaryId="999"),
                         lambda d: d["unit_catalog"][1].update(status=0),
                         lambda d: d["unit_catalog"][1].update(isBase=0),
                         lambda d: d["units"]["finished"].update(category="重量"),
                         lambda d: d["units"]["finished"].update(primaryName="千克"),
                         lambda d: d["unit_catalog"].append(dict(d["unit_catalog"][1]))):
            data = readings()
            mutation(data)
            with self.subTest(mutation=mutation), self.assertRaises(factory.FixtureContractError):
                factory.build_fixture(**data)

    def test_numbering_accepts_Y_and_rejects_inactive_or_unknown_flags(self):
        for flag in ("1", "0", "N", None, ""):
            data = readings()
            data["numbering_rules"][0]["enableFlag"] = flag
            with self.subTest(flag=flag), self.assertRaises(factory.FixtureContractError):
                factory.build_fixture(**data)

    def test_actual_process_identity_checked_by_code_name_and_chinese_category(self):
        for change in ({"processCode": "ASM-X"}, {"processName": "装配外协"},
                       {"processCategory": "ASSEMBLY"}, {"processCategory": "通用"},
                       {"processName": "测试质检"}):
            data = readings()
            role = "quality" if change == {"processName": "测试质检"} else "assembly"
            data["processes"][role].update(change)
            with self.subTest(change=change), self.assertRaises(factory.FixtureContractError):
                factory.build_fixture(**data)

    def test_unverified_or_test_workshop_station_is_blocked_even_with_real_id(self):
        data = readings()
        data["workstations"] = {
            "assembly": {"workstationId": "46", "workstationName": "装配线A", "workshopId": "12", "status": 1},
            "packaging": {"workstationId": "53", "workstationName": "包装线A", "workshopId": "14", "status": 1},
            "quality": {"workstationId": "52", "workstationName": "精度检验台", "workshopId": "13", "status": 1},
        }
        data["workshops"] = {
            "assembly": {"workshopId": "12", "workshopName": "装配车间", "status": 1},
            "packaging": {"workshopId": "14", "workshopName": "包装车间", "status": 1},
            "quality": {"workshopId": "13", "workshopName": "测试车间", "status": 1},
        }
        with self.assertRaises(factory.FixtureContractError):
            factory.build_fixture(**data)

    def test_synthetic_separate_station_rows_never_claim_process_defaults(self):
        data = readings()  # 合成的有效样本，并非观测到的质量检验（QC）可用性。
        self.assertTrue(all(row["workstationId"] is None for row in data["processes"].values()))
        plan = factory.build_fixture(**data)
        self.assertEqual(plan["workstationBindingStatus"], "SEPARATE_READBACK_BOUND")
        self.assertTrue(plan["masterDataReady"])
        self.assertFalse(plan["readyForExecution"])
        self.assertIn("new_component_moq", plan["blockers"])
        self.assertEqual([s["workstationId"] for s in plan["requests"][5]["body"]["steps"]],
                         ["81", "82", "83"])
        self.assertFalse(plan["evidence"]["actualCapacityEvidence"])

    def test_station_workshop_binding_must_match_independent_readbacks(self):
        for change in ({"workshopId": "999"}, {"workstationName": "临时装配线"},
                       {"status": 0}, {"workstationId": None}):
            data = readings()
            data["workstations"]["assembly"].update(change)
            with self.subTest(change=change), self.assertRaises(factory.FixtureContractError):
                factory.build_fixture(**data)
        data = readings()
        data["workshops"]["assembly"].update(status=0)
        with self.assertRaises(factory.FixtureContractError):
            factory.build_fixture(**data)
        data = readings()
        data["workshops"].pop("quality")
        with self.assertRaises(factory.FixtureContractError):
            factory.build_fixture(**data)

    def test_box_moq_requires_piece_unit_not_individual_unit(self):
        data = moq_input(self.plan)
        data["component_readbacks"]["box"]["baseUomName"] = "个"
        with self.assertRaises(factory.FixtureContractError):
            factory.decide_lot_quantity(**data)

    def test_box_cannot_be_rebound_to_other_piece_named_unit(self):
        data = readings()
        other = dict(data["units"]["piece"], uomId="57")
        data["units"]["box"] = other
        data["unit_catalog"].append(other)
        with self.assertRaises(factory.FixtureContractError):
            factory.build_fixture(**data)

    def test_box_and_finished_spec_are_still_one_individual_box(self):
        bodies = {r["key"]: r["body"] for r in self.plan["requests"]}
        self.assertIn("独立纸盒1件", bodies["material.finished"]["materialSpec"])
        self.assertIn("每套1件", bodies["material.box"]["materialSpec"])
        for role in ("bolt", "nut", "washer", "box"):
            body = bodies[f"material.{role}"]
            self.assertEqual({body[field] for field in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId")}, {"26"})
        self.assertEqual(bodies["material.box"]["baseUomId"], self.reads["units"]["piece"]["uomId"])
        self.assertEqual(self.reads["units"]["box"]["uomName"], "件")

    def test_default_is_offline_non_executable_and_has_no_quantity_guess(self):
        self.assertFalse(self.plan["writeEnabled"])
        self.assertTrue(self.plan["requiresSeparateAuthorization"])
        self.assertEqual(self.plan["quantityStatus"], "AWAITING_NEW_COMPONENT_MOQ_READBACK")
        self.assertIsNone(self.plan["finishedQuantity"])
        self.assertEqual(len(self.plan["requests"]), 11)
        json.dumps(self.plan, ensure_ascii=False, allow_nan=False)

    def test_normal_names_processed_parent_and_four_purchased_components(self):
        bodies = {r["key"]: r["body"] for r in self.plan["requests"]}
        finished = bodies["material.finished"]
        self.assertEqual(finished["materialName"], "M12×60六角螺栓安装套件（独立盒装）")
        self.assertEqual(finished["sourceType"], "PROCESS")
        self.assertEqual(finished["baseUomId"], self.reads["units"]["finished"]["uomId"])
        self.assertEqual(finished["materialType"], 3)
        for component in COMPONENTS:
            body = bodies[f"material.{component}"]
            self.assertEqual(body["sourceType"], "PROCUREMENT")
            self.assertEqual(body["isPhantom"], 0)
            self.assertEqual(len({body[k] for k in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId")}), 1)
        for body in bodies.values():
            for field in ("materialName", "bomName", "routingName"):
                if field in body:
                    self.assertNotRegex(body[field], r"(?i)测试|演示|临时|TEST|DEMO|MOCK")
            self.assertNotIn("referencePrice", body)
            self.assertNotIn("standardCost", body)

    def test_bom_is_independent_one_piece_per_kit_and_no_child_bom(self):
        header = self.plan["requests"][6]["body"]
        self.assertEqual(header["sourceType"], "PROCESS")
        self.assertEqual(header["bomType"], "MBOM")
        self.assertEqual(header["parentMaterialId"], "$new:material.finished.materialId")
        self.assertEqual(header["routingId"], "$new:routing.routingId")
        self.assertNotIn("bomCode", header)
        for i, component in enumerate(COMPONENTS):
            body = self.plan["requests"][7 + i]["body"]
            self.assertEqual(body["bomId"], "$new:bom.bomId")
            self.assertEqual(body["childMaterialId"], f"$new:material.{component}.materialId")
            self.assertEqual(body["quantityPerParent"], 1)
            self.assertEqual(body["lossRate"], 0)
            self.assertEqual(body["sourceType"], "PROCUREMENT")
            self.assertNotIn("supplyType", body)
            self.assertEqual(body["operationStep"], 20 if component == "box" else 10)
            self.assertNotIn("childBomId", body)
        self.assertNotIn("0.542923", json.dumps(self.plan))

    def test_purchase_source_does_not_guess_subcontract_supply_semantics(self):
        for request in self.plan["requests"][7:]:
            self.assertNotIn("supplyType", request["body"])

    def test_server_numbering_uses_blank_not_a_fake_preview_or_code(self):
        for request in self.plan["requests"][:5]:
            self.assertEqual(request["body"]["materialCode"], "")
        self.assertEqual(self.plan["requests"][5]["body"]["routingCode"], "")
        self.assertEqual(set(self.plan["numberingRules"]), {"MATERIAL_CODE", "ROUTING_CODE"})
        self.assertTrue(all(r["method"] == "POST" for r in self.plan["requests"]))
        self.assertNotIn("/system/autocode/get", json.dumps(self.plan["requests"]))

    def test_explicit_read_processes_preserve_long_ids_hours_and_sequence(self):
        route = self.plan["requests"][5]["body"]
        self.assertEqual([step["sequenceNum"] for step in route["steps"]], [10, 20, 30])
        for step, role in zip(route["steps"], ("assembly", "packaging", "quality")):
            read = self.reads["processes"][role]
            self.assertEqual(step["processId"], read["processId"])
            self.assertEqual(step["workstationId"], self.reads["workstations"][role]["workstationId"])
            self.assertEqual(step["standardHours"], read["standardHours"])
            self.assertEqual(step["setupHours"], read["setupHours"])
        self.assertEqual(route["steps"][2]["qualityRequired"], 1)
        self.assertNotIn("totalHours", route)
        self.assertNotIn("laborRate", json.dumps(route))

    def test_missing_inactive_wrong_category_or_duplicate_process_is_blocked(self):
        for mutation in (lambda d: d["processes"].pop("packaging"),
                         lambda d: d["processes"]["assembly"].update(status=0),
                         lambda d: d["processes"]["assembly"].update(processCategory="PROCESSING"),
                         lambda d: d["processes"]["packaging"].update(processId=d["processes"]["assembly"]["processId"]),
                         lambda d: d["processes"]["quality"].update(qualityRequired=0),
                         lambda d: d["processes"]["assembly"].update(isOutsource=1)):
            with self.subTest(mutation=mutation):
                data = readings()
                mutation(data)
                with self.assertRaises(factory.FixtureContractError):
                    factory.build_fixture(**data)

    def test_no_guessed_hours_workstation_or_synthetic_process_name(self):
        for patch_row in ({"standardHours": None}, {"standardHours": "0"},
                          {"standardHours": "NaN"}, {"standardHours": "-1"},
                          {"standardHours": 0.0125}, {"setupHours": None},
                          {"setupHours": "-1"},
                          {"processName": "TEST-组装"}):
            with self.subTest(patch=patch_row):
                data = readings()
                data["processes"]["assembly"].update(patch_row)
                with self.assertRaises(factory.FixtureContractError):
                    factory.build_fixture(**data)

    def test_wrong_inactive_or_converted_discrete_unit_is_blocked(self):
        for role, updates in (("finished", {"uomName": "个"}), ("piece", {"uomName": "kg"}),
                              ("box", {"uomName": "套"}), ("piece", {"status": 0}),
                              ("piece", {"conversionRate": "0.5"}), ("piece", {"isBase": 0}),
                              ("piece", {"uomId": True}), ("box", {"uomId": "0"})):
            with self.subTest(role=role, updates=updates):
                data = readings()
                data["units"][role].update(updates)
                with self.assertRaises(factory.FixtureContractError):
                    factory.build_fixture(**data)

    def test_unit_26_cannot_be_relabelled_as_individual_ge(self):
        data = readings()
        data["units"]["piece"]["uomName"] = "个"
        data["units"]["box"]["uomName"] = "个"
        data["units"]["finished"]["primaryName"] = "个"
        with self.assertRaisesRegex(factory.FixtureContractError, r"unit\.piece"):
            factory.build_fixture(**data)

    def test_categories_are_required_by_actual_ddl(self):
        for updates in ({"categoryId": None}, {"status": 0}, {"categoryName": "测试分类"}):
            data = readings()
            data["categories"]["finished"].update(updates)
            with self.assertRaises(factory.FixtureContractError):
                factory.build_fixture(**data)

    def test_missing_disabled_or_ambiguous_numbering_rules_are_blocked(self):
        for mutate in (lambda d: d["numbering_rules"].pop(),
                       lambda d: d["numbering_rules"][0].update(enableFlag="0"),
                       lambda d: d["numbering_rules"].append(deepcopy(d["numbering_rules"][0]))):
            data = readings()
            mutate(data)
            with self.assertRaises(factory.FixtureContractError):
                factory.build_fixture(**data)

    def test_same_day_dates_only_mean_software_sample(self):
        self.assertEqual(set(self.plan["dates"].values()), {DAY})
        for key in ("physicalAcceptance", "actualReceiptEvidence", "actualCapacityEvidence"):
            self.assertFalse(self.plan["evidence"][key])
        self.assertEqual(self.plan["evidence"]["level"], "SOFTWARE_SAMPLE_ONLY")

    def test_chronological_chain_accepts_equalities_and_earlier_days(self):
        self.reads["dates"].update(order="2026-09-25", requisition="2026-09-26",
                                 receipt="2026-09-27", issue="2026-09-28")
        self.assertEqual(factory.build_fixture(**self.reads)["dates"]["completion"], DAY)

    def test_every_adjacent_reversed_date_is_blocked(self):
        for earlier, later in zip(("order", "requisition", "receipt", "issue"),
                                   ("requisition", "receipt", "issue", "completion")):
            data = readings()
            data["dates"][later] = "2026-09-28"
            with self.subTest(earlier=earlier, later=later):
                with self.assertRaises(factory.FixtureContractError):
                    factory.build_fixture(**data)

    def test_future_receipt_or_completion_never_becomes_actual_evidence(self):
        self.reads["dates"] = {key: "2026-09-30" for key in self.reads["dates"]}
        with self.assertRaises(factory.FixtureContractError):
            factory.build_fixture(**self.reads)

    def test_missing_invalid_or_time_valued_sample_date_is_blocked(self):
        for value in (None, "2026-02-30", "2026-9-29", "2026-09-29T00:00:00", "today"):
            data = readings()
            data["dates"]["receipt"] = value
            with self.subTest(value=value):
                with self.assertRaises(factory.FixtureContractError):
                    factory.build_fixture(**data)

    def test_templates_cannot_pass_request_guard(self):
        for request in self.plan["requests"][5:]:
            with self.assertRaises(factory.FixtureContractError):
                factory.assert_request_ready(request)

    def test_resolver_uses_verified_new_readbacks_and_never_mutates_input(self):
        rows = created_records(self.plan)
        before = deepcopy(self.plan)
        for request in self.plan["requests"]:
            resolved = factory.resolve_new_references(request, created_records=rows, preexisting_ids=baseline())
            factory.assert_request_ready(resolved)
            self.assertNotIn("$new:", json.dumps(resolved))
        self.assertEqual(self.plan, before)

    def test_existing_parent_or_bom_cannot_be_rebound_under_new_name(self):
        for key, id_key in (("material.finished", "materialId"), ("routing", "routingId"), ("bom", "bomId")):
            rows = created_records(self.plan)
            rows[key][id_key] = "1"
            request = self.plan["requests"][5 if key == "material.finished" else 6 if key == "routing" else 7]
            with self.subTest(key=key):
                with self.assertRaises(factory.FixtureContractError):
                    factory.resolve_new_references(request, created_records=rows, preexisting_ids=baseline())

    def test_resolver_blocks_missing_baseline_missing_row_and_wrong_readback_identity(self):
        for mutate_rows, existing in ((lambda r: r.pop("material.finished"), baseline()),
                                       (lambda r: r["material.finished"].update(materialName="旧安装包"), baseline()),
                                       (lambda r: r["material.finished"].update(sourceType="PROCUREMENT"), baseline()),
                                       (lambda r: None, {})):
            rows = created_records(self.plan)
            mutate_rows(rows)
            with self.assertRaises(factory.FixtureContractError):
                factory.resolve_new_references(self.plan["requests"][5], created_records=rows, preexisting_ids=existing)

    def test_resolver_refuses_numeric_new_reference_bypass(self):
        rows = created_records(self.plan)
        for index, field in ((5, "materialId"), (6, "parentMaterialId"),
                             (6, "routingId"), (7, "bomId"), (7, "childMaterialId")):
            request = deepcopy(self.plan["requests"][index])
            request["body"][field] = "1"
            with self.subTest(index=index, field=field):
                with self.assertRaises(factory.FixtureContractError):
                    factory.resolve_new_references(request, created_records=rows, preexisting_ids=baseline())

    def test_resolver_refuses_swapped_component_role(self):
        request = deepcopy(self.plan["requests"][7])
        request["body"]["childMaterialId"] = "$new:material.nut.materialId"
        with self.assertRaises(factory.FixtureContractError):
            factory.resolve_new_references(request, created_records=created_records(self.plan), preexisting_ids=baseline())

    def test_resolver_refuses_duplicate_new_material_ids(self):
        rows = created_records(self.plan)
        rows["material.nut"]["materialId"] = rows["material.bolt"]["materialId"]
        with self.assertRaises(factory.FixtureContractError):
            factory.resolve_new_references(self.plan["requests"][7], created_records=rows, preexisting_ids=baseline())

    def test_guard_refuses_swapping_hardware_or_box_unit_to_legacy_56(self):
        for index in (1, 4):
            request = deepcopy(self.plan["requests"][index])
            for field in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId"):
                request["body"][field] = "56"
            with self.subTest(index=index), self.assertRaises(factory.FixtureContractError):
                factory.assert_request_ready(request)

    def test_created_component_readback_refuses_unit_56_even_when_fields_agree(self):
        rows = created_records(self.plan)
        for field in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId"):
            rows["material.box"][field] = "56"
        with self.assertRaises(factory.FixtureContractError):
            factory.resolve_new_references(self.plan["requests"][7], created_records=rows, preexisting_ids=baseline())

    def test_guard_refuses_nonblank_business_code_even_if_it_looks_like_server_preview(self):
        for index, field in ((0, "materialCode"), (5, "routingCode")):
            rows = created_records(self.plan)
            request = factory.resolve_new_references(self.plan["requests"][index], created_records=rows, preexisting_ids=baseline())
            request["body"][field] = "PREVIEW-NOT-RESERVED"
            with self.assertRaises(factory.FixtureContractError):
                factory.assert_request_ready(request)

    def test_guard_rejects_updates_deletes_generator_and_create_with_existing_primary_id(self):
        for method, path, body_update in (("PUT", "/erp/bd/material", {}),
                                           ("DELETE", "/erp/bd/material/1", {}),
                                           ("GET", "/system/autocode/get/MATERIAL_CODE", {}),
                                           ("POST", "/erp/bd/material", {"materialId": "1"}),
                                           ("POST", "/erp/bd/material", {"referencePrice": "0.01"})):
            request = deepcopy(self.plan["requests"][0])
            request.update(method=method, path=path)
            request["body"].update(body_update)
            with self.assertRaises(factory.FixtureContractError):
                factory.assert_request_ready(request)

    def test_guard_rejects_fractional_historical_consumption_child_bom_and_loss(self):
        rows = created_records(self.plan)
        original = factory.resolve_new_references(self.plan["requests"][10], created_records=rows, preexisting_ids=baseline())
        for updates in ({"quantityPerParent": "0.542923"}, {"lossRate": "1"},
                         {"childBomId": "1"}, {"sourceType": "PROCESS"}, {"operationStep": 15}):
            request = deepcopy(original)
            request["body"].update(updates)
            with self.assertRaises(factory.FixtureContractError):
                factory.assert_request_ready(request)

    def test_moq_reads_select_600_or_1000_without_inventing_a_quote(self):
        data = moq_input(self.plan)
        result = factory.decide_lot_quantity(**data)
        self.assertEqual(result["finishedQuantity"], 600)
        self.assertEqual(set(result["componentDemand"].values()), {600})
        data["moq_reads"]["box"]["row"]["minOrderQty"] = "1000"
        result = factory.decide_lot_quantity(**data)
        self.assertEqual(result["finishedQuantity"], 1000)
        self.assertNotIn("price", json.dumps(result))
        self.assertNotIn("leadTime", json.dumps(result))
        self.assertFalse(result["actualReceiptEvidence"])
        self.assertTrue(result["historicalMrpUnchanged"])

    def test_moq_rejects_legacy_unit_even_when_unit_basis_is_confirmed(self):
        for component in ("bolt", "box"):
            data = moq_input(self.plan)
            for field in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId"):
                data["component_readbacks"][component][field] = "56"
            data["moq_reads"][component]["quantityUomId"] = "56"
            with self.subTest(component=component), self.assertRaises(factory.FixtureContractError):
                factory.decide_lot_quantity(**data)

    def test_moq_is_a_floor_not_a_guessed_order_multiple(self):
        data = moq_input(self.plan)
        data["moq_reads"]["bolt"]["row"]["minOrderQty"] = "700"
        self.assertEqual(factory.decide_lot_quantity(**data)["finishedQuantity"], 1000)

    def test_no_eligible_candidate_does_not_enlarge_old_shortage_or_auto_scale(self):
        data = moq_input(self.plan)
        data["moq_reads"]["bolt"]["row"]["minOrderQty"] = "1500"
        with self.assertRaises(factory.FixtureContractError):
            factory.decide_lot_quantity(**data)

    def test_lot_multiple_is_an_explicit_positive_integer_not_moq(self):
        data = moq_input(self.plan)
        self.assertEqual(factory.decide_lot_quantity(**data, kit_lot_multiple=250)["finishedQuantity"], 1000)
        for bad in (0, -1, True, "0.5", 600.0):
            with self.subTest(bad=bad):
                with self.assertRaises(factory.FixtureContractError):
                    factory.decide_lot_quantity(**data, kit_lot_multiple=bad)

    def test_fractional_unknown_negative_or_nonfinite_moq_is_blocked(self):
        for bad in (None, "0.542923", "NaN", "Infinity", "-1", True, 400.5):
            data = moq_input(self.plan)
            data["moq_reads"]["bolt"]["row"]["minOrderQty"] = bad
            with self.subTest(bad=bad):
                with self.assertRaises(factory.FixtureContractError):
                    factory.decide_lot_quantity(**data)

    def test_zero_moq_means_no_floor_but_not_zero_discrete_production(self):
        data = moq_input(self.plan)
        for read in data["moq_reads"].values():
            read["row"]["minOrderQty"] = "0"
        self.assertEqual(factory.decide_lot_quantity(**data)["finishedQuantity"], 600)

    def test_missing_moq_unit_confirmation_or_wrong_new_material_is_blocked(self):
        for mutate in (lambda d: d["moq_reads"].pop("box"),
                       lambda d: d["moq_reads"]["bolt"].update(unitBasisConfirmed=False),
                       lambda d: d["moq_reads"]["bolt"].update(quantityUomId="999"),
                       lambda d: d["moq_reads"]["bolt"]["row"].update(materialId="1"),
                       lambda d: d["moq_reads"]["bolt"]["row"].update(status=0),
                       lambda d: d["component_readbacks"]["bolt"].update(sourceType="PROCESS"),
                       lambda d: d["component_readbacks"]["bolt"].update(purchaseUomId="999"),
                       lambda d: d["component_readbacks"]["bolt"].update(baseUomName="kg"),
                       lambda d: d["component_readbacks"]["bolt"].update(materialId="1")):
            data = moq_input(self.plan)
            mutate(data)
            with self.assertRaises(factory.FixtureContractError):
                factory.decide_lot_quantity(**data)

    def test_stale_future_or_out_of_effect_supplier_read_is_blocked(self):
        for mutation in ({"readDate": "2026-09-28"}, {"readDate": "2026-09-30"},
                         {"effectiveDate": "2026-09-30"}, {"expiryDate": "2026-09-28"}):
            data = moq_input(self.plan)
            read = data["moq_reads"]["bolt"]
            if "readDate" in mutation:
                read.update(mutation)
            else:
                read["row"].update(mutation)
            with self.assertRaises(factory.FixtureContractError):
                factory.decide_lot_quantity(**data)

    def test_supplier_date_time_boundary_is_valid_but_is_not_actual_arrival(self):
        data = moq_input(self.plan)
        data["moq_reads"]["bolt"]["row"].update(effectiveDate="2026-09-01 00:00:00", expiryDate="2026-09-29 23:59:59")
        self.assertEqual(factory.decide_lot_quantity(**data)["finishedQuantity"], 600)

    def test_lot_candidates_must_be_nonzero_integer_counts(self):
        for candidates in ((), (0, 600), ("0.5", 600), (True, 1000), (600.0, 1000), (-1, 1000)):
            with self.subTest(candidates=candidates):
                with self.assertRaises(factory.FixtureContractError):
                    factory.decide_lot_quantity(**moq_input(self.plan), candidate_lots=candidates)

    def test_deterministic_detached_output_and_no_input_mutation(self):
        data = readings()
        before = deepcopy(data)
        a = factory.build_fixture(**data)
        b = factory.build_fixture(**data)
        self.assertEqual(a, b)
        a["requests"][0]["body"]["materialName"] = "被修改"
        self.assertNotEqual(a, b)
        self.assertEqual(data, before)
        moq = moq_input(self.plan)
        before = deepcopy(moq)
        factory.decide_lot_quantity(**moq)
        self.assertEqual(moq, before)

    def test_factory_has_no_files_network_subprocess_or_database_side_effects(self):
        data = moq_input(self.plan)
        rows = created_records(self.plan)
        with patch("builtins.open", side_effect=AssertionError("filesystem I/O is forbidden")), \
             patch("socket.socket", side_effect=AssertionError("network is forbidden")), \
             patch("subprocess.run", side_effect=AssertionError("process is forbidden")):
            factory.build_fixture(**self.reads)
            factory.decide_lot_quantity(**data)
            factory.resolve_new_references(self.plan["requests"][7], created_records=rows, preexisting_ids=baseline())
        tree = ast.parse(Path(factory.__file__).read_text(encoding="utf-8-sig"))
        allowed = {"copy", "datetime", "decimal", "re"}
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                self.assertTrue({alias.name.split(".")[0] for alias in node.names} <= allowed)
            if isinstance(node, ast.ImportFrom):
                self.assertIn(node.module.split(".")[0], allowed)

    def test_generated_body_fields_are_in_actual_java_bos(self):
        base = ROOT / "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd/domain/bo"
        source_by_path = {"/erp/bd/material": "BdMaterialBo.java", "/erp/bd/routing": "BdRoutingBo.java",
                          "/erp/bd/bom": "BdBomBo.java", "/erp/bd/bom/detail": "BdBomDetailBo.java"}
        for request in self.plan["requests"]:
            source = (base / source_by_path[request["path"]]).read_text(encoding="utf-8-sig")
            fields = set(re.findall(r"private\s+[\w<>\[\]]+\s+(\w+)\s*;", source))
            self.assertTrue(set(request["body"]) <= fields, request["key"])
        step_source = (base / "BdRoutingStepBo.java").read_text(encoding="utf-8-sig")
        fields = set(re.findall(r"private\s+[\w<>\[\]]+\s+(\w+)\s*;", step_source))
        for step in self.plan["requests"][5]["body"]["steps"]:
            self.assertTrue(set(step) <= fields)

    def test_create_paths_and_numbering_entries_are_in_actual_controllers_services(self):
        base = ROOT / "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/bd"
        for controller, path in (("BdMaterialController.java", "/erp/bd/material"),
                                 ("BdRoutingController.java", "/erp/bd/routing"),
                                 ("BdBomController.java", "/erp/bd/bom"),
                                 ("BdBomDetailController.java", "/erp/bd/bom/detail")):
            source = (base / "controller" / controller).read_text(encoding="utf-8-sig")
            self.assertIn(f'@RequestMapping("{path}")', source)
            self.assertRegex(source, r"@PostMapping\s+public R<Void> add")
        for service, rule in (("BdMaterialServiceImpl.java", "MATERIAL_CODE"),
                              ("BdRoutingServiceImpl.java", "ROUTING_CODE")):
            source = (base / "service/impl" / service).read_text(encoding="utf-8-sig")
            self.assertIn(rule, source)
            self.assertIn("genSerialCode", source)
        process = (base / "controller/BdProcessController.java").read_text(encoding="utf-8-sig")
        self.assertIn('@GetMapping("/list")', process)
        process_service = (base / "service/impl/BdProcessServiceImpl.java").read_text(encoding="utf-8-sig")
        self.assertIn("bo.getProcessCategory()", process_service)
        self.assertIn("bo.getStatus()", process_service)


if __name__ == "__main__":
    unittest.main(verbosity=2)
