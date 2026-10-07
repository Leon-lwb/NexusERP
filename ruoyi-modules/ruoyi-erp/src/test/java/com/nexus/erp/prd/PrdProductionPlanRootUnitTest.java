package com.nexus.erp.prd;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialCategoryMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdMaterialRequirementService;
import com.nexus.erp.prd.service.IPrdWorkOrderService;
import com.nexus.erp.prd.service.impl.PrdProductionPlanServiceImpl;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoSalesOrderLineService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 工单基本单位出生回归：运行真实生成方法并捕获实际 insert 入参，不由 Mapper 伪造返回工单。
 * 依赖边界使用内存夹具，不连接现有数据库、编号服务、MQ 或应用；不代表生产验收。
 */
@ExtendWith(MockitoExtension.class)
class PrdProductionPlanRootUnitTest {
    @Mock private PrdProductionPlanMapper baseMapper;
    @Mock private BdBomMapper bdBomMapper;
    @Mock private BdMaterialMapper bdMaterialMapper;
    @Mock private BdUnitOfMeasureMapper bdUnitOfMeasureMapper;
    @Mock private BdMaterialCategoryMapper bdMaterialCategoryMapper;
    @Mock private BdBomDetailMapper bdBomDetailMapper;
    @Mock private IPrdMaterialRequirementService prdMaterialRequirementService;
    @Mock private ISoSalesOrderLineService soSalesOrderLineService;
    @Mock private SoSalesOrderLineMapper soSalesOrderLineMapper;
    @Mock private SoSalesOrderHeaderMapper soSalesOrderHeaderMapper;
    @Mock private AutoCodeUtil autoCodeUtil;
    @Mock private PrdWorkOrderMapper prdWorkOrderMapper;
    @Mock private IPrdWorkOrderService prdWorkOrderService;
    @InjectMocks private PrdProductionPlanServiceImpl service;

    private PrdProductionPlan plan;
    private PrdMaterialRequirement requirement;

