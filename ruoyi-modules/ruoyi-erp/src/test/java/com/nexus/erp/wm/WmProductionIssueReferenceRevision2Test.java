package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.service.helper.WmProductionIssueReferenceAmounts;
import com.nexus.erp.wm.service.impl.WmProductionIssueServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 第二候选金额反例：只模拟持久层，禁止外部连接及旧账写入。 */
@ExtendWith(MockitoExtension.class)
class WmProductionIssueReferenceRevision2Test {
    @Mock WmProductionIssueMapper baseMapper;
    @Mock WmProductionIssueLineMapper lineMapper;
    @InjectMocks WmProductionIssueServiceImpl service;

    @BeforeAll
    static void init() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
    }

    @Test void emptyEvidenceNeverProvesKnownZero() {
        assertNull(WmProductionIssueReferenceAmounts.totalAmount(List.of()), "R1：无有效明细不能证明已知零");
    }

    @Test void completedHeaderWithoutEffectiveLinesReadsUnknown() {
        when(baseMapper.selectVoById(41L)).thenReturn(header(41L));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        WmProductionIssueVo result = service.selectById(41L);
        assertNull(result.getTotalAmount(), "R1：存量头无明细必须未计价");
        assertKnown(result, false);
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
        verify(baseMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test void listWithoutEffectiveLinesReadsUnknown() {
        when(baseMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(header(41L)));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = service.selectList(new WmProductionIssueBo()).get(0);
        assertNull(result.getTotalAmount(), "R1：普通列表缺失有效行必须未计价");
        assertKnown(result, false);
    }

    @Test void pageWithoutEffectiveLinesReadsUnknown() {
        Page<WmProductionIssueVo> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(header(41L)));
        when(baseMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> page);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = service.selectPageList(new WmProductionIssueBo(), new PageQuery(10, 1)).getRows().get(0);
        assertNull(result.getTotalAmount(), "R1：分页列表缺失有效行必须未计价");
        assertKnown(result, false);
    }

    @Test void unknownIssuedQuantityDoesNotInheritQuantityNvlForReferenceAmount() {
        when(baseMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        WmProductionIssueLine row = line(null, "1", null);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        ReflectionTestUtils.invokeMethod(service, "recalcHeaderTotals", 41L);
        ArgumentCaptor<WmProductionIssue> value = ArgumentCaptor.forClass(WmProductionIssue.class);
        verify(baseMapper).updateById(value.capture());
        assertEquals(BigDecimal.ZERO, value.getValue().getTotalQty(), "R2：账面总数量保留原兼容语义");
        assertNull(value.getValue().getTotalAmount(), "R2：参考金额不得继承未知已领量的零兜底");
    }

    @Test void knownZeroPriceStillHasExplicitTrueContract() {
        when(baseMapper.selectVoById(41L)).thenReturn(header(41L));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line("6", "0", null)));
        WmProductionIssueVo result = service.selectById(41L);
        assertEquals(BigDecimal.ZERO, result.getTotalAmount(), "R6：完整已知零价仍应真实为零");
        assertKnown(result, true);
    }

    @Test void unknownReferenceHasExplicitFalseContract() {
        when(baseMapper.selectVoById(41L)).thenReturn(header(41L));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line("6", null, null)));
        WmProductionIssueVo result = service.selectById(41L);
        assertNull(result.getTotalAmount(), "R6：缺失参考价为未知");
        assertKnown(result, false);
    }

    @Test void activeClearPersistsNullWithIssueAndLineScope() {
        WmProductionIssueLine stored = editableStoredLine();
        WmProductionIssueLine request = editableRequestLine();
        setClear(request, true);
        WmProductionIssue issue = editableIssue("CREATED");
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
        when(lineMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        ReflectionTestUtils.invokeMethod(service, "syncLines", issue, List.of(request));
        ArgumentCaptor<LambdaUpdateWrapper<WmProductionIssueLine>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(lineMapper).update(isNull(), update.capture());
        var wrapper = update.getValue();
        assertTrue(wrapper.getSqlSet().contains("unit_price=") && wrapper.getSqlSet().contains("amount="),
            "R3：须显式写空参考单价与金额，而非被非空更新策略忽略");
        assertTrue(wrapper.getSqlSegment().contains("issue_id") && wrapper.getSqlSegment().contains("line_id"),
            "R3：主动清价只能修改本单本行");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null), "R3：清价参数必须含空值");
        assertNull(request.getUnitPrice(), "R3：请求清价后参考单价应为空");
        assertNull(request.getAmount(), "R3：请求清价后参考金额应为空");
    }

    @Test void omittedPriceWhenOnlyBatchChangesPreservesKnownReference() {
        WmProductionIssueLine request = editableRequestLine();
        request.setBatchId(99L);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(editableStoredLine()));
        ReflectionTestUtils.invokeMethod(service, "syncLines", editableIssue("CREATED"), List.of(request));
        verify(lineMapper, never()).update(any(), any(Wrapper.class));
        ArgumentCaptor<WmProductionIssueLine> saved = ArgumentCaptor.forClass(WmProductionIssueLine.class);
        verify(lineMapper).updateById(saved.capture());
        assertEquals(BigDecimal.ONE, saved.getValue().getUnitPrice(), "R3：省略参考价格只改批次不能清既有价");
        assertEquals(new BigDecimal("6"), saved.getValue().getAmount(), "R3：省略参考金额应保留持久值");
    }

    @Test void clearPriceRoundTripCannotReviveNotNullStoredValues() {
        WmProductionIssueLine stored = editableStoredLine();
        WmProductionIssueLine request = editableRequestLine();
        setClear(request, true);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
        doAnswer(invocation -> {
            WmProductionIssueLine value = invocation.getArgument(0);
            if (value.getUnitPrice() != null) stored.setUnitPrice(value.getUnitPrice());
            if (value.getAmount() != null) stored.setAmount(value.getAmount());
            return 1;
        }).when(lineMapper).updateById(any(WmProductionIssueLine.class));
        doAnswer(invocation -> {
            LambdaUpdateWrapper<WmProductionIssueLine> update = invocation.getArgument(1);
            assertTrue(update.getSqlSet().contains("unit_price=") && update.getSqlSet().contains("amount="),
                "R3：模拟持久层只允许明确字段写空，不假定非空更新策略会清空");
            stored.setUnitPrice(null);
            stored.setAmount(null);
            return 1;
        }).when(lineMapper).update(isNull(), any(Wrapper.class));
        ReflectionTestUtils.invokeMethod(service, "syncLines", editableIssue("CREATED"), List.of(request));
        when(baseMapper.selectVoById(41L)).thenReturn(header(41L));
        WmProductionIssueVo result = service.selectById(41L);
        assertNull(stored.getUnitPrice(), "R3：明确清价后存储单价不得复活");
        assertNull(stored.getAmount(), "R3：明确清价后存储金额不得复活");
        assertNull(result.getTotalAmount(), "R3：保存后读取参考合计仍须未知");
        assertKnown(result, false);
    }

    @Test void failedExplicitClearCannotSilentlyKeepOldReferenceValues() {
        WmProductionIssueLine request = editableRequestLine();
        setClear(request, true);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(editableStoredLine()));
        when(lineMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class,
            () -> ReflectionTestUtils.invokeMethod(service, "syncLines", editableIssue("CREATED"), List.of(request)),
            "R3：显式清价未更新成功必须拒绝，不能让旧参考价静默复活");
    }

    @Test void terminalIssueCannotClearReferencePrices() {
        WmProductionIssueLine request = editableRequestLine();
        setClear(request, true);
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(41L);
        bo.setWmProductionIssueLineList(List.of(request));
        when(baseMapper.selectById(41L)).thenReturn(editableIssue("COMPLETED"));
        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "R3：已完成单据不能主动清价");
        verifyNoInteractions(lineMapper);
        verify(baseMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test void clearFlagIsNotADatabaseColumn() {
        var properties = TableInfoHelper.getTableInfo(WmProductionIssueLine.class).getFieldList();
        var bean = new BeanWrapperImpl(new WmProductionIssueLine());
        assertTrue(bean.isWritableProperty("clearReferencePrice"), "R3：需显式请求清价标志以区分省略与主动清空");
        assertTrue(properties.stream().noneMatch(field -> "clearReferencePrice".equals(field.getProperty())),
            "R3：清价标志不能新增持久列或依赖 DDL");
    }

    private static void assertKnown(WmProductionIssueVo value, boolean expected) {
        var bean = new BeanWrapperImpl(value);
        assertTrue(bean.isReadableProperty("referenceAmountKnown"), "R6：参考金额已知状态必须是显式布尔契约");
        assertEquals(expected, bean.getPropertyValue("referenceAmountKnown"), "R6：缺失状态不能当作已知");
    }

    private static void setClear(WmProductionIssueLine line, boolean clear) {
        var bean = new BeanWrapperImpl(line);
        assertTrue(bean.isWritableProperty("clearReferencePrice"), "R3：需显式清价标志避免空值被非空更新忽略");
        bean.setPropertyValue("clearReferencePrice", clear);
    }

    private static WmProductionIssueVo header(long id) {
        WmProductionIssueVo header = new WmProductionIssueVo();
        header.setIssueId(id);
        header.setStatus("COMPLETED");
        header.setTotalQty(new BigDecimal("24"));
        header.setTotalAmount(BigDecimal.ZERO);
        return header;
    }

    private static WmProductionIssue editableIssue(String status) {
        WmProductionIssue issue = new WmProductionIssue();
        issue.setIssueId(41L);
        issue.setStatus(status);
        return issue;
    }

    private static WmProductionIssueLine editableStoredLine() {
        WmProductionIssueLine line = editableRequestLine();
        line.setIssuedQty(BigDecimal.ZERO);
        line.setUnitPrice(BigDecimal.ONE);
        line.setAmount(new BigDecimal("6"));
        return line;
    }

    private static WmProductionIssueLine editableRequestLine() {
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setIssueId(41L);
        line.setLineId(42L);
        line.setMaterialId(23L);
        line.setBaseUomId("个");
        line.setCurrentQty(new BigDecimal("6"));
        return line;
    }

    private static WmProductionIssueLine line(String issued, String price, String amount) {
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setIssueId(41L);
        line.setCurrentQty(BigDecimal.ZERO);
        line.setIssuedQty(issued == null ? null : new BigDecimal(issued));
        line.setUnitPrice(price == null ? null : new BigDecimal(price));
        line.setAmount(amount == null ? null : new BigDecimal(amount));
        return line;
    }
}
