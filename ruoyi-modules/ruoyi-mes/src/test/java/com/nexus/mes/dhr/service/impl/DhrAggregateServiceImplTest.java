package com.nexus.mes.dhr.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.mes.dhr.domain.DhrSectionCode;
import com.nexus.mes.dhr.mapper.DhrAggregateMapper;
import com.nexus.mes.dhr.support.DhrSignatureAccessor;
import com.nexus.mes.pro.domain.ProWorkOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DHR 分册聚合 Service 单元测试。
 *
 * <p>覆盖（修复前必失败 —— 审计 §4 第 7 项：DHR 聚合能力不存在）：</p>
 * <ol>
 *   <li>10 个固定分册 key 恒存在（无数据也要能落空分册）；</li>
 *   <li>关联口径诚实标注：直连 {@code BY_WORK_ORDER}；IQC 走批次链 {@code BY_LOT_IQC}、
 *       回退 {@code BY_MATERIAL_TIME}；OQC 仅 {@code BY_MATERIAL_TIME}；
 *       设备 {@code BY_EQUIPMENT_TIME}；SN 绑定 {@code BY_SN}；</li>
 *   <li>SOP 分册带 master 库签名摘要（经 DhrSignatureAccessor，REQUIRES_NEW 走 master）；</li>
 *   <li>缺少租户上下文时快速失败（多租户隔离不可降级）。</li>
 * </ol>
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DhrAggregateServiceImplTest {

    @Mock private DhrAggregateMapper aggregateMapper;
    @Mock private DhrSignatureAccessor signatureAccessor;
    @InjectMocks private DhrAggregateServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn("000000");
    }

    @AfterEach
    void tearDown() {
        if (loginHelperMock != null) {
            loginHelperMock.close();
        }
    }

    private ProWorkOrder workOrder() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(100L);
        wo.setWoNo("WO001");
        wo.setMaterialId(200L);
        wo.setEquipmentId(300L);
        wo.setPlannedQty(new BigDecimal("100"));
        wo.setCreateTime(new Date());
        return wo;
    }

    private Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private List<Map<String, Object>> list(Map<String, Object>... rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            result.add(r);
        }
        return result;
    }

    private List<String> linkagesOf(List<Map<String, Object>> items, String entryType) {
        List<String> linkages = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (entryType.equals(item.get("entry_type"))) {
                linkages.add(String.valueOf(item.get("linkage")));
            }
        }
        return linkages;
    }

    private String entryTypesOf(List<Map<String, Object>> items) {
        List<String> types = new ArrayList<>();
        for (Map<String, Object> item : items) {
            types.add(String.valueOf(item.get("entry_type")));
        }
        return String.join(",", types);
    }

    @Test
    void aggregateAlwaysReturnsAllTenSections() {
        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        assertEquals(10, result.size());
        for (String code : DhrSectionCode.ordered()) {
            assertNotNull(result.get(code), "缺少分册: " + code);
            assertTrue(result.get(code).isEmpty(), "无数据分册应为空列表: " + code);
        }
    }

    @Test
    void aggregateMarksDirectWorkOrderLinkage() {
        when(aggregateMapper.selectWorkOrder(eq(100L), eq("000000")))
            .thenReturn(list(row("wo_no", "WO001")));
        when(aggregateMapper.selectFlowCardSteps(eq(100L), eq("000000")))
            .thenReturn(list(row("step_record_id", 1L)));
        when(aggregateMapper.selectWoConsumptions(eq(100L), eq("000000")))
            .thenReturn(list(row("consumption_id", 5L, "lot_id", 50L)));

        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        assertEquals(List.of("BY_WORK_ORDER"), linkagesOf(result.get(DhrSectionCode.WO_INFO), "WORK_ORDER"));
        assertEquals(List.of("BY_WORK_ORDER"),
            linkagesOf(result.get(DhrSectionCode.PROCESS), "FLOW_CARD_STEP"));
        assertEquals(List.of("BY_WORK_ORDER"),
            linkagesOf(result.get(DhrSectionCode.MATERIAL), "WO_CONSUMPTION"));
    }

    @Test
    void iqcUsesLotChainWhenIqcIdAvailable() {
        when(aggregateMapper.selectWoConsumptions(eq(100L), eq("000000")))
            .thenReturn(list(row("consumption_id", 5L, "lot_id", 50L)));
        when(aggregateMapper.selectLotsByIds(any(), eq("000000")))
            .thenReturn(list(row("lot_id", 50L, "lot_code", "LOT-A", "iqc_id", 7L)));
        when(aggregateMapper.selectIqcByIds(any(), eq("000000")))
            .thenReturn(list(row("iqc_id", 7L, "iqc_no", "IQC001")));

        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        List<Map<String, Object>> qc = result.get(DhrSectionCode.QC);
        assertEquals(List.of("BY_LOT_IQC"), linkagesOf(qc, "IQC"));
        // 批次主档经消耗关联
        assertEquals(List.of("BY_WO_CONSUMPTION"), linkagesOf(result.get(DhrSectionCode.MATERIAL), "LOT"));
    }

    @Test
    void iqcFallsBackToMaterialTimeWindow() {
        // 无消耗 → 无批次 → 无 iqc_id：回退「物料 + 工单时间窗」，并如实标注
        when(aggregateMapper.selectIqcByMaterialTime(eq(200L), any(), any(), eq("000000")))
            .thenReturn(list(row("iqc_id", 9L, "iqc_no", "IQC009")));
        when(aggregateMapper.selectOqcByMaterialTime(eq(200L), any(), any(), eq("000000")))
            .thenReturn(list(row("oqc_id", 11L, "oqc_no", "OQC011")));

        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        List<Map<String, Object>> qc = result.get(DhrSectionCode.QC);
        assertEquals(List.of("BY_MATERIAL_TIME"), linkagesOf(qc, "IQC"));
        // OQC 无工单外键，唯一口径就是物料 + 时间窗
        assertEquals(List.of("BY_MATERIAL_TIME"), linkagesOf(qc, "OQC"));
        verify(aggregateMapper).selectOqcByMaterialTime(eq(200L), any(), any(), eq("000000"));
    }

    @Test
    void equipmentAndStationDeviceUseEquipmentTimeLinkage() {
        when(aggregateMapper.selectStationReports(eq(100L), eq("000000")))
            .thenReturn(list(row("report_id", 1L, "station_code", "ST01")));
        when(aggregateMapper.selectStationDevicesByStations(any(), eq("000000")))
            .thenReturn(list(row("device_id", 1L, "device_code", "PC01")));
        when(aggregateMapper.selectEquipmentByWorkcenterCodes(any(), eq("000000")))
            .thenReturn(list(row("equipment_id", 300L, "equipment_code", "EQ001")));
        when(aggregateMapper.selectMaintenanceRecords(any(), any(), any(), eq("000000")))
            .thenReturn(list(row("record_id", 2L, "equipment_id", 300L)));

        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        List<Map<String, Object>> equipment = result.get(DhrSectionCode.EQUIPMENT);
        assertEquals(List.of("BY_EQUIPMENT_TIME"), linkagesOf(equipment, "STATION_DEVICE"));
        assertEquals(List.of("BY_EQUIPMENT_TIME"), linkagesOf(equipment, "MAINTENANCE_RECORD"));
        verify(aggregateMapper).selectMaintenanceRecords(any(), any(), any(), eq("000000"));
    }

    @Test
    void snBindingsUseSnLinkage() {
        when(aggregateMapper.selectSnByWo(eq(100L), any(), eq("000000")))
            .thenReturn(list(row("sn_id", 1L, "sn_code", "SN001")));
        when(aggregateMapper.selectSnBindingsBySnIds(any(), eq("000000")))
            .thenReturn(list(row("binding_id", 3L, "sn_id", 1L, "lot_code", "LOT-A")));

        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        List<Map<String, Object>> sn = result.get(DhrSectionCode.SN_TRACE);
        assertEquals(List.of("BY_WORK_ORDER"), linkagesOf(sn, "SN"));
        assertEquals(List.of("BY_SN"), linkagesOf(sn, "SN_LOT_BINDING"));
    }

    @Test
    void sopSectionCarriesSignatureSummaryFromMaster() {
        when(aggregateMapper.selectSopViewLogs(eq(100L), eq("000000")))
            .thenReturn(list(row("log_id", 1L, "sop_id", 88L, "version_no", "V1")));
        SignatureVo signature = new SignatureVo();
        signature.setSignatureId(999L);
        signature.setSignerName("张三");
        signature.setAuthMethod("LOGIN_ONLY");
        signature.setSignedAt(new Date());
        when(signatureAccessor.listByBiz(SignBizType.SOP_VIEW, 88L)).thenReturn(List.of(signature));

        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        List<Map<String, Object>> sop = result.get(DhrSectionCode.SOP);
        assertEquals(1, sop.size());
        assertEquals("SOP_VIEW", entryTypesOf(sop));
        assertEquals("BY_WORK_ORDER", String.valueOf(sop.get(0).get("linkage")));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) sop.get(0).get("signature");
        assertNotNull(summary, "SOP 分册应带 es_signature(SOP_VIEW) 摘要");
        assertEquals(999L, summary.get("signature_id"));
        assertEquals("张三", summary.get("signer_name"));
    }

    @Test
    void sopSignatureFailureDoesNotBreakAggregation() {
        when(aggregateMapper.selectSopViewLogs(eq(100L), eq("000000")))
            .thenReturn(list(row("log_id", 1L, "sop_id", 88L)));
        when(signatureAccessor.listByBiz(anyString(), any()))
            .thenThrow(new ServiceException("Table 'ry-mes.es_signature' doesn't exist"));

        Map<String, List<Map<String, Object>>> result = service.aggregate(workOrder(), null, null);

        assertEquals(1, result.get(DhrSectionCode.SOP).size());
        assertNull(result.get(DhrSectionCode.SOP).get(0).get("signature"));
    }

    @Test
    void aggregateFailsFastWithoutTenantContext() {
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.aggregate(workOrder(), null, null));
        assertTrue(ex.getMessage().contains("租户"));
    }
}