    @BeforeEach
    void setUp() {
        plan = new PrdProductionPlan();
        plan.setPlanId(1001L);
        plan.setTenantId("000000");
        plan.setPlanName("真实根物料计划");
        plan.setBomId(4001L);
        plan.setTotalQuantity(new BigDecimal("10.2500"));
        requirement = newRequirement(2001L);
        BdBom bom = new BdBom();
        bom.setBomId(4001L);
        bom.setParentMaterialId(3001L);
        bom.setTenantId("000000");
        when(baseMapper.selectById(1001L)).thenReturn(plan);
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(requirement);
        when(bdBomMapper.selectById(4001L)).thenReturn(bom);
        when(prdWorkOrderMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("正常工单出生必须具有真实根物料基本单位，而不是靠后续人工补齐")
    void rootUnitIsBornFromTrustedMaterialAndUnit() {
        stubValidSource();
        when(autoCodeUtil.genSerialCode("PRD_WORK_ORDER_CODE", null)).thenReturn("WO-ROOT-UNIT-001");
        stubSuccessfulInsert();

        assertEquals(1, service.generateWorkOrders(1001L, List.of(2001L)));
        PrdWorkOrder inserted = capturedWorkOrder();
        System.out.printf("实际生成 insert 入参：unitId=%s, unitName=%s, materialCode=%s%n",
            inserted.getUnitId(), inserted.getUnitName(), inserted.getMaterialCode());
        assertEquals(7001L, inserted.getUnitId(), "实际插入工单的基本单位 ID 不能为空");
        assertAll(
            () -> assertEquals("套", inserted.getUnitName(), "单位名称只能来自主数据单位"),
            () -> assertEquals(3001L, inserted.getMaterialId()),
            () -> assertEquals("FG-TRUSTED", inserted.getMaterialCode(), "不能信任需求单缓存编码"),
            () -> assertEquals("可信根成品", inserted.getMaterialName(), "不能信任需求单缓存名称"),
            () -> assertEquals("标准规格", inserted.getMaterialSpec(), "不能信任需求单缓存规格"),
            () -> assertEquals(plan.getTotalQuantity(), inserted.getPlannedQty(), "保留计划产量，不取组件合计"),
            () -> assertEquals(BigDecimal.ZERO, inserted.getCompletedQty()),
            () -> assertEquals(BigDecimal.ZERO, inserted.getScrapQty()),
            () -> assertEquals(1001L, inserted.getPlanId()),
            () -> assertEquals(4001L, inserted.getBomId()),
            () -> assertEquals("MATERIAL_REQUIREMENT", inserted.getSourceType()),
            () -> assertEquals(2001L, inserted.getSourceId()),
            () -> assertEquals("MR-2001", inserted.getSourceNo()),
            () -> assertEquals(IPrdWorkOrderService.STATUS_CREATED, inserted.getWoStatus()),
            () -> assertEquals("过期需求编码", requirement.getMaterialCode(), "规范化不能反写历史需求")
        );
        var order = inOrder(bdMaterialMapper, bdUnitOfMeasureMapper, autoCodeUtil, prdWorkOrderMapper);
        order.verify(bdMaterialMapper).selectById(3001L);
        order.verify(bdUnitOfMeasureMapper).selectById(7001L);
        order.verify(autoCodeUtil).genSerialCode("PRD_WORK_ORDER_CODE", null);
        order.verify(prdWorkOrderMapper).insert(inserted);
    }

    enum InvalidMaterial {
        MISSING, WRONG_ID, OTHER_TENANT, NO_TENANT, DISABLED, UNKNOWN_STATUS,
        DELETED, UNKNOWN_DELETE_FLAG, NO_BASE_UNIT, INVALID_BASE_UNIT, NO_CODE, NO_NAME
    }

    @ParameterizedTest(name = "根物料来源拒绝：{0}")
    @EnumSource(InvalidMaterial.class)
    @DisplayName("缺失无效或跨租户根物料必须在编号前拒绝")
    void invalidRootMaterialIsRejectedBeforeNumbering(InvalidMaterial reason) {
        BdMaterial material = validMaterial();
        switch (reason) {
            case MISSING -> material = null;
            case WRONG_ID -> material.setMaterialId(3002L);
            case OTHER_TENANT -> material.setTenantId("000001");
            case NO_TENANT -> material.setTenantId(null);
            case DISABLED -> material.setStatus(0L);
            case UNKNOWN_STATUS -> material.setStatus(null);
            case DELETED -> material.setDelFlag("1");
            case UNKNOWN_DELETE_FLAG -> material.setDelFlag(null);
            case NO_BASE_UNIT -> material.setBaseUomId(null);
            case INVALID_BASE_UNIT -> material.setBaseUomId(0L);
            case NO_CODE -> material.setMaterialCode(" ");
            case NO_NAME -> material.setMaterialName(null);
        }
        when(bdMaterialMapper.selectById(3001L)).thenReturn(material);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.generateWorkOrders(1001L, List.of(2001L)), "无可信根物料不能出生工单");
        assertTrue(error.getMessage().contains("物料"), "拒绝应明确指出根物料来源");
        assertNoNumberOrWrite();
        verifyNoInteractions(bdUnitOfMeasureMapper);
    }

    enum InvalidUnit {
        MISSING, WRONG_ID, OTHER_TENANT, NO_TENANT, DISABLED, UNKNOWN_STATUS,
        DELETED, UNKNOWN_DELETE_FLAG, NOT_BASE, UNKNOWN_BASE_FLAG, NO_PRIMARY, PRIMARY_DRIFT,
        RATE_NOT_ONE, NO_RATE, NO_SCALE, NEGATIVE_SCALE, TOO_LARGE_SCALE,
        NO_VERSION, BLANK_VERSION, NO_VERIFIED_AT, NO_NAME
    }

    @ParameterizedTest(name = "基本单位来源拒绝：{0}")
    @EnumSource(InvalidUnit.class)
    @DisplayName("基本单位身份归属状态换算和精度未核验必须拒绝")
    void invalidBaseUnitIsRejectedBeforeNumbering(InvalidUnit reason) {
        BdUnitOfMeasure unit = validUnit();
        switch (reason) {
            case MISSING -> unit = null;
            case WRONG_ID -> unit.setUomId(7002L);
            case OTHER_TENANT -> unit.setTenantId("000001");
            case NO_TENANT -> unit.setTenantId(null);
            case DISABLED -> unit.setStatus(0L);
            case UNKNOWN_STATUS -> unit.setStatus(null);
            case DELETED -> unit.setDelFlag("1");
            case UNKNOWN_DELETE_FLAG -> unit.setDelFlag(null);
            case NOT_BASE -> unit.setIsBase(0L);
            case UNKNOWN_BASE_FLAG -> unit.setIsBase(null);
            case NO_PRIMARY -> unit.setPrimaryId(null);
            case PRIMARY_DRIFT -> unit.setPrimaryId(7002L);
            case RATE_NOT_ONE -> unit.setConversionRate(new BigDecimal("2"));
            case NO_RATE -> unit.setConversionRate(null);
            case NO_SCALE -> unit.setQuantityScale(null);
            case NEGATIVE_SCALE -> unit.setQuantityScale(-1);
            case TOO_LARGE_SCALE -> unit.setQuantityScale(5);
            case NO_VERSION -> unit.setConversionVersion(null);
            case BLANK_VERSION -> unit.setConversionVersion(" ");
            case NO_VERIFIED_AT -> unit.setConversionVerifiedAt(null);
            case NO_NAME -> unit.setUomName(" ");
        }
        when(bdMaterialMapper.selectById(3001L)).thenReturn(validMaterial());
        when(bdUnitOfMeasureMapper.selectById(7001L)).thenReturn(unit);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.generateWorkOrders(1001L, List.of(2001L)), "无可信基本单位不能出生工单");
        assertTrue(error.getMessage().contains("单位"), "拒绝应明确指出基本单位来源");
        assertNoNumberOrWrite();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.25001", "10.00001", "0.00001"})
    @DisplayName("计划数量超过真实单位精度不得舍入伪造工单产量")
    void plannedQuantityBeyondUnitPrecisionIsRejected(String quantity) {
        plan.setTotalQuantity(new BigDecimal(quantity));
        stubValidSource();
        assertThrows(ServiceException.class, () -> service.generateWorkOrders(1001L, List.of(2001L)));
        assertNoNumberOrWrite();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    @DisplayName("整数的尾随零不应错误判为超过单位精度")
    void verifiedScaleAcceptsIntegerWithTrailingZeros(int scale) {
        plan.setTotalQuantity(new BigDecimal("10.0000"));
        BdUnitOfMeasure unit = validUnit();
        unit.setQuantityScale(scale);
        when(bdMaterialMapper.selectById(3001L)).thenReturn(validMaterial());
        when(bdUnitOfMeasureMapper.selectById(7001L)).thenReturn(unit);
        stubSuccessfulInsert();
        assertEquals(1, service.generateWorkOrders(1001L, List.of(2001L)));
        PrdWorkOrder inserted = capturedWorkOrder();
        assertEquals(7001L, inserted.getUnitId(), "不同合法精度仍必须有基本单位出生");
        assertEquals(plan.getTotalQuantity(), inserted.getPlannedQty(), "不能改变原计划数值或精度");
    }

    @Test
    @DisplayName("整批待生成来源后项单位漂移时前项不得消耗编号或部分插入")
    void laterUnitDriftRejectsWholeBatchBeforeNumbering() {
        addSecondRequirement();
        BdUnitOfMeasure drifted = validUnit();
        drifted.setPrimaryId(7002L);
        when(bdMaterialMapper.selectById(3001L)).thenReturn(validMaterial());
        // 同一计划只能有同一根物料，此夹具模拟第二来源读取时归属已漂移，不能放行前项。
        when(bdUnitOfMeasureMapper.selectById(7001L)).thenReturn(validUnit(), drifted);
        assertThrows(ServiceException.class, () -> service.generateWorkOrders(1001L, List.of(2001L, 2002L)));
        assertNoNumberOrWrite();
        verify(bdUnitOfMeasureMapper, times(2)).selectById(7001L);
    }

    @Test
    @DisplayName("整批后项材料读取失败必须原异常传播且无编号无写")
    void laterMaterialReadFailureDoesNotPartiallyGenerate() {
        addSecondRequirement();
        IllegalStateException failure = new IllegalStateException("测试来源读取失败");
        when(bdMaterialMapper.selectById(3001L)).thenReturn(validMaterial()).thenThrow(failure);
        when(bdUnitOfMeasureMapper.selectById(7001L)).thenReturn(validUnit());
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> service.generateWorkOrders(1001L, List.of(2001L, 2002L))), "不得吞异常当作来源已验证");
        assertNoNumberOrWrite();
    }

