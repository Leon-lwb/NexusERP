package com.nexus.mes.flash.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.domain.bo.TestRecordBo;
import com.nexus.mes.flash.domain.bo.TestReportBo;
import com.nexus.mes.flash.domain.vo.TestItemVo;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.flash.service.ITestItemService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestRecordServiceImpl 单元测试 — MES 测试域 P0 修复回归
 * <p>覆盖: MES-TEST-01 必测项全覆盖(缺项不判 PASS)、MES-TEST-02 ENUM 判定 fail-closed、
 * MES-TEST-03 已判定行不可重复覆盖(CAS 条件更新)、MES-TEST-04 FAIL 触发 NCR(同一 testNo 幂等)
 * 与"该 SN 存在未处置 FAIL 行"查询。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class TestRecordServiceImplTest {

    @Mock
    private TestRecordMapper baseMapper;
    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private ITestItemService testItemService;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private INcrOrderService ncrOrderService;

    @InjectMocks
    private TestRecordServiceImpl service;

    @Captor
    private ArgumentCaptor<TestRecord> recordCaptor;

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TestRecord.class);
        TableInfoHelper.initTableInfo(assistant, ProSn.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(TestRecord.class);
        TableInfoHelper.remove(ProSn.class);
    }

    private ProSn sn() {
        ProSn sn = new ProSn();
        sn.setSnId(1L);
        sn.setSnCode("SN-1");
        sn.setMaterialId(9L);
        sn.setSnStatus("IN_PROGRESS");
        return sn;
    }

    private TestItemVo item(long id, String code, String dataType, String passEnum) {
        TestItemVo vo = new TestItemVo();
        vo.setTestItemId(id);
        vo.setItemCode(code);
        vo.setItemName(code + "-名称");
        vo.setDataType(dataType);
        vo.setPassEnum(passEnum);
        return vo;
    }

    private TestReportBo report(String... codesAndValues) {
        TestReportBo bo = new TestReportBo();
        bo.setSnCode("SN-1");
        java.util.List<TestReportBo.TestItemValueBo> items = new java.util.ArrayList<>();
        for (int i = 0; i < codesAndValues.length; i += 2) {
            TestReportBo.TestItemValueBo v = new TestReportBo.TestItemValueBo();
            v.setItemCode(codesAndValues[i]);
            v.setValue(codesAndValues[i + 1]);
            items.add(v);
        }
        bo.setItems(items);
        return bo;
    }

    // ==================== MES-TEST-01 必测项全覆盖 ====================

    @Test
    void autoReport_missingMandatoryItemIsRejectedAndNothingInserted() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(
            item(11L, "VOLT", "NUMERIC", null),
            item(12L, "RF-TXPWR", "NUMERIC", null)));
        // 设备只上报 VOLT, 缺 RF-TXPWR -> 修复前 judged=1 判通过并落 1 行 PASS
        Map<String, Object> result = service.reportTestAutoBatch(List.of(report("VOLT", "5.0")));

        assertEquals(0, result.get("success"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> failed = (List<Map<String, String>>) result.get("failed");
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).get("reason").contains("缺少必测项"), failed.toString());
        assertTrue(failed.get(0).get("reason").contains("RF-TXPWR"), failed.toString());
        verify(baseMapper, never()).insert(any(TestRecord.class));
    }

    @Test
    void autoReport_allMandatoryItemsCoveredIsAccepted() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(
            item(11L, "VOLT", "NUMERIC", null),
            item(12L, "RF-TXPWR", "NUMERIC", null)));
        Map<String, Object> result = service.reportTestAutoBatch(List.of(report("VOLT", "5.0", "RF-TXPWR", "12")));

        assertEquals(1, result.get("success"));
        verify(baseMapper, org.mockito.Mockito.times(2)).insert(any(TestRecord.class));
    }

    // ==================== MES-TEST-02 ENUM fail-closed ====================

    @Test
    void autoReport_enumWithUnparsablePassEnumJudgesFail() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(
            item(21L, "COLOR", "ENUM", "{not-json")));
        Map<String, Object> result = service.reportTestAutoBatch(List.of(report("COLOR", "NG")));

        assertEquals(1, result.get("success"));
        verify(baseMapper).updateById(recordCaptor.capture());
        assertEquals("FAIL", recordCaptor.getValue().getResult(), "ENUM 合格值集合解析失败必须判 FAIL(fail-closed)");
    }

    @Test
    void autoReport_enumWithBlankPassEnumJudgesFail() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(
            item(22L, "COLOR", "ENUM", "")));
        service.reportTestAutoBatch(List.of(report("COLOR", "RED")));

        verify(baseMapper).updateById(recordCaptor.capture());
        assertEquals("FAIL", recordCaptor.getValue().getResult(), "ENUM 未配置合格值集合必须判 FAIL(fail-closed)");
    }

    @Test
    void autoReport_enumWithValidPassEnumStillPasses() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(
            item(23L, "COLOR", "ENUM", "[\"RED\",\"BLUE\"]")));
        service.reportTestAutoBatch(List.of(report("COLOR", "RED")));

        verify(baseMapper).updateById(recordCaptor.capture());
        assertEquals("PASS", recordCaptor.getValue().getResult());
    }

    // ==================== MES-TEST-03 已判定行不可覆盖 ====================

    @Test
    void reportRows_rejectsAlreadyJudgedRow() {
        TestRecord judged = new TestRecord();
        judged.setTestRecordId(5L);
        judged.setTestNo("T-1");
        judged.setItemCode("VOLT");
        judged.setDataType("NUMERIC");
        judged.setResult("FAIL");
        when(baseMapper.selectById(5L)).thenReturn(judged);

        TestRecordBo row = new TestRecordBo();
        row.setTestRecordId(5L);
        row.setNumericValue(new BigDecimal("5.0"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.reportTestRows(List.of(row)));
        assertTrue(ex.getMessage().contains("已判定"), ex.getMessage());
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void reportRows_casConflictIsRejected() {
        TestRecord draft = new TestRecord();
        draft.setTestRecordId(6L);
        draft.setTestNo("T-2");
        draft.setItemCode("VOLT");
        draft.setDataType("NUMERIC");
        draft.setResult("");
        when(baseMapper.selectById(6L)).thenReturn(draft);
        when(baseMapper.update(any(), any())).thenReturn(0);

        TestRecordBo row = new TestRecordBo();
        row.setTestRecordId(6L);
        row.setNumericValue(new BigDecimal("5.0"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.reportTestRows(List.of(row)));
        assertTrue(ex.getMessage().contains("并发重复提交"), ex.getMessage());
    }

    @Test
    void reportRows_judgesDraftRowViaConditionalUpdate() {
        TestRecord draft = new TestRecord();
        draft.setTestRecordId(7L);
        draft.setTestNo("T-3");
        draft.setItemCode("VOLT");
        draft.setDataType("NUMERIC");
        draft.setResult("");
        draft.setUpperLimit(new BigDecimal("5.5"));
        draft.setLowerLimit(new BigDecimal("4.5"));
        when(baseMapper.selectById(7L)).thenReturn(draft);
        when(baseMapper.update(any(), any())).thenReturn(1);

        TestRecordBo row = new TestRecordBo();
        row.setTestRecordId(7L);
        row.setNumericValue(new BigDecimal("9.9"));

        service.reportTestRows(List.of(row));

        verify(baseMapper).update(recordCaptor.capture(), any());
        assertEquals("FAIL", recordCaptor.getValue().getResult(), "超上限须判 FAIL");
        verify(baseMapper, never()).updateById(any(TestRecord.class));
    }

    // ==================== MES-FLASH-09 设备通道操作人留痕 ====================

    @Test
    void autoReport_doesNotTrustClientOperatorIdentity() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(
            item(31L, "VOLT", "NUMERIC", null)));
        TestReportBo bo = report("VOLT", "5.0");
        bo.setOperatorId(999L);
        bo.setOperatorName("张三");
        bo.setOperatorCode("E1001");

        service.reportTestAutoBatch(List.of(bo));

        verify(baseMapper).insert(recordCaptor.capture());
        TestRecord inserted = recordCaptor.getValue();
        assertNull(inserted.getTesterId(), "免登录设备通道不得落客户端传入的用户ID(可冒名)");
        assertEquals("设备上报:E1001(未验证)", inserted.getTesterName(), "客户端姓名不得直接作为可信操作人落库");
    }

    @Test
    void autoReportBatch_emptyListIsRejected() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.reportTestAutoBatch(java.util.Collections.emptyList()));
        assertTrue(ex.getMessage().contains("不能为空"));
    }

    // ==================== MES-TEST-04 FAIL 触发 NCR ====================

    private TestItemVo numericItem(long id, String code, String upperLimit) {
        TestItemVo vo = item(id, code, "NUMERIC", null);
        vo.setUpperLimit(new BigDecimal(upperLimit));
        return vo;
    }

    private TestRecord draft(long id, String testNo, String itemCode) {
        TestRecord draft = new TestRecord();
        draft.setTestRecordId(id);
        draft.setTestNo(testNo);
        draft.setItemCode(itemCode);
        draft.setDataType("NUMERIC");
        draft.setResult("");
        draft.setUpperLimit(new BigDecimal("5.5"));
        draft.setLowerLimit(new BigDecimal("4.5"));
        return draft;
    }

    private TestRecordBo draftRow(long id, String value) {
        TestRecordBo row = new TestRecordBo();
        row.setTestRecordId(id);
        row.setNumericValue(new BigDecimal(value));
        return row;
    }

    @Test
    void autoReport_multipleFailItemsCreateSingleNcr() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        // AUTO 路径的 testNo 由 AutoCodeUtil 生成: 纯单测必须桩出非空单号,
        // 否则 autoCreateNcrForFail 会因 testNo 为空而跳过建单(该跳过是生产上的正确防御)
        when(autoCodeUtil.genSerialCode(anyString(), any())).thenReturn("T-AUTO-1");
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(
            numericItem(11L, "VOLT", "5"),
            numericItem(12L, "RF-TXPWR", "10")));

        Map<String, Object> result = service.reportTestAutoBatch(List.of(report("VOLT", "9.9", "RF-TXPWR", "99")));

        assertEquals(1, result.get("success"));
        // 同一 testNo 两项 FAIL -> 只建一单 NCR(幂等)
        verify(ncrOrderService, times(1)).autoCreateFromExit(any(), any(), any(), any(), anyString());
    }

    @Test
    void autoReport_passDoesNotCreateNcr() {
        when(proSnMapper.selectOne(any())).thenReturn(sn());
        // 同样桩出 testNo: 保证该用例是在"确实产生了测试单"的前提下断言不建 NCR(而非因 testNo 为空而跳过)
        when(autoCodeUtil.genSerialCode(anyString(), any())).thenReturn("T-AUTO-2");
        when(testItemService.selectByMaterial(any(), any())).thenReturn(List.of(numericItem(11L, "VOLT", "5")));

        service.reportTestAutoBatch(List.of(report("VOLT", "5.0")));

        verify(ncrOrderService, never()).autoCreateFromExit(any(), any(), any(), any(), anyString());
    }

    @Test
    void reportRows_multipleFailRowsOfSameTestNoCreateSingleNcr() {
        when(baseMapper.selectById(7L)).thenReturn(draft(7L, "T-3", "VOLT"));
        when(baseMapper.selectById(8L)).thenReturn(draft(8L, "T-3", "RF-TXPWR"));
        when(baseMapper.update(any(), any())).thenReturn(1);
        // 判定前该测试单无 FAIL 行 -> 允许建单
        when(baseMapper.selectCount(any())).thenReturn(0L);

        service.reportTestRows(List.of(draftRow(7L, "9.9"), draftRow(8L, "9.9")));

        verify(ncrOrderService, times(1)).autoCreateFromExit(any(), any(), any(), any(), anyString());
        // 同一 testNo 的 FAIL 快照只查一次(避免把本次刚判 FAIL 的行当历史单)
        verify(baseMapper, times(1)).selectCount(any());
    }

    @Test
    void reportRows_skipsNcrWhenTestNoAlreadyHasFailRow() {
        when(baseMapper.selectById(9L)).thenReturn(draft(9L, "T-9", "VOLT"));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(baseMapper.selectCount(any())).thenReturn(1L);

        service.reportTestRows(List.of(draftRow(9L, "9.9")));

        verify(ncrOrderService, never()).autoCreateFromExit(any(), any(), any(), any(), anyString());
    }

    @Test
    void reportRows_passDoesNotCreateNcr() {
        when(baseMapper.selectById(11L)).thenReturn(draft(11L, "T-11", "VOLT"));
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.reportTestRows(List.of(draftRow(11L, "5.0")));

        verify(ncrOrderService, never()).autoCreateFromExit(any(), any(), any(), any(), anyString());
    }

    @Test
    void hasUnhandledFail_reflectsFailRows() {
        when(baseMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.hasUnhandledFail(1L));

        when(baseMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.hasUnhandledFail(1L));
        assertFalse(service.hasUnhandledFail(null));
    }
}
