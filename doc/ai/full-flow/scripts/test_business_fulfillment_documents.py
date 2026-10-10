"""离线合成用例只验证资料生成器，不代表系统读回、外部签署或实物履约。"""
from copy import deepcopy
from datetime import date
from decimal import Decimal
import ast
import hashlib
import json
import os
from pathlib import Path
import re
from types import SimpleNamespace
import stat
import tempfile
import unittest
from unittest.mock import patch

try:
    import business_fulfillment_documents as documents
except ModuleNotFoundError as exc:
    if exc.name != "business_fulfillment_documents":
        raise
    documents = None

ORIGIN = "LOCAL_SOFTWARE_BUSINESS_VALIDATION"
_UNSET = object()
ROW_FIELDS = {
    "orderId", "orderNo", "nativeOrderVersion", "materialId", "materialName",
    "vendorId", "vendorName", "baseUomId", "quantity", "requestKey",
    "unitPriceExcludingTax", "taxRate", "currency", "exchangeRate",
    "promisedArrivalDate", "validFrom", "validUntil", "supplierQuoteReference",
    "supplierContact", "paymentTerms", "deliveryTerms", "priceSource",
}
# 当前原生 SupplierTermsRequest 为 15 字段，以实际 Java 记录而非历史计数为准。
TERMS_FIELDS = {
    "requestKey", "expectedOrderVersion", "unitPriceExcludingTax", "taxRate",
    "currency", "exchangeRate", "promisedArrivalDate", "validFrom", "validUntil",
    "supplierQuoteReference", "supplierContact", "evidenceReference",
    "evidenceSha256", "paymentTerms", "deliveryTerms",
}


def payload():
    """构造普通业务命名的合成输入，不作为生产值或已核验报价使用。"""
    return {
        "businessDate": "2026-10-09",
        "dataset": {"origin": ORIGIN, "description": "紧固件采购履约"},
        "orders": [{
            "orderId": "9007199254741001", "orderNo": "CG-20261009-01",
            "nativeOrderVersion": 0, "materialId": "9007199254741101",
            "materialName": "不锈钢垫圈", "vendorId": "9007199254741201",
            "vendorName": "华东紧固件供应中心", "baseUomId": "26",
            "quantity": "22.0000", "requestKey": "supply:20261009:01",
            "unitPriceExcludingTax": "12.3456", "taxRate": "0.1300",
            "currency": "CNY", "exchangeRate": "1.0000",
            "promisedArrivalDate": "2026-10-12", "validFrom": "2026-10-09",
            "validUntil": "2026-10-15", "supplierQuoteReference": "BJ-20261009-01",
            "supplierContact": "供应中心业务部", "paymentTerms": "验收后30天结算",
            "deliveryTerms": "按订单约定送达指定仓库",
            "priceSource": {
                "sourceQuoteId": "9007199254741301", "sourceOrderId": "9007199254741401",
                "sourceQuoteHash": "1" * 64, "sourceEvidenceSha256": "2" * 64,
                "sourceUnitPriceExcludingTax": "12.3456", "sourceTaxRate": "0.1300",
                "basis": "既有已批准本地采购报价原单价",
            },
        }],
    }


def second_order(first):
    """复制字段并指定另一原单的独立标识及请求键。"""
    row = deepcopy(first)
    row.update(orderId="9007199254741002", orderNo="CG-20261009-02",
               requestKey="supply:20261009:02", supplierQuoteReference="BJ-20261009-02")
    return row


