package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.service.IBdBomService;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import com.nexus.erp.prd.service.PrdWorkOrderTrackingPolicyService;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.service.IWmStockReservationService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdWorkOrderServiceImplTest {

    @Mock private PrdWorkOrderMapper baseMapper;
    @Mock private PrdProductionPlanMapper prdProductionPlanMapper;
    @Mock private PrdMaterialRequirementMapper requirementMapper;
    @Mock private PrdMaterialRequirementDetailMapper detailMapper;
    @Mock private WmProductionIssueMapper productionIssueMapper;
    @Mock private WmProductionIssueLineMapper productionIssueLineMapper;
    @Mock private AutoCodeUtil autoCodeUtil;
    @Mock private ReliableMessageProducer messageProducer;
    @Mock private IBdBomService bomService;
    @Mock private IWmStockReservationService stockReservationService;

    @Mock private PrdWorkOrderTrackingPolicyService trackingPolicies;

    @InjectMocks private PrdWorkOrderServiceImpl workOrderService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PrdMaterialRequirement.class);
        TableInfoHelper.initTableInfo(assistant, PrdMaterialRequirementDetail.class);
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, PrdProductionPlan.class);
    }

    @BeforeEach
    void initSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void release_createsPendingIssueWithoutWarehouseAndSchedulesMesMessage() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(11L);
        wo.setWoNo("WO-11");
        wo.setWoStatus("CREATED");
        wo.setPlanId(22L);
        wo.setSourceType("MATERIAL_REQUIREMENT");
        wo.setSourceId(44L);
        wo.setWorkshopId(3L);
        wo.setTenantId("1001");
        wo.setBackflushFlag(0);
        when(baseMapper.selectById(11L)).thenReturn(wo);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        PrdMaterialRequirement requirement = new PrdMaterialRequirement();
        requirement.setRequirementId(44L);
        requirement.setPlanId(22L);
        requirement.setTenantId("1001");
        when(requirementMapper.selectById(44L)).thenReturn(requirement);

        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();
        detail.setDetailId(55L);
        detail.setRequirementId(44L);
        detail.setMaterialId(66L);
        detail.setRequiredQuantity(new BigDecimal("3"));
        detail.setBackflushFlag(0);
        when(detailMapper.selectList(any())).thenReturn(List.of(detail));
        when(productionIssueMapper.insert(any(WmProductionIssue.class)))
                .thenAnswer(
                        invocation -> {
                            WmProductionIssue issue = invocation.getArgument(0);
                            issue.setIssueId(77L);
                            return 1;
                        });

        workOrderService.release(11L);

        assertEquals("RELEASED", wo.getWoStatus());
        verify(baseMapper).update(isNull(), any());
        verify(baseMapper, never()).updateById(any(PrdWorkOrder.class));
        ArgumentCaptor<WmProductionIssue> issueCaptor =
                ArgumentCaptor.forClass(WmProductionIssue.class);
        verify(productionIssueMapper).insert(issueCaptor.capture());
        WmProductionIssue issue = issueCaptor.getValue();
        assertEquals(11L, issue.getWoId());
        assertEquals("1001", issue.getTenantId());
        assertEquals("CREATED", issue.getStatus());
        assertNull(issue.getWarehouseId(), "车间 ID=3 不能充当领料仓库，亦不能推断其他仓库");
        assertNull(issue.getWarehouseCode());
        assertNull(issue.getWarehouseName());
        assertTrue(issue.getRemark().contains("原料领料仓库待人工选择"));
        verify(productionIssueMapper, never()).deleteById(77L);

        ArgumentCaptor<WmProductionIssueLine> lineCaptor =
                ArgumentCaptor.forClass(WmProductionIssueLine.class);
        verify(productionIssueLineMapper).insert(lineCaptor.capture());
        WmProductionIssueLine line = lineCaptor.getValue();
        assertEquals(77L, line.getIssueId());
        assertEquals(66L, line.getMaterialId());
        assertEquals(new BigDecimal("3"), line.getRequiredQty());
        assertEquals(BigDecimal.ZERO, line.getIssuedQty(), "下达不代表领料已发生");
        assertEquals("PENDING", line.getLineStatus());

        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        verify(messageProducer, never()).send(any(), any(), any());
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        ArgumentCaptor<WorkOrderReleaseMessage> messageCaptor =
                ArgumentCaptor.forClass(WorkOrderReleaseMessage.class);
        verify(messageProducer)
                .send(
                        eq(ExchangeConstants.TOPIC_EXCHANGE),
                        eq(RoutingKeyConstants.MES_WORKORDER_RELEASE),
                        messageCaptor.capture());
        assertEquals(11L, messageCaptor.getValue().getWoId());
        assertEquals(3L, messageCaptor.getValue().getWorkshopId());
        assertEquals(1001L, messageCaptor.getValue().getTenantId());
    }

    @Test
    void release_withoutPlan_allowsStockShortfallAndSchedulesMesWithoutIssue() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(13L);
        wo.setWoNo("WO-13");
        wo.setWoStatus("CREATED");
        wo.setWorkshopId(3L);
        wo.setBomId(99L);
        wo.setPlannedQty(new BigDecimal("2"));
        wo.setBackflushFlag(0);
        when(baseMapper.selectById(13L)).thenReturn(wo);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        IndentedBomLineVo rawMaterial = new IndentedBomLineVo();
        rawMaterial.setMaterialId(66L);
        rawMaterial.setCumulativeQuantity(new BigDecimal("3"));
        when(bomService.explodeBom(99L)).thenReturn(List.of(rawMaterial));
        when(stockReservationService.checkAvailable(66L, null)).thenReturn(BigDecimal.ZERO);

        workOrderService.release(13L);

        verify(stockReservationService).checkAvailable(66L, null);
        assertEquals("RELEASED", wo.getWoStatus(), "库存短缺仅告警，不阻断工单下达");
        verify(requirementMapper, never()).selectList(any());
        verify(productionIssueMapper, never()).insert(any(WmProductionIssue.class));
        verify(productionIssueLineMapper, never()).insert(any(WmProductionIssueLine.class));
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void release_withOnlyBackflushDetails_allowsReleaseWithoutIssueLines() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(12L);
        wo.setWoNo("WO-12");
        wo.setWoStatus("CREATED");
        wo.setPlanId(23L);
        wo.setSourceType("MATERIAL_REQUIREMENT");
        wo.setSourceId(45L);
        wo.setTenantId("1001");
        wo.setWorkshopId(3L);
        wo.setBackflushFlag(0);
        when(baseMapper.selectById(12L)).thenReturn(wo);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        PrdMaterialRequirement requirement = new PrdMaterialRequirement();
        requirement.setRequirementId(45L);
        requirement.setPlanId(23L);
        requirement.setTenantId("1001");
        when(requirementMapper.selectById(45L)).thenReturn(requirement);

        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();
        detail.setRequirementId(45L);
        detail.setBackflushFlag(1);
        when(detailMapper.selectList(any())).thenReturn(List.of(detail));
        when(productionIssueMapper.insert(any(WmProductionIssue.class)))
                .thenAnswer(
                        invocation -> {
                            WmProductionIssue issue = invocation.getArgument(0);
                            issue.setIssueId(78L);
                            return 1;
                        });

        workOrderService.release(12L);

        assertEquals("RELEASED", wo.getWoStatus());
        verify(productionIssueLineMapper, never()).insert(any(WmProductionIssueLine.class));
        verify(productionIssueMapper).deleteById(78L);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void release_whenAnotherCallerClaimedTheOrder_doesNotCreateIssueOrEmitMesEvent() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("CREATED");
        wo.setPlanId(71L);
        wo.setSourceType("MATERIAL_REQUIREMENT");
        wo.setSourceId(81L);
        wo.setTenantId("1001");
        when(baseMapper.selectById(51L)).thenReturn(wo);
        PrdMaterialRequirement requirement = new PrdMaterialRequirement();
        requirement.setRequirementId(81L);
        requirement.setPlanId(71L);
        requirement.setTenantId("1001");
        lenient().when(requirementMapper.selectById(81L)).thenReturn(requirement);
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of());
        /**
         * 模拟陈旧读取：带条件的状态转换必须影响 0 行。
         */
        RuntimeException failure =
                assertThrows(RuntimeException.class, () -> workOrderService.release(51L));
        assertTrue(failure.getMessage().contains("状态已变更"), failure.getMessage());
        verify(productionIssueMapper, never()).insert(any(WmProductionIssue.class));
        verify(messageProducer, never()).send(any(), any(), any());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void release_multiWorkOrderPlan_issuesOnlyItsOwnSourceRequirementDetails() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoNo("WO-51");
        wo.setWoStatus("CREATED");
        wo.setPlanId(71L);
        wo.setSourceType("MATERIAL_REQUIREMENT");
        wo.setSourceId(81L);
        wo.setTenantId("1001");
        wo.setBackflushFlag(0);
        when(baseMapper.selectById(51L)).thenReturn(wo);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);

        PrdMaterialRequirement own = new PrdMaterialRequirement();
        own.setRequirementId(81L);
        own.setPlanId(71L);
        own.setTenantId("1001");
        PrdMaterialRequirement other = new PrdMaterialRequirement();
        other.setRequirementId(82L);
        other.setPlanId(71L);
        other.setTenantId("1001");
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of(own, other));
        lenient().when(requirementMapper.selectById(81L)).thenReturn(own);

        PrdMaterialRequirementDetail ownDetail = new PrdMaterialRequirementDetail();
        ownDetail.setDetailId(91L);
        ownDetail.setRequirementId(81L);
        ownDetail.setMaterialId(101L);
        ownDetail.setRequiredQuantity(new BigDecimal("2"));
        PrdMaterialRequirementDetail otherDetail = new PrdMaterialRequirementDetail();
        otherDetail.setDetailId(92L);
        otherDetail.setRequirementId(82L);
        otherDetail.setMaterialId(102L);
        otherDetail.setRequiredQuantity(new BigDecimal("7"));
        lenient()
                .when(detailMapper.selectList(any()))
                .thenReturn(List.of(ownDetail), List.of(otherDetail));
        when(productionIssueMapper.insert(any(WmProductionIssue.class)))
                .thenAnswer(
                        invocation -> {
                            WmProductionIssue issue = invocation.getArgument(0);
                            issue.setIssueId(111L);
                            return 1;
                        });

        workOrderService.release(51L);

        ArgumentCaptor<WmProductionIssueLine> captor =
                ArgumentCaptor.forClass(WmProductionIssueLine.class);
        verify(productionIssueLineMapper).insert(captor.capture());
        assertEquals(101L, captor.getValue().getMaterialId());
        assertEquals(91L, captor.getValue().getRequirementDetailId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<PrdMaterialRequirementDetail>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(detailMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<PrdMaterialRequirementDetail> query = queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("requirement_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(81L));
    }

    @Test
    void approvedWorkOrder_releasesItsPlanButDoesNotEmitMesReleaseBeforeOrderRelease() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("CREATED");
        wo.setPlanId(71L);
        when(baseMapper.selectById(51L)).thenReturn(wo);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("51");
        business.setActStatus(ActStatus.pass);

        workOrderService.afterFlowHandle(business);

        assertEquals("APPROVED", wo.getWoStatus());
        verify(prdProductionPlanMapper).update(isNull(), any());
        assertTrue(
                TransactionSynchronizationManager.getSynchronizations().isEmpty(),
                "审批不是下达，不应排队发送 MES 工单快照");
        verify(messageProducer, never()).send(any(), any(), any());
    }

    @Test
    void release_rejectsSourceRequirementFromAnotherPlan() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("CREATED");
        wo.setPlanId(71L);
        wo.setSourceType("MATERIAL_REQUIREMENT");
        wo.setSourceId(81L);
        wo.setTenantId("1001");
        when(baseMapper.selectById(51L)).thenReturn(wo);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);
        PrdMaterialRequirement wrongPlan = new PrdMaterialRequirement();
        wrongPlan.setRequirementId(81L);
        wrongPlan.setPlanId(72L);
        wrongPlan.setTenantId("1001");
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of(wrongPlan));
        lenient().when(requirementMapper.selectById(81L)).thenReturn(wrongPlan);
        lenient().when(detailMapper.selectList(any())).thenReturn(List.of());

        RuntimeException failure =
                assertThrows(RuntimeException.class, () -> workOrderService.release(51L));
        assertTrue(failure.getMessage().contains("来源物料需求"), failure.getMessage());
        verify(baseMapper, never()).update(isNull(), any());
        verify(productionIssueLineMapper, never()).insert(any(WmProductionIssueLine.class));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void release_rejectsSourceRequirementFromAnotherTenant() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("APPROVED");
        wo.setPlanId(71L);
        wo.setSourceType("MATERIAL_REQUIREMENT");
        wo.setSourceId(81L);
        wo.setTenantId("1001");
        when(baseMapper.selectById(51L)).thenReturn(wo);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);
        PrdMaterialRequirement wrongTenant = new PrdMaterialRequirement();
        wrongTenant.setRequirementId(81L);
        wrongTenant.setPlanId(71L);
        wrongTenant.setTenantId("2002");
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of(wrongTenant));
        lenient().when(requirementMapper.selectById(81L)).thenReturn(wrongTenant);
        lenient().when(detailMapper.selectList(any())).thenReturn(List.of());

        RuntimeException failure =
                assertThrows(RuntimeException.class, () -> workOrderService.release(51L));
        assertTrue(failure.getMessage().contains("来源物料需求"), failure.getMessage());
        verify(baseMapper, never()).update(isNull(), any());
        verify(productionIssueLineMapper, never()).insert(any(WmProductionIssueLine.class));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void release_withoutSourceRequirement_doesNotBorrowAnotherWorkOrdersMaterials() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("CREATED");
        wo.setPlanId(71L);
        wo.setBackflushFlag(0);
        when(baseMapper.selectById(51L)).thenReturn(wo);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);
        PrdMaterialRequirement other = new PrdMaterialRequirement();
        other.setRequirementId(82L);
        other.setPlanId(71L);
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of(other));
        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();
        detail.setRequirementId(82L);
        detail.setMaterialId(102L);
        detail.setRequiredQuantity(BigDecimal.ONE);
        lenient().when(detailMapper.selectList(any())).thenReturn(List.of(detail));

        workOrderService.release(51L);

        verify(productionIssueMapper, never()).insert(any(WmProductionIssue.class));
        verify(productionIssueLineMapper, never()).insert(any(WmProductionIssueLine.class));
    }

    @Test
    void repeatApprovalDoesNotRegressAlreadyReleasedOrder() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("RELEASED");
        wo.setPlanId(71L);
        when(baseMapper.selectById(51L)).thenReturn(wo);
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("51");
        business.setActStatus(ActStatus.pass);

        workOrderService.afterFlowHandle(business);

        assertEquals("RELEASED", wo.getWoStatus());
        verify(prdProductionPlanMapper, never()).update(isNull(), any());
        verify(baseMapper, never()).update(isNull(), any());
        verify(baseMapper, never()).updateById(any(PrdWorkOrder.class));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void release_claimsApprovedOrderWithIdAndPreviousStatusInAtomicWhereClause() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("APPROVED");
        wo.setTenantId("1001");
        when(baseMapper.selectById(51L)).thenReturn(wo);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);

        workOrderService.release(51L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<PrdWorkOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<PrdWorkOrder> update = captor.getValue();
        String where = update.getSqlSegment();
        assertTrue(where.contains("wo_id") && where.contains("wo_status"), where);
        assertTrue(update.getParamNameValuePairs().containsValue(51L));
        assertTrue(update.getParamNameValuePairs().containsValue("APPROVED"));
        assertTrue(update.getSqlSet().contains("wo_status"));
        verify(baseMapper, never()).updateById(any(PrdWorkOrder.class));
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void release_planSourceIdIsNotAnMrpRequirementId() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("CREATED");
        wo.setPlanId(71L);
        wo.setSourceType("PLAN");
        wo.setSourceId(71L);
        wo.setTenantId("1001");
        when(baseMapper.selectById(51L)).thenReturn(wo);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of());

        workOrderService.release(51L);

        verify(requirementMapper, never()).selectById(any());
        verify(requirementMapper, never()).selectList(any());
        verify(productionIssueMapper, never()).insert(any(WmProductionIssue.class));
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void release_mrpOrderWithMissingSourceMustBeAuditedNotGuessThePlanRequirement() {
        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(51L);
        wo.setWoStatus("CREATED");
        wo.setPlanId(71L);
        wo.setSourceType("MATERIAL_REQUIREMENT");
        wo.setTenantId("1001");
        when(baseMapper.selectById(51L)).thenReturn(wo);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);
        lenient().when(requirementMapper.selectList(any())).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> workOrderService.release(51L));
        verify(baseMapper, never()).update(isNull(), any());
        verify(productionIssueMapper, never()).insert(any(WmProductionIssue.class));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void concurrentReleaseOfTwoStaleSnapshots_onlyOneCallerRegistersAndSendsMesEvent()
            throws Exception {
        CyclicBarrier bothReadCreated = new CyclicBarrier(2);
        AtomicBoolean alreadyClaimed = new AtomicBoolean();
        when(baseMapper.selectById(51L))
                .thenAnswer(
                        invocation -> {
                            PrdWorkOrder wo = new PrdWorkOrder();
                            wo.setWoId(51L);
                            wo.setWoNo("WO-51");
                            wo.setWoStatus("CREATED");
                            wo.setTenantId("1001");
                            bothReadCreated.await(5, TimeUnit.SECONDS);
                            return wo;
                        });
        lenient()
                .when(baseMapper.update(isNull(), any()))
                .thenAnswer(invocation -> alreadyClaimed.compareAndSet(false, true) ? 1 : 0);

        var workers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> caller =
                    () -> {
                        TransactionSynchronizationManager.initSynchronization();
                        try {
                            workOrderService.release(51L);
                            assertEquals(
                                    1,
                                    TransactionSynchronizationManager.getSynchronizations().size());
                            TransactionSynchronizationManager.getSynchronizations()
                                    .get(0)
                                    .afterCommit();
                            return true;
                        } catch (RuntimeException failure) {
                            assertTrue(
                                    TransactionSynchronizationManager.getSynchronizations()
                                            .isEmpty());
                            return false;
                        } finally {
                            TransactionSynchronizationManager.clearSynchronization();
                        }
                    };
            var first = workers.submit(caller);
            var second = workers.submit(caller);
            int successes =
                    (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                            + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes, "两个请求均读到 CREATED 时，只能有一个成功下达");
            verify(messageProducer, times(1))
                    .send(
                            eq(ExchangeConstants.TOPIC_EXCHANGE),
                            eq(RoutingKeyConstants.MES_WORKORDER_RELEASE),
                            any(WorkOrderReleaseMessage.class));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void approvalCallbackUsesPreviousStatusGuardInsteadOfReopeningConcurrentlyReleasedOrder() {
        PrdWorkOrder staleApproval = new PrdWorkOrder();
        staleApproval.setWoId(51L);
        staleApproval.setWoStatus("APPROVING");
        staleApproval.setPlanId(71L);
        when(baseMapper.selectById(51L)).thenReturn(staleApproval);
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("51");
        business.setActStatus(ActStatus.pass);

        workOrderService.afterFlowHandle(business);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<PrdWorkOrder>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<PrdWorkOrder> update = captor.getValue();
        assertTrue(update.getSqlSegment().contains("wo_status"));
        assertTrue(update.getParamNameValuePairs().containsValue("APPROVING"));
        verify(baseMapper, never()).updateById(any(PrdWorkOrder.class));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }
}
