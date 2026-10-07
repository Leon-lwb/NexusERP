package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.impl.WmProductionIssueServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 新增领料来源的有界归属回归；调用真实服务，仅模拟持久化与租户上下文，不连接外部服务。 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmProductionIssueSourceAffiliationTest {
    private static final String TENANT = "000000";
    private static final Long ISSUE_ID = 41L;
    private static final Long WO_ID = 11L;
    private static final Long PLAN_ID = 101L;
    private static final Long REQUIREMENT_ID = 201L;
    private static final Long DETAIL_ID = 301L;
    private static final Long MATERIAL_ID = 401L;
    private static final String BASE_UNIT = "2";

    @Mock WmProductionIssueMapper issueMapper;
    @Mock WmProductionIssueLineMapper lineMapper;
    @Mock WmMaterialStockMapper stockMapper;
    @Mock WmWarehouseMapper warehouseMapper;
    @Mock IWmStockOperationService stockOperationService;
    @Mock AutoCodeUtil autoCodeUtil;
    @Mock PrdWorkOrderMapper workOrderMapper;
    @Mock PrdProductionPlanMapper planMapper;
    @Mock PrdMaterialRequirementMapper requirementMapper;
    @Mock PrdMaterialRequirementDetailMapper detailMapper;
    @InjectMocks WmProductionIssueServiceImpl service;

    private MockedStatic<TenantHelper> tenantContext;
    private WmProductionIssue storedIssue;
    private List<WmProductionIssueLine> storedLines;
    private PrdWorkOrder workOrder;
    private PrdProductionPlan plan;
    private PrdMaterialRequirement requirement;
    private PrdMaterialRequirementDetail detail;
    private Map<Long, PrdMaterialRequirement> requirements;
    private Map<Long, PrdMaterialRequirementDetail> details;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(WmProductionIssue.class, WmProductionIssueLine.class,
            PrdWorkOrder.class, PrdProductionPlan.class, PrdMaterialRequirement.class, PrdMaterialRequirementDetail.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach
    void prepare() {
        tenantContext = mockStatic(TenantHelper.class);
        tenantContext.when(TenantHelper::getTenantId).thenReturn(TENANT);
        workOrder = new PrdWorkOrder();
        workOrder.setWoId(WO_ID);
        workOrder.setWoNo("离线来源工单");
        workOrder.setPlanId(PLAN_ID);
        workOrder.setSourceType("MATERIAL_REQUIREMENT");
        workOrder.setSourceId(REQUIREMENT_ID);
        workOrder.setMaterialId(501L);
        workOrder.setWoStatus("IN_PROGRESS");
        workOrder.setTenantId(TENANT);
        workOrder.setDelFlag("0");
        plan = new PrdProductionPlan();
        plan.setPlanId(PLAN_ID);
        plan.setTenantId(TENANT);
        plan.setStatus("DRAFT");
        plan.setDelFlag("0");
        requirement = new PrdMaterialRequirement();
        requirement.setRequirementId(REQUIREMENT_ID);
        requirement.setPlanId(PLAN_ID);
        requirement.setMaterialId(501L);
        requirement.setStatus("DRAFT");
        requirement.setTenantId(TENANT);
        requirement.setDelFlag("0");
        detail = new PrdMaterialRequirementDetail();
        detail.setDetailId(DETAIL_ID);
        detail.setRequirementId(REQUIREMENT_ID);
        detail.setMaterialId(MATERIAL_ID);
        detail.setMaterialName("离线测试原料");
        detail.setUnit(BASE_UNIT);
        detail.setRequiredQuantity(new BigDecimal("3"));
        detail.setIssuedQuantity(BigDecimal.ZERO);
        detail.setTenantId(TENANT);
        detail.setDelFlag("0");
        requirements = new HashMap<>();
        requirements.put(REQUIREMENT_ID, requirement);
        details = new HashMap<>();
        details.put(DETAIL_ID, detail);
        storedIssue = new WmProductionIssue();
        storedIssue.setIssueId(ISSUE_ID);
        storedIssue.setIssueCode("OFFLINE-SOURCE");
        storedIssue.setWoId(WO_ID);
        storedIssue.setWoNo(workOrder.getWoNo());
        storedIssue.setTenantId(TENANT);
        storedIssue.setStatus("CREATED");
        WmProductionIssueLine original = newSourceLine();
        original.setLineId(42L);
        original.setIssueId(ISSUE_ID);
        original.setTenantId(TENANT);
        storedLines = new ArrayList<>(List.of(original));

        // 宽松桩用于同一组入口矩阵；只提供数据，不在桩内复制来源校验算法。
        lenient().when(issueMapper.selectById(ISSUE_ID)).thenAnswer(invocation -> copy(storedIssue, new WmProductionIssue()));
        lenient().when(issueMapper.update(any(WmProductionIssue.class), any(Wrapper.class))).thenAnswer(invocation -> {
            copyNonNullProperties(invocation.getArgument(0), storedIssue);
            return 1;
        });
        /** 单独模拟成功的显式参考金额写空，保留普通非空更新的原持久化语义。 */
        lenient().when(issueMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<WmProductionIssue> update = invocation.getArgument(1);
            assertTrue(update.getSqlSet().contains("total_amount="), "来源正控只能显式清空参考金额");
            assertTrue(update.getSqlSegment().contains("issue_id"), "清空参考金额必须限定当前来源单头");
            assertTrue(update.getParamNameValuePairs().containsValue(ISSUE_ID), "清空条件必须对应当前单头");
            assertTrue(update.getParamNameValuePairs().containsValue(null), "未知参考金额必须明确写空");
            storedIssue.setTotalAmount(null);
            return 1;
        });
        lenient().when(issueMapper.updateById(any(WmProductionIssue.class))).thenAnswer(invocation -> {
            copyNonNullProperties(invocation.getArgument(0), storedIssue);
            return 1;
        });
        lenient().when(issueMapper.insert(any(WmProductionIssue.class))).thenAnswer(invocation -> {
            WmProductionIssue row = invocation.getArgument(0);
            row.setIssueId(ISSUE_ID);
            storedIssue = copy(row, new WmProductionIssue());
            return 1;
        });
        lenient().when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> activeLines());
        lenient().when(lineMapper.insert(any(WmProductionIssueLine.class))).thenAnswer(invocation -> {
            WmProductionIssueLine row = invocation.getArgument(0);
            row.setLineId(900L + storedLines.size());
            storedLines.add(copy(row, new WmProductionIssueLine()));
            return 1;
        });
        lenient().when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenAnswer(invocation -> {
            WmProductionIssueLine row = invocation.getArgument(0);
            WmProductionIssueLine stored = storedLines.stream()
                .filter(value -> value.getLineId().equals(row.getLineId())).findFirst().orElseThrow();
            copyNonNullProperties(row, stored);
            return 1;
        });
        lenient().when(lineMapper.deleteById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            storedLines.stream().filter(row -> row.getLineId().equals(id)).findFirst().orElseThrow().setDelFlag("1");
            return 1;
        });
        lenient().when(workOrderMapper.selectById(anyLong())).thenAnswer(invocation -> workOrder);
        lenient().when(workOrderMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> workOrder);
        lenient().when(planMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> plan);
        lenient().when(requirementMapper.selectById(anyLong())).thenAnswer(invocation -> requirements.get(invocation.getArgument(0)));
        lenient().when(requirementMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation ->
            requirements.get(queryValue(invocation.getArgument(0), "requirement_id")));
        lenient().when(requirementMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> new ArrayList<>(requirements.values()));
        lenient().when(detailMapper.selectById(anyLong())).thenAnswer(invocation -> details.get(invocation.getArgument(0)));
        lenient().when(detailMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation ->
            details.get(queryValue(invocation.getArgument(0), "detail_id")));
        lenient().when(detailMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Object id = queryValue(invocation.getArgument(0), "requirement_id");
            return details.values().stream().filter(row -> row.getRequirementId().equals(id)).toList();
        });
    }

    @AfterEach
    void closeContext() {
        tenantContext.close();
    }

    @ParameterizedTest(name = "无行ID替换未领旧行必须拒绝：{0}")
    @EnumSource(SourceFault.class)
    void replacementWithUntrustedSourceIsRejectedBeforeAnyLineMutation(SourceFault fault) {
        WmProductionIssueBo bo = edit();
        WmProductionIssueLine requested = newSourceLine();
        fault.apply(this, requested);
        bo.setWmProductionIssueLineList(List.of(requested));
        List<WmProductionIssueLine> before = activeLines();

        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "非法新增来源必须拒绝，不能删除原行后写入任意需求ID");

        assertEquals(before, activeLines(), "来源拒绝时原行ID、物料、单位及累计量必须保留");
        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "新建单据携带非法需求来源必须在写单头前拒绝：{0}")
    @EnumSource(SourceFault.class)
    void insertWithUntrustedSourceIsRejectedBeforeHeaderPersistence(SourceFault fault) {
        WmProductionIssueBo bo = create();
        WmProductionIssueLine requested = newSourceLine();
        fault.apply(this, requested);
        if (fault == SourceFault.MISSING_HEADER_WORK_ORDER) {
            bo.setWoId(null);
        }
        // 新建入口会重置客户端行ID，不能因此跳过来源验证。
        requested.setLineId(777L);
        bo.setWmProductionIssueLineList(List.of(requested));

        assertThrows(ServiceException.class, () -> service.insertByBo(bo), "非法MRP来源不可通过新建单据认领");

        verify(issueMapper, never()).insert(any(WmProductionIssue.class));
        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("先核验全部新增来源再保存，后续非法行不能留下前面的合法行")
    void validatesWholeRequestedSetBeforeSavingAnyLine() {
        WmProductionIssueBo bo = edit();
        WmProductionIssueLine invalid = newSourceLine();
        invalid.setRequirementDetailId(9999L);
        bo.setWmProductionIssueLineList(List.of(newSourceLine(), invalid));

        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "后续来源不存在时必须完整拒绝");

        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "真实租户缺失时不可认领MRP，新增单据={0}")
    @ValueSource(booleans = {false, true})
    void rejectsSourceClaimWithoutRealTenantContext(boolean insert) {
        tenantContext.when(TenantHelper::getTenantId).thenReturn(null);
        WmProductionIssueBo bo = insert ? create() : edit();
        bo.setWmProductionIssueLineList(List.of(newSourceLine()));

        assertThrows(ServiceException.class, () -> save(bo, insert), "不能从客户端或来源行推测缺失的真实租户");

        assertNoLineMutationOrPosting();
        verifyNoInteractions(workOrderMapper, planMapper, requirementMapper, detailMapper);
        if (insert) {
            verify(issueMapper, never()).insert(any(WmProductionIssue.class));
        }
    }

    @Test
    @DisplayName("持久单头与真实租户不符时不能新增MRP来源行")
    void rejectsHeaderFromDifferentTenantBeforeSourceLookup() {
        storedIssue.setTenantId("999999");
        WmProductionIssueBo bo = edit();
        bo.setWmProductionIssueLineList(List.of(newSourceLine()));

        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "真实租户与持久单头必须一致");

        verifyNoInteractions(workOrderMapper, planMapper, requirementMapper, detailMapper);
        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "合法同来源新增可保存且来源ID不漂移，新建单据={0}")
    @ValueSource(booleans = {false, true})
    void acceptsNewLineFromExactWorkOrderRequirement(boolean insert) {
        WmProductionIssueBo bo = insert ? create() : edit();
        WmProductionIssueLine requested = newSourceLine();
        requested.setLineStatus("ISSUED");
        bo.setWmProductionIssueLineList(List.of(requested));

        assertEquals(1, save(bo, insert), "有效来源应正常保存");

        ArgumentCaptor<WmProductionIssueLine> inserted = ArgumentCaptor.forClass(WmProductionIssueLine.class);
        verify(lineMapper).insert(inserted.capture());
        WmProductionIssueLine actual = inserted.getValue();
        assertAll(
            () -> assertEquals(ISSUE_ID, actual.getIssueId(), "新增行归属于后端确定的父单"),
            () -> assertEquals(DETAIL_ID, actual.getRequirementDetailId(), "保存精确来源明细ID"),
            () -> assertEquals(MATERIAL_ID, actual.getMaterialId(), "来源物料一致"),
            () -> assertEquals(BASE_UNIT, actual.getBaseUomId(), "来源基本单位一致"),
            () -> assertEquals(TENANT, actual.getTenantId(), "MRP新增行租户由真实上下文维护"),
            () -> assertEquals(BigDecimal.ZERO, actual.getIssuedQty(), "新增行已领量为零"),
            () -> assertEquals("PENDING", actual.getLineStatus(), "新增行状态不得认领执行结果"));
        verifyNoInteractions(stockOperationService, stockMapper, warehouseMapper);
    }

    @ParameterizedTest(name = "无MRP手工行独立兼容，新建单据={0}")
    @ValueSource(booleans = {false, true})
    void preservesSourceLessManualLinePath(boolean insert) {
        WmProductionIssueBo bo = insert ? create() : edit();
        if (insert) {
            bo.setWoId(null);
        }
        workOrder.setSourceType("PLAN");
        WmProductionIssueLine requested = newSourceLine();
        requested.setRequirementDetailId(null);
        bo.setWmProductionIssueLineList(List.of(requested));

        assertEquals(1, save(bo, insert), "无需求ID的独立手工路径不能被MRP校验一刀切禁止");

        verify(lineMapper).insert(any(WmProductionIssueLine.class));
        verifyNoInteractions(workOrderMapper, planMapper, requirementMapper, detailMapper, stockOperationService);
    }

    @ParameterizedTest(name = "头工单切换不可使存量行换归属：{0}")
    @ValueSource(strings = {"仅单头", "空全集", "原行全集"})
    void keepsExistingHeaderWorkOrderRebindingGuard(String mode) {
        WmProductionIssueBo bo = edit();
        bo.setWoId(99L);
        if ("空全集".equals(mode)) {
            bo.setWmProductionIssueLineList(List.of());
        } else if ("原行全集".equals(mode)) {
            bo.setWmProductionIssueLineList(activeLines());
        }

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateByBo(bo), "工单切换必须在行同步前拒绝");

        assertEquals("领料单号及来源工单不可改绑", error.getMessage(), "应保留既有来源工单守卫");
        verify(issueMapper, never()).update(any(WmProductionIssue.class), any(Wrapper.class));
        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("有ID行保留原ID、原需求来源和后端执行字段，来源置空不解除绑定")
    void existingIdentityAndServerFieldsRemainStable() {
        WmProductionIssueLine original = storedLines.get(0);
        original.setIssuedQty(BigDecimal.ONE);
        original.setShortQty(new BigDecimal("2"));
        original.setLineStatus("PARTIAL");
        storedIssue.setStatus("PARTIAL");
        WmProductionIssueLine requested = activeLines().get(0);
        requested.setRequirementDetailId(null);
        requested.setTenantId("999999");
        requested.setIssuedQty(new BigDecimal("99"));
        requested.setShortQty(BigDecimal.ZERO);
        requested.setRequiredQty(new BigDecimal("99"));
        requested.setLineStatus("ISSUED");
        WmProductionIssueBo bo = edit();
        bo.setWmProductionIssueLineList(List.of(requested));

        service.updateByBo(bo);

        WmProductionIssueLine actual = activeLines().get(0);
        assertAll(
            () -> assertEquals(42L, actual.getLineId(), "原行ID必须稳定"),
            () -> assertEquals(DETAIL_ID, actual.getRequirementDetailId(), "来源置空必须恢复原绑定"),
            () -> assertEquals(TENANT, actual.getTenantId(), "有ID行租户必须保留持久行现值，不能跨租户搬移来源"),
            () -> assertEquals(BigDecimal.ONE, actual.getIssuedQty(), "累计已领量由后端维护"),
            () -> assertEquals(new BigDecimal("2"), actual.getShortQty(), "欠领量由后端维护"),
            () -> assertEquals(new BigDecimal("3"), actual.getRequiredQty(), "应领量由后端维护"),
            () -> assertEquals("PARTIAL", actual.getLineStatus(), "执行状态由后端维护"));
        verify(lineMapper, never()).insert(any(WmProductionIssueLine.class));
        verifyNoInteractions(workOrderMapper, planMapper, requirementMapper, detailMapper);
    }

    @Test
    @DisplayName("有ID行仍不可换成同计划的另一MRP来源")
    void existingIdCannotRebindRequirement() {
        WmProductionIssueLine requested = activeLines().get(0);
        requested.setRequirementDetailId(302L);
        WmProductionIssueBo bo = edit();
        bo.setWmProductionIssueLineList(List.of(requested));

        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "有ID行来源改绑仍必须拒绝");

        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("自动建行只选工单直接来源MRP，不能枚举同计划其他MRP")
    void buildLinesOnlyUsesExactWorkOrderSourceRequirement() {
        PrdMaterialRequirement sibling = copy(requirement, new PrdMaterialRequirement());
        sibling.setRequirementId(202L);
        sibling.setMaterialId(502L);
        requirements.put(202L, sibling);
        PrdMaterialRequirementDetail siblingDetail = copy(detail, new PrdMaterialRequirementDetail());
        siblingDetail.setDetailId(302L);
        siblingDetail.setRequirementId(202L);
        siblingDetail.setMaterialId(402L);
        details.put(302L, siblingDetail);
        PrdMaterialRequirementDetail backflush = copy(detail, new PrdMaterialRequirementDetail());
        backflush.setDetailId(303L);
        backflush.setBackflushFlag(1);
        details.put(303L, backflush);

        List<WmProductionIssueLine> result = service.buildLinesFromWorkOrder(WO_ID);

        assertEquals(List.of(DETAIL_ID), result.stream().map(WmProductionIssueLine::getRequirementDetailId).toList(),
            "仅选直接来源的有效非倒冲明细，同计划或同物料不能充当归属依据");
        assertEquals(BASE_UNIT, result.get(0).getBaseUomId(), "基本单位必须来自有效需求明细");
        ArgumentCaptor<LambdaQueryWrapper<PrdMaterialRequirementDetail>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(detailMapper).selectList(query.capture());
        assertScoped(query.getValue(), "requirement_id", REQUIREMENT_ID);
        verify(requirementMapper, never()).selectList(any(Wrapper.class));
        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("非MRP工单不能用同数值sourceId或planId自动认领需求")
    void sourceLessWorkOrderDoesNotBuildMrpLines() {
        workOrder.setSourceType("PLAN");

        assertTrue(service.buildLinesFromWorkOrder(WO_ID).isEmpty(), "非MRP工单保持手工路径，不提供伪MRP候选");

        verifyNoInteractions(requirementMapper, detailMapper);
        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "自动建行拒绝失效或不一致的直接来源：{0}")
    @EnumSource(value = SourceFault.class, names = {"MISSING_REQUIREMENT", "DELETED_REQUIREMENT", "FOREIGN_REQUIREMENT",
        "WRONG_PLAN", "MISSING_PLAN", "MISSING_SOURCE", "WRONG_ROOT_MATERIAL", "MISSING_DETAIL_UNIT",
        "MISSING_PLAN_ROW", "DELETED_PLAN_ROW", "FOREIGN_PLAN_ROW"})
    void buildLinesRejectsInvalidSourceChain(SourceFault fault) {
        fault.apply(this, newSourceLine());

        assertThrows(ServiceException.class, () -> service.buildLinesFromWorkOrder(WO_ID), "失效直接来源不能转换成可认领候选");

        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "新增MRP定额使用可信未领余额并忽略伪造后端字段，新建单据={0}")
    @ValueSource(booleans = {false, true})
    void newSourceQuotaComesFromRemainingRequirementInsteadOfClient(boolean insert) {
        detail.setRequiredQuantity(new BigDecimal("6"));
        detail.setIssuedQuantity(new BigDecimal("2"));
        detail.setBomQuantity(new BigDecimal("1.5"));
        detail.setLossRate(new BigDecimal("0.05"));
        WmProductionIssueLine requested = newSourceLine();
        requested.setQuantity(new BigDecimal("999"));
        requested.setRequiredQty(new BigDecimal("999"));
        requested.setShortQty(new BigDecimal("999"));
        requested.setBomQty(new BigDecimal("999"));
        requested.setLossRate(new BigDecimal("99"));
        WmProductionIssueBo bo = insert ? create() : edit();
        bo.setWmProductionIssueLineList(List.of(requested));

        assertEquals(1, save(bo, insert), "真实余额内的本次量可以保存，客户端后端字段不能扩大来源额度");

        ArgumentCaptor<WmProductionIssueLine> row = ArgumentCaptor.forClass(WmProductionIssueLine.class);
        verify(lineMapper).insert(row.capture());
        WmProductionIssueLine actual = row.getValue();
        assertAll(
            () -> assertEquals(new BigDecimal("4"), actual.getRequiredQty(), "新行定额必须来自来源需求减已发的余额"),
            () -> assertEquals(new BigDecimal("4"), actual.getShortQty(), "新行欠领量必须使用可信余额，不能透传伪造值"),
            () -> assertEquals(new BigDecimal("4"), actual.getQuantity(), "兼容数量字段同样由来源初始化"),
            () -> assertEquals(new BigDecimal("1.5"), actual.getBomQty(), "BOM用量必须来自需求明细"),
            () -> assertEquals(new BigDecimal("0.05"), actual.getLossRate(), "损耗率必须来自需求明细"),
            () -> assertEquals(BigDecimal.ONE, actual.getCurrentQty(), "合法部分领料本次量仍由客户端明确选择"),
            () -> assertEquals(BigDecimal.ZERO, actual.getIssuedQty(), "来源已发量不能变成新行伪造已领量"));
        verifyNoInteractions(stockOperationService, stockMapper, warehouseMapper);
    }

    @ParameterizedTest(name = "新增MRP本次量必须在可信余额内，新建单据={0}，本次量={1}")
    @CsvSource({"false, 5", "true, 5", "false, -1", "true, -1"})
    void rejectsNewCurrentQuantityOutsideSourceBalance(boolean insert, String current) {
        detail.setRequiredQuantity(new BigDecimal("6"));
        detail.setIssuedQuantity(new BigDecimal("2"));
        WmProductionIssueLine requested = newSourceLine();
        requested.setRequiredQty(new BigDecimal("999"));
        requested.setShortQty(new BigDecimal("999"));
        requested.setCurrentQty(new BigDecimal(current));
        WmProductionIssueBo bo = insert ? create() : edit();
        bo.setWmProductionIssueLineList(List.of(requested));

        assertThrows(ServiceException.class, () -> save(bo, insert), "伪造应领或欠领量不能放行超余额或负数本次量");

        assertNoLineMutationOrPosting();
        if (insert) {
            verify(issueMapper, never()).insert(any(WmProductionIssue.class));
        }
    }

    @ParameterizedTest(name = "缺失或异常来源数量不能被客户端补成额度：{0}")
    @ValueSource(strings = {"需求缺失", "需求为负", "已发为负"})
    void invalidSourceQuantitiesCannotBeRepairedByClientQuota(String fault) {
        if ("需求缺失".equals(fault)) {
            detail.setRequiredQuantity(null);
        } else if ("需求为负".equals(fault)) {
            detail.setRequiredQuantity(BigDecimal.ONE.negate());
        } else {
            detail.setIssuedQuantity(BigDecimal.ONE.negate());
        }
        WmProductionIssueLine requested = newSourceLine();
        requested.setCurrentQty(BigDecimal.ZERO);
        requested.setRequiredQty(new BigDecimal("999"));
        WmProductionIssueBo bo = edit();
        bo.setWmProductionIssueLineList(List.of(requested));

        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "无有效来源数量时不能相信客户端补写额度");

        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "来源已领满或超领时不能新建正数领料量，来源已发={0}")
    @ValueSource(strings = {"3", "4"})
    void exhaustedSourceCannotBeClaimedAgainWithForgedQuota(String issued) {
        detail.setIssuedQuantity(new BigDecimal(issued));
        WmProductionIssueLine requested = newSourceLine();
        requested.setRequiredQty(new BigDecimal("999"));
        requested.setShortQty(new BigDecimal("999"));
        WmProductionIssueBo bo = edit();
        bo.setWmProductionIssueLineList(List.of(requested));

        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "已领余额为零时不能靠新行伪造额度");

        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "自动建行同样使用可信未领余额，来源已发={0}")
    @ValueSource(strings = {"1", "3"})
    void buildLinesUsesRemainingSourceQuantity(String issued) {
        detail.setIssuedQuantity(new BigDecimal(issued));
        BigDecimal remaining = new BigDecimal("3").subtract(new BigDecimal(issued));

        WmProductionIssueLine line = service.buildLinesFromWorkOrder(WO_ID).get(0);

        assertAll(
            () -> assertEquals(remaining, line.getRequiredQty(), "候选行定额来自真实未领余额"),
            () -> assertEquals(remaining, line.getShortQty(), "候选行欠领量与可信余额一致"),
            () -> assertEquals(remaining, line.getCurrentQty(), "候选默认本次量不得再次认领已发数量"),
            () -> assertEquals(remaining, line.getQuantity(), "候选兼容数量字段必须来自来源余额"));
        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("有效DRAFT根MRP与生产中工单可以正常构建单位26的12、6、6三源行")
    void draftRootAndInProgressWorkOrderKeepNormalManufacturingShape() {
        detail.setUnit("26");
        detail.setRequiredQuantity(new BigDecimal("12"));
        for (long id : List.of(302L, 303L)) {
            PrdMaterialRequirementDetail child = copy(detail, new PrdMaterialRequirementDetail());
            child.setDetailId(id);
            child.setMaterialId(id + 100L);
            child.setRequiredQuantity(new BigDecimal("6"));
            details.put(id, child);
        }

        List<WmProductionIssueLine> lines = service.buildLinesFromWorkOrder(WO_ID);

        assertAll(
            () -> assertEquals(3, lines.size(), "DRAFT根MRP不能被凭空新增的CONFIRMED状态要求拒绝"),
            () -> assertEquals(List.of(new BigDecimal("6"), new BigDecimal("6"), new BigDecimal("12")),
                lines.stream().map(WmProductionIssueLine::getRequiredQty).sorted().toList(), "三源行应保留12、6、6的有效需求数量"),
            () -> assertTrue(lines.stream().allMatch(line -> "26".equals(line.getBaseUomId())), "单位代码26应保持为字符串且来自来源"),
            () -> assertEquals("DRAFT", requirement.getStatus(), "只读校验不能改MRP流程状态"),
            () -> assertEquals("DRAFT", plan.getStatus(), "有效计划不能被凭空新增的状态门槛拒绝或改状态"),
            () -> assertEquals("IN_PROGRESS", workOrder.getWoStatus(), "只读校验不能改变工单状态"),
            () -> assertNull(requirement.getParentRequirementId(), "有效根MRP无需伪造祖先需求"));
        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("来源查询明确使用真实租户、有效行和精确ID，不切换到客户端租户")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sourceQueriesAreExplicitlyTenantAndActiveScoped() {
        WmProductionIssueBo bo = edit();
        bo.setWmProductionIssueLineList(List.of(newSourceLine()));

        service.updateByBo(bo);

        ArgumentCaptor<Wrapper> woQuery = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper> planQuery = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper> requirementQuery = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper> detailQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).selectOne(woQuery.capture());
        verify(planMapper).selectOne(planQuery.capture());
        verify(requirementMapper).selectOne(requirementQuery.capture());
        verify(detailMapper).selectOne(detailQuery.capture());
        assertScoped(woQuery.getValue(), "wo_id", WO_ID);
        assertScoped(planQuery.getValue(), "plan_id", PLAN_ID);
        assertScoped(requirementQuery.getValue(), "requirement_id", REQUIREMENT_ID);
        assertScoped(detailQuery.getValue(), "detail_id", DETAIL_ID);
        tenantContext.verify(TenantHelper::getTenantId, atLeastOnce());
        tenantContext.verifyNoMoreInteractions();
    }

    private void assertScoped(Wrapper<?> wrapper, String idColumn, Long id) {
        assertAll(
            () -> assertEquals(id, queryValue(wrapper, idColumn), "读取必须限定精确来源ID"),
            () -> assertEquals(TENANT, queryValue(wrapper, "tenant_id"), "读取必须使用真实租户而非请求租户"),
            () -> assertEquals("0", queryValue(wrapper, "del_flag"), "读取必须限定有效未删除行"));
    }

    /** 仅解析查询的列与参数，用于内存查找及查询范围断言，不模拟业务归属判断。 */
    private static Object queryValue(Wrapper<?> wrapper, String column) {
        var match = Pattern.compile(column + " = #\\{ew\\.paramNameValuePairs\\.([A-Za-z0-9]+)}")
            .matcher(wrapper.getSqlSegment());
        assertTrue(match.find(), "查询中必须包含精确列条件：" + column);
        return ((LambdaQueryWrapper<?>) wrapper).getParamNameValuePairs().get(match.group(1));
    }

    private WmProductionIssueBo edit() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(ISSUE_ID);
        return bo;
    }

    private WmProductionIssueBo create() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueCode("OFFLINE-SOURCE-NEW");
        bo.setWoId(WO_ID);
        storedLines.clear();
        return bo;
    }

    private int save(WmProductionIssueBo bo, boolean insert) {
        return insert ? service.insertByBo(bo) : service.updateByBo(bo);
    }

    private static WmProductionIssueLine newSourceLine() {
        WmProductionIssueLine row = new WmProductionIssueLine();
        row.setMaterialId(MATERIAL_ID);
        row.setBaseUomId(BASE_UNIT);
        row.setRequirementDetailId(DETAIL_ID);
        row.setRequiredQty(new BigDecimal("3"));
        row.setIssuedQty(BigDecimal.ZERO);
        row.setCurrentQty(BigDecimal.ONE);
        row.setShortQty(new BigDecimal("3"));
        row.setLineStatus("PENDING");
        row.setDelFlag("0");
        return row;
    }

    private List<WmProductionIssueLine> activeLines() {
        return storedLines.stream().filter(row -> "0".equals(row.getDelFlag()))
            .map(row -> copy(row, new WmProductionIssueLine())).toList();
    }

    private static <T> T copy(Object source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }

    /** 模拟实体的默认非空更新，显式零值必须保存；查询始终返回副本。 */
    private static void copyNonNullProperties(Object source, Object target) {
        BeanWrapper input = new BeanWrapperImpl(source);
        BeanWrapper output = new BeanWrapperImpl(target);
        for (PropertyDescriptor property : input.getPropertyDescriptors()) {
            String name = property.getName();
            if (input.isReadableProperty(name) && output.isWritableProperty(name)) {
                Object value = input.getPropertyValue(name);
                if (value != null) {
                    output.setPropertyValue(name, value);
                }
            }
        }
    }

    private void assertNoLineMutationOrPosting() {
        verify(lineMapper, never()).insert(any(WmProductionIssueLine.class));
        verify(lineMapper, never()).updateById(any(WmProductionIssueLine.class));
        verify(lineMapper, never()).deleteById(anyLong());
        verifyNoInteractions(stockOperationService, stockMapper, warehouseMapper);
    }

    private enum SourceFault {
        MISSING_HEADER_WORK_ORDER("单头工单关联缺失"), MISSING_WORK_ORDER("工单缺失"), DELETED_WORK_ORDER("工单已删除"), FOREIGN_WORK_ORDER("工单跨租户"),
        MISSING_REQUIREMENT("需求头缺失"), DELETED_REQUIREMENT("需求头已删除"), FOREIGN_REQUIREMENT("需求头跨租户"),
        MISSING_DETAIL("需求明细缺失"), DELETED_DETAIL("需求明细已删除"), FOREIGN_DETAIL("需求明细跨租户"),
        DIFFERENT_REQUIREMENT("同计划另一MRP"), WRONG_PLAN("原计划不一致"), MISSING_PLAN("原计划缺失"),
        MISSING_PLAN_ROW("计划实体缺失"), DELETED_PLAN_ROW("计划实体已删除"), FOREIGN_PLAN_ROW("计划实体跨租户"),
        MISSING_SOURCE("工单需求来源缺失"), NON_MRP_SOURCE("非MRP来源同数值ID"),
        WRONG_ROOT_MATERIAL("工单与需求头成品不一致"), WRONG_MATERIAL("领料物料不一致"),
        MISSING_DETAIL_MATERIAL("需求明细物料缺失"), WRONG_UNIT("基本单位不一致"),
        MISSING_DETAIL_UNIT("需求明细单位缺失"), MISSING_LINE_UNIT("领料单位缺失"), BOTH_UNITS_MISSING("双方单位均缺失"),
        BACKFLUSH_DETAIL("倒冲明细"), BACKFLUSH_WORK_ORDER("倒冲工单"), FORGED_LINE_TENANT("客户端伪造行租户");

        private final String label;
        SourceFault(String label) {
            this.label = label;
        }
        @Override
        public String toString() {
            return label;
        }

        void apply(WmProductionIssueSourceAffiliationTest test, WmProductionIssueLine line) {
            switch (this) {
                case MISSING_HEADER_WORK_ORDER -> test.storedIssue.setWoId(null);
                case MISSING_WORK_ORDER -> test.workOrder = null;
                case DELETED_WORK_ORDER -> test.workOrder.setDelFlag("1");
                case FOREIGN_WORK_ORDER -> test.workOrder.setTenantId("999999");
                case MISSING_REQUIREMENT -> test.requirements.clear();
                case DELETED_REQUIREMENT -> test.requirement.setDelFlag("1");
                case FOREIGN_REQUIREMENT -> test.requirement.setTenantId("999999");
                case MISSING_DETAIL -> test.details.clear();
                case DELETED_DETAIL -> test.detail.setDelFlag("1");
                case FOREIGN_DETAIL -> test.detail.setTenantId("999999");
                case DIFFERENT_REQUIREMENT -> {
                    PrdMaterialRequirement other = copy(test.requirement, new PrdMaterialRequirement());
                    other.setRequirementId(202L);
                    test.requirements.put(202L, other);
                    test.detail.setRequirementId(202L);
                }
                case WRONG_PLAN -> test.requirement.setPlanId(102L);
                case MISSING_PLAN -> test.workOrder.setPlanId(null);
                case MISSING_PLAN_ROW -> test.plan = null;
                case DELETED_PLAN_ROW -> test.plan.setDelFlag("1");
                case FOREIGN_PLAN_ROW -> test.plan.setTenantId("999999");
                case MISSING_SOURCE -> test.workOrder.setSourceId(null);
                case NON_MRP_SOURCE -> test.workOrder.setSourceType("PLAN");
                case WRONG_ROOT_MATERIAL -> test.requirement.setMaterialId(502L);
                case WRONG_MATERIAL -> line.setMaterialId(402L);
                case MISSING_DETAIL_MATERIAL -> test.detail.setMaterialId(null);
                case WRONG_UNIT -> line.setBaseUomId("3");
                case MISSING_DETAIL_UNIT -> test.detail.setUnit(null);
                case MISSING_LINE_UNIT -> line.setBaseUomId(null);
                case BOTH_UNITS_MISSING -> {
                    test.detail.setUnit(null);
                    line.setBaseUomId(null);
                }
                case BACKFLUSH_DETAIL -> test.detail.setBackflushFlag(1);
                case BACKFLUSH_WORK_ORDER -> test.workOrder.setBackflushFlag(1);
                case FORGED_LINE_TENANT -> line.setTenantId("999999");
            }
        }
    }
}
