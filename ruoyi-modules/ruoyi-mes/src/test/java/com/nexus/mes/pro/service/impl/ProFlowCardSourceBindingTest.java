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

/** 同租户错配工序不得越过入口父卡，刷写门禁和跳站的锁定读使用同一来源约束。 */
@ExtendWith(MockitoExtension.class)
class ProFlowCardSourceBindingTest {
    @Mock private ProFlowCardStepMapper stepMapper;
    @Mock private ProWorkOrderStepMapper workOrderStepMapper;
    @Mock private ProWorkOrderStepProgressService progressService;
    @Mock private com.nexus.mes.trace.MesPreparedWorkOrderPolicies preparedPolicies;
    @InjectMocks private ProFlowCardServiceImpl service;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach void prepareSources() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "source"), ProFlowCardStep.class);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        ReflectionTestUtils.setField(service, "flashGateMode", "FORCE");
        var reference = new ProFlowCardStep();
        reference.setStepRecordId(1L);
        reference.setCardId(100L);
        reference.setTenantId("000000");
        when(stepMapper.selectById(1L)).thenReturn(reference);
        when(stepMapper.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> query = call.getArgument(0);
            // 模拟真正按父卡执行的查询：卡100不能匹配卡200的步骤，而非让 mock 忽略 SQL 来源。
            if (query.getSqlSegment().contains("card_id")) return null;
            var foreign = new ProFlowCardStep();
            foreign.setStepRecordId(1L);
            foreign.setCardId(200L);
            foreign.setMesWoId(2200L);
            foreign.setWoStepId(2001L);
            foreign.setSequenceNum(20);
            return foreign;
        });
    }

    @AfterEach void clearTenant() { tenant.close(); }

    @Test void flashGateCannotInspectAnotherCardsStepUnderRequestedParentLock() {
        ReflectionTestUtils.invokeMethod(service, "checkFlashStepGate", 1L, 100L);
        verifyNoInteractions(workOrderStepMapper);
        var query = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(stepMapper).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("card_id"));
        assertTrue(query.getValue().getSqlSegment().contains("tenant_id"));
    }

    @Test void skipRejectsMismatchedStepBeforeTryingStateMutation() {
        var bo = new ProSkipBo();
        bo.setCardId(100L);
        bo.setStepRecordId(1L);
        bo.setReason("来源配对校验");
        assertThrows(RuntimeException.class, () -> service.doSkip(bo));
        verify(stepMapper, never()).update(any(), any());
        verify(progressService, never()).rebuildForCard(any());
    }
}
