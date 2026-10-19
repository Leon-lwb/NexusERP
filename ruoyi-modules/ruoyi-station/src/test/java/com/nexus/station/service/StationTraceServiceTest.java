package com.nexus.station.service;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.flash.domain.FlashRecord;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.mapper.FlashRecordMapper;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.station.domain.MesSnBindLog;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.mapper.StationSnBindLogMapper;
import com.nexus.station.mapper.StationReportMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * StationTraceService 单元测试 — SN 全履历时间线聚合(C13)。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class StationTraceServiceTest {

    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private ProFlowCardStepMapper stepMapper;
    @Mock
    private FlashRecordMapper flashRecordMapper;
    @Mock
    private TestRecordMapper testRecordMapper;
    @Mock
    private StationReportMapper reportMapper;
    @Mock
    private StationSnBindLogMapper bindLogMapper;
    @InjectMocks
    private StationTraceService service;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> snColumns = new HashMap<>();
        snColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(ProSn.class)).thenReturn(snColumns);
        Map<String, ColumnCache> stepColumns = new HashMap<>();
        stepColumns.put("CARDID", new ColumnCache("card_id", "card_id"));
        stepColumns.put("SEQUENCENUM", new ColumnCache("sequence_num", "sequence_num"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(ProFlowCardStep.class)).thenReturn(stepColumns);
        Map<String, ColumnCache> flashColumns = new HashMap<>();
        flashColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        flashColumns.put("FLASHTIME", new ColumnCache("flash_time", "flash_time"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(FlashRecord.class)).thenReturn(flashColumns);
        Map<String, ColumnCache> testColumns = new HashMap<>();
        testColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        testColumns.put("TESTTIME", new ColumnCache("test_time", "test_time"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(TestRecord.class)).thenReturn(testColumns);
        Map<String, ColumnCache> reportColumns = new HashMap<>();
        reportColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        reportColumns.put("CREATETIME", new ColumnCache("create_time", "create_time"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesStationReport.class)).thenReturn(reportColumns);
        Map<String, ColumnCache> bindColumns = new HashMap<>();
        bindColumns.put("PARENTSNCODE", new ColumnCache("parent_sn_code", "parent_sn_code"));
        bindColumns.put("COMPONENTSNCODE", new ColumnCache("component_sn_code", "component_sn_code"));
        bindColumns.put("CREATETIME", new ColumnCache("create_time", "create_time"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesSnBindLog.class)).thenReturn(bindColumns);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    @Test
    void trace_snNotFound_throws() {
        when(proSnMapper.selectOne(any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.trace("SN-404"));
    }

    @Test
    void trace_blankSn_throws() {
        assertThrows(ServiceException.class, () -> service.trace(" "));
    }

    @Test
    void trace_fullTimeline_mergedSortedDesc() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(stepMapper.selectList(any())).thenReturn(List.of(step(30, "ASM", "IN_PROGRESS")));
        // 刷写: 时间最晚 → 时间线第一条
        FlashRecord flash = new FlashRecord();
        flash.setFlashTypeCode("firmware_file");
        flash.setSnCode("SN-1");
        flash.setContentVersion("v1.2.0");
        flash.setFlashTime(date("2026-08-21 15:00:00"));
        flash.setResult("PASS");
        when(flashRecordMapper.selectList(any())).thenReturn(List.of(flash));
        when(testRecordMapper.selectList(any())).thenReturn(List.of());
        MesStationReport report = new MesStationReport();
        report.setSnCode("SN-1");
        report.setStationCode("STN|JZ|L1|ASM|GENERAL");
        report.setCreateTime(date("2026-08-21 10:00:00"));
        report.setResult("FAIL");
        report.setDefectCodes("D-FUN-SHORT");
        when(reportMapper.selectList(any())).thenReturn(List.of(report));
        MesSnBindLog bindLog = new MesSnBindLog();
        bindLog.setAction("BIND");
        bindLog.setParentSnCode("SN-1");
        bindLog.setComponentSnCode("SN-C1");
        bindLog.setCreateTime(date("2026-08-21 09:00:00"));
        when(bindLogMapper.selectList(any())).thenReturn(List.of(bindLog));

        Map<String, Object> data = service.trace("SN-1");

        Map<?, ?> snSummary = (Map<?, ?>) data.get("sn");
        assertEquals("GW-1000", snSummary.get("materialCode"));
        assertEquals(1, ((List<?>) data.get("steps")).size());
        List<?> records = (List<?>) data.get("records");
        assertEquals(3, records.size());
        // 按时间倒序: 刷写(15:00) → 过站报工(10:00) → 绑定(09:00)
        assertEquals("FLASH", ((Map<?, ?>) records.get(0)).get("type"));
        assertTrue(((Map<?, ?>) records.get(0)).get("summary").toString().contains("v1.2.0"));
        assertEquals("PASS_REPORT", ((Map<?, ?>) records.get(1)).get("type"));
        assertTrue(((Map<?, ?>) records.get(1)).get("summary").toString().contains("D-FUN-SHORT"));
        assertEquals("SN_BIND", ((Map<?, ?>) records.get(2)).get("type"));
        assertTrue(((Map<?, ?>) records.get(2)).get("summary").toString().contains("SN-C1"));
    }

    @Test
    void trace_testRecords_groupByTestNo_anyFailIsFail() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(flashRecordMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectList(any())).thenReturn(List.of());
        when(bindLogMapper.selectList(any())).thenReturn(List.of());
        // 同一测试单 2 个 item 行(一 PASS 一 FAIL) + 另一测试单 1 行(全 PASS)
        when(testRecordMapper.selectList(any())).thenReturn(List.of(
            test("T1", "VOLTAGE", "PASS", date("2026-08-21 14:00:00")),
            test("T1", "CURRENT", "FAIL", date("2026-08-21 14:00:00")),
            test("T2", "VOLTAGE", "PASS", date("2026-08-21 12:00:00"))));

        Map<String, Object> data = service.trace("SN-1");

        List<?> records = (List<?>) data.get("records");
        assertEquals(2, records.size(), "同一测试单多 item 行应归一");
        Map<?, ?> first = (Map<?, ?>) records.get(0);
        assertEquals("TEST", first.get("type"));
        assertEquals("FAIL", first.get("result"), "组内任一 FAIL 整组 FAIL");
        assertTrue(first.get("summary").toString().contains("T1"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) ((Map<?, ?>) first.get("detail")).get("items");
        assertEquals(2, items.size());
        assertEquals("PASS", ((Map<?, ?>) records.get(1)).get("result"));
    }

    @Test
    void trace_bindLog_unbindAndRebindSummary() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(stepMapper.selectList(any())).thenReturn(List.of());
        when(flashRecordMapper.selectList(any())).thenReturn(List.of());
        when(testRecordMapper.selectList(any())).thenReturn(List.of());
        when(reportMapper.selectList(any())).thenReturn(List.of());
        MesSnBindLog unbind = new MesSnBindLog();
        unbind.setAction("UNBIND");
        unbind.setParentSnCode("SN-1");
        unbind.setComponentSnCode("SN-C1");
        unbind.setReason("换板");
        unbind.setCreateTime(date("2026-08-21 11:00:00"));
        MesSnBindLog rebind = new MesSnBindLog();
        rebind.setAction("REBIND");
        rebind.setParentSnCode("SN-1");
        rebind.setComponentSnCode("SN-C1");
        rebind.setNewComponentSnCode("SN-C2");
        rebind.setCreateTime(date("2026-08-21 11:30:00"));
        when(bindLogMapper.selectList(any())).thenReturn(List.of(unbind, rebind));

        Map<String, Object> data = service.trace("SN-1");

        List<?> records = (List<?>) data.get("records");
        assertEquals(2, records.size());
        assertTrue(((Map<?, ?>) records.get(0)).get("summary").toString().contains("换板 → SN-C2"),
            "REBIND 摘要应含换板新 SN: " + records.get(0));
        assertTrue(((Map<?, ?>) records.get(1)).get("summary").toString().contains("解绑"));
    }

    // ==================== 辅助 ====================

    private ProSn sn() {
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-1");
        sn.setCardId(100L);
        sn.setWoNo("WO-1");
        sn.setCardNo("FC-1");
        sn.setMaterialCode("GW-1000");
        sn.setMaterialName("网关主板");
        sn.setSnStatus("IN_PRODUCTION");
        return sn;
    }

    private ProFlowCardStep step(int seq, String processCode, String status) {
        ProFlowCardStep s = new ProFlowCardStep();
        s.setSequenceNum(seq);
        s.setProcessCode(processCode);
        s.setProcessName(processCode);
        s.setStepStatus(status);
        return s;
    }

    private TestRecord test(String testNo, String itemCode, String result, Date testTime) {
        TestRecord r = new TestRecord();
        r.setTestNo(testNo);
        r.setSnCode("SN-1");
        r.setItemCode(itemCode);
        r.setResult(result);
        r.setTestTime(testTime);
        return r;
    }

    private Date date(String s) {
        return new Date(java.sql.Timestamp.valueOf(s).getTime());
    }
    @Test
    @SuppressWarnings("unchecked")
    void unknownTestResultNeverBecomesAPassingQualityFact() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testRecordMapper.selectList(any())).thenReturn(List.of(test("UNKNOWN-TEST","V",null,date("2026-10-19 00:00:00"))));
        var result=service.trace("SN-1");
        var records=(List<Map<String,Object>>)result.get("records");
        var record=records.stream().filter(r->"TEST".equals(r.get("type"))).findFirst().orElseThrow();
        assertEquals("UNKNOWN",record.get("result"));
        var detail=(Map<String,Object>)record.get("detail");var items=(List<Map<String,Object>>)detail.get("items");
        assertEquals(false,items.get(0).get("pass"));
    }

    @Test
    void sourceLimitIsVisibleRatherThanPresentedAsCompleteHistory() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testRecordMapper.selectCount(any())).thenReturn(201L);
        var result=service.trace("SN-1");
        assertEquals(true,result.get("truncated"));
        assertTrue(((List<?>)result.get("truncatedSources")).contains("TEST"));
        assertEquals("LEGACY_SN_CODE",result.get("traceScope"));
    }
}
