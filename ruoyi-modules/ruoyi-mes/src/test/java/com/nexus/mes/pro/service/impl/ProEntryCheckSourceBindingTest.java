package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.bo.ProEntryBo;
import com.nexus.mes.pro.domain.bo.ProExitBo;
import com.nexus.mes.pro.mapper.ProAndonMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProStepCheckRuleMapper;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import com.nexus.mes.sop.service.ISopViewLogService;
import com.nexus.mes.tg.service.ITgToolingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 写事务只允许锁入口父卡的工序，不让同租户错配请求先锁另一工单的子行。 */
@ExtendWith(MockitoExtension.class)
class ProEntryCheckSourceBindingTest {
    @Mock private ProFlowCardStepMapper stepMapper;
    @Mock private ProStepCheckRuleMapper ruleMapper;
    @Mock private ProAndonMapper andonMapper;
    @Mock private QcIpqcMapper qcIpqcMapper;
    @Mock private EqEquipmentMapper eqEquipmentMapper;
    @Mock private ISopProcessBindingService sopBindingService;
    @Mock private ISopViewLogService sopViewLogService;
    @Mock private ITgToolingService tgToolingService;
    @Mock private ILtLotService ltLotService;
    @InjectMocks private ProEntryCheckServiceImpl service;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach void beginMesWriteContext() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "binding"), ProFlowCardStep.class);
        DynamicDataSourceContextHolder.push("mes");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
    }

    @AfterEach void clearContext() {
        tenant.close();
        TransactionSynchronizationManager.clear();
        DynamicDataSourceContextHolder.clear();
    }

    @Test void mismatchedEntryParentIsRejectedBeforeSerialGateOrRules() {
        when(stepMapper.selectById(1L)).thenReturn(step(100L));
        when(stepMapper.selectOne(any(Wrapper.class))).thenReturn(step(200L), null);
        var context = new ProEntryBo();
        context.setCardId(100L);
        assertThrows(ServiceException.class, () -> service.runEntryChecks(1L, context));
        verify(stepMapper, times(1)).selectOne(any(Wrapper.class));
        verifyNoInteractions(ruleMapper);
    }

    @Test void mismatchedExitParentIsRejectedBeforeInspectionRules() {
        when(stepMapper.selectById(1L)).thenReturn(step(100L));
        when(stepMapper.selectOne(any(Wrapper.class))).thenReturn(step(200L), null);
        var context = new ProExitBo();
        context.setCardId(100L);
        assertThrows(ServiceException.class, () -> service.runExitChecks(1L, context));
        verify(stepMapper, times(1)).selectOne(any(Wrapper.class));
        verifyNoInteractions(ruleMapper);
    }

    @Test void missingWriteParentIsRejectedBeforeAnyChildRead() {
        assertThrows(ServiceException.class, () -> service.runEntryChecks(1L, new ProEntryBo()));
        verifyNoInteractions(stepMapper, ruleMapper);
    }

    @Test void lockingQueryScopesTenantCardAndStepBeforeTakingChildLock() {
        ProFlowCardStep current = step(100L);
        current.setSequenceNum(null);
        when(stepMapper.selectById(1L)).thenReturn(current);
        when(stepMapper.selectOne(any(Wrapper.class))).thenReturn(current);
        var context = new ProEntryBo();
        context.setCardId(100L);
        assertTrue(service.runEntryChecks(1L, context).getPassed());
        ArgumentCaptor<Wrapper<ProFlowCardStep>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(stepMapper).selectOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"), "锁子行前必须约束当前租户");
        assertTrue(sql.contains("card_id"), "锁子行前必须约束入口已锁父卡");
        assertTrue(sql.contains("step_record_id"));
        assertTrue(sql.endsWith("FOR UPDATE"));
    }

    private ProFlowCardStep step(Long cardId) {
        var step = new ProFlowCardStep();
        step.setStepRecordId(1L);
        step.setCardId(cardId);
        step.setMesWoId(cardId + 1000L);
        step.setTenantId("000000");
        step.setDelFlag("0");
        step.setSequenceNum(20);
        return step;
    }
}