    @Test
    @DisplayName("已生成来源在新增单位检查前跳过，不修补或重写历史缺失单位工单")
    void existingSourceSkipsNewUnitValidationAndHistoricalWrites() {
        when(prdWorkOrderMapper.selectCount(any())).thenReturn(1L);
        assertEquals(0, service.generateWorkOrders(1001L, List.of(2001L)));
        verifyNoInteractions(bdMaterialMapper, bdUnitOfMeasureMapper, autoCodeUtil);
        verify(prdWorkOrderMapper).selectCount(any());
        verifyNoMoreInteractions(prdWorkOrderMapper);
    }

    @Test
    @DisplayName("混合批次仅检查未生成来源，历史来源不能因新检查失去幂等跳过")
    void mixedExistingAndNewSourceValidatesOnlyNewSource() {
        addSecondRequirement();
        when(prdWorkOrderMapper.selectCount(any())).thenReturn(1L, 0L);
        stubValidSource();
        stubSuccessfulInsert();
        assertEquals(1, service.generateWorkOrders(1001L, List.of(2001L, 2002L)));
        PrdWorkOrder inserted = capturedWorkOrder();
        assertEquals(2002L, inserted.getSourceId(), "历史来源不能被重建");
        assertEquals(7001L, inserted.getUnitId());
        verify(bdMaterialMapper).selectById(3001L);
        verify(bdUnitOfMeasureMapper).selectById(7001L);
        verify(prdWorkOrderMapper, times(2)).selectCount(any());
        verifyNoMoreInteractions(prdWorkOrderMapper);
    }

