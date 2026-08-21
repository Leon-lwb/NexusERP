package com.nexus.station.service;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.mapper.StationReportMapper;
import com.nexus.station.mapper.StationSchWorkerMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * StationPassService 单元测试 — 覆盖联调缺陷 F-5/F-7 回归 + 四重校验 + 幂等。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class StationPassServiceTest {

    @Mock
    private StationReportMapper reportMapper;
    @Mock
    private StationSchWorkerMapper schWorkerMapper;
    @Mock
    private StationDeviceMapper deviceMapper;
    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private ProFlowCardMapper flowCardMapper;
    @Mock
    private ProFlowCardStepMapper stepMapper;
    @Mock
    private TestRecordMapper testRecordMapper;
    @Mock
    private IProFlowCardService flowCardService;
    @Mock
    private IProSnService proSnService;
    @Mock
    private NcrOrderMapper ncrOrderMapper;
    @InjectMocks
    private StationPassService service;

    private MockedStatic<TenantHelper> tenantHelperMock;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        tenantHelperMock = mockStatic(TenantHelper.class);
        // TenantHelper.dynamic 直接执行调用体(不依赖真实租户上下文)
        tenantHelperMock.when(() -> TenantHelper.dynamic(anyString(), any(java.util.function.Supplier.class)))
            .thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());
        // CALLS_REAL_METHODS: extract() 走真实方法(反序列化 lambda 拿属性名), 仅 getColumnMap 打桩
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> snColumns = new HashMap<>();
        snColumns.put("SNID", new ColumnCache("sn_id", "sn_id"));
        snColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        snColumns.put("CARDID", new ColumnCache("card_id", "card_id"));
        snColumns.put("CURRENTSTEPSEQ", new ColumnCache("current_step_seq", "current_step_seq"));
        snColumns.put("SNSTATUS", new ColumnCache("sn_status", "sn_status"));
        snColumns.put("PARENTSNID", new ColumnCache("parent_sn_id", "parent_sn_id"));
        snColumns.put("PARENTSNCODE", new ColumnCache("parent_sn_code", "parent_sn_code"));
        Map<String, ColumnCache> stepColumns = new HashMap<>();
        stepColumns.put("STEPRECORDID", new ColumnCache("step_record_id", "step_record_id"));
        stepColumns.put("CARDID", new ColumnCache("card_id", "card_id"));
        stepColumns.put("SEQUENCENUM", new ColumnCache("sequence_num", "sequence_num"));
        stepColumns.put("PROCESSCODE", new ColumnCache("process_code", "process_code"));
        stepColumns.put("PROCESSNAME", new ColumnCache("process_name", "process_name"));
        stepColumns.put("STEPSTATUS", new ColumnCache("step_status", "step_status"));
        stepColumns.put("PLANNEDQTY", new ColumnCache("planned_qty", "planned_qty"));
        stepColumns.put("GOODQTY", new ColumnCache("good_qty", "good_qty"));
        stepColumns.put("DEFECTQTY", new ColumnCache("defect_qty", "defect_qty"));
        stepColumns.put("SCRAPQTY", new ColumnCache("scrap_qty", "scrap_qty"));
        stepColumns.put("OPERATORID", new ColumnCache("operator_id", "operator_id"));
        stepColumns.put("OPERATORNAME", new ColumnCache("operator_name", "operator_name"));
        stepColumns.put("ACTUALENDTIME", new ColumnCache("actual_end_time", "actual_end_time"));
        Map<String, ColumnCache> reportColumns = new HashMap<>();
        reportColumns.put("REPORTID", new ColumnCache("report_id", "report_id"));
        reportColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        reportColumns.put("STATIONCODE", new ColumnCache("station_code", "station_code"));
        reportColumns.put("RESULT", new ColumnCache("result", "result"));
        reportColumns.put("CLIENTKEY", new ColumnCache("client_key", "client_key"));
        Map<String, ColumnCache> deviceColumns = new HashMap<>();
        deviceColumns.put("DEVICEID", new ColumnCache("device_id", "device_id"));
        deviceColumns.put("DEVICECODE", new ColumnCache("device_code", "device_code"));
        deviceColumns.put("STATIONCODE", new ColumnCache("station_code", "station_code"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(ProSn.class)).thenReturn(snColumns);
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(ProFlowCardStep.class)).thenReturn(stepColumns);
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesStationReport.class)).thenReturn(reportColumns);
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(com.nexus.station.domain.MesStationDevice.class))
            .thenReturn(deviceColumns);
        Map<String, ColumnCache> testRecordColumns = new HashMap<>();
        testRecordColumns.put("TESTRECORDID", new ColumnCache("test_record_id", "test_record_id"));
        testRecordColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        testRecordColumns.put("RESULT", new ColumnCache("result", "result"));
        testRecordColumns.put("TESTTIME", new ColumnCache("test_time", "test_time"));
        Map<String, ColumnCache> ncrColumns = new HashMap<>();
        ncrColumns.put("CARDID", new ColumnCache("card_id", "card_id"));
        ncrColumns.put("STATUS", new ColumnCache("status", "status"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(com.nexus.mes.flash.domain.TestRecord.class))
            .thenReturn(testRecordColumns);
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(com.nexus.mes.ncr.domain.NcrOrder.class))
            .thenReturn(ncrColumns);
    }

    @AfterEach
    void tearDown() {
        if (tenantHelperMock != null) tenantHelperMock.close();
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    // ==================== F-5 回归: SN 当前工序定位 ====================

    @Test
    void f5_currentStepSeqNull_stillLocatesStepByFirstUnfinished() {
        // 新 SN 的 current_step_seq 为 NULL(库中无维护逻辑), 应按「卡上第一条未完成工序」定位
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-1");
        sn.setCardId(1L);
        sn.setCurrentStepSeq(null);
        sn.setSnStatus("IN_PRODUCTION");
        when(proSnMapper.selectOne(any())).thenReturn(sn);
        ProFlowCardStep asm = step(3L, 30, "ASM", "IN_PROGRESS");
        when(stepMapper.selectOne(any())).thenReturn(asm);
        // PREV_STEP 查询(当前工序之前的未完成工序): 前道均已完成 → 空列表
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(testRecordMapper.selectOne(any())).thenReturn(null);
        when(ncrOrderMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> data = service.validate("SN-1", "STN|JZ|L1|ASM|GENERAL");

        assertTrue((Boolean) data.get("pass"), "current_step_seq=NULL 时仍应定位到 ASM 工序并通过校验");
        assertEquals("ASM", ((Map<?, ?>) data.get("snInfo")).get("currentProcessName"));
    }

    @Test
    void f5_staleCurrentStepSeq_resolvesActualFirstUnfinished() {
        // 老数据 current_step_seq=3 与工序序号 10/20/30 不符, 仍应定位实际第一条未完成工序
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-1");
        sn.setCardId(1L);
        sn.setCurrentStepSeq(3);
        sn.setSnStatus("IN_PRODUCTION");
        when(proSnMapper.selectOne(any())).thenReturn(sn);
        ProFlowCardStep weld = step(2L, 20, "WELD", "IN_PROGRESS");
        when(stepMapper.selectOne(any())).thenReturn(weld);
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(testRecordMapper.selectOne(any())).thenReturn(null);
        when(ncrOrderMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> data = service.validate("SN-1", "STN|JZ|L1|WELD|GENERAL");

        assertTrue((Boolean) data.get("pass"));
    }

    // ==================== 四重校验矩阵 ====================

    @Test
    void validate_stationMismatch_failsWithStationMatchCheck() {
        ProSn sn = inProductionSn();
        when(proSnMapper.selectOne(any())).thenReturn(sn);
        ProFlowCardStep asm = step(3L, 30, "ASM", "IN_PROGRESS");
        when(stepMapper.selectOne(any())).thenReturn(asm);
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(testRecordMapper.selectOne(any())).thenReturn(null);
        lenient().when(ncrOrderMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> data = service.validate("SN-1", "STN|JZ|L1|PKG|GENERAL");

        assertFalse((Boolean) data.get("pass"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) data.get("checks");
        Map<String, Object> match = checks.stream()
            .filter(c -> "STATION_MATCH".equals(c.get("code"))).findFirst().orElseThrow();
        assertEquals(false, match.get("ok"));
    }

    @Test
    void validate_duplicatePass_failsWithDuplicateCheck() {
        ProSn sn = inProductionSn();
        when(proSnMapper.selectOne(any())).thenReturn(sn);
        ProFlowCardStep asm = step(3L, 30, "ASM", "IN_PROGRESS");
        when(stepMapper.selectOne(any())).thenReturn(asm);
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectCount(any())).thenReturn(1L); // 已报过 PASS
        when(testRecordMapper.selectOne(any())).thenReturn(null);
        when(ncrOrderMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> data = service.validate("SN-1", "STN|JZ|L1|ASM|GENERAL");

        assertFalse((Boolean) data.get("pass"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) data.get("checks");
        assertTrue(checks.stream().filter(c -> "DUPLICATE".equals(c.get("code")))
            .allMatch(c -> !((Boolean) c.get("ok"))));
    }

    // ==================== F-3 回归: QUALITY_HOLD 最新测试记录判定 ====================

    @Test
    void f3_latestTestPass_afterHistoricalFail_releasesHold() {
        ProSn sn = inProductionSn();
        when(proSnMapper.selectOne(any())).thenReturn(sn);
        ProFlowCardStep asm = step(3L, 30, "ASM", "IN_PROGRESS");
        when(stepMapper.selectOne(any())).thenReturn(asm);
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectCount(any())).thenReturn(0L);
        lenient().when(ncrOrderMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.selectOne(any())).thenReturn(null);
        // 最新一条测试记录 PASS(此前有历史 FAIL, 但取的是最新一条)
        com.nexus.mes.flash.domain.TestRecord latest = new com.nexus.mes.flash.domain.TestRecord();
        latest.setResult("PASS");
        when(testRecordMapper.selectOne(any())).thenReturn(latest);

        Map<String, Object> data = service.validate("SN-1", "STN|JZ|L1|ASM|GENERAL");

        assertTrue((Boolean) data.get("pass"), "复测 PASS 后应解除质量扣押");
    }

    @Test
    void validate_latestTestFail_holds() {
        ProSn sn = inProductionSn();
        when(proSnMapper.selectOne(any())).thenReturn(sn);
        ProFlowCardStep asm = step(3L, 30, "ASM", "IN_PROGRESS");
        when(stepMapper.selectOne(any())).thenReturn(asm);
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectCount(any())).thenReturn(0L);
        lenient().when(ncrOrderMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.selectOne(any())).thenReturn(null);
        com.nexus.mes.flash.domain.TestRecord latest = new com.nexus.mes.flash.domain.TestRecord();
        latest.setResult("FAIL");
        when(testRecordMapper.selectOne(any())).thenReturn(latest);

        Map<String, Object> data = service.validate("SN-1", "STN|JZ|L1|ASM|GENERAL");

        assertFalse((Boolean) data.get("pass"));
    }

    // ==================== C3 报工 + 幂等 ====================

    @Test
    void reportSingle_idempotentReplay_returnsWithoutInsert() {
        when(reportMapper.selectByClientKey(any())).thenReturn(new MesStationReport()); // 幂等命中

        StationPassReportDto dto = new StationPassReportDto();
        dto.setSnCode("SN-1");
        dto.setStationCode("STN|JZ|L1|ASM|GENERAL");
        dto.setResult("PASS");
        dto.setClientTs(1787295823000L);

        Map<String, Object> data = service.reportSingle(dto,
            new StationPassService.OperatorInfo(105L, "SCZX-1", "张明"));

        assertTrue(data.isEmpty());
        verify(reportMapper, never()).insert(any(MesStationReport.class));
        verify(proSnMapper, never()).update(any(), any());
    }

    @Test
    void reportSingle_passAccumulatesAndAdvancesToActualNextSeq() {
        ProSn sn = inProductionSn();
        when(proSnMapper.selectOne(any())).thenReturn(sn);
        // 卡上工序 30(ASM, IN_PROGRESS) 与 40(PKG, PENDING)
        ProFlowCardStep asm = step(3L, 30, "ASM", "IN_PROGRESS");
        ProFlowCardStep pkg = step(4L, 40, "PKG", "PENDING");
        when(stepMapper.selectOne(any())).thenReturn(asm);
        when(stepMapper.selectList(any())).thenReturn(List.of(asm, pkg));
        ProFlowCard card = new ProFlowCard();
        card.setCardId(1L);
        when(flowCardMapper.selectById(1L)).thenReturn(card);
        when(reportMapper.selectByClientKey(any())).thenReturn(null);
        // 收口重查: planned=50, good=51 → 未到 planned 不收口(good 已含本次前值+1)
        ProFlowCardStep fresh = step(3L, 30, "ASM", "IN_PROGRESS");
        fresh.setPlannedQty(java.math.BigDecimal.valueOf(100));
        fresh.setGoodQty(java.math.BigDecimal.valueOf(51));
        when(stepMapper.selectById(3L)).thenReturn(fresh);

        StationPassReportDto dto = new StationPassReportDto();
        dto.setSnCode("SN-1");
        dto.setStationCode("STN|JZ|L1|ASM|GENERAL");
        dto.setResult("PASS");
        dto.setOperatorId(105L);
        dto.setClientTs(1787295823000L);

        service.reportSingle(dto, new StationPassService.OperatorInfo(105L, null, null));

        ArgumentCaptor<MesStationReport> captor = ArgumentCaptor.forClass(MesStationReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals("SINGLE|SN-1|STN|JZ|L1|ASM|GENERAL|1787295823000", captor.getValue().getClientKey());
        assertEquals(105L, captor.getValue().getOperatorId());
        // SN 前移到实际下一道工序 seq=40(而非 30+1=31) — F-5 advanceSn 回归
        verify(proSnMapper).update(any(), any());
    }

    @Test
    void reportSingle_missingClientTs_rejected() {
        StationPassReportDto dto = new StationPassReportDto();
        dto.setSnCode("SN-1");
        dto.setStationCode("STN|JZ|L1|ASM|GENERAL");
        dto.setResult("PASS");

        assertThrows(ServiceException.class, () ->
            service.reportSingle(dto, new StationPassService.OperatorInfo(null, null, null)));
    }

    // ==================== resolveProcessCode: 工位码解析 ====================

    @Test
    void resolveProcessCode_stationCodeFormat() {
        when(deviceMapper.selectOne(any())).thenReturn(null);
        assertEquals("ASM", service.resolveProcessCode("STN|注塑车间|产线1|ASM|GENERAL"));
        assertNull(service.resolveProcessCode("not-a-station-code"));
    }

    @Test
    void resolveProcessCode_heartbeatBindingTakesPriority() {
        com.nexus.station.domain.MesStationDevice device = new com.nexus.station.domain.MesStationDevice();
        device.setProcessCode("OP-99");
        when(deviceMapper.selectOne(any())).thenReturn(device);
        assertEquals("OP-99", service.resolveProcessCode("STN|JZ|L1|ASM|GENERAL"));
    }

    // ==================== F-7 回归: 操作员解析(独立于事务) ====================

    @Test
    void f7_resolveOperator_byCodeLooksUpWorkerArchive() {
        Map<String, Object> worker = new HashMap<>();
        worker.put("user_id", 105L);
        worker.put("user_name", "张明");
        when(schWorkerMapper.selectByEmployeeId("SCZX-1")).thenReturn(worker);

        StationPassService.OperatorInfo info = service.resolveOperator(null, "SCZX-1");

        assertEquals(105L, info.userId());
        assertEquals("SCZX-1", info.code());
        assertEquals("张明", info.name());
    }

    @Test
    void resolveOperator_unknownCodeStillRecordsCode() {
        when(schWorkerMapper.selectByEmployeeId("GHOST")).thenReturn(null);
        StationPassService.OperatorInfo info = service.resolveOperator(null, "GHOST");
        assertNull(info.userId());
        assertEquals("GHOST", info.code());
    }

    @Test
    void resolveOperator_idDirect() {
        StationPassService.OperatorInfo info = service.resolveOperator(105L, "SCZX-1");
        assertEquals(105L, info.userId());
        verifyNoInteractions(schWorkerMapper);
    }

    // ==================== 工位码归一化(M3 设备编码自动录入) ====================

    @Test
    void normalize_sixSegmentStationCode_stripsDeviceSegment() {
        assertEquals("STN|注塑车间|产线1|ASM|GENERAL",
            StationPassService.normalizeStationCode("STN|注塑车间|产线1|ASM|GENERAL|IPC-0007"));
    }

    @Test
    void normalize_fiveSegmentAndBlankUnchanged() {
        assertEquals("STN|注塑车间|产线1|ASM|GENERAL",
            StationPassService.normalizeStationCode("STN|注塑车间|产线1|ASM|GENERAL"));
        assertEquals("STN|注塑车间|产线1|ASM|GENERAL",
            StationPassService.normalizeStationCode("STN|注塑车间|产线1|ASM|GENERAL|"));
        assertNull(StationPassService.normalizeStationCode(null));
        // 空白原样返回(入口处由 isBlank 校验拦截)
        assertEquals(" ", StationPassService.normalizeStationCode(" "));
    }

    @Test
    void normalize_nonStnPrefixWithPipesUntouched() {
        assertEquals("CUSTOM|A|B|C|D|E", StationPassService.normalizeStationCode("CUSTOM|A|B|C|D|E"));
    }

    // ==================== 辅助 ====================

    private ProSn inProductionSn() {
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-1");
        sn.setCardId(1L);
        sn.setCardNo("FC-1");
        sn.setWoNo("WO-1");
        sn.setCurrentStepSeq(30);
        sn.setSnStatus("IN_PRODUCTION");
        return sn;
    }

    private ProFlowCardStep step(Long id, Integer seq, String processCode, String status) {
        ProFlowCardStep s = new ProFlowCardStep();
        s.setStepRecordId(id);
        s.setCardId(1L);
        s.setSequenceNum(seq);
        s.setProcessCode(processCode);
        s.setProcessName(processCode);
        s.setStepStatus(status);
        return s;
    }
}
