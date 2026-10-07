package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.PrdWorkReport;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.mapper.PrdWorkReportMapper;
import com.nexus.erp.prd.service.impl.PrdWorkReportServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 既有报工审核的父→子锁序和当前重读反例；模拟 Mapper 不代表 InnoDB 并发验证。 */
class PrdWorkReportParentLockOrderTest {
    private PrdWorkReportMapper reports;
    private PrdWorkOrderMapper orders;
    private PrdWorkReportServiceImpl service;
    private PrdWorkReport old;
    private PrdWorkReport current;
    private PrdWorkOrder wo;

    @BeforeAll
    static void tableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), PrdWorkReport.class);
    }

    @BeforeEach
    void before() {
        reports = mock(PrdWorkReportMapper.class); orders = mock(PrdWorkOrderMapper.class);
        service = new PrdWorkReportServiceImpl(reports, orders);
        old = report("1"); current = report("2");
        wo = new PrdWorkOrder(); wo.setWoId(201L); wo.setPlannedQty(new BigDecimal("6")); wo.setCompletedQty(BigDecimal.ZERO);
        when(reports.selectById(701L)).thenReturn(old);
        when(reports.selectOne(any())).thenReturn(current);
        when(orders.selectById(201L)).thenReturn(wo);
        when(orders.selectOne(any())).thenReturn(wo);
        when(reports.update(any(), any())).thenReturn(1);
        when(orders.update(any(), any())).thenReturn(1);
    }

    private static PrdWorkReport report(String quantity) {
        PrdWorkReport report = new PrdWorkReport(); report.setReportId(701L); report.setWoId(201L);
        report.setReportStatus("SUBMITTED"); report.setReportedQty(new BigDecimal(quantity)); return report;
    }

    private PrdWorkReport approval() {
        PrdWorkReport request = new PrdWorkReport(); request.setReportId(701L); request.setReportStatus("APPROVED"); return request;
    }

    @Test
    @DisplayName("报工审核必须先锁父工单，再锁报工子行，最后原CAS与累计回写")
    void approvalLocksParentBeforeChildCas() {
        assertEquals(1, service.updatePrdWorkReport(approval()), "既有审核仍应完成一次");
        InOrder order = inOrder(orders, reports);
        order.verify(reports).selectById(701L);
        order.verify(orders).selectOne(argThat(wrapper -> wrapper.getSqlSegment().contains("FOR UPDATE")));
        order.verify(reports).selectOne(argThat(wrapper -> wrapper.getSqlSegment().contains("FOR UPDATE")));
        order.verify(reports).update(isNull(), any());
        order.verify(orders).update(isNull(), any());
    }

    @Test
    @DisplayName("报工父锁之后必须重读子事实，不得把发现父编号时的旧数量1写回")
    @SuppressWarnings("unchecked")
    void approvalUsesCurrentLockedQuantityNotDiscoverySnapshot() {
        service.updatePrdWorkReport(approval());
        ArgumentCaptor<Wrapper<PrdWorkOrder>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(orders).update(isNull(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSet().contains("completed_qty = completed_qty + 2"), "累计回写必须使用锁读的报工2，而不是旧快照1");
    }

    @Test
    @DisplayName("报工父来源变化时必须拒绝，不能在已持父锁后改锁另一父工单")
    void changedParentFailsBeforeAnyMutation() {
        current.setWoId(202L);
        assertThrows(ServiceException.class, () -> service.updatePrdWorkReport(approval()), "锁读发现报工父来源漂移必须回滚拒绝");
        verify(reports, never()).update(any(), any());
        verify(orders, never()).update(any(), any());
    }

    @Test
    @DisplayName("既有取消或驳回路径只改报工子行，不在子写之后反向取得父锁")
    void nonApprovalDoesNotAcquireParentAfterChildWrite() {
        PrdWorkReport request = new PrdWorkReport(); request.setReportId(701L); request.setReportStatus("REJECTED");
        when(reports.updateById(request)).thenReturn(1);
        assertEquals(1, service.updatePrdWorkReport(request), "非审核修改保留既有行为");
        verify(reports).updateById(request);
        verifyNoInteractions(orders);
    }
}