    @Test
    @DisplayName("混合批次新来源单位错误仍拒绝，不能依赖旧工单的单位放行")
    void mixedExistingAndInvalidNewSourceHasNoWrites() {
        addSecondRequirement();
        when(prdWorkOrderMapper.selectCount(any())).thenReturn(1L, 0L);
        when(bdMaterialMapper.selectById(3001L)).thenReturn(validMaterial());
        when(bdUnitOfMeasureMapper.selectById(7001L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.generateWorkOrders(1001L, List.of(2001L, 2002L)));
        assertNoNumberOrWrite();
        verify(prdWorkOrderMapper, times(2)).selectCount(any());
        verifyNoMoreInteractions(prdWorkOrderMapper);
    }

    @Test
    @DisplayName("同一请求重复来源只出生一张有完整单位的工单")
    void repeatedRequirementIdCreatesOnlyOneWorkOrder() {
        stubValidSource();
        stubSuccessfulInsert();
        assertEquals(1, service.generateWorkOrders(1001L, List.of(2001L, 2001L)));
        assertEquals(7001L, capturedWorkOrder().getUnitId());
        verify(autoCodeUtil).genSerialCode("PRD_WORK_ORDER_CODE", null);
    }

    @ParameterizedTest(name = "工单写入影响行数：{0}")
    @ValueSource(ints = {0, 2, -1})
    @DisplayName("工单插入必须恰好一行，零行或异常行数不得返回生成成功")
    void insertMustAffectExactlyOneRow(int affectedRows) {
        stubValidSource();
        when(prdWorkOrderMapper.insert(any(PrdWorkOrder.class))).thenReturn(affectedRows);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.generateWorkOrders(1001L, List.of(2001L)), "零行不能伪造生成一张成功工单");
        assertTrue(error.getMessage().contains("工单") && error.getMessage().contains("写入"),
            "写入失败应有明确中文异常，并由原事务回滚");
        verify(prdWorkOrderMapper).insert(any(PrdWorkOrder.class));
    }

