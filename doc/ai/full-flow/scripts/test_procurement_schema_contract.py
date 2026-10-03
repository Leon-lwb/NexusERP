"""采购与原生IQC的纯内存结构测试；固定夹具来自已审查DDL，不连接数据库。"""
import copy
import importlib
import importlib.util
import json
import re
import unittest


# 仅冻结显式必需表及关键列；生产合同不解析SQL，也不读取夹具进行来源认证。
FRESH_DDL_SNAPSHOT = """
ry-erp.bd_material_vendor
  bigint! relation_id material_id vendor_id
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  bigint? order_uom_id moq_uom_id
  decimal(18,4)? verified_moq_qty order_multiple units_per_pack
  tinyint? allow_split
  varchar(64)?@utf8mb4_unicode_ci terms_version
  datetime(0)? terms_effective_at terms_expires_at terms_verified_at
  #PRIMARY relation_id

ry-erp.bd_unit_of_measure
  bigint! uom_id
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  decimal(18,6)? conversion_rate
  tinyint~? quantity_scale
  varchar(64)?@utf8mb4_unicode_ci conversion_version
  datetime(0)? conversion_verified_at
  #PRIMARY uom_id

ry-erp.pu_certified_purchase_command
  varchar(20)!@utf8mb4_bin tenant_id
  varchar(128)!@utf8mb4_bin request_key
  char(64)!@ascii_bin request_hash source_fingerprint result_fingerprint catalog_fingerprint
  bigint! actor_id material_id vendor_id requisition_id
  varchar(32)!@ascii_bin entry_type
  date! window_end
  varchar(64)!@utf8mb4_unicode_ci requisition_no
  varchar(20)!@ascii_bin command_status
  datetime(0)! create_time update_time
  #PRIMARY tenant_id request_key
  #uk_certified_purchase_requisition tenant_id requisition_id

ry-erp.pu_certified_purchase_order_operation
  bigint! command_id order_id actor_id
  varchar(20)!@utf8mb4_bin tenant_id
  varchar(128)!@utf8mb4_bin request_key
  char(64)!@utf8mb4_bin request_hash result_fingerprint
  varchar(32)!@utf8mb4_bin operation result_status
  int! expected_version result_version
  bigint? quote_id
  char(64)?@utf8mb4_bin quote_hash
  date? expected_arrival_date
  decimal(18,2)? total_amount amount_excluding_tax tax_amount
  datetime(3)! occurred_at
  #PRIMARY command_id
  #uk_operation_request tenant_id request_key
  #uk_order_operation tenant_id order_id operation

ry-erp.pu_certified_purchase_order_terms
  bigint! quote_id order_id entered_by vendor_id material_id base_uom_id
  varchar(20)!@utf8mb4_bin tenant_id
  int! quote_version
  varchar(128)!@utf8mb4_bin request_key source_request_key
  char(64)!@utf8mb4_bin request_hash source_fingerprint catalog_fingerprint order_fingerprint evidence_sha256 quote_hash
  datetime(3)! entered_at
  decimal(18,4)! quantity unit_price_excluding_tax
  decimal(10,4)! tax_rate
  varchar(16)!@utf8mb4_bin currency
  decimal(18,6)! exchange_rate
  date! promised_arrival_date valid_from valid_until
  varchar(128)!@utf8mb4_general_ci supplier_quote_reference supplier_contact
  varchar(255)!@utf8mb4_general_ci evidence_reference payment_terms delivery_terms
  varchar(64)!@utf8mb4_bin rounding_policy
  #PRIMARY quote_id
  #uk_quote_request tenant_id request_key
  #uk_order_quote_version tenant_id order_id quote_version

ry-erp.pu_certified_purchase_order_terms_confirmation
  bigint! confirmation_id quote_id actor_id
  varchar(20)!@utf8mb4_bin tenant_id
  char(64)!@utf8mb4_bin quote_hash request_hash
  varchar(128)!@utf8mb4_bin request_key
  varchar(64)!@utf8mb4_bin permission_code
  datetime(3)! confirmed_at
  varchar(500)!@utf8mb4_general_ci review_note
  #PRIMARY confirmation_id
  #uk_confirmation_request tenant_id request_key
  #uk_quote_confirmation tenant_id quote_id

ry-erp.pu_purchase_order
  bigint! order_id
  decimal(18,6)? exchange_rate
  decimal(18,2)? total_amount amount_excluding_tax tax_amount
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  int? certified_version
  bigint? certified_terms_id
  #PRIMARY order_id

ry-erp.pu_purchase_order_line
  bigint! line_id
  bigint? requisition_line_id
  decimal(18,4)? order_quantity received_quantity returned_quantity unit_price unit_price_excluding_tax
  decimal(10,4)? tax_rate
  decimal(18,2)? line_amount line_amount_excluding_tax line_tax_amount
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  #PRIMARY line_id

ry-erp.pu_purchase_requisition
  bigint! requisition_id
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  bigint? decision_run_id decision_action_id
  varchar(128)?@utf8mb4_unicode_ci decision_idempotency_key
  int! version
  #PRIMARY requisition_id
  #uk_pur_decision_idem tenant_id decision_idempotency_key

ry-erp.pu_purchase_requisition_line
  bigint! line_id
  decimal(18,4)? apply_quantity approved_quantity
  bigint? source_id
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  #PRIMARY line_id

ry-erp.pu_requirement_allocation_event
  bigint! event_id allocation_id requisition_line_id
  varchar(20)!@utf8mb4_unicode_ci tenant_id
  int! event_sequence
  varchar(128)!@utf8mb4_unicode_ci event_key
  varchar(40)!@utf8mb4_unicode_ci event_type to_status
  varchar(40)?@utf8mb4_unicode_ci from_status
  decimal(18,4)! occupied_base_before occupied_base_after
  bigint? order_line_id actor_id
  datetime(0)! occurred_at
  #PRIMARY event_id
  #uk_prae_tenant_event_key tenant_id event_key
  #uk_prae_tenant_allocation_sequence tenant_id allocation_id event_sequence

ry-erp.pu_requirement_allocation_ledger
  bigint! allocation_id material_id detail_id requisition_line_id base_uom_id
  varchar(20)!@utf8mb4_unicode_ci tenant_id
  varchar(32)!@utf8mb4_unicode_ci source_type
  int! allocation_no version
  bigint? order_line_id
  decimal(18,4)! covered_demand_base_qty occupied_base_qty fulfilled_base_qty released_base_qty
  varchar(40)!@utf8mb4_unicode_ci allocation_status
  varchar(128)!@utf8mb4_unicode_ci allocation_key
  varchar(128)?@utf8mb4_unicode_ci decision_idempotency_key
  datetime(0)! create_time
  datetime(0)? transfer_time released_at update_time
  varchar(255)?@utf8mb4_unicode_ci release_reason
  #PRIMARY allocation_id
  #uk_pral_tenant_key tenant_id allocation_key
  #uk_pral_tenant_req_detail_no tenant_id requisition_line_id detail_id allocation_no

ry-erp.pu_requirement_material_guard
  varchar(20)!@utf8mb4_unicode_ci tenant_id
  bigint! material_id
  int! version
  datetime(0)? update_time
  #PRIMARY tenant_id material_id

ry-erp.wm_certified_iqc_evidence
  bigint! evidence_id inspection_id receive_id material_id base_uom_id inspector_id
  varchar(20)!@ascii_bin tenant_id
  varchar(64)!@utf8mb4_unicode_ci inspection_code
  tinyint~! quantity_scale
  decimal(14,4)! inspected_quantity
  varchar(16)!@ascii_bin inspect_result
  varchar(48)!@ascii_bin source_kind
  varchar(96)!@ascii_bin source_version
  char(64)!@ascii_bin actual_body_sha256 receipt_scope_sha256 evidence_sha256
  varchar(128)!@ascii_bin first_message_id
  datetime(3)! source_created_at completed_at observed_at certified_at
  datetime(3)? source_updated_at
  bigint? birth_id
  char(64)?@utf8mb4_unicode_ci birth_sha256
  #PRIMARY evidence_id
  #uk_certified_iqc_inspection tenant_id inspection_id
  #uk_certified_iqc_scope tenant_id receive_id material_id
  #uk_certified_iqc_message tenant_id first_message_id

ry-erp.wm_certified_receipt_posting
  varchar(20)!@utf8mb4_unicode_ci tenant_id
  bigint! receive_id material_id actor_id
  char(64)!@utf8mb4_unicode_ci source_fingerprint
  decimal(18,4)! total_quantity
  datetime(0)! posted_at
  varchar(16)!@utf8mb4_unicode_ci notification_status
  #PRIMARY tenant_id receive_id

ry-erp.wm_material_receive_detail
  bigint! detail_id material_id
  bigint? line_id receive_id
  varchar(64)?@utf8mb4_unicode_ci base_uom_id
  decimal(18,4)! quantity
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  #PRIMARY detail_id

ry-erp.wm_material_receive_line
  bigint! line_id material_id
  bigint? receive_id purchase_line_id
  varchar(64)?@utf8mb4_unicode_ci base_uom_id
  decimal(18,4)! quantity_received
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  #PRIMARY line_id

ry-erp.wm_material_stock
  bigint! stock_id material_id
  varchar(64)?@utf8mb4_unicode_ci base_uom_id
  decimal(18,4)? stock_quantity reserved_quantity
  tinyint! frozen_flag
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  int! version
  decimal(14,6)? unit_cost
  decimal(14,4)? total_cost
  #PRIMARY stock_id

ry-erp.wm_material_stock_tx
  bigint! tx_id material_id material_stock_id
  varchar(64)?@utf8mb4_unicode_ci base_uom_id source_doc_type
  bigint? source_doc_id source_doc_line_id
  decimal(18,4)? tx_quantity before_quantity after_quantity
  varchar(20)?@utf8mb4_unicode_ci tenant_id
  decimal(14,6)? unit_cost
  decimal(14,4)? tx_amount
  #PRIMARY tx_id

ry-erp.wm_receipt_iqc_birth
  bigint! birth_id receive_id material_id base_uom_id captured_at vendor_id
  varchar(20)!@ascii_bin tenant_id
  tinyint~! quantity_scale
  decimal(14,4)! quantity
  char(64)!@utf8mb4_unicode_ci receipt_scope_sha256 birth_sha256
  varchar(64)!@utf8mb4_unicode_ci receive_code material_code
  varchar(255)!@utf8mb4_unicode_ci material_name vendor_name
  #PRIMARY birth_id
  #uk_iqc_birth_scope tenant_id receive_id material_id

ry-mes.qc_inspect_detail
  bigint! detail_id check_id
  varchar(20)!@utf8mb4_unicode_ci tenant_id
  varchar(16)!@utf8mb4_unicode_ci check_type
  #PRIMARY detail_id

ry-mes.qc_iqc
  bigint! iqc_id material_id
  varchar(20)!@utf8mb4_unicode_ci tenant_id
  varchar(64)!@utf8mb4_unicode_ci iqc_no
  bigint? receive_id
  decimal(14,4)! quantity
  decimal(14,4)? sample_qty
  datetime(0)? create_time
  #PRIMARY iqc_id
  #uk_iqc_no tenant_id iqc_no
  #uk_iqc_receive_material tenant_id receive_id material_id

ry-mes.qc_receipt_iqc_birth
  bigint! iqc_id birth_id receive_id material_id base_uom_id captured_at vendor_id
  varchar(20)!@utf8mb4_unicode_ci tenant_id
  int! quantity_scale
  decimal(14,4)! quantity
  varchar(64)!@utf8mb4_unicode_ci receipt_scope_sha256 birth_sha256 receive_code material_code binding_sha256
  varchar(255)!@utf8mb4_unicode_ci material_name vendor_name
  datetime(3)! bound_at
  #PRIMARY iqc_id
  #uk_native_iqc tenant_id iqc_id
  #uk_native_birth tenant_id birth_id
  #uk_native_scope tenant_id receive_id material_id

"""


