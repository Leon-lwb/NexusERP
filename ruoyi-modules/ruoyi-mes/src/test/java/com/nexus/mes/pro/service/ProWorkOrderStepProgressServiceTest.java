package com.nexus.mes.pro.service;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.mapper.ProWorkOrderStepProgressMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 投影服务的真实锁序、幂等覆盖写及错误事务反例，不使用生产数据库。 */
class ProWorkOrderStepProgressServiceTest {
    ProWorkOrderStepProgressMapper mapper;
    ProWorkOrderStepProgressService service;
    MockedStatic<TenantHelper> tenant;
    ProWorkOrder wo;
    ProWorkOrderStep ws;
    ProFlowCard card;
    ProFlowCardStep cs;

    @BeforeEach
    void setup() {
        mapper=mock(ProWorkOrderStepProgressMapper.class);
        service=new ProWorkOrderStepProgressService(mapper, mock(MesProgressTransactionGuard.class));
        tenant=mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        DynamicDataSourceContextHolder.push("mes");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        wo=new ProWorkOrder();wo.setMesWoId(10L);wo.setTenantId("000000");wo.setDelFlag("0");wo.setMaterialId(200L);wo.setPlannedQty(BigDecimal.TEN);wo.setCompletedQty(BigDecimal.TEN);wo.setWoStatus("COMPLETED");
        ws=new ProWorkOrderStep();ws.setStepId(11L);ws.setMesWoId(10L);ws.setTenantId("000000");ws.setDelFlag("0");ws.setSequenceNum(10);ws.setProcessId(300L);ws.setPlannedQty(BigDecimal.TEN);ws.setCompletedQty(BigDecimal.ZERO);ws.setScrapQty(BigDecimal.ZERO);ws.setStepStatus("PENDING");
        card=new ProFlowCard();card.setCardId(12L);card.setMesWoId(10L);card.setTenantId("000000");card.setDelFlag("0");card.setMaterialId(200L);card.setPlannedQty(BigDecimal.TEN);card.setCardStatus("COMPLETED");
        cs=new ProFlowCardStep();cs.setStepRecordId(13L);cs.setCardId(12L);cs.setMesWoId(10L);cs.setTenantId("000000");cs.setDelFlag("0");cs.setWoStepId(11L);cs.setSequenceNum(10);cs.setProcessId(300L);cs.setPlannedQty(BigDecimal.TEN);cs.setGoodQty(BigDecimal.TEN);cs.setDefectQty(BigDecimal.ZERO);cs.setScrapQty(BigDecimal.ZERO);cs.setStepStatus("COMPLETED");
        when(mapper.lockWorkOrder("000000",10L)).thenReturn(wo);
    }
    @AfterEach
    void cleanup(){ tenant.close();DynamicDataSourceContextHolder.clear();TransactionSynchronizationManager.clear(); }
    @Test
    void cardReferenceIsRecheckedAfterSharedParentLock(){
        when(mapper.findCardParent("000000",12L)).thenReturn(10L);
        when(mapper.lockCard("000000",12L)).thenReturn(card);
        assertSame(card,service.lockForCard(12L));
        var order=inOrder(mapper);order.verify(mapper).findCardParent("000000",12L);order.verify(mapper).lockWorkOrder("000000",10L);order.verify(mapper).lockCard("000000",12L);
    }
    @Test
    void snParentIsLockedBeforeCallerCanLockSn(){
        when(mapper.findSnParents("000000","SN-1")).thenReturn(List.of(10L));
        assertEquals(10L,service.lockForSn("SN-1"));
        var order=inOrder(mapper);order.verify(mapper).findSnParents("000000","SN-1");order.verify(mapper).lockWorkOrder("000000",10L);
    }
    @Test
    void duplicateSnParentDoesNotPickOne(){
        when(mapper.findSnParents("000000","SN-1")).thenReturn(List.of(10L,10L));
        assertThrows(ServiceException.class,()->service.lockForSn("SN-1"));
        verify(mapper,never()).lockWorkOrder(anyString(),anyLong());
    }
    @Test
    void changedCardParentIsRejectedBeforeProjection(){
        when(mapper.findCardParent("000000",12L)).thenReturn(10L);card.setMesWoId(99L);when(mapper.lockCard("000000",12L)).thenReturn(card);
        assertThrows(ServiceException.class,()->service.rebuildForCard(12L));verify(mapper,never()).lockWorkOrderSteps(anyString(),anyLong());
    }
    @Test
    void missingTenantOrWrongDatasourceDoesNotReachMapper(){
        DynamicDataSourceContextHolder.clear();DynamicDataSourceContextHolder.push("erp");
        assertThrows(ServiceException.class,()->service.lockForWorkOrder(10L));verify(mapper,never()).lockWorkOrder(anyString(),anyLong());
    }
    @Test
    void noWriteTransactionOrReadOnlyDoesNotReachMapper(){
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThrows(ServiceException.class,()->service.lockForWorkOrder(10L));
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(ServiceException.class,()->service.lockForWorkOrder(10L));verify(mapper,never()).lockWorkOrder(anyString(),anyLong());
    }
    @Test
    void explicitRebuildOnlyWritesStepProgress(){
        scope();when(mapper.updateProgress(eq("000000"),eq(10L),eq(11L),eq(BigDecimal.TEN),eq(BigDecimal.ZERO),eq("COMPLETED"),isNull(),isNull())).thenReturn(1);
        service.rebuild(10L);
        verify(mapper).updateProgress("000000",10L,11L,BigDecimal.TEN,BigDecimal.ZERO,"COMPLETED",null,null);
        assertEquals(BigDecimal.TEN,wo.getCompletedQty(),"不得再增加工单头数量");assertEquals(BigDecimal.TEN,ws.getPlannedQty(),"不得覆盖计划快照");
    }
    @Test
    void sameProjectionDoesNotManufactureUpdateTime(){
        scope();ws.setCompletedQty(BigDecimal.TEN);ws.setStepStatus("COMPLETED");service.rebuild(10L);
        verify(mapper,never()).updateProgress(anyString(),anyLong(),anyLong(),any(),any(),anyString(),any(),any());
    }
    @Test
    void failedProjectionWriteThrowsForOuterTransactionRollback(){
        scope();assertThrows(ServiceException.class,()->service.rebuild(10L));
    }
    void scope(){when(mapper.lockWorkOrderSteps("000000",10L)).thenReturn(List.of(ws));when(mapper.lockCards("000000",10L)).thenReturn(List.of(card));when(mapper.lockCardSteps("000000",10L)).thenReturn(List.of(cs));}
}