    @ParameterizedTest(name = "批次后项缺少根物料：{0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("缺失需求或根物料不能静默跳过并生成前项工单")
    void missingRequestedSourceRejectsWholeBatchBeforeNumbering(boolean missingMaterial) {
        PrdMaterialRequirement missing = missingMaterial ? newRequirement(2002L) : null;
        if (missing != null) {
            missing.setMaterialId(null);
        }
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2002L)).thenReturn(missing);
        /** 合法前项夹具用于揭示旧路径部分写入；修复后整批提前拒绝，所以这些下游桩允许未调用。 */
        lenient().when(prdWorkOrderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(bdMaterialMapper.selectById(3001L)).thenReturn(validMaterial());
        lenient().when(bdUnitOfMeasureMapper.selectById(7001L)).thenReturn(validUnit());
        lenient().when(prdWorkOrderMapper.insert(any(PrdWorkOrder.class))).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.generateWorkOrders(1001L, List.of(2001L, 2002L)), "缺失后项不得留下前项成功工单");
        assertTrue(error.getMessage().contains(missingMaterial ? "根物料" : "需求"), "异常应明确缺失的业务来源");
        assertNoNumberOrWrite();
        verifyNoInteractions(bdMaterialMapper, bdUnitOfMeasureMapper);
    }

    private void stubSuccessfulInsert() {
        when(prdWorkOrderMapper.insert(any(PrdWorkOrder.class))).thenReturn(1);
    }

    private void stubValidSource() {
        when(bdMaterialMapper.selectById(3001L)).thenReturn(validMaterial());
        when(bdUnitOfMeasureMapper.selectById(7001L)).thenReturn(validUnit());
    }

    private void addSecondRequirement() {
        when(prdMaterialRequirementService.selectPrdMaterialRequirementByRequirementId(2002L))
            .thenReturn(newRequirement(2002L));
    }

    private PrdWorkOrder capturedWorkOrder() {
        ArgumentCaptor<PrdWorkOrder> captor = ArgumentCaptor.forClass(PrdWorkOrder.class);
        verify(prdWorkOrderMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void assertNoNumberOrWrite() {
        verifyNoInteractions(autoCodeUtil);
        verify(prdWorkOrderMapper, never()).insert(any(PrdWorkOrder.class));
        verify(prdWorkOrderMapper, never()).updateById(any(PrdWorkOrder.class));
        verify(prdWorkOrderMapper, never()).deleteById(any(Long.class));
    }

    private static PrdMaterialRequirement newRequirement(long id) {
        PrdMaterialRequirement row = new PrdMaterialRequirement();
        row.setRequirementId(id);
        row.setTenantId("000000");
        row.setPlanId(1001L);
        row.setBomId(4001L);
        row.setMaterialId(3001L);
        row.setRequirementNo("MR-" + (id == 2001L ? "2001" : "2002"));
        row.setMaterialCode("过期需求编码");
        row.setMaterialName("过期需求名称");
        row.setMaterialSpec("过期需求规格");
        row.setTotalRequiredQuantity(new BigDecimal("999.0000"));
        return row;
    }

    private static BdMaterial validMaterial() {
        BdMaterial row = new BdMaterial();
        row.setMaterialId(3001L);
        row.setTenantId("000000");
        row.setMaterialCode("FG-TRUSTED");
        row.setMaterialName("可信根成品");
        row.setMaterialSpec("标准规格");
        row.setBaseUomId(7001L);
        row.setStatus(1L);
        row.setDelFlag("0");
        return row;
    }

    private static BdUnitOfMeasure validUnit() {
        BdUnitOfMeasure row = new BdUnitOfMeasure();
        row.setUomId(7001L);
        row.setTenantId("000000");
        row.setUomCode("SET-ROOT");
        row.setUomName("套");
        row.setPrimaryId(7001L);
        row.setIsBase(1L);
        row.setConversionRate(new BigDecimal("1.000000"));
        row.setStatus(1L);
        row.setDelFlag("0");
        row.setQuantityScale(4);
        row.setConversionVersion("fixture-unit-v1");
        row.setConversionVerifiedAt(new Date(1759276800000L));
        return row;
    }
}