def fresh_metadata():
    """把内存中的已核DDL夹具转换为目录输入，不读取源码或环境。"""
    result = {}
    entry = None
    for line in FRESH_DDL_SNAPSHOT.splitlines():
        if not line.strip():
            continue
        if not line.startswith(" "):
            entry = {"engine": "InnoDB", "columns": {}, "indexes": []}
            result[line] = entry
            continue
        spec, names = line.strip().split(" ", 1)
        if spec.startswith("#"):
            entry["indexes"].extend((spec[1:], 0, n, column, None, "BTREE")
                                    for n, column in enumerate(names.split(), 1))
            continue
        definition, _, collation = spec.partition("@")
        nullable = "YES" if definition.endswith("?") else "NO"
        definition = definition[:-1]
        unsigned = definition.endswith("~")
        definition = definition.rstrip("~")
        kind = definition.split("(", 1)[0]
        numbers = tuple(map(int, re.findall(r"[0-9]+", definition)))
        column_type = definition + (" unsigned" if unsigned else "")
        for column in names.split():
            entry["columns"][column] = {
                "type": kind, "nullable": nullable,
                "precision": numbers[0] if kind == "decimal" else None,
                "scale": numbers[1] if kind == "decimal" else None,
                "characterMaximumLength": numbers[0] if kind in ("char", "varchar") else None,
                "columnType": column_type, "collation": collation or None,
                "datetimePrecision": numbers[0] if kind == "datetime" else None,
                "extra": "", "generationExpression": "",
            }
    return result


class ProcurementSchemaContractTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec("procurement_schema_contract"),
                             "只读采购/IQC V2结构合同尚未实现")
        self.m = importlib.import_module("procurement_schema_contract")
        self.metadata = fresh_metadata()

    def test_fresh_ddl_snapshot_matches_only_schema(self):
        self.assertEqual([], self.m.metadata_gaps(self.metadata))
        report = self.m.schema_evidence(self.metadata)
        self.assertEqual("SCHEMA_METADATA_MATCH", report["status"])
        self.assertEqual("NOT_PERFORMED", report["sourceCertification"])
        self.assertIs(False, report["businessWriteAuthorized"])
        self.assertIs(False, report["ddlAuthorized"])
        json.dumps(report, allow_nan=False)

    def test_scope_is_exactly_permanent_tables_and_critical_columns(self):
        expected = set(self.metadata)
        actual = {schema + "." + table for schema, tables in self.m.TABLES.items() for table in tables}
        self.assertEqual(expected, actual)
        self.assertIn("ry-erp.wm_receipt_iqc_birth", actual)
        self.assertIn("ry-mes.qc_receipt_iqc_birth", actual)
        for unrelated in ("bd_vendor", "wm_warehouse", "wm_arrival_notice", "prd_production_plan"):
            self.assertNotIn("ry-erp." + unrelated, actual)

    def test_missing_every_required_table_fails_closed(self):
        for key in self.metadata:
            with self.subTest(table=key):
                bad = copy.deepcopy(self.metadata)
                del bad[key]
                self.assertIn(key + ": table unavailable", self.m.metadata_gaps(bad))
                self.assertEqual("BLOCKED", self.m.schema_evidence(bad)["status"])

    def test_missing_every_required_column_fails_closed(self):
        for key, entry in self.metadata.items():
            for column in entry["columns"]:
                with self.subTest(table=key, column=column):
                    bad = copy.deepcopy(self.metadata)
                    del bad[key]["columns"][column]
                    self.assertTrue(self.m.metadata_gaps(bad))

    def test_stock_only_metadata_does_not_prove_v2(self):
        stock = {key: entry for key, entry in self.metadata.items()
                 if key in ("ry-erp.wm_material_stock", "ry-erp.wm_material_stock_tx")}
        report = self.m.schema_evidence(stock)
        self.assertEqual("BLOCKED", report["status"])
        self.assertIn("ry-erp.wm_receipt_iqc_birth: table unavailable", report["gaps"])
        self.assertIn("ry-mes.qc_receipt_iqc_birth: table unavailable", report["gaps"])

    def test_all_required_tables_must_be_innodb(self):
        for key in self.metadata:
            with self.subTest(table=key):
                bad = copy.deepcopy(self.metadata)
                bad[key]["engine"] = "MyISAM"
                self.assertTrue(self.m.metadata_gaps(bad))

    def test_every_tenant_boundary_is_required_varchar20(self):
        for key in self.metadata:
            with self.subTest(table=key):
                bad = copy.deepcopy(self.metadata)
                bad[key]["columns"]["tenant_id"]["type"] = "bigint"
                self.assertTrue(self.m.metadata_gaps(bad))

    def test_every_decimal_requires_exact_native_precision(self):
        for key, entry in self.metadata.items():
            for name, column in entry["columns"].items():
                if column["type"] != "decimal":
                    continue
                for change in ({"type": "double"}, {"precision": 17}, {"scale": column["scale"] + 1},
                               {"precision": "18"}, {"scale": True}):
                    with self.subTest(table=key, column=name, change=change):
                        bad = copy.deepcopy(self.metadata)
                        bad[key]["columns"][name].update(change)
                        self.assertTrue(self.m.metadata_gaps(bad))

    def test_iqc_quantities_keep14_4_and_posting_keeps18_4(self):
        for key, name in (("ry-erp.wm_receipt_iqc_birth", "quantity"),
                          ("ry-erp.wm_certified_iqc_evidence", "inspected_quantity"),
                          ("ry-mes.qc_receipt_iqc_birth", "quantity"), ("ry-mes.qc_iqc", "quantity")):
            self.assertEqual((14, 4), (self.metadata[key]["columns"][name]["precision"],
                                     self.metadata[key]["columns"][name]["scale"]))
            bad = copy.deepcopy(self.metadata)
            bad[key]["columns"][name].update(precision=18, columnType="decimal(18,4)")
            self.assertTrue(self.m.metadata_gaps(bad))
        self.assertEqual(18, self.metadata["ry-erp.wm_certified_receipt_posting"]["columns"]["total_quantity"]["precision"])

    def test_non_nullable_new_facts_cannot_be_weakened(self):
        for key, name in (("ry-erp.wm_receipt_iqc_birth", "base_uom_id"),
                          ("ry-mes.qc_receipt_iqc_birth", "birth_id"),
                          ("ry-erp.pu_certified_purchase_command", "tenant_id"),
                          ("ry-erp.pu_certified_purchase_order_terms", "quantity")):
            with self.subTest(table=key, column=name):
                self.metadata[key]["columns"][name]["nullable"] = "YES"
                self.assertTrue(self.m.metadata_gaps(self.metadata))
                self.metadata = fresh_metadata()

    def test_incremental_nullable_history_is_not_upgraded_or_rejected(self):
        self.metadata["ry-erp.pu_requirement_allocation_event"]["columns"]["event_sequence"]["nullable"] = "YES"
        self.assertEqual([], self.m.metadata_gaps(self.metadata))
        for name in ("birth_id", "birth_sha256"):
            self.assertEqual("YES", self.metadata["ry-erp.wm_certified_iqc_evidence"]["columns"][name]["nullable"])
        self.assertEqual("NOT_PERFORMED", self.m.schema_evidence(self.metadata)["sourceCertification"])

    def test_identifier_lengths_and_binary_collations_are_required(self):
        key = "ry-erp.pu_certified_purchase_command"
        for change in ({"characterMaximumLength": 64}, {"characterMaximumLength": "128"},
                       {"collation": "utf8mb4_unicode_ci"}, {"columnType": "varchar(64)"}):
            with self.subTest(change=change):
                bad = copy.deepcopy(self.metadata)
                bad[key]["columns"]["request_key"].update(change)
                self.assertTrue(self.m.metadata_gaps(bad))

    def test_signedness_and_datetime_precision_are_not_guessed(self):
        mutations = (("ry-erp.wm_receipt_iqc_birth", "quantity_scale", "columnType", "tinyint"),
                     ("ry-mes.qc_receipt_iqc_birth", "bound_at", "datetimePrecision", 0),
                     ("ry-erp.pu_certified_purchase_order_terms", "entered_at", "datetimePrecision", 0),
                     ("ry-erp.pu_purchase_order", "certified_version", "columnType", "int unsigned"))
        for key, name, field, value in mutations:
            with self.subTest(table=key, column=name):
                bad = copy.deepcopy(self.metadata)
                bad[key]["columns"][name][field] = value
                self.assertTrue(self.m.metadata_gaps(bad))

    def test_integer_display_width_does_not_change_type_contract(self):
        self.metadata["ry-erp.pu_purchase_order"]["columns"]["certified_version"]["columnType"] = "int(11)"
        self.assertEqual([], self.m.metadata_gaps(self.metadata))

    def test_existing_native_iqc_created_precision_accepts_zero_to_three(self):
        column = self.metadata["ry-mes.qc_iqc"]["columns"]["create_time"]
        for precision in (0, 1, 2, 3):
            column.update(datetimePrecision=precision, columnType="datetime(" + str(precision) + ")")
            self.assertEqual([], self.m.metadata_gaps(self.metadata))
        column.update(datetimePrecision=6, columnType="datetime(6)")
        self.assertTrue(self.m.metadata_gaps(self.metadata))

    def test_generated_authoritative_column_fails_closed(self):
        key = "ry-erp.wm_material_stock"
        for field, value in (("extra", "STORED GENERATED"), ("generationExpression", "secret-expression")):
            bad = copy.deepcopy(self.metadata)
            bad[key]["columns"]["stock_quantity"][field] = value
            gaps = self.m.metadata_gaps(bad)
            self.assertTrue(gaps)
            self.assertNotIn("secret-expression", json.dumps(gaps))

    def test_missing_extended_column_metadata_is_not_accepted(self):
        column = self.metadata["ry-erp.pu_certified_purchase_command"]["columns"]["request_key"]
        for field in tuple(column):
            with self.subTest(field=field):
                bad = copy.deepcopy(self.metadata)
                del bad["ry-erp.pu_certified_purchase_command"]["columns"]["request_key"][field]
                self.assertTrue(self.m.metadata_gaps(bad))

    def test_missing_each_permanent_unique_key_fails_closed(self):
        for key, entry in self.metadata.items():
            for index in {row[0] for row in entry["indexes"]}:
                with self.subTest(table=key, index=index):
                    bad = copy.deepcopy(self.metadata)
                    bad[key]["indexes"] = [row for row in entry["indexes"] if row[0] != index]
                    self.assertTrue(self.m.metadata_gaps(bad))

    def test_unique_keys_reject_non_unique_prefix_reordered_extra_or_duplicate_parts(self):
        key = "ry-mes.qc_receipt_iqc_birth"
        rows = self.metadata[key]["indexes"]
        scope = [row for row in rows if row[0] == "uk_native_scope"]
        variants = [[(r[0], 1, *r[2:]) for r in scope],
                    [(*r[:4], 6, r[5]) for r in scope],
                    [(*r[:5], "HASH") for r in scope],
                    [(*r[:2], True, *r[3:]) for r in scope],
                    [(r[0], r[1], r[2], scope[-r[2]][3], r[4], r[5]) for r in scope],
                    scope + [("uk_native_scope", 0, 4, "binding_sha256", None, "BTREE")],
                    scope + [scope[0]]]
        for replacement in variants:
            with self.subTest(replacement=replacement):
                bad = copy.deepcopy(self.metadata)
                bad[key]["indexes"] = [r for r in rows if r[0] != "uk_native_scope"] + replacement
                self.assertTrue(self.m.metadata_gaps(bad))

    def test_primary_keys_cannot_be_replaced_by_named_unique_indexes(self):
        bad = copy.deepcopy(self.metadata)
        key = "ry-erp.wm_certified_receipt_posting"
        bad[key]["indexes"] = [("arbitrary", *r[1:]) for r in bad[key]["indexes"]]
        self.assertTrue(self.m.metadata_gaps(bad))

    def test_named_command_contract_rejects_extra_unique_key_but_allows_secondary_indexes(self):
        key = "ry-erp.pu_certified_purchase_command"
        self.metadata[key]["indexes"].append(("idx_actor", 1, 1, "actor_id", None, "BTREE"))
        self.assertEqual([], self.m.metadata_gaps(self.metadata))
        self.metadata[key]["indexes"].append(("global_request", 0, 1, "request_key", None, "BTREE"))
        self.assertTrue(self.m.metadata_gaps(self.metadata))

    def test_equivalent_non_command_unique_key_names_are_allowed(self):
        key = "ry-erp.wm_receipt_iqc_birth"
        self.metadata[key]["indexes"] = [("renamed_scope" if r[0] != "PRIMARY" else r[0], *r[1:])
                                          for r in self.metadata[key]["indexes"]]
        self.assertEqual([], self.m.metadata_gaps(self.metadata))

    def test_separate_indexes_and_embedded_indexes_are_equivalent(self):
        indexes = {key: entry.pop("indexes") for key, entry in self.metadata.items()}
        self.assertEqual([], self.m.metadata_gaps(self.metadata, indexes))
        self.assertEqual("SCHEMA_METADATA_MATCH", self.m.schema_evidence(self.metadata, indexes)["status"])
        self.assertTrue(self.m.metadata_gaps(self.metadata, {}))

    def test_order_independent_deterministic_sanitized_report(self):
        bad = copy.deepcopy(self.metadata)
        bad["ry-erp.wm_receipt_iqc_birth"]["engine"] = "private-password"
        bad["ry-mes.qc_receipt_iqc_birth"]["indexes"][0] = ("private-token", 0, 1, "private-column", None, "BTREE")
        bad["private-schema.private-table"] = {"secret": "secret-payload"}
        before = copy.deepcopy(bad)
        report = self.m.schema_evidence(bad)
        reversed_input = dict(reversed(list(bad.items())))
        for entry in reversed_input.values():
            if "indexes" in entry:
                entry["indexes"] = list(reversed(entry["indexes"]))
        self.assertEqual(report, self.m.schema_evidence(reversed_input))
        encoded = json.dumps(report, allow_nan=False, sort_keys=True)
        for value in ("private-password", "private-token", "private-column", "private-schema", "secret-payload"):
            self.assertNotIn(value, encoded)
        self.assertEqual(before["ry-erp.wm_receipt_iqc_birth"]["engine"], bad["ry-erp.wm_receipt_iqc_birth"]["engine"])

    def test_bad_metadata_shapes_produce_fixed_diagnostics(self):
        for value in (None, [], "private-password", 1):
            with self.subTest(value=value):
                self.assertEqual(["procurement_schema_metadata_invalid"], self.m.metadata_gaps(value))
        for value in (None, [], "private-password", {"columns": []}):
            bad = copy.deepcopy(self.metadata)
            bad["ry-erp.wm_receipt_iqc_birth"] = value
            self.assertTrue(self.m.metadata_gaps(bad))
            self.assertNotIn("private-password", json.dumps(self.m.schema_evidence(bad)))

    def test_schema_only_never_reads_or_authorizes_historical_data(self):
        self.metadata["historicalRows"] = {"verified": True, "stockCount": 0, "sourceCertified": True}
        report = self.m.schema_evidence(self.metadata)
        self.assertEqual("SCHEMA_METADATA_MATCH", report["status"])
        self.assertEqual("NOT_PERFORMED", report["sourceCertification"])
        self.assertIs(False, report["businessWriteAuthorized"])
        self.assertIs(False, report["ddlAuthorized"])
        self.assertNotIn("stockCount", report)

    def test_cursor_query_helpers_use_only_parameterized_information_schema_selects(self):
        for schema in self.m.TABLES:
            for function in (self.m.table_metadata_query, self.m.column_metadata_query, self.m.index_metadata_query):
                sql, parameters = function(schema)
                self.assertTrue(sql.startswith("SELECT "))
                self.assertIn("information_schema.", sql)
                self.assertNotIn(schema, sql)
                self.assertEqual(schema, parameters[0])
                self.assertEqual(len(parameters), sql.count("%s"))
            column_sql, _ = self.m.column_metadata_query(schema)
            for name in ("IS_NULLABLE", "CHARACTER_MAXIMUM_LENGTH", "COLUMN_TYPE", "COLLATION_NAME",
                         "DATETIME_PRECISION", "EXTRA", "GENERATION_EXPRESSION"):
                self.assertIn(name, column_sql)
            index_sql, _ = self.m.index_metadata_query(schema)
            self.assertIn("INDEX_TYPE", index_sql)
            self.assertNotIn("NON_UNIQUE=0", index_sql)
        for function in (self.m.table_metadata_query, self.m.column_metadata_query, self.m.index_metadata_query):
            with self.assertRaisesRegex(self.m.SchemaMetadataError, "^采购结构元数据输入无效，详细内容已抑制$"):
                function("private-password")

    def test_cursor_tuple_rows_round_trip_and_duplicate_rows_fail_closed(self):
        schema = "ry-erp"
        tables, columns, indexes = [], [], []
        fields = ("type", "precision", "scale", "nullable", "characterMaximumLength", "columnType",
                  "collation", "datetimePrecision", "extra", "generationExpression")
        for key, entry in self.metadata.items():
            if not key.startswith(schema + "."):
                continue
            table = key.split(".", 1)[1]
            tables.append((table, entry["engine"]))
            columns.extend((table, name, *(spec[field] for field in fields)) for name, spec in entry["columns"].items())
            indexes.extend((table, *row) for row in entry["indexes"])
        shaped = self.m.metadata_shape(schema, tables, columns, indexes)
        self.assertEqual({key: value for key, value in self.metadata.items() if key.startswith(schema + ".")}, shaped)
        for args in ((tables + tables[:1], columns, indexes), (tables, columns + columns[:1], indexes),
                     (tables, columns, indexes + indexes[:1]), (tables, [columns[0][:-1]], indexes),
                     (tables, [("private-table", *columns[0][1:])], indexes)):
            with self.subTest(args=args), self.assertRaises(self.m.SchemaMetadataError) as error:
                self.m.metadata_shape(schema, *args)
            self.assertNotIn("private-table", str(error.exception))


if __name__ == "__main__":
    unittest.main()
