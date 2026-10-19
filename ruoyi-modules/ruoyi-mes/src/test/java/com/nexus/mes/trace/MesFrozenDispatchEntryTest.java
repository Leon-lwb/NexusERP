package com.nexus.mes.trace;

import com.baomidou.lock.LockTemplate;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.pro.domain.ProDispatch;
import com.nexus.mes.pro.mapper.*;
import com.nexus.mes.pro.service.*;
import com.nexus.mes.pro.service.impl.ProDispatchServiceImpl;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MesFrozenDispatchEntryTest {
    ProDispatchMapper mapper;
    ProWorkOrderStepProgressService progress;
    MesPreparedWorkOrderPolicies prepared;
    ProDispatchServiceImpl service;

    @BeforeEach
    void setup() {
        mapper = mock(ProDispatchMapper.class);
        progress = mock(ProWorkOrderStepProgressService.class);
        prepared = mock(MesPreparedWorkOrderPolicies.class);
        service =
                new ProDispatchServiceImpl(
                        mapper,
                        mock(ProFlowCardStepMapper.class),
                        progress,
                        mock(ProFlowCardMapper.class),
                        mock(IProFlowCardService.class),
                        mock(LockTemplate.class),
                        prepared);
        var dispatch = new ProDispatch();
        dispatch.setDispatchId(55L);
        dispatch.setCardId(11L);
        when(mapper.selectById(55L)).thenReturn(dispatch);
    }

    @Test
    void publicDispatchCompletionPreparesPolicyBeforeAnyParentLock() {
        doThrow(new ServiceException("stop at first lock")).when(progress).lockForCard(11L);
        assertThrows(
                ServiceException.class,
                () ->
                        service.completeDispatch(
                                55L, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));
        var order = inOrder(prepared, progress);
        order.verify(prepared).prepareCard(11L);
        order.verify(progress).lockForCard(11L);
    }

    @Test
    void unknownReleaseCannotTakeTheFirstDispatchParentLock() {
        doThrow(new ServiceException("UNKNOWN")).when(prepared).prepareCard(11L);
        assertThrows(
                ServiceException.class,
                () ->
                        service.completeDispatch(
                                55L, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));
        verifyNoInteractions(progress);
    }

    @Test
    void directTransactionalDispatchImplementationAlsoRequiresPreparation() {
        doThrow(new ServiceException("UNKNOWN")).when(prepared).prepareCard(11L);
        assertThrows(
                ServiceException.class,
                () ->
                        service.doCompleteDispatch(
                                55L, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));
        verifyNoInteractions(progress);
    }
}
