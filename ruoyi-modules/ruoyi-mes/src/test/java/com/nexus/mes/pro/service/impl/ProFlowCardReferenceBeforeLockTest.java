package com.nexus.mes.pro.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.bo.ProSkipBo;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 刷写和跳站以非锁定真实父引用拒绝错配，不能只靠当前读条件避免外父行锁。 */
@ExtendWith(MockitoExtension.class)
class ProFlowCardReferenceBeforeLockTest {
    @Mock private ProFlowCardStepMapper stepMapper;
    @Mock private ProWorkOrderStepMapper workOrderStepMapper;
    @Mock private ProWorkOrderStepProgressService progressService;
    @InjectMocks private ProFlowCardServiceImpl service;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach void prepareSources() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "source"), ProFlowCardStep.class);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        ReflectionTestUtils.setField(service, "flashGateMode", "FORCE");
        var foreign = new ProFlowCardStep();
        foreign.setStepRecordId(1L);
        foreign.setCardId(200L);
        foreign.setTenantId("000000");
        foreign.setWoStepId(2001L);
        foreign.setSequenceNum(20);
        lenient().when(stepMapper.selectById(1L)).thenReturn(foreign);
        var card = new com.nexus.mes.pro.domain.ProFlowCard();
        card.setCardId(100L);
        card.setMesWoId(1100L);
        card.setTenantId("000000");
        card.setPlannedQty(java.math.BigDecimal.ONE);
        lenient().when(progressService.lockForCard(100L)).thenReturn(card);
    }

    @AfterEach void clearTenant() { tenant.close(); }

    @Test void flashGateRejectsReferenceBeforeAnyChildLock() {
        assertThrows(com.nexus.common.core.exception.ServiceException.class,
            () -> ReflectionTestUtils.invokeMethod(service, "checkFlashStepGate", 1L, 100L));
        verify(stepMapper).selectById(1L);
        verify(stepMapper, never()).selectOne(any(Wrapper.class));
        verifyNoInteractions(workOrderStepMapper);
    }

    @Test void skipRejectsReferenceBeforeAnyChildLock() {
        var bo = new ProSkipBo();
        bo.setCardId(100L);
        bo.setStepRecordId(1L);
        bo.setReason("验证真实父引用");
        assertThrows(com.nexus.common.core.exception.ServiceException.class, () -> service.doSkip(bo));
        verify(stepMapper).selectById(1L);
        verify(stepMapper, never()).selectOne(any(Wrapper.class));
        verify(stepMapper, never()).update(any(), any());
    }
    @Test void startRejectsReferenceBeforeConditionalStateUpdate() {
        assertThrows(com.nexus.common.core.exception.ServiceException.class, () -> service.startStep(100L, 1L, 7L));
        verify(stepMapper, never()).update(any(), any());
    }

    @Test void completionRejectsReferenceEvenWithValidQuantity() {
        assertThrows(com.nexus.common.core.exception.ServiceException.class,
            () -> service.doCompleteStep(100L, 1L, java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
        verify(stepMapper, never()).selectOne(any(Wrapper.class));
        verify(stepMapper, never()).update(any(), any());
    }

    @Test void aggregateCompletionRejectsReferenceBeforeConditionalStateUpdate() {
        assertThrows(com.nexus.common.core.exception.ServiceException.class, () -> service.checkAndCompleteStepUnderLock(100L, 1L));
        verify(stepMapper, never()).selectOne(any(Wrapper.class));
        verify(stepMapper, never()).update(any(), any());
    }

}
