package com.nexus.mes.lt.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.MaterialPolicyPort;
import com.nexus.mes.lt.domain.bo.*;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.mapper.LtWoConsumptionMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.trace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * 验证适配器接线；数量、租户和并发业务行为由制造追溯集成套件覆盖。
 */
@ExtendWith(MockitoExtension.class)
class LtWoConsumptionServiceImplTest {
    @Mock LtWoConsumptionMapper baseMapper;
    @Mock ILtLotService lotService;
    @Mock MesMaterialTraceWriter traceWriter;
    @Mock MaterialPolicyPort policies;
    @Mock MesPreparedWorkOrderPolicies preparedPolicies;
    @Mock MesTraceContext traceContext;
    @InjectMocks LtWoConsumptionServiceImpl service;
    MockedStatic<LoginHelper> login;
    TrackingPolicy policy = new TrackingPolicy(true, false, true, 1L, TrackingStage.STRICT);

    @BeforeEach
    void setup() {
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(901L);
        lenient().when(traceContext.tenant()).thenReturn("000001");
        lenient().when(policies.policy("000001", 20L)).thenReturn(policy);
    }

    @AfterEach
    void cleanup() {
        login.close();
    }

    LtWoConsumptionBo item(long lot, long allocation, int qty) {
        var bo = new LtWoConsumptionBo();
        bo.setCommandId("consume");
        bo.setWoId(10L);
        bo.setMaterialId(20L);
        bo.setLotId(lot);
        bo.setSourceAllocationId(allocation);
        bo.setConsumedQty(new BigDecimal(qty));
        bo.setProcessId(30L);
        bo.setOperationRef("real-operation");
        return bo;
    }

    @Test
    void insertUsesAuthenticatedActorAndPreservesSourceAllocation() {
        var bo = item(1, 2, 3);
        bo.setOperatorId(999L);
        when(traceWriter.pick(any(), eq(policy))).thenReturn(List.of(77L));
        assertTrue(service.insertByBo(bo));
        var pick = ArgumentCaptor.forClass(MesMaterialTraceWriter.Pick.class);
        verify(traceWriter).pick(pick.capture(), eq(policy));
        assertEquals(901L, pick.getValue().actorId());
        assertEquals(2L, pick.getValue().lines().get(0).allocationId());
        assertEquals(77L, bo.getConsumptionId());
        verify(lotService, never()).consume(anyLong(), any());
    }

    @Test
    void traceEnabledPickReadsFrozenWorkOrderPolicyBeforeWriterAndIgnoresCurrentMaster() {
        var frozen = new TrackingPolicy(true, false, false, 7L, TrackingStage.STRICT);
        lenient().when(preparedPolicies.enabled()).thenReturn(true);
        lenient()
                .when(preparedPolicies.componentForWorkOrder("MATERIAL_PICK", "consume", 10L, 20L))
                .thenReturn(frozen);
        when(traceWriter.pick(any(), any())).thenReturn(List.of(77L));
        service.insertByBo(item(1, 2, 3));
        var order = inOrder(preparedPolicies, traceWriter);
        order.verify(preparedPolicies).componentForWorkOrder("MATERIAL_PICK", "consume", 10L, 20L);
        order.verify(traceWriter).pick(any(), eq(frozen));
        verify(policies, never()).policy(anyString(), anyLong());
    }

    @Test
    void unknownFrozenWorkOrderPolicyNeverStartsConsumption() {
        lenient().when(preparedPolicies.enabled()).thenReturn(true);
        lenient()
                .when(
                        preparedPolicies.componentForWorkOrder(
                                anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new ServiceException("legacy UNKNOWN"));
        lenient().when(traceWriter.pick(any(), any())).thenReturn(List.of(77L));
        assertThrows(ServiceException.class, () -> service.insertByBo(item(1, 2, 3)));
        verifyNoInteractions(traceWriter);
    }

    @Test
    void insertRejectsNullRequest() {
        assertThrows(ServiceException.class, () -> service.insertByBo(null));
        verifyNoInteractions(traceWriter);
    }

    @Test
    void policyFailureCannotStartLocalConsumption() {
        var bo = item(1, 2, 3);
        when(policies.policy("000001", 20L)).thenThrow(new ServiceException("policy unavailable"));
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        verifyNoInteractions(traceWriter);
    }

    @Test
    void preflightRunsBeforeWriterStartsItsTransaction() {
        var bo = item(1, 2, 3);
        when(traceWriter.pick(any(), eq(policy))).thenReturn(List.of(77L));
        service.insertByBo(bo);
        var order = inOrder(traceContext, policies, traceWriter);
        order.verify(traceContext).requireNoTransaction();
        order.verify(policies).policy("000001", 20L);
        order.verify(traceWriter).pick(any(), eq(policy));
    }

    @Test
    void pickPreservesAllLotsAndActualSnOperationInOneCommand() {
        var bo = new LtWoPickBo();
        bo.setCommandId("pick-command");
        bo.setWoId(10L);
        bo.setMaterialId(20L);
        bo.setSnId(88L);
        bo.setProcessId(30L);
        bo.setOperationRef("assembly-pass");
        bo.setPicks(List.of(item(1, 2, 3), item(4, 5, 6)));
        when(traceWriter.pick(any(), eq(policy))).thenReturn(List.of(77L, 78L));
        assertTrue(service.confirmPick(bo));
        var request = ArgumentCaptor.forClass(MesMaterialTraceWriter.Pick.class);
        verify(traceWriter).pick(request.capture(), eq(policy));
        assertEquals(2, request.getValue().lines().size());
        assertEquals(88L, request.getValue().snId());
        assertEquals("assembly-pass", request.getValue().operationRef());
        verify(lotService, never()).consume(anyLong(), any());
    }

    @Test
    void confirmPickRejectsNullPicks() {
        assertThrows(ServiceException.class, () -> service.confirmPick(new LtWoPickBo()));
    }

    @Test
    void confirmPickRejectsEmptyPicks() {
        var bo = new LtWoPickBo();
        bo.setPicks(List.of());
        assertThrows(ServiceException.class, () -> service.confirmPick(bo));
    }

    @Test
    void deleteByIdsReturnsFalseForNullIds() {
        assertFalse(service.deleteByIds(null));
    }

    @Test
    void deleteByIdsReturnsFalseForEmptyIds() {
        assertFalse(service.deleteByIds(new Long[0]));
    }

    @Test
    void deleteCannotRemoveActualConsumptionAudit() {
        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[] {1L, 2L}));
        verify(baseMapper, never()).deleteByIds(anyCollection());
    }

    @Test
    void recommendationRetainsExistingLotService() {
        var lot = new LtLotVo();
        when(lotService.recommendFefo(20L, BigDecimal.TEN)).thenReturn(List.of(lot));
        assertEquals(List.of(lot), service.recommendForWo(10L, 20L, BigDecimal.TEN));
    }
}