class BusinessFulfillmentDocumentsTests(unittest.TestCase):
    """从公共函数验证真实文件字节、全批预检与封闭式拒绝。"""

    def setUp(self):
        self.assertIsNotNone(documents, "缺少 business_fulfillment_documents.build_documents 实现")
        self.temporary = tempfile.TemporaryDirectory(
            prefix="quote-doc-worker-tests-", dir=os.environ.get("QUOTE_DOC_WORKER_TMPDIR"))
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.output = self.root / "采购资料"
        self.data = payload()

    def build(self, data=_UNSET, output=None):
        """使用临时私有目录调用唯一生产入口。"""
        return documents.build_documents(self.data if data is _UNSET else data,
                                         self.output if output is None else output)

    def entry(self, manifest):
        """读取第一单清单，不把清单就绪当作业务审批完成。"""
        return manifest["orders"][0]

    def snapshot(self, directory=None):
        """保存现有输出字节，用于证明拒绝时没有覆盖或补写。"""
        directory = self.output if directory is None else directory
        if not directory.exists():
            return None
        return {p.relative_to(directory).as_posix(): p.read_bytes()
                for p in directory.rglob("*") if p.is_file()}

    def rejected(self, data, output=None):
        """所有输入拒绝都必须早于目录创建及证据写入。"""
        directory = self.output if output is None else output
        before = self.snapshot(directory)
        with self.assertRaises(ValueError):
            self.build(data, directory)
        self.assertEqual(self.snapshot(directory), before)

    def test_native_zero_version_produces_only_actual_native_terms_fields(self):
        entry = self.entry(self.build())
        self.assertEqual(set(entry), {"orderId", "evidencePath", "evidenceSha256", "submissionReady", "termsBody"})
        self.assertEqual(set(entry["termsBody"]), TERMS_FIELDS)
        self.assertTrue(entry["submissionReady"])
        self.assertEqual(entry["termsBody"]["expectedOrderVersion"], 0)
        self.assertIs(type(entry["termsBody"]["expectedOrderVersion"]), int)
        self.assertFalse({"quantity", "actor", "verified"} & set(entry["termsBody"]))

    def test_terms_whitelist_matches_real_java_request_contract(self):
        service = Path(__file__).resolve().parents[4] / (
            "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/service/"
            "PuCertifiedPurchaseOrderWorkflowService.java")
        source = service.read_text(encoding="utf-8")
        declaration = re.search(r"record SupplierTermsRequest\((.*?)\) \{", source, re.S).group(1)
        fields = set(re.findall(r"(?:String|Integer|BigDecimal|LocalDate)\s+(\w+)", declaration))
        self.assertEqual(set(self.entry(self.build())["termsBody"]), fields)

    def test_sha256_is_of_actual_utf8_evidence_bytes(self):
        entry = self.entry(self.build())
        evidence = Path(entry["evidencePath"])
        self.assertTrue(evidence.is_absolute())
        raw = evidence.read_bytes()
        self.assertEqual(entry["evidenceSha256"], hashlib.sha256(raw).hexdigest())
        self.assertEqual(entry["termsBody"]["evidenceSha256"], entry["evidenceSha256"])
        self.assertEqual(entry["termsBody"]["evidenceReference"], str(evidence))
        self.assertIn("不锈钢垫圈".encode("utf-8"), raw)
        self.assertFalse(raw.startswith(b"\xef\xbb\xbf"))
        self.assertNotIn(b"\r\n", raw)
        self.assertEqual(json.loads(raw)["order"]["quantity"], "22.0000")
        self.assertEqual(len(list(self.output.glob("*.json"))), 1)
        self.assertEqual(len(list(self.output.glob("*.md"))), 1)

    def test_null_native_version_is_evidence_only_without_default_zero(self):
        self.data["orders"][0]["nativeOrderVersion"] = None
        entry = self.entry(self.build())
        self.assertIsNone(entry["termsBody"])
        self.assertIs(entry["submissionReady"], False)
        evidence = json.loads(Path(entry["evidencePath"]).read_bytes())
        self.assertIsNone(evidence["order"]["nativeOrderVersion"])
        self.assertIn("不可提交", next(self.output.glob("*.md")).read_text(encoding="utf-8"))

    def test_known_nonzero_native_version_is_preserved(self):
        self.data["orders"][0]["nativeOrderVersion"] = 7
        self.assertEqual(self.entry(self.build())["termsBody"]["expectedOrderVersion"], 7)

    def test_input_is_not_mutated(self):
        before = deepcopy(self.data)
        self.build()
        self.assertEqual(self.data, before)

    def test_business_names_are_ordinary_and_local_audit_is_visible_in_both_formats(self):
        entry = self.entry(self.build())
        for path in self.output.iterdir():
            self.assertNotRegex(path.name, re.compile(r"TEST|DEMO|MOCK|测试用例", re.I))
            text = path.read_text(encoding="utf-8")
            self.assertIn(ORIGIN, text)
            self.assertIn("本机软件", text)
            self.assertIn("非外部", text)
            self.assertIn("非实物", text)
            self.assertIn(self.data["orders"][0]["orderNo"], text)
        document = json.loads(Path(entry["evidencePath"]).read_bytes())
        self.assertEqual(document["dataset"], self.data["dataset"])
        self.assertEqual(document["order"], self.data["orders"][0])

    def test_supplied_price_source_is_retained_without_fabricated_approval_or_actor(self):
        entry = self.entry(self.build())
        document = json.loads(Path(entry["evidencePath"]).read_bytes())
        self.assertEqual(document["order"]["priceSource"], self.data["orders"][0]["priceSource"])
        self.assertNotIn("verified", json.dumps(document))
        self.assertNotIn('"actor"', json.dumps(document))

    def test_no_amount_or_kpi_calculation_is_generated(self):
        entry = self.entry(self.build())
        document = json.loads(Path(entry["evidencePath"]).read_bytes())
        raw = json.dumps({"document": document, "terms": entry["termsBody"]})
        for field in ("totalAmount", "taxAmount", "amountExcludingTax", "grossUnitPrice", "KPI"):
            self.assertNotIn(field, raw)
        for key in ("quantity", "unitPriceExcludingTax", "taxRate", "exchangeRate"):
            self.assertIs(type(document["order"][key]), str)

    def test_same_input_reuses_same_bytes_and_does_not_rewrite(self):
        first = self.build()
        for path in self.output.iterdir():
            os.utime(path, ns=(1_600_000_000_000_000_000, 1_600_000_000_000_000_000))
        before = {p.name: (p.stat().st_mtime_ns, p.read_bytes()) for p in self.output.iterdir()}
        self.assertEqual(self.build(deepcopy(self.data)), first)
        self.assertEqual({p.name: (p.stat().st_mtime_ns, p.read_bytes()) for p in self.output.iterdir()}, before)

    def test_input_dictionary_order_does_not_change_evidence_bytes(self):
        first = self.build()
        reordered = dict(reversed(list(self.data.items())))
        reordered["orders"] = [dict(reversed(list(self.data["orders"][0].items())))]
        self.assertEqual(self.build(reordered), first)

    def test_drift_in_any_order_content_fails_closed_without_overwrite(self):
        self.build()
        changes = {
            "orderNo": "CG-20261009-03", "nativeOrderVersion": 1, "materialId": "201",
            "materialName": "镀锌垫圈", "vendorId": "301", "vendorName": "华南供应中心",
            "baseUomId": "27", "quantity": "23", "requestKey": "supply:20261009:03",
            "unitPriceExcludingTax": "12.3457", "taxRate": "0.09", "exchangeRate": "1",
            "promisedArrivalDate": "2026-10-13", "validFrom": "2026-10-08", "validUntil": "2026-10-16",
            "supplierQuoteReference": "BJ-20261009-03", "supplierContact": "业务部二组",
            "paymentTerms": "验收后15天结算", "deliveryTerms": "指定库区交接",
            "priceSource": dict(self.data["orders"][0]["priceSource"], sourceQuoteId="901", basis="原已批准本地报价依据"),
        }
        for field, value in changes.items():
            with self.subTest(field=field):
                changed = deepcopy(self.data)
                changed["orders"][0][field] = value
                self.rejected(changed)

    def test_business_date_and_dataset_drift_are_bound_to_bytes(self):
        self.build()
        changed = deepcopy(self.data)
        changed["businessDate"] = "2026-10-10"
        self.rejected(changed)
        changed = deepcopy(self.data)
        changed["dataset"]["description"] = "垫圈采购履约"
        self.rejected(changed)

    def test_null_to_zero_change_requires_new_review_and_cannot_overwrite(self):
        self.data["orders"][0]["nativeOrderVersion"] = None
        self.build()
        changed = deepcopy(self.data)
        changed["orders"][0]["nativeOrderVersion"] = 0
        self.rejected(changed)

    def test_multiple_orders_keep_independent_scope_and_hashes(self):
        self.data["orders"].append(second_order(self.data["orders"][0]))
        self.data["orders"][1]["nativeOrderVersion"] = None
        result = self.build()
        self.assertEqual(len(result["orders"]), 2)
        self.assertEqual([r["orderId"] for r in result["orders"]], [r["orderId"] for r in self.data["orders"]])
        self.assertNotEqual(result["orders"][0]["evidenceSha256"], result["orders"][1]["evidenceSha256"])
        self.assertIsNone(result["orders"][1]["termsBody"])
        self.assertEqual(len(list(self.output.iterdir())), 4)

    def test_later_invalid_row_is_checked_before_any_output_is_created(self):
        self.data["orders"].append(second_order(self.data["orders"][0]))
        self.data["orders"][1]["taxRate"] = 0.13
        self.rejected(self.data)
        self.assertFalse(self.output.exists())

    def test_later_conflicting_json_is_checked_before_earlier_order_is_written(self):
        later = second_order(self.data["orders"][0])
        single = deepcopy(self.data)
        single["orders"] = [later]
        saved = self.entry(self.build(single))
        later = deepcopy(later)
        later["quantity"] = "44"
        self.data["orders"].append(later)
        self.rejected(self.data)
        self.assertEqual(len(list(self.output.iterdir())), 2)
        self.assertEqual(hashlib.sha256(Path(saved["evidencePath"]).read_bytes()).hexdigest(), saved["evidenceSha256"])

    def test_later_conflicting_markdown_is_checked_before_any_new_json_is_written(self):
        single = deepcopy(self.data)
        single["orders"] = [second_order(self.data["orders"][0])]
        self.build(single)
        markdown = next(self.output.glob("*.md"))
        markdown.write_bytes(markdown.read_bytes() + "\n补充文字".encode("utf-8"))
        self.data["orders"].append(single["orders"][0])
        self.rejected(self.data)
        self.assertEqual(len(list(self.output.iterdir())), 2)

    def test_partial_identical_pair_can_be_reused_without_rewriting_existing_evidence(self):
        manifest = self.build()
        next(self.output.glob("*.md")).unlink()
        evidence = Path(self.entry(manifest)["evidencePath"])
        before = evidence.stat().st_mtime_ns
        self.assertEqual(self.build(), manifest)
        self.assertEqual(evidence.stat().st_mtime_ns, before)
        self.assertEqual(len(list(self.output.iterdir())), 2)

    def test_duplicate_order_id_is_rejected(self):
        later = second_order(self.data["orders"][0])
        later["orderId"] = self.data["orders"][0]["orderId"]
        self.data["orders"].append(later)
        self.rejected(self.data)

    def test_duplicate_order_number_is_rejected_even_with_different_id(self):
        later = second_order(self.data["orders"][0])
        later["orderNo"] = self.data["orders"][0]["orderNo"].lower()
        self.data["orders"].append(later)
        self.rejected(self.data)

    def test_duplicate_request_key_is_rejected(self):
        later = second_order(self.data["orders"][0])
        later["requestKey"] = self.data["orders"][0]["requestKey"].upper()
        self.data["orders"].append(later)
        self.rejected(self.data)

    def test_every_explicit_order_field_is_required_including_null_version_key(self):
        self.assertEqual(set(self.data["orders"][0]), ROW_FIELDS)
        for field in sorted(ROW_FIELDS):
            with self.subTest(field=field):
                changed = deepcopy(self.data)
                del changed["orders"][0][field]
                self.rejected(changed)

    def test_payload_dataset_orders_and_price_source_require_exact_shapes(self):
        cases = [None, [], {}, {"businessDate": "2026-10-09", "orders": []}]
        for key, value in (("dataset", None), ("dataset", []), ("orders", None),
                           ("orders", {}), ("orders", []), ("orders", [None])):
            changed = deepcopy(self.data)
            changed[key] = value
            cases.append(changed)
        for value in (None, [], {}, {"sourceQuoteId": "901"}, {"basis": "既有本地报价"}):
            changed = deepcopy(self.data)
            changed["orders"][0]["priceSource"] = value
            cases.append(changed)
        for changed in cases:
            self.rejected(changed)

    def test_unknown_fields_actor_verified_and_credentials_are_not_accepted(self):
        for scope in ("top", "dataset", "order", "priceSource"):
            for field in ("actor", "verified", "password", "accessToken", "sourceConfirmationId", "signature", "extra"):
                with self.subTest(scope=scope, field=field):
                    changed = deepcopy(self.data)
                    container = {"top": changed, "dataset": changed["dataset"],
                                 "order": changed["orders"][0], "priceSource": changed["orders"][0]["priceSource"]}[scope]
                    container[field] = "不允许传入"
                    self.rejected(changed)

    def test_versions_reject_unsafe_numbers_strings_boolean_negative_and_int_max(self):
        for value in (False, True, -1, 1.0, "0", "NULL", 2_147_483_647, 2_147_483_648, Decimal("0")):
            with self.subTest(kind=type(value).__name__):
                changed = deepcopy(self.data)
                changed["orders"][0]["nativeOrderVersion"] = value
                self.rejected(changed)
        self.data["orders"][0]["nativeOrderVersion"] = 2_147_483_646
        self.assertEqual(self.entry(self.build())["termsBody"]["expectedOrderVersion"], 2_147_483_646)

    def test_scope_ids_reject_noncanonical_unsafe_and_out_of_long_range_values(self):
        for field in ("orderId", "materialId", "vendorId", "baseUomId"):
            for value in (None, False, 0, -1, 1.0, float("nan"), float("inf"),
                          9_007_199_254_741_001, "0", "01", "+1", "-1", "1.0", "1e2",
                          " 1", "１", "9223372036854775808", "../801"):
                with self.subTest(field=field, kind=type(value).__name__):
                    changed = deepcopy(self.data)
                    changed["orders"][0][field] = value
                    self.rejected(changed)

    def test_safe_integer_ids_and_exact_long_strings_are_serialized_as_strings(self):
        self.data["orders"][0].update(orderId="9223372036854775807", materialId=201, vendorId=301, baseUomId=26)
        entry = self.entry(self.build())
        row = json.loads(Path(entry["evidencePath"]).read_bytes())["order"]
        self.assertEqual(entry["orderId"], "9223372036854775807")
        for field in ("orderId", "materialId", "vendorId", "baseUomId"):
            self.assertIs(type(row[field]), str)

    def test_decimal_inputs_must_be_plain_decimal_text_not_float_or_decimal_objects(self):
        for field in ("quantity", "unitPriceExcludingTax", "taxRate", "exchangeRate"):
            for value in (1, 1.0, True, Decimal("1"), None, "NaN", "Infinity", "1e0",
                          "+1", "-0", " 1", "1 ", ".1", "1.", "01", "１", "1" * 257):
                with self.subTest(field=field, kind=type(value).__name__):
                    changed = deepcopy(self.data)
                    changed["orders"][0][field] = value
                    self.rejected(changed)

    def test_price_and_quantity_follow_native_four_place_fourteen_digit_limits(self):
        for field in ("quantity", "unitPriceExcludingTax"):
            for value in ("0", "0.00000", "-1", "0.00001", "1.23456", "100000000000000"):
                changed = deepcopy(self.data)
                changed["orders"][0][field] = value
                self.rejected(changed)
        self.data["orders"][0].update(quantity="99999999999999.9999000", unitPriceExcludingTax="0.0001000")
        self.data["orders"][0]["priceSource"]["sourceUnitPriceExcludingTax"] = "0.0001000"
        body = self.entry(self.build())["termsBody"]
        self.assertEqual(body["unitPriceExcludingTax"], "0.0001000")

    def test_tax_rate_is_zero_to_one_with_native_normalized_scale(self):
        for value in ("1.0001", "0.00001", "0.12345", "-0.1"):
            changed = deepcopy(self.data)
            changed["orders"][0]["taxRate"] = value
            self.rejected(changed)
        for index, value in enumerate(("0", "1.00000", "0.12340000")):
            changed = deepcopy(self.data)
            changed["orders"][0]["taxRate"] = value
            result = self.build(changed, self.root / f"税率资料-{index}")
            self.assertEqual(self.entry(result)["termsBody"]["taxRate"], value)

    def test_currency_is_exact_cny_and_exchange_rate_is_decimal_one(self):
        for field, value in (("currency", "USD"), ("currency", "cny"), ("currency", " CNY"),
                             ("exchangeRate", "0"), ("exchangeRate", "1.0001"), ("exchangeRate", 1.0)):
            changed = deepcopy(self.data)
            changed["orders"][0][field] = value
            self.rejected(changed)

    def test_all_dates_are_strict_iso_dates_in_native_mysql_range(self):
        for field in ("businessDate", "promisedArrivalDate", "validFrom", "validUntil"):
            for value in (None, date(2026, 10, 9), "2026-1-09", "20261009", "2026-02-30",
                          "2026-10-09T00:00:00", "0999-10-09", "10000-10-09", "2026-10-09 "):
                with self.subTest(field=field):
                    changed = deepcopy(self.data)
                    container = changed if field == "businessDate" else changed["orders"][0]
                    container[field] = value
                    self.rejected(changed)

    def test_date_relationships_match_native_require_dates(self):
        for updates in ({"validFrom": "2026-10-10"}, {"validUntil": "2026-10-08"},
                        {"promisedArrivalDate": "2026-10-08"},
                        {"validFrom": "2026-10-08", "validUntil": "2026-10-07"}):
            changed = deepcopy(self.data)
            changed["orders"][0].update(updates)
            self.rejected(changed)
        # 原生 helper 不要求承诺日落在报价有效期内，不能自增这个守卫。
        self.data["orders"][0].update(validUntil="2026-10-09", promisedArrivalDate="2026-10-20")
        self.assertTrue(self.entry(self.build())["submissionReady"])

    def test_native_text_fields_require_trimmed_nonempty_bounded_utf16_text(self):
        limits = {"supplierQuoteReference": 128, "supplierContact": 128,
                  "paymentTerms": 255, "deliveryTerms": 255}
        for field, maximum in limits.items():
            for value in (None, "", " ", " 前后空格", "前后空格 ", "字" * (maximum + 1),
                          "😀" * (maximum // 2 + 1), "业务\n另一段", "业务\x00内容", "业务\u200b内容"):
                changed = deepcopy(self.data)
                changed["orders"][0][field] = value
                self.rejected(changed)
        self.data["orders"][0]["supplierQuoteReference"] = "字" * 128
        self.data["orders"][0]["paymentTerms"] = "字" * 255
        self.assertTrue(self.entry(self.build())["submissionReady"])

    def test_order_numbers_cannot_be_paths_or_reserved_device_names(self):
        for value in ("../原单", "..\\原单", "/原单", "C:\\原单", "单/号", "单\\号",
                      "CON", "NUL", "COM1", "LPT9", "单号.", "单号:"):
            changed = deepcopy(self.data)
            changed["orders"][0]["orderNo"] = value
            self.rejected(changed)

    def test_request_key_matches_native_permanent_key_pattern_and_length(self):
        for value in ("", "键", "supply/01", "supply.01", "supply 01", "a" * 129):
            changed = deepcopy(self.data)
            changed["orders"][0]["requestKey"] = value
            self.rejected(changed)
        self.data["orders"][0]["requestKey"] = "a" * 128
        self.assertTrue(self.entry(self.build())["submissionReady"])

    def test_user_visible_business_fields_reject_test_demo_mock_markers(self):
        for field in ("orderNo", "materialName", "vendorName", "supplierQuoteReference", "supplierContact",
                      "paymentTerms", "deliveryTerms", "requestKey"):
            for marker in ("TEST", "demo", "MoCk", "测试用例", "ＴＥＳＴ"):
                changed = deepcopy(self.data)
                changed["orders"][0][field] = f"业务-{marker}-资料"
                self.rejected(changed)
        for scope, field in (("dataset", "description"), ("priceSource", "basis")):
            changed = deepcopy(self.data)
            container = changed["dataset"] if scope == "dataset" else changed["orders"][0]["priceSource"]
            container[field] = "采购DEMO资料"
            self.rejected(changed)

    def test_external_origin_is_rejected_at_dataset_and_price_source(self):
        for origin in ("EXTERNAL_SUPPLIER_SIGNED", "external", "PRODUCTION", "LOCAL_SOFTWARE_BUSINESS_VALIDATION ", None):
            changed = deepcopy(self.data)
            changed["dataset"]["origin"] = origin
            self.rejected(changed)
            changed = deepcopy(self.data)
            changed["orders"][0]["priceSource"]["origin"] = origin
            self.rejected(changed)
        self.data["orders"][0]["priceSource"]["origin"] = ORIGIN
        self.assertTrue(self.entry(self.build())["submissionReady"])

    def test_price_source_requires_exact_original_quote_id_and_business_basis(self):
        for field, value in (("sourceQuoteId", 9_007_199_254_741_001), ("sourceQuoteId", "0"),
                             ("sourceQuoteId", "01"), ("sourceQuoteId", "1.0"), ("basis", "")):
            changed = deepcopy(self.data)
            changed["orders"][0]["priceSource"][field] = value
            self.rejected(changed)

    def test_credential_values_are_rejected_without_echoing_the_secret(self):
        secrets = ("Bearer sensitive-credential", "Authorization: sensitive-credential",
                   "password=sensitive-credential", "api_key: sensitive-credential",
                   "accessToken=sensitive-credential", "client_secret=sensitive-credential",
                   "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature",
                   "-----BEGIN PRIVATE KEY----- sensitive-credential",
                   "https://user:sensitive-credential@host/path", "AKIA1234567890ABCDEF")
        for value in secrets:
            changed = deepcopy(self.data)
            changed["orders"][0]["supplierContact"] = value
            with self.assertRaises(ValueError) as caught:
                self.build(changed)
            self.assertNotIn("sensitive-credential", str(caught.exception))
            self.assertFalse(self.output.exists())

    def test_raw_api_tokens_and_chinese_credential_labels_are_rejected(self):
        # 均为格式合成占位内容，绝不使用任何有效服务凭据。
        values = ("sk-" + "a" * 40, "ghp_" + "b" * 36, "github_pat_" + "c" * 40,
                  "AIza" + "d" * 35, "Authorization=sensitive-credential",
                  "Cookie: session=sensitive-credential", "密码：sensitive-credential",
                  "令牌=sensitive-credential", "密钥：sensitive-credential")
        for value in values:
            changed = deepcopy(self.data)
            changed["orders"][0]["priceSource"]["basis"] = value
            self.rejected(changed)

    def test_markdown_escapes_input_that_could_forge_content_or_local_audit(self):
        self.data["orders"][0]["materialName"] = "垫圈|规格[说明]<b>材质</b>"
        entry = self.entry(self.build())
        text = next(self.output.glob("*.md")).read_text(encoding="utf-8")
        self.assertNotIn("<b>", text)
        self.assertIn("&lt;b&gt;", text)
        self.assertIn("\\|", text)
        self.assertIn("\\[说明\\]", text)
        self.assertEqual(json.loads(Path(entry["evidencePath"]).read_bytes())["order"]["materialName"],
                         self.data["orders"][0]["materialName"])

    def test_output_directory_rejects_traversal_relative_network_and_root_paths(self):
        for directory in (self.root / ".." / "越界资料", Path("相对资料"),
                          Path("//remote/share/采购资料"), Path(self.root.anchor)):
            with self.subTest(kind=str(directory)):
                with self.assertRaises(ValueError):
                    self.build(output=directory)
        self.assertFalse(self.output.exists())

    def test_existing_output_directory_file_is_not_overwritten(self):
        self.output.write_bytes(b"private-file")
        with self.assertRaises(ValueError):
            self.build()
        self.assertEqual(self.output.read_bytes(), b"private-file")

    def test_existing_evidence_directory_is_rejected_before_missing_markdown_is_written(self):
        entry = self.entry(self.build())
        evidence = Path(entry["evidencePath"])
        evidence.unlink()
        evidence.mkdir()
        next(self.output.glob("*.md")).unlink()
        with self.assertRaises(ValueError):
            self.build()
        self.assertTrue(evidence.is_dir())
        self.assertEqual(len(list(self.output.iterdir())), 1)

    def test_evidence_reference_respects_native_255_utf16_limit_before_writing(self):
        directory = self.root / ("长" * 110) / ("路径" * 55)
        with self.assertRaises(ValueError):
            self.build(output=directory)
        self.assertFalse(directory.exists())

    def test_reparse_directory_and_parent_are_rejected_without_traversal(self):
        self.output.mkdir()
        original = Path.lstat
        for unsafe in (self.output, self.root):
            def flagged(path, *args, **kwargs):
                result = original(path, *args, **kwargs)
                if path == unsafe:
                    return SimpleNamespace(st_mode=result.st_mode, st_nlink=result.st_nlink,
                                           st_file_attributes=stat.FILE_ATTRIBUTE_REPARSE_POINT)
                return result
            with patch.object(Path, "lstat", flagged):
                with self.assertRaises(ValueError):
                    self.build()
            self.assertEqual(list(self.output.iterdir()), [])

    def test_reparse_evidence_is_rejected_even_when_bytes_are_identical(self):
        entry = self.entry(self.build())
        evidence = Path(entry["evidencePath"])
        original = Path.lstat
        def flagged(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if path == evidence:
                return SimpleNamespace(st_mode=result.st_mode, st_nlink=result.st_nlink,
                                       st_file_attributes=stat.FILE_ATTRIBUTE_REPARSE_POINT)
            return result
        before = self.snapshot()
        with patch.object(Path, "lstat", flagged):
            with self.assertRaises(ValueError):
                self.build()
        self.assertEqual(self.snapshot(), before)

    def test_hardlinked_evidence_is_not_reused(self):
        entry = self.entry(self.build())
        evidence = Path(entry["evidencePath"])
        os.link(evidence, self.root / "另一证据别名.json")
        self.rejected(self.data)

    def test_input_drift_during_writes_cannot_change_frozen_manifest_context(self):
        before = deepcopy(self.data)
        original = Path.mkdir
        def drift_after_validation(path, *args, **kwargs):
            if path == self.output:
                self.data["businessDate"] = "2026-10-10"
                self.data["dataset"]["origin"] = "EXTERNAL_SUPPLIER_SIGNED"
                self.data["orders"][0]["quantity"] = "44"
                self.data["orders"][0]["nativeOrderVersion"] = None
                self.data["orders"][0]["priceSource"]["sourceQuoteId"] = "901"
            return original(path, *args, **kwargs)
        with patch.object(Path, "mkdir", drift_after_validation):
            manifest = self.build()
        self.assertEqual(manifest["businessDate"], before["businessDate"])
        self.assertEqual(manifest["dataset"], before["dataset"])
        entry = self.entry(manifest)
        self.assertEqual(entry["termsBody"]["expectedOrderVersion"], 0)
        evidence = json.loads(Path(entry["evidencePath"]).read_bytes())
        self.assertEqual(evidence["businessDate"], manifest["businessDate"])
        self.assertEqual(evidence["order"], before["orders"][0])

    def test_output_paths_reject_windows_aliases_alternate_streams_and_controls(self):
        # 所有路径都在临时目录内，不能用真实父目录穿越来测试安全守卫。
        for component in ("采购资料.", "采购资料 ", "...", "采购资料:附流",
                          "采购\t资料", "采购\u200b资料"):
            directory = self.root / component
            with self.subTest(component=repr(component)):
                with self.assertRaises(ValueError):
                    self.build(output=directory)
        directory = self.root / "子目录" / ".. " / "穿越资料"
        with self.assertRaises(ValueError):
            self.build(output=directory)

    def test_price_source_accepts_exact_six_core_fields_without_optional_flags(self):
        del self.data["orders"][0]["priceSource"]["basis"]
        try:
            manifest = self.build()
        except ValueError:
            self.fail("生成器尚不接受既有已批准本地quote的六字段来源schema")
        row = json.loads(Path(self.entry(manifest)["evidencePath"]).read_bytes())["order"]
        self.assertEqual(row["priceSource"], self.data["orders"][0]["priceSource"])
        self.assertTrue(self.entry(manifest)["submissionReady"])

    def test_price_source_requires_every_explicit_historical_field_before_writing(self):
        fields = {"sourceQuoteId", "sourceOrderId", "sourceQuoteHash", "sourceEvidenceSha256",
                  "sourceUnitPriceExcludingTax", "sourceTaxRate"}
        for field in fields:
            changed = deepcopy(self.data)
            del changed["orders"][0]["priceSource"][field]
            self.rejected(changed)

    def test_historical_source_ids_and_hashes_are_exact_supplied_values(self):
        for field in ("sourceQuoteId", "sourceOrderId"):
            for value in (None, False, 1.0, 9_007_199_254_741_001, "0", "01", "9223372036854775808"):
                changed = deepcopy(self.data)
                changed["orders"][0]["priceSource"][field] = value
                self.rejected(changed)
        for field in ("sourceQuoteHash", "sourceEvidenceSha256"):
            for value in (None, "", "1" * 63, "1" * 65, "G" * 64, "A" * 64, " " + "1" * 64):
                changed = deepcopy(self.data)
                changed["orders"][0]["priceSource"][field] = value
                self.rejected(changed)

    def test_historical_price_and_tax_require_native_decimal_text_precision(self):
        for field in ("sourceUnitPriceExcludingTax", "sourceTaxRate"):
            for value in (1, 1.0, Decimal("1"), "1e0", "NaN", "0.12345"):
                changed = deepcopy(self.data)
                changed["orders"][0]["priceSource"][field] = value
                self.rejected(changed)
        for field, value in (("sourceUnitPriceExcludingTax", "0"),
                             ("sourceUnitPriceExcludingTax", "100000000000000"),
                             ("sourceTaxRate", "1.0001")):
            changed = deepcopy(self.data)
            changed["orders"][0]["priceSource"][field] = value
            self.rejected(changed)

    def test_new_unit_price_matches_historical_quote_without_rounding(self):
        changed = deepcopy(self.data)
        changed["orders"][0]["priceSource"]["sourceUnitPriceExcludingTax"] = "12.3457"
        self.rejected(changed)
        self.data["orders"][0]["unitPriceExcludingTax"] = "12.3456000"
        self.assertEqual(self.entry(self.build())["termsBody"]["unitPriceExcludingTax"], "12.3456000")

    def test_historical_tax_hashes_and_approval_are_not_inherited_into_new_terms(self):
        self.data["orders"][0]["priceSource"]["sourceTaxRate"] = "0.0900"
        entry = self.entry(self.build())
        self.assertEqual(entry["termsBody"]["taxRate"], "0.1300")
        self.assertNotEqual(entry["evidenceSha256"], self.data["orders"][0]["priceSource"]["sourceEvidenceSha256"])
        self.assertNotEqual(entry["evidenceSha256"], self.data["orders"][0]["priceSource"]["sourceQuoteHash"])
        self.assertEqual(set(entry["termsBody"]), TERMS_FIELDS)
        self.assertFalse({"confirmation", "signature", "verified", "actor", "sourceConfirmationId"} & set(entry["termsBody"]))

    def test_historical_source_scope_hash_and_price_drift_are_bound_to_new_bytes(self):
        self.build()
        changes = {"sourceQuoteId": "901", "sourceOrderId": "902", "sourceQuoteHash": "3" * 64,
                   "sourceEvidenceSha256": "4" * 64, "sourceTaxRate": "0.0900",
                   "sourceUnitPriceExcludingTax": "12.3457"}
        for field, value in changes.items():
            changed = deepcopy(self.data)
            changed["orders"][0]["priceSource"][field] = value
            if field == "sourceUnitPriceExcludingTax":
                changed["orders"][0]["unitPriceExcludingTax"] = value
            self.rejected(changed)

    def test_markdown_shows_historical_source_values_without_fake_new_confirmation(self):
        self.build()
        text = next(self.output.glob("*.md")).read_text(encoding="utf-8")
        for field in ("sourceQuoteId", "sourceOrderId", "sourceQuoteHash", "sourceEvidenceSha256",
                      "sourceUnitPriceExcludingTax", "sourceTaxRate"):
            self.assertIn(self.data["orders"][0]["priceSource"][field], text)
        self.assertIn("不能继承旧复核", text)
        self.assertIn("不能替代本批新资料哈希", text)

    def test_generator_has_only_offline_standard_library_dependencies(self):
        source = Path(documents.__file__).read_text(encoding="utf-8-sig")
        tree = ast.parse(source)
        allowed = {"__future__", "datetime", "decimal", "hashlib", "html", "json", "os", "pathlib", "re", "stat", "unicodedata"}
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    self.assertIn(alias.name.split(".")[0], allowed)
            elif isinstance(node, ast.ImportFrom):
                self.assertIn(node.module.split(".")[0], allowed)
        self.assertEqual([node.name for node in tree.body if isinstance(node, ast.FunctionDef) and not node.name.startswith("_")],
                         ["build_documents"])


if __name__ == "__main__":
    unittest.main()
