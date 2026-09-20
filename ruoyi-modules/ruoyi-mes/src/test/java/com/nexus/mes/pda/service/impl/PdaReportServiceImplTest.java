package com.nexus.mes.pda.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.pda.domain.bo.PdaReportCompleteBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pro.domain.ProDispatch;
import com.nexus.mes.pro.mapper.ProDispatchMapper;
import com.nexus.mes.pro.service.IProDispatchService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PDA 报工测试(MES-PDA-05 / MES-PDA-04 超报上限)
 * <p>
 * 核心断言: 失败以可读 {@link MesActionResultVo#fail} 返回而非抛出异常
 * (修复前在 @Transactional 内 catch 内层事务异常 -> 提交期 UnexpectedRollbackException 500);
 * 超报被前置校验拦截。
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class PdaReportServiceImplTest {

    @Mock
    private IProDispatchService dispatchService;
    @Mock
    private ProDispatchMapper dispatchMapper;
    @InjectMocks
    private PdaReportServiceImpl service;

    private ProDispatch dispatch(String status, String plannedQty) {
        ProDispatch d = new ProDispatch();
        d.setDispatchId(1L);
        d.setDispatchNo("DIS-001");
        d.setDispatchStatus(status);
        d.setPlannedQty(new BigDecimal(plannedQty));
        return d;
    }

    private PdaReportCompleteBo bo(String goodQty) {
        PdaReportCompleteBo bo = new PdaReportCompleteBo();
        bo.setGoodQty(new BigDecimal(goodQty));
        return bo;
    }

    @Test
    void completeReturnsReadableFailureWhenOverReported() {
        when(dispatchMapper.selectById(1L)).thenReturn(dispatch(IProDispatchService.STATUS_IN_PROGRESS, "10"));

        MesActionResultVo result = service.complete(1L, bo("11"));

        assertFalse(Boolean.TRUE.equals(result.getOk()));
        assertTrue(result.getMessage().contains("不能超过派工计划数量"), result.getMessage());
        verify(dispatchService, never()).completeDispatch(anyLong(), any(), any(), any());
    }

    @Test
    void completeReturnsReadableFailureInsteadOfThrowingWhenInnerServiceFails() {
        when(dispatchMapper.selectById(1L)).thenReturn(dispatch(IProDispatchService.STATUS_IN_PROGRESS, "10"));
        doThrow(new ServiceException("报工数量合计必须等于派工计划数量 10"))
            .when(dispatchService).completeDispatch(eq(1L), any(), any(), any());

        // 关键: 不抛异常(修复前共享事务已 rollback-only, 提交期抛 UnexpectedRollbackException -> 500)
        MesActionResultVo result = service.complete(1L, bo("9"));

        assertFalse(Boolean.TRUE.equals(result.getOk()));
        assertEquals("报工数量合计必须等于派工计划数量 10", result.getMessage());
    }

    @Test
    void startReturnsReadableFailureInsteadOfThrowingWhenInnerServiceFails() {
        when(dispatchMapper.selectById(1L)).thenReturn(dispatch(IProDispatchService.STATUS_PENDING, "10"));
        doThrow(new ServiceException("派工状态已变更, 仅待开始可开工")).when(dispatchService).startDispatch(1L);

        MesActionResultVo result = service.start(1L);

        assertFalse(Boolean.TRUE.equals(result.getOk()));
        assertEquals("派工状态已变更, 仅待开始可开工", result.getMessage());
    }

    @Test
    void completeIsIdempotentWhenDispatchAlreadyCompleted() {
        when(dispatchMapper.selectById(1L)).thenReturn(dispatch(IProDispatchService.STATUS_COMPLETED, "10"));

        MesActionResultVo result = service.complete(1L, bo("10"));

        assertTrue(Boolean.TRUE.equals(result.getOk()), result.getMessage());
        verifyNoInteractions(dispatchService);
    }

    @Test
    void completeReturnsReadableFailureWhenNotStarted() {
        when(dispatchMapper.selectById(1L)).thenReturn(dispatch(IProDispatchService.STATUS_PENDING, "10"));

        MesActionResultVo result = service.complete(1L, bo("10"));

        assertFalse(Boolean.TRUE.equals(result.getOk()));
        assertTrue(result.getMessage().contains("请先开工"), result.getMessage());
        verifyNoInteractions(dispatchService);
    }
}
