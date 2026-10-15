package com.nexus.mes.report.adapter;

import static com.nexus.mes.report.adapter.FoundationReportFixture.*;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** MES基础报表保留真实业务口径与独立数据源隔离。 */
class FoundationDatasetTest {
    /** 验证无计划日期的待执行工单仍可按创建日期检索，数量按物料和单位分组。 */
    @Test
    void workOrdersIncludeUnplannedRecordsAndRespectScopes() throws Exception {
        var f = new FoundationReportFixture("pro_work_order");
        for (int i = 0; i < 4; i++)
            f.insert(
                    "pro_work_order",
                    "mes_wo_id",
                    i + 1L,
                    "wo_no",
                    "WO-" + i,
                    "material_id",
                    4L,
                    "material_code",
                    "M004",
                    "material_name",
                    "合成物料",
                    "unit_name",
                    "PCS",
                    "workshop_id",
                    5L,
                    "workshop_name",
                    "测试车间",
                    "wo_status",
                    "CREATED",
                    "planned_qty",
                    new BigDecimal("12.1250"),
                    "create_time",
                    "2026-10-15 23:30:00",
                    "tenant_id",
                    i == 1 ? "B" : "A",
                    "create_dept",
                    i == 2 ? 20L : 10L,
                    "del_flag",
                    i == 3 ? "1" : "0");
        var p = f.provider("WorkOrderTrackingDatasetProvider");
        var r =
                p.query(
                        context("mes:pro:work:list"),
                        query(
                                p,
                                Map.of(
                                        "fromDate",
                                        "2026-10-01",
                                        "toDate",
                                        "2026-10-15",
                                        "workOrderId",
                                        "1",
                                        "materialId",
                                        "4",
                                        "workshopId",
                                        "5")));
        assertEquals(1, r.total());
        assertEquals("已创建", r.rows().get(0).get("status"));
        assertNull(r.rows().get(0).get("planStartTime"));
        assertEquals(
                0,
                new BigDecimal("12.1250")
                        .compareTo((BigDecimal) r.rows().get(0).get("plannedQuantity")));
        assertEquals(
                List.of("materialCode", "unitName"),
                p.descriptor().field("plannedQuantity").aggregationKeys());
        assertThrows(
                DatasetAccessException.class,
                () -> p.query(context("mes:pro:workOrder:list"), query(p, Map.of())));
    }

    /** 验证待检记录不因检验时间为空而消失，无单位的检验量禁止相加。 */
    @Test
    void iqcRecordsIncludePendingAndForbidAmbiguousTotals() throws Exception {
        var f = new FoundationReportFixture("qc_iqc");
        for (int i = 0; i < 3; i++)
            f.insert(
                    "qc_iqc",
                    "iqc_id",
                    i + 1L,
                    "iqc_no",
                    "IQC-" + i,
                    "material_id",
                    4L,
                    "vendor_id",
                    8L,
                    "material_code",
                    "M004",
                    "quantity",
                    new BigDecimal("0.1250"),
                    "sample_qty",
                    new BigDecimal("0.0250"),
                    "status",
                    "PENDING",
                    "inspect_result",
                    "PENDING",
                    "create_time",
                    "2026-10-15 23:30:00",
                    "tenant_id",
                    i == 1 ? "B" : "A",
                    "del_flag",
                    i == 2 ? "1" : "0");
        var p = f.provider("IqcRecordsDatasetProvider");
        var r =
                p.query(
                        context("mes:qc:iqc:list"),
                        query(
                                p,
                                Map.of(
                                        "fromDate",
                                        "2026-10-01",
                                        "toDate",
                                        "2026-10-15",
                                        "iqcId",
                                        "1",
                                        "materialId",
                                        "4",
                                        "vendorId",
                                        "8")));
        assertEquals(1, r.total());
        assertEquals("待检验", r.rows().get(0).get("status"));
        assertEquals("待判定", r.rows().get(0).get("result"));
        assertNull(r.rows().get(0).get("inspectTime"));
        assertFalse(p.descriptor().field("quantity").aggregatable());
        assertFalse(p.descriptor().field("sampleQuantity").aggregatable());
    }

    /** 验证MES候选按完整ID精确回显，且查询权限与列表权限不会互相替代。 */
    @Test
    void mesLookupsRequireTheirOwnPermissionAndKeepExactId() throws Exception {
        var f = new FoundationReportFixture("qc_iqc", "pro_work_order");
        f.insert(
                "qc_iqc",
                "iqc_id",
                Long.MAX_VALUE,
                "iqc_no",
                "IQC-MAX",
                "material_id",
                4L,
                "quantity",
                1,
                "material_name",
                "合成物料");
        var p = new MesReportLookupProvider(f.jdbc);
        var binding =
                p.bindings().stream()
                        .filter(b -> b.datasetCode().equals("MES_IQC_RECORDS"))
                        .findFirst()
                        .orElseThrow();
        var option =
                p.selected(binding, context("mes:qc:iqc:list"), Long.toString(Long.MAX_VALUE))
                        .orElseThrow();
        assertEquals(Long.toString(Long.MAX_VALUE), option.value());
        assertTrue(option.label().contains("IQC-MAX"));
        assertThrows(
                DatasetAccessException.class,
                () ->
                        p.selected(
                                binding,
                                context("mes:qc:iqc:query"),
                                Long.toString(Long.MAX_VALUE)));
        f.jdbc.update("UPDATE qc_iqc SET create_dept=20");
        assertTrue(
                p.selected(binding, context("mes:qc:iqc:list"), Long.toString(Long.MAX_VALUE))
                        .isEmpty());
    }
}
