package com.nexus.station.service;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.station.domain.MesMaterialLoad;
import com.nexus.station.domain.dto.StationMaterialLoadDto;
import com.nexus.station.mapper.MesMaterialLoadMapper;
import com.nexus.station.mapper.StationErpBatchMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * StationMaterialService 单元测试 — 上料防错比对与台账(C19/C20)。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class StationMaterialServiceTest {

    private static final String STATION = "STN|注塑车间|产线1|ASM|GENERAL";

    @Mock
    private StationPassService stationPassService;
    @Mock
    private ProFlowCardStepMapper stepMapper;
    @Mock
    private ProFlowCardMapper flowCardMapper;
    @Mock
    private ProWorkOrderBomLineMapper bomLineMapper;
    @Mock
    private MesMaterialLoadMapper loadMapper;
    @Mock
    private StationErpBatchMapper erpBatchMapper;
    @InjectMocks
    private StationMaterialService service;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> stepColumns = new HashMap<>();
        stepColumns.put("PROCESSCODE", new ColumnCache("process_code", "process_code"));
        stepColumns.put("STEPSTATUS", new ColumnCache("step_status", "step_status"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(ProFlowCardStep.class)).thenReturn(stepColumns);
        Map<String, ColumnCache> lineColumns = new HashMap<>();
        lineColumns.put("MESWOID", new ColumnCache("mes_wo_id", "mes_wo_id"));
        lineColumns.put("PROCESSCODE", new ColumnCache("process_code", "process_code"));
        lineColumns.put("SORTORDER", new ColumnCache("sort_order", "sort_order"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(ProWorkOrderBomLine.class)).thenReturn(lineColumns);
        Map<String, ColumnCache> loadColumns = new HashMap<>();
        loadColumns.put("LOADID", new ColumnCache("load_id", "load_id"));
        loadColumns.put("MESWOID", new ColumnCache("mes_wo_id", "mes_wo_id"));
        loadColumns.put("STATIONCODE", new ColumnCache("station_code", "station_code"));
        loadColumns.put("MATERIALID", new ColumnCache("material_id", "material_id"));
        loadColumns.put("STATUS", new ColumnCache("status", "status"));
        loadColumns.put("LOADTIME", new ColumnCache("load_time", "load_time"));
        loadColumns.put("REMARK", new ColumnCache("remark", "remark"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesMaterialLoad.class)).thenReturn(loadColumns);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    // ==================== C19 上料校验+登记 ====================

    @Test
    void load_batchCodeMainMaterial_matchedAndLedgerWritten() {
        stubInProgressChain();
        ProWorkOrderBomLine line = bomLine(2001L, "IF-001", "车架", "ASM", null);
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line));
        when(erpBatchMapper.selectBatchByCode("B202608180001")).thenReturn(batch(2001L, "B202608180001"));
        // 换料: 已有同物料 ACTIVE 记录
        MesMaterialLoad old = new MesMaterialLoad();
        old.setLoadId(1L);
        when(loadMapper.selectList(any())).thenReturn(List.of(old));

        Map<String, Object> data = service.load(dto("B202608180001"));

        assertTrue((Boolean) data.get("pass"));
        assertEquals("WO-1", data.get("woNo"));
        Map<?, ?> matched = (Map<?, ?>) data.get("matched");
        assertEquals("IF-001", matched.get("materialCode"));
        assertEquals(false, matched.get("isAlternative"));
        // 旧 ACTIVE 记录置 REPLACED(换料语义)
        verify(loadMapper).update(any(), any());
        ArgumentCaptor<MesMaterialLoad> captor = ArgumentCaptor.forClass(MesMaterialLoad.class);
        verify(loadMapper).insert(captor.capture());
        MesMaterialLoad saved = captor.getValue();
        assertEquals("B202608180001", saved.getBatchCode());
        assertEquals("IF-001", saved.getMaterialCode());
        assertEquals("N", saved.getIsAlternative());
        assertEquals(StationMaterialService.STATUS_ACTIVE, saved.getStatus());
        assertEquals("SCZX-1", saved.getOperatorCode());
    }

    @Test
    void load_rcBarcode_resolvedAndMatched() {
        stubInProgressChain();
        when(bomLineMapper.selectList(any())).thenReturn(List.of(bomLine(2002L, "IF-002", "车轮", null, null)));
        // RC 条码实例行: 只有 batch_id/batch_code/material_id(无物料名, 匹配后以快照行为准)
        Map<String, Object> barcodeRow = new HashMap<>();
        barcodeRow.put("barcodeCode", "RC20260607000001");
        barcodeRow.put("batchId", 501L);
        barcodeRow.put("batchCode", "B202606040001");
        barcodeRow.put("materialId", 2002L);
        when(erpBatchMapper.selectBarcodeByCode("RC20260607000001")).thenReturn(barcodeRow);
        when(loadMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.load(dto("RC20260607000001"));

        assertTrue((Boolean) data.get("pass"));
        assertEquals("B202606040001", ((Map<?, ?>) data.get("batch")).get("batchCode"));
        ArgumentCaptor<MesMaterialLoad> captor = ArgumentCaptor.forClass(MesMaterialLoad.class);
        verify(loadMapper).insert(captor.capture());
        assertEquals("IF-002", captor.getValue().getMaterialCode());
        assertEquals("B202606040001", captor.getValue().getBatchCode());
    }

    @Test
    void load_alternativeMaterial_matchedAsAlternative() {
        stubInProgressChain();
        ProWorkOrderBomLine line = bomLine(2001L, "IF-001", "车架", "ASM", 100L);
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line));
        Map<String, Object> alt = new HashMap<>();
        alt.put("bomDetailId", 100L);
        alt.put("altMaterialId", 3001L);
        alt.put("altMaterialCode", "IF-001-A");
        alt.put("substitutionType", "FULL");
        when(erpBatchMapper.selectAlternativesByDetailIds(anyList())).thenReturn(List.of(alt));
        when(erpBatchMapper.selectBatchByCode("B202608180002")).thenReturn(batch(3001L, "B202608180002"));
        when(loadMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.load(dto("B202608180002"));

        assertTrue((Boolean) data.get("pass"));
        Map<?, ?> matched = (Map<?, ?>) data.get("matched");
        assertTrue((Boolean) matched.get("isAlternative"), "替代料应匹配成功");
        assertEquals("FULL", matched.get("substitutionType"));
        // 台账仍以 BOM 主料行信息留痕(可追溯对应用量), 标记替代
        ArgumentCaptor<MesMaterialLoad> captor = ArgumentCaptor.forClass(MesMaterialLoad.class);
        verify(loadMapper).insert(captor.capture());
        assertEquals("Y", captor.getValue().getIsAlternative());
        assertEquals("IF-001", captor.getValue().getMaterialCode());
    }

    @Test
    void load_notAllowed_returnsAllowedList() {
        stubInProgressChain();
        when(bomLineMapper.selectList(any())).thenReturn(List.of(
            bomLine(2001L, "IF-001", "车架", "ASM", null),
            bomLine(2002L, "IF-002", "车轮", "ASM", null)));
        when(erpBatchMapper.selectBatchByCode("B202608180099")).thenReturn(batch(9999L, "B202608180099"));

        Map<String, Object> data = service.load(dto("B202608180099"));

        assertFalse((Boolean) data.get("pass"));
        assertTrue(String.valueOf(data.get("message")).contains("物料不匹配"));
        List<?> allowed = (List<?>) data.get("allowed");
        assertEquals(2, allowed.size());
        assertEquals("IF-001", ((Map<?, ?>) allowed.get(0)).get("materialCode"));
        // 不匹配不写台账
        verify(loadMapper, never()).insert(any(MesMaterialLoad.class));
    }

    @Test
    void load_noInProgressStep_returnsMessage() {
        when(stationPassService.resolveProcessCode(STATION)).thenReturn("ASM");
        when(stepMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.load(dto("B202608180001"));

        assertFalse((Boolean) data.get("pass"));
        assertTrue(String.valueOf(data.get("message")).contains("无法定位上料工单"));
        verify(loadMapper, never()).insert(any(MesMaterialLoad.class));
    }

    @Test
    void load_unknownBarcodePrefix_throws() {
        assertThrows(ServiceException.class, () -> service.load(dto("X123456")));
    }

    @Test
    void load_batchNotFound_throws() {
        when(stationPassService.resolveProcessCode(STATION)).thenReturn("ASM");
        when(stepMapper.selectList(any())).thenReturn(List.of(step()));
        when(flowCardMapper.selectById(100L)).thenReturn(card());
        when(bomLineMapper.selectList(any())).thenReturn(List.of(bomLine(2001L, "IF-001", "车架", "ASM", null)));
        when(erpBatchMapper.selectBatchByCode("B404")).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.load(dto("B404")));
    }

    @Test
    void load_blankArgs_throw() {
        StationMaterialLoadDto noStation = dto("B1");
        noStation.setStationCode(" ");
        assertThrows(ServiceException.class, () -> service.load(noStation));
        StationMaterialLoadDto noBarcode = dto(" ");
        assertThrows(ServiceException.class, () -> service.load(noBarcode));
    }

    // ==================== C20 当前上料状态 ====================

    @Test
    void current_mergesRequiredAndLoads() {
        when(stationPassService.resolveProcessCode(STATION)).thenReturn("ASM");
        when(stepMapper.selectList(any())).thenReturn(List.of(step()));
        when(flowCardMapper.selectById(100L)).thenReturn(card());
        when(bomLineMapper.selectList(any())).thenReturn(List.of(
            bomLine(2001L, "IF-001", "车架", "ASM", null),
            bomLine(2002L, "IF-002", "车轮", "ASM", null)));
        MesMaterialLoad active = new MesMaterialLoad();
        active.setMaterialId(2001L);
        active.setMaterialCode("IF-001");
        active.setMaterialName("车架");
        active.setBatchCode("B202608180001");
        active.setIsAlternative("N");
        active.setOperatorName("张明");
        when(loadMapper.selectList(any())).thenReturn(List.of(active));

        Map<String, Object> data = service.current(STATION);

        assertEquals("WO-1", data.get("woNo"));
        List<?> required = (List<?>) data.get("required");
        assertEquals(2, required.size());
        Map<?, ?> loaded = (Map<?, ?>) required.get(0);
        assertTrue((Boolean) loaded.get("loaded"));
        assertEquals("B202608180001", loaded.get("batchCode"));
        Map<?, ?> missing = (Map<?, ?>) required.get(1);
        assertFalse((Boolean) missing.get("loaded"), "未上料物料应标记缺料");
        assertEquals(1, ((List<?>) data.get("loads")).size());
    }

    @Test
    void current_noInProgress_returnsMessage() {
        when(stationPassService.resolveProcessCode(STATION)).thenReturn("ASM");
        when(stepMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.current(STATION);

        assertTrue(((List<?>) data.get("required")).isEmpty());
        assertTrue(String.valueOf(data.get("message")).contains("无唯一在制工序"));
    }

    // ==================== 辅助 ====================

    private void stubInProgressChain() {
        when(stationPassService.resolveProcessCode(STATION)).thenReturn("ASM");
        when(stepMapper.selectList(any())).thenReturn(List.of(step()));
        when(flowCardMapper.selectById(100L)).thenReturn(card());
    }

    private ProFlowCardStep step() {
        ProFlowCardStep step = new ProFlowCardStep();
        step.setStepRecordId(10L);
        step.setCardId(100L);
        step.setProcessCode("ASM");
        step.setStepStatus("IN_PROGRESS");
        return step;
    }

    private ProFlowCard card() {
        ProFlowCard card = new ProFlowCard();
        card.setCardId(100L);
        card.setMesWoId(7L);
        card.setWoNo("WO-1");
        card.setCardNo("FC-1");
        return card;
    }

    private ProWorkOrderBomLine bomLine(Long materialId, String code, String name, String processCode, Long bomDetailId) {
        ProWorkOrderBomLine line = new ProWorkOrderBomLine();
        line.setMesWoId(7L);
        line.setBomDetailId(bomDetailId);
        line.setChildMaterialId(materialId);
        line.setChildMaterialCode(code);
        line.setChildMaterialName(name);
        line.setQuantityPerParent(new java.math.BigDecimal("2.00"));
        line.setTotalQuantity(new java.math.BigDecimal("2.04"));
        line.setProcessCode(processCode);
        line.setWorkstationCode("GZZ0002");
        return line;
    }

    private Map<String, Object> batch(Long materialId, String batchCode) {
        Map<String, Object> batch = new HashMap<>();
        batch.put("batchId", 501L);
        batch.put("batchCode", batchCode);
        batch.put("materialId", materialId);
        batch.put("materialCode", "M-" + materialId);
        batch.put("materialName", "物料" + materialId);
        return batch;
    }

    private StationMaterialLoadDto dto(String barcode) {
        StationMaterialLoadDto dto = new StationMaterialLoadDto();
        dto.setStationCode(STATION);
        dto.setBarcode(barcode);
        dto.setOperatorCode("SCZX-1");
        dto.setOperatorName("张明");
        return dto;
    }
}
