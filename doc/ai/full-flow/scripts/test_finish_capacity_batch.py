"""离线容量与 FINISH 来源回归；全部业务身份为合成值，无连接或写入。"""
import importlib.util
from pathlib import Path
import unittest
from decimal import Decimal


class FinishCapacityBatchTest(unittest.TestCase):
    def setUp(self):
        path = Path(__file__).with_name("finish_capacity_batch.py")
        self.assertTrue(path.exists(), "容量规划器尚未实施")
        spec = importlib.util.spec_from_file_location("finish_capacity_batch", path)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.categories = [
            {"categoryId": "1", "parentId": "0", "categoryCode": "FINISH", "status": 1},
            {"categoryId": "2", "parentId": "1", "categoryCode": "CHILD", "status": 1},
            {"categoryId": "3", "parentId": "0", "categoryCode": "RAW", "status": 1}]

    def product(self, id="10", hours="1", setup="1", resource="WORKSTATION:1", labor="1"):
        return {"materialId": id, "categoryId": "2", "status": 1,
                "steps": [{"resourceKey": resource, "workshopKey": "LABOR:1",
                           "unitHours": hours, "setupHours": setup, "laborCount": labor}]}

    def plan(self, products=None, budgets=None, **kwargs):
        return self.module.plan_orders([self.product()] if products is None else products, self.categories,
            {"WORKSTATION:1": "20", "LABOR:1": "20"} if budgets is None else budgets, **kwargs)

    def test_finish_descendants_only(self):
        self.assertEqual({"1", "2"}, self.module.finish_category_ids(self.categories))

    def test_missing_finish_refused(self):
        with self.assertRaises(ValueError): self.module.finish_category_ids(self.categories[1:])

    def test_non_finish_product_refused(self):
        product = self.product(); product["categoryId"] = "3"
        with self.assertRaises(ValueError): self.plan([product])

    def test_disabled_product_refused(self):
        product = self.product(); product["status"] = 0
        with self.assertRaises(ValueError): self.plan([product])

    def test_duplicate_category_refused(self):
        with self.assertRaises(ValueError): self.module.finish_category_ids(self.categories + [self.categories[0]])

    def test_invalid_snowflake_id_refused(self):
        with self.assertRaises(ValueError): self.plan([self.product(id="9223372036854775808")])

    def test_one_lot_respects_setup_and_utilization(self):
        value = self.plan(lots_per_product=1, utilization="0.8")
        self.assertEqual(15, value["orders"][0]["quantity"])
        self.assertEqual("16.00", value["loads"]["WORKSTATION:1"])

    def test_batch_shared_station_conservation(self):
        value = self.plan([self.product(), self.product(id="11")], lots_per_product=2, utilization="0.8")
        self.assertEqual(4, len(value["orders"]))
        self.assertEqual([3,3,3,3], [row["quantity"] for row in value["orders"]])
        self.assertLessEqual(Decimal(value["loads"]["WORKSTATION:1"]), Decimal("16"))

    def test_existing_occupation_deducted(self):
        value = self.plan(lots_per_product=1, utilization="1", occupied={"WORKSTATION:1":"10", "LABOR:1":"10"})
        self.assertEqual(9, value["orders"][0]["quantity"])

    def test_labor_pool_is_shared_across_different_workstations(self):
        value = self.plan([self.product(), self.product(id="11",resource="WORKSTATION:2")],
            {"WORKSTATION:1":"100", "WORKSTATION:2":"100", "LABOR:1":"20"},lots_per_product=1,utilization="1")
        self.assertEqual([9,9], [row["quantity"] for row in value["orders"]])
        self.assertEqual("20.00", value["loads"]["LABOR:1"])

    def test_labor_count_cannot_be_ignored(self):
        value = self.plan([self.product(labor="2")], {"WORKSTATION:1":"100", "LABOR:1":"20"},lots_per_product=1,utilization="1")
        self.assertEqual(9, value["orders"][0]["quantity"])

    def test_unknown_resource_refused(self):
        with self.assertRaises(ValueError): self.plan(budgets={"LABOR:1":"20"})

    def test_zero_or_negative_capacity_refused(self):
        for capacity in ("0", "-1", "NaN", "Infinity"):
            with self.subTest(capacity=capacity), self.assertRaises(ValueError):
                self.plan(budgets={"WORKSTATION:1":capacity, "LABOR:1":"20"})

    def test_overbooked_resource_refused(self):
        with self.assertRaises(ValueError): self.plan(occupied={"WORKSTATION:1":"21"})

    def test_unknown_occupied_resource_refused(self):
        with self.assertRaises(ValueError): self.plan(occupied={"WORKSTATION:99":"1"})

    def test_negative_occupation_refused(self):
        with self.assertRaises(ValueError): self.plan(occupied={"WORKSTATION:1":"-1"})

    def test_insufficient_setup_plus_one_piece_refused(self):
        with self.assertRaises(ValueError): self.plan(budgets={"WORKSTATION:1":"1", "LABOR:1":"1"})

    def test_per_lot_cap_is_not_exceeded(self):
        product = self.product(); product["maxLotQuantity"] = 5
        value = self.plan([product], lots_per_product=2, utilization="1")
        self.assertEqual([5,5], [row["quantity"] for row in value["orders"]])

    def test_precision_is_conservative_at_native_centihour_boundary(self):
        value = self.plan([self.product(hours="0.0049", setup="0")],
            {"WORKSTATION:1":"0.01", "LABOR:1":"0.01"},lots_per_product=1,utilization="1")
        self.assertEqual(2, value["orders"][0]["quantity"])
        self.assertEqual("0.01", value["loads"]["WORKSTATION:1"])

    def test_invalid_unit_hours_refused(self):
        for hours in ("0", "-1", "NaN"):
            with self.subTest(hours=hours), self.assertRaises(ValueError): self.plan([self.product(hours=hours)])

    def test_large_snowflake_id_kept_as_string(self):
        value = self.plan([self.product(id="9007199254740993")],lots_per_product=1)
        self.assertEqual("9007199254740993", value["orders"][0]["materialId"])

    def test_utilization_cannot_expand_base_capacity(self):
        for ratio in ("0", "1.01", "NaN", "-1"):
            with self.subTest(ratio=ratio), self.assertRaises(ValueError): self.plan(utilization=ratio)

    def test_no_implicit_product_or_lot_limits(self):
        with self.assertRaises(ValueError): self.plan(lots_per_product=0)
        with self.assertRaises(ValueError): self.plan([])

    def test_duplicate_material_identity_refused(self):
        with self.assertRaises(ValueError): self.plan([self.product(),self.product()])

    def test_quantity_weights_fill_capacity_without_reusing_it(self):
        fast = self.product(); fast["quantityWeight"] = 3
        value = self.plan([fast, self.product(id="11")], lots_per_product=1, utilization="0.8")
        self.assertEqual([9,3], [row["quantity"] for row in value["orders"]])
        self.assertEqual("14.00", value["loads"]["WORKSTATION:1"])

    def test_invalid_quantity_weight_refused(self):
        for weight in (0, -1, True, "2"):
            product = self.product(); product["quantityWeight"] = weight
            with self.subTest(weight=weight), self.assertRaises(ValueError): self.plan([product])

    def daily(self, demand=None, limits=None, operators=None, labor="16", fixed=None):
        method = getattr(self.module, "allocate_day_capacity", None)
        self.assertTrue(callable(method), "逐日人力守恒分配器尚未实施")
        return method(demand or {"A":"1", "B":"1"}, limits or {"A":"8", "B":"8"},
                      operators or {"A":"2", "B":"2"}, labor, fixed=fixed)

    def test_daily_equal_split_conserves_operator_hours(self):
        self.assertEqual({"A":"4.00", "B":"4.00"}, self.daily())

    def test_daily_weighted_split(self):
        self.assertEqual({"A":"6.00", "B":"2.00"}, self.daily({"A":"3", "B":"1"}))

    def test_daily_physical_limit_clamps_and_redistributes(self):
        self.assertEqual({"A":"2.00", "B":"6.00"}, self.daily({"A":"9", "B":"1"},{"A":"2", "B":"8"}))

    def test_daily_leave_reduces_total_capacity(self):
        self.assertEqual({"A":"2.00", "B":"2.00"}, self.daily(labor="8"))

    def test_daily_fixed_calendar_preserved_without_duplicate_capacity(self):
        self.assertEqual({"A":"6.00", "B":"2.00"}, self.daily(fixed={"A":"6"}))

    def test_daily_fixed_overbooking_refused(self):
        with self.assertRaises(ValueError): self.daily(labor="8",fixed={"A":"6"})

    def test_daily_closed_day_has_no_implicit_eight_hour_fallback(self):
        self.assertEqual({"A":"0.00", "B":"0.00"}, self.daily(limits={"A":"0","B":"0"},labor="0"))

    def test_daily_unknown_operator_resource_refused(self):
        with self.assertRaises(ValueError): self.daily(operators={"A":"2"})

    def native_unit(self, **changes):
        method = getattr(self.module, "native_unit_ready", None)
        self.assertTrue(callable(method), "原生基本单位准入检查尚未实施")
        unit = {"uomId":"5", "primaryId":"5", "isBase":1, "status":1,
                "conversionRate":"1", "quantityScale":0, "conversionVersion":"v1",
                "conversionVerifiedAt":"2026-10-06 09:00:00", "uomName":"件"}
        unit.update(changes)
        return method({"baseUomId":"5", "status":1}, unit)

    def test_native_verified_base_unit_ready(self):
        self.assertTrue(self.native_unit())

    def test_rate_one_derived_unit_is_not_native_base(self):
        self.assertFalse(self.native_unit(isBase=0,primaryId="6"))

    def test_native_base_requires_verified_precision(self):
        self.assertFalse(self.native_unit(quantityScale=None))

    def test_native_base_requires_version_and_verification_time(self):
        self.assertFalse(self.native_unit(conversionVersion=None))
        self.assertFalse(self.native_unit(conversionVerifiedAt=None))

    def test_native_base_rejects_foreign_tenant(self):
        self.assertFalse(self.native_unit(tenantId="111111"))

    def test_deterministic_plan_does_not_mutate_inputs(self):
        import copy
        products=[self.product(),self.product(id="11")]; original=copy.deepcopy(products)
        a=self.plan(products,lots_per_product=2); b=self.plan(products,lots_per_product=2)
        self.assertEqual(a,b); self.assertEqual(original,products)


if __name__ == "__main__": unittest.main()
