package com.nexus.erp.prd;

import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdMaterialRequirementService;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import com.nexus.erp.prd.service.impl.PrdProductionPlanServiceImpl;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoSalesOrderLineService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生产计划转工单数量口径回归测试。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdProductionPlanServiceImplTest {

    @Mock
    private PrdProductionPlanMapper baseMapper;
    @Mock
    private BdBomMapper bdBomMapper;
    @Mock
    private BdMaterialMapper bdMaterialMapper;
    @Mock
    private BdMaterialCategoryMapper bdMaterialCategoryMapper;
    @Mock
    private BdBomDetailMapper bdBomDetailMapper;
    @Mock
    private IPrdMaterialRequirementService prdMaterialRequirementService;
    @Mock
    private ISoSalesOrderLineService soSalesOrderLineService;
    @Mock
    private SoSalesOrderLineMapper soSalesOrderLineMapper;
    @Mock
    private SoSalesOrderHeaderMapper soSalesOrderHeaderMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private PrdWorkOrderMapper prdWorkOrderMapper;
    @Mock
    private IPrdWorkOrderService prdWorkOrderService;

    @InjectMocks
    private PrdProductionPlanServiceImpl productionPlanService;

    /**
     * 根需求的 totalRequiredQuantity 是 BOM 子物料需求合计，成品工单必须使用生产计划数量。
     */
    @Test
    void generateWorkOrders_rootRequirementUsesPlanQuantity() {
        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanId(1001L);
        plan.setPlanName("精密控制柜生产计划");
        plan.setTenantId("000000");
        plan.setTotalQuantity(new BigDecimal("10"));
        plan.setBomId(4001L);

        PrdMaterialRequirement rootRequirement = new PrdMaterialRequirement();
        rootRequirement.setRequirementId(2001L);
        rootRequirement.setPlanId(1001L);
        rootRequirement.setTenantId("000000");
        rootRequirement.setParentRequirementId(null);
        rootRequirement.setBomId(4001L);
        rootRequirement.setRequirementNo("MR202609290001");
        rootRequirement.setMaterialId(3001L);
        rootRequirement.setMaterialCode("FG-CONTROL-CABINET");
        rootRequirement.setMaterialName("精密控制柜");
        rootRequirement.setTotalRequiredQuantity(new BigDecimal("5.4292"));

        BdBom bom = workOrderBom();
        bom.setParentMaterialId(3001L);
        lenient().when(bdBomMapper.selectById(4001L)).thenReturn(bom);
        when(baseMapper.selectById(1001L)).thenReturn(plan);
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L))
            .thenReturn(rootRequirement);
        when(prdWorkOrderMapper.selectCount(any())).thenReturn(0L);
        when(autoCodeUtil.genSerialCode("PRD_WORK_ORDER_CODE", null)).thenReturn("WO202609290001");

        assertEquals(1, productionPlanService.generateWorkOrders(1001L, List.of(2001L)));

        ArgumentCaptor<PrdWorkOrder> workOrderCaptor = ArgumentCaptor.forClass(PrdWorkOrder.class);
        verify(prdWorkOrderMapper).insert(workOrderCaptor.capture());
        assertEquals(0, new BigDecimal("10").compareTo(workOrderCaptor.getValue().getPlannedQty()));
    }
    /** 虚拟件跳层产生的非根需求同样没有可信产量，不能退回使用组件合计。 */
    @Test
    void generateWorkOrders_phantomChildWithoutParentFailsClosedWithoutOutputQuantity() {
        PrdMaterialRequirement child = workOrderRequirement();
        child.setRequirementId(2002L);
        child.setParentRequirementId(null);
        child.setBomId(4002L);
        child.setMaterialId(39L);
        when(baseMapper.selectById(1001L)).thenReturn(workOrderPlan());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2002L)).thenReturn(child);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2002L)));
        assertTrue(error.getMessage().contains("子装配"));
        assertTrue(error.getMessage().contains("数量"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    /** 两个真实计划不能复用同一条物料需求，尤其不能套用 A 的计划号生成 B 的工单。 */
    @Test
    void generateWorkOrders_rejectsRequirementFromDifferentPlanBeforeCodeGeneration() {
        PrdProductionPlan plan = workOrderPlan();
        PrdMaterialRequirement foreign = workOrderRequirement();
        foreign.setPlanId(1002L);
        when(baseMapper.selectById(1001L)).thenReturn(plan);
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(foreign);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L)));
        assertTrue(error.getMessage().contains("生产计划"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    @Test
    void generateWorkOrders_rejectsRequirementFromDifferentTenantBeforeCodeGeneration() {
        PrdMaterialRequirement foreign = workOrderRequirement();
        foreign.setTenantId("000001");
        when(baseMapper.selectById(1001L)).thenReturn(workOrderPlan());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(foreign);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L)));
        assertTrue(error.getMessage().contains("租户"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    @Test
    void generateWorkOrders_rejectsRootProductDifferentFromSelectedBomParent() {
        PrdMaterialRequirement wrongProduct = workOrderRequirement();
        wrongProduct.setMaterialId(39L);
        when(baseMapper.selectById(1001L)).thenReturn(workOrderPlan());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(wrongProduct);
        lenient().when(bdBomMapper.selectById(4001L)).thenReturn(workOrderBom());

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L)));
        assertTrue(error.getMessage().contains("BOM"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    /** B 需求不是 A 的根 BOM，即使 parentRequirementId 为 null（虚拟件跳层）也不能拿组件合计当子装配产量。 */
    @Test
    void generateWorkOrders_childAssemblyWithoutTrustedParentQuantityFailsClosed() {
        PrdMaterialRequirement child = workOrderRequirement();
        child.setRequirementId(2002L);
        child.setParentRequirementId(2001L);
        child.setBomId(4002L);
        child.setMaterialId(39L);
        child.setTotalRequiredQuantity(new BigDecimal("5.4292"));
        when(baseMapper.selectById(1001L)).thenReturn(workOrderPlan());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2002L)).thenReturn(child);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2002L)));
        assertTrue(error.getMessage().contains("子装配"));
        assertTrue(error.getMessage().contains("数量"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    /** 即使首项为合法根工单，后项无法确认数量时也不能先消耗编号、落半套工单。 */
    @Test
    void generateWorkOrders_batchValidRootThenChildDoesNotPartiallyInsert() {
        PrdMaterialRequirement child = workOrderRequirement();
        child.setRequirementId(2002L);
        child.setParentRequirementId(2001L);
        child.setBomId(4002L);
        child.setMaterialId(39L);
        when(baseMapper.selectById(1001L)).thenReturn(workOrderPlan());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(workOrderRequirement());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2002L)).thenReturn(child);
        lenient().when(bdBomMapper.selectById(4001L)).thenReturn(workOrderBom());

        assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L, 2002L)));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    @Test
    void generateWorkOrders_rejectsRootBomFromDifferentTenantBeforeWrite() {
        BdBom foreignBom = workOrderBom();
        foreignBom.setTenantId("000001");
        when(baseMapper.selectById(1001L)).thenReturn(workOrderPlan());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(workOrderRequirement());
        lenient().when(bdBomMapper.selectById(4001L)).thenReturn(foreignBom);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L)));
        assertTrue(error.getMessage().contains("租户"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    @Test
    void generateWorkOrders_rejectsMissingRootBomBeforeWrite() {
        when(baseMapper.selectById(1001L)).thenReturn(workOrderPlan());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(workOrderRequirement());

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L)));
        assertTrue(error.getMessage().contains("BOM"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    @Test
    void generateWorkOrders_rejectsNullPlanTenantInsteadOfTreatingNullsAsSameTenant() {
        PrdProductionPlan plan = workOrderPlan();
        plan.setTenantId(null);
        PrdMaterialRequirement requirement = workOrderRequirement();
        requirement.setTenantId(null);
        when(baseMapper.selectById(1001L)).thenReturn(plan);
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(requirement);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L)));
        assertTrue(error.getMessage().contains("租户"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    @Test
    void generateWorkOrders_zeroPlanOutputQuantityFailsClosedInsteadOfUsingComponentsTotal() {
        PrdProductionPlan plan = workOrderPlan();
        plan.setTotalQuantity(BigDecimal.ZERO);
        when(baseMapper.selectById(1001L)).thenReturn(plan);
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(workOrderRequirement());
        lenient().when(bdBomMapper.selectById(4001L)).thenReturn(workOrderBom());

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateWorkOrders(1001L, List.of(2001L)));
        assertTrue(error.getMessage().contains("数量"));
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
    }

    /** 本机已有合法根工单场景：重复生成返回 0，不更新工单、不消耗编号。 */
    @Test
    void generateWorkOrders_existingLocalRootWorkOrderIsNotChanged() {
        long planId = 2104503668382289922L;
        long requirementId = 2104506638926794754L;
        PrdProductionPlan plan = workOrderPlan();
        plan.setPlanId(planId);
        plan.setBomId(1L);
        PrdMaterialRequirement requirement = workOrderRequirement();
        requirement.setRequirementId(requirementId);
        requirement.setPlanId(planId);
        requirement.setBomId(1L);
        requirement.setMaterialId(1L);
        BdBom bom = workOrderBom();
        bom.setBomId(1L);
        bom.setParentMaterialId(1L);
        when(baseMapper.selectById(planId)).thenReturn(plan);
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(requirementId)).thenReturn(requirement);
        lenient().when(bdBomMapper.selectById(1L)).thenReturn(bom);
        when(prdWorkOrderMapper.selectCount(any())).thenReturn(1L);

        assertEquals(0, productionPlanService.generateWorkOrders(planId, List.of(requirementId)));
        verify(prdWorkOrderMapper).selectCount(any());
        verify(autoCodeUtil, never()).genSerialCode(any(), any());
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
        org.mockito.Mockito.verifyNoMoreInteractions(prdWorkOrderMapper);
    }

    private static PrdProductionPlan workOrderPlan() {
        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanId(1001L);
        plan.setPlanName("高强度螺栓组件生产计划");
        plan.setTenantId("000000");
        plan.setBomId(4001L);
        plan.setTotalQuantity(new BigDecimal("10"));
        return plan;
    }

    private static PrdMaterialRequirement workOrderRequirement() {
        PrdMaterialRequirement requirement = new PrdMaterialRequirement();
        requirement.setRequirementId(2001L);
        requirement.setPlanId(1001L);
        requirement.setTenantId("000000");
        requirement.setBomId(4001L);
        requirement.setMaterialId(38L);
        requirement.setMaterialName("高强度螺栓组件 M12");
        requirement.setTotalRequiredQuantity(new BigDecimal("5.4292"));
        return requirement;
    }

    private static BdBom workOrderBom() {
        BdBom bom = new BdBom();
        bom.setBomId(4001L);
        bom.setParentMaterialId(38L);
        bom.setTenantId("000000");
        return bom;
    }
    /** BOM 主键并非成品物料主键：根节点应使用选中 BOM 的父项物料。 */
    @Test
    void generateMaterialRequirements_rootUsesSelectedBomParentMaterialNotBomId() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        SoSalesOrderLine line = new SoSalesOrderLine();
        line.setLineId(8001L);
        line.setTenantId("000000");
        line.setMaterialId(38L);
        BdMaterial product = new BdMaterial();
        product.setMaterialId(38L);
        product.setMaterialCode("IF20260914001");
        product.setMaterialName("高强度螺栓组件 M12");
        BdMaterial part = new BdMaterial();
        part.setMaterialId(39L);
        part.setMaterialCode("MAT20260914039");
        BdBomDetailVo detail = new BdBomDetailVo();
        detail.setBomId(2L);
        detail.setChildMaterialId(39L);
        detail.setQuantityPerParent(BigDecimal.ONE);
        detail.setLossRate(BigDecimal.ZERO);

        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);
        lenient().when(soSalesOrderLineMapper.selectById(8001L)).thenReturn(line);
        when(bdBomDetailMapper.selectVoList(any())).thenReturn(List.of(detail));
        when(bdMaterialMapper.selectById(any())).thenAnswer(invocation -> {
            Long materialId = invocation.getArgument(0);
            return materialId.equals(38L) ? product : materialId.equals(39L) ? part : null;
        });
        doAnswer(invocation -> {
            PrdMaterialRequirement requirement = invocation.getArgument(0);
            requirement.setRequirementId(5001L);
            return 1;
        }).when(prdMaterialRequirementService).insertPrdMaterialRequirement(any());

        List<PrdMaterialRequirement> created = productionPlanService.generateMaterialRequirementsWithDetails(1003L);

        assertEquals(1, created.size());
        assertEquals(2L, created.get(0).getBomId());
        assertEquals(38L, created.get(0).getMaterialId());
        assertEquals(0, new BigDecimal("10").compareTo(created.get(0).getTotalRequiredQuantity()));
        verify(bdMaterialMapper, never()).selectById(2L);
        verify(soSalesOrderLineMapper).selectById(8001L);
        verify(prdMaterialRequirementService).insertPrdMaterialRequirementDetails(any());
    }

    @Test
    void generateMaterialRequirements_rejectsDifferentSalesLineMaterialBeforeInsert() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        SoSalesOrderLine line = new SoSalesOrderLine();
        line.setLineId(8001L);
        line.setTenantId("000000");
        line.setMaterialId(40L);
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);
        lenient().when(soSalesOrderLineMapper.selectById(8001L)).thenReturn(line);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("物料"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_rejectsBomFromAnotherTenantBeforeInsert() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        bom.setTenantId("000001");
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("租户"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_rejectsStaleBomVersionBeforeInsert() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        bom.setBomVersion("2.0");
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("版本"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_rejectsExpiredBomBeforeInsert() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        bom.setExpiryDate(new Date(System.currentTimeMillis() - 86_400_000L));
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("有效"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_rejectsFutureBomBeforeInsert() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        bom.setEffectiveDate(new Date(System.currentTimeMillis() + 86_400_000L));
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("有效"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_rejectsInactiveSelectedBomWithoutFallback() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        bom.setIsCurrent(0L);
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("有效"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
        verify(bdBomMapper, never()).selectList(any());
    }


    @Test
    void generateMaterialRequirements_rejectsCrossTenantSalesLineBeforeInsert() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        SoSalesOrderLine line = new SoSalesOrderLine();
        line.setLineId(8001L);
        line.setTenantId("000001");
        line.setMaterialId(38L);
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);
        lenient().when(soSalesOrderLineMapper.selectById(8001L)).thenReturn(line);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("租户"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_rejectsMissingBomParentBeforeInsert() {
        PrdProductionPlan plan = mrpPlan();
        BdBom bom = selectedBom();
        bom.setParentMaterialId(null);
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("父项物料"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_bomExpiryTodayIsStillValidCalendarDay() {
        PrdProductionPlan plan = mrpPlan();
        plan.setOrderLineId(null);
        BdBom bom = selectedBom();
        bom.setExpiryDate(java.sql.Date.valueOf(LocalDate.now(ZoneId.of("Asia/Shanghai"))));
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(bom);

        assertTrue(productionPlanService.generateMaterialRequirementsWithDetails(1003L).isEmpty());
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
    }

    @Test
    void generateMaterialRequirements_existingRootWithBomIdAsMaterialFailsClosedForAudit() {
        PrdProductionPlan plan = mrpPlan();
        PrdMaterialRequirement corrupted = new PrdMaterialRequirement();
        corrupted.setPlanId(1003L);
        corrupted.setRequirementId(5001L);
        corrupted.setBomId(2L);
        corrupted.setParentRequirementId(null);
        corrupted.setMaterialId(2L); /** 历史错误：BOM ID 被写成根物料 ID */
        corrupted.setTenantId("000000");
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        lenient().when(bdBomMapper.selectById(2L)).thenReturn(selectedBom());
        lenient().when(soSalesOrderLineMapper.selectById(8001L)).thenReturn(relatedOrderLine());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementList(any())).thenReturn(List.of(corrupted));

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("人工审计"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirementDetails(any());
    }

    @Test
    void generateMaterialRequirements_existingRequirementKeepsIdempotentReturn() {
        PrdProductionPlan plan = mrpPlan();
        PrdMaterialRequirement root = new PrdMaterialRequirement();
        root.setPlanId(1003L);
        root.setRequirementId(5001L);
        root.setBomId(2L);
        root.setMaterialId(38L);
        root.setTenantId("000000");
        PrdMaterialRequirement child = new PrdMaterialRequirement();
        child.setPlanId(1003L);
        child.setRequirementId(5002L);
        child.setParentRequirementId(5001L);
        child.setBomId(3L);
        child.setMaterialId(39L);
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        lenient().when(bdBomMapper.selectById(2L)).thenReturn(selectedBom());
        lenient().when(soSalesOrderLineMapper.selectById(8001L)).thenReturn(relatedOrderLine());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementList(any())).thenReturn(List.of(root, child));

        assertEquals(List.of(root, child), productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirementDetails(any());
    }

    @Test
    void generateMaterialRequirements_existingPhantomRootWithoutVerifiableRootRequiresAudit() {
        PrdProductionPlan plan = mrpPlan();
        PrdMaterialRequirement child = new PrdMaterialRequirement();
        child.setPlanId(1003L);
        child.setRequirementId(5002L);
        child.setParentRequirementId(null); /** 虚拟件跳层或历史异常都可能让子件没有父节点 */
        child.setBomId(3L);
        child.setMaterialId(39L);
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        lenient().when(bdBomMapper.selectById(2L)).thenReturn(selectedBom());
        lenient().when(soSalesOrderLineMapper.selectById(8001L)).thenReturn(relatedOrderLine());
        when(prdMaterialRequirementService.selectPrdMaterialRequirementList(any())).thenReturn(List.of(child));

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> productionPlanService.generateMaterialRequirementsWithDetails(1003L));
        assertTrue(error.getMessage().contains("人工审计"));
        assertTrue(error.getMessage().contains("虚拟件"));
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirement(any());
        verify(prdMaterialRequirementService, never()).insertPrdMaterialRequirementDetails(any());
    }

    /** 顶层物料是虚拟件时，仅递归子 BOM 建主需求；没有根需求记录是原有合法语义。 */
    @Test
    void generateMaterialRequirements_newPhantomRootStillCreatesOnlyChildRequirement() {
        PrdProductionPlan plan = mrpPlan();
        plan.setOrderLineId(null);
        BdBom rootBom = selectedBom();
        BdBom childBom = selectedBom();
        childBom.setBomId(3L);
        childBom.setParentMaterialId(39L);
        BdMaterial phantomRoot = new BdMaterial();
        phantomRoot.setMaterialId(38L);
        phantomRoot.setIsPhantom(1);
        BdMaterial child = new BdMaterial();
        child.setMaterialId(39L);
        BdMaterial leaf = new BdMaterial();
        leaf.setMaterialId(40L);
        BdBomDetailVo rootDetail = new BdBomDetailVo();
        rootDetail.setBomId(2L);
        rootDetail.setChildMaterialId(39L);
        rootDetail.setQuantityPerParent(BigDecimal.ONE);
        BdBomDetailVo childDetail = new BdBomDetailVo();
        childDetail.setBomId(3L);
        childDetail.setChildMaterialId(40L);
        childDetail.setQuantityPerParent(BigDecimal.ONE);
        when(baseMapper.selectById(1003L)).thenReturn(plan);
        when(bdBomMapper.selectById(2L)).thenReturn(rootBom);
        when(bdBomMapper.selectById(3L)).thenReturn(childBom);
        when(bdBomMapper.selectList(any())).thenReturn(List.of(childBom), List.of());
        when(bdBomDetailMapper.selectVoList(any())).thenReturn(List.of(rootDetail), List.of(childDetail));
        when(bdMaterialMapper.selectById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return id.equals(38L) ? phantomRoot : id.equals(39L) ? child : id.equals(40L) ? leaf : null;
        });
        doAnswer(invocation -> {
            PrdMaterialRequirement requirement = invocation.getArgument(0);
            requirement.setRequirementId(5002L);
            return 1;
        }).when(prdMaterialRequirementService).insertPrdMaterialRequirement(any());

        List<PrdMaterialRequirement> created = productionPlanService.generateMaterialRequirementsWithDetails(1003L);

        assertEquals(1, created.size());
        assertEquals(3L, created.get(0).getBomId());
        assertEquals(39L, created.get(0).getMaterialId());
        assertEquals(null, created.get(0).getParentRequirementId());
        verify(prdMaterialRequirementService).insertPrdMaterialRequirement(any());
    }

    private static SoSalesOrderLine relatedOrderLine() {
        SoSalesOrderLine line = new SoSalesOrderLine();
        line.setLineId(8001L);
        line.setTenantId("000000");
        line.setMaterialId(38L);
        return line;
    }
    private static PrdProductionPlan mrpPlan() {
        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanId(1003L);
        plan.setBomId(2L);
        plan.setBomVersion("1.0");
        plan.setOrderLineId(8001L);
        plan.setTotalQuantity(new BigDecimal("10"));
        plan.setTenantId("000000");
        return plan;
    }

    private static BdBom selectedBom() {
        BdBom bom = new BdBom();
        bom.setBomId(2L);
        bom.setParentMaterialId(38L);
        bom.setTenantId("000000");
        bom.setBomVersion("1.0");
        bom.setIsCurrent(1L);
        bom.setStatus(1L);
        bom.setSourceType("PROCESS");
        bom.setEffectiveDate(new Date(System.currentTimeMillis() - 86_400_000L));
        bom.setExpiryDate(new Date(System.currentTimeMillis() + 86_400_000L));
        return bom;
    }
}
