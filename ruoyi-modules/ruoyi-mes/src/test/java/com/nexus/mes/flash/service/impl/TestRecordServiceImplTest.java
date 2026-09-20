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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestRecordServiceImpl 单元测试 — MES 测试域 P0 修复回归
 * <p>覆盖: MES-TEST-01 必测项全覆盖(缺项不判 PASS)、MES-TEST-02 ENUM 判定 fail-closed、
 * MES-TEST-03 已判定行不可重复覆盖(CAS 条件更新)。</p>
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
}
