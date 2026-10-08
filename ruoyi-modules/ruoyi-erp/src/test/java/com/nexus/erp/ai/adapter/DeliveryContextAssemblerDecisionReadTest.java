package com.nexus.erp.ai.adapter;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 ERP Port 和装配器的历史读取门禁；只隔离 Mapper，不启动数据库或创建草稿。 */
class DeliveryContextAssemblerDecisionReadTest {
    private static final String TENANT = "TENANT-B";
    private static final DecisionBusinessRef ROOT =
        new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
    private static final DecisionActor VIEWER = actor(Set.of("ai:decision:query", "prd:work:query"));

    private PrdWorkOrderMapper workOrders;
    private SoSalesOrderHeaderMapper salesOrders;
    private PrdSchedulePlanMapper schedulePlans;
    private DeliveryContextAssembler assembler;
    private ErpDecisionShortageSimulation shortage;
    private ErpDecisionApsSimulation schedules;
    private ErpDecisionFreshnessChecker freshness;
    private ErpDecisionActionExecutor actions;
    private ErpCertifiedPurchaseDecisionAdapter purchases;
    private ErpDecisionPurchaseContractService contracts;
    private ErpIndustrialDecisionDataAdapter port;
    private MockedStatic<SpringUtil> spring;
    private MockedStatic<LoginHelper> login;

    @BeforeEach
    void setup() throws Exception {
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isLogin).thenReturn(false);
        workOrders = mock(PrdWorkOrderMapper.class);
        salesOrders = mock(SoSalesOrderHeaderMapper.class);
        schedulePlans = mock(PrdSchedulePlanMapper.class);
        Map<Class<?>, Object> mappers = Map.of(PrdWorkOrderMapper.class, workOrders,
            SoSalesOrderHeaderMapper.class, salesOrders, PrdSchedulePlanMapper.class, schedulePlans);
        var constructor = DeliveryContextAssembler.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(mappers::get).toArray();
        assembler = (DeliveryContextAssembler) constructor.newInstance(arguments);
        shortage = mock(ErpDecisionShortageSimulation.class);
        schedules = mock(ErpDecisionApsSimulation.class);
        freshness = mock(ErpDecisionFreshnessChecker.class);
        actions = mock(ErpDecisionActionExecutor.class);
        purchases = mock(ErpCertifiedPurchaseDecisionAdapter.class);
        contracts = mock(ErpDecisionPurchaseContractService.class);
        port = new ErpIndustrialDecisionDataAdapter(assembler, shortage, schedules, freshness,
            actions, purchases, contracts);
    }

    @AfterEach
    void close() {
        TenantHelper.clearDynamic();
        login.close();
        spring.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "APPROVING", "APPROVED", "RELEASED", "IN_PROGRESS",
        "COMPLETED", "CLOSED", " completed ", " closed "})
    void queryOnlyViewerCanReadExactUndeletedWorkOrderIncludingTerminalHistory(String status) {
        TenantHelper.setDynamic("TENANT-A");
        when(workOrders.selectById(11L)).thenAnswer(call -> {
            assertEquals(TENANT, TenantHelper.getTenantId(), "根对象必须在读取人的租户内查询");
            return workOrder(11L, TENANT, "0", status);
        });

        assertTrue(port.canReadDecision(ROOT, VIEWER), "已完工/关闭不能抹去有权限的历史决策读取");

        assertEquals("TENANT-A", TenantHelper.getDynamic(), "读取后须恢复外层租户");
        verify(workOrders).selectById(11L);
        verifyNoMoreInteractions(workOrders);
        verifyNoBusinessOperations();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CLOSED"})
    void terminalHistoryReadDoesNotPermitAnalysisFreshnessOrNewBusinessAction(String status) {
        var analyst = actor(Set.of("ai:decision:query", "ai:decision:run", "ai:decision:execute",
            "prd:work:query", "pu:requisition:add"));
        when(workOrders.selectById(11L)).thenReturn(workOrder(11L, TENANT, "0", status));

        assertTrue(port.canReadDecision(ROOT, analyst));
        assertFalse(port.canAccess(ROOT, analyst), "新分析和重新分析仍须拒绝终态根");
        assertThrows(ServiceException.class, () -> port.loadDeliveryContext(ROOT, 30, analyst));
        assertThrows(ServiceException.class, () -> port.checkFreshness(ROOT, "frozen-hash", analyst));
        assertNull(TenantHelper.getDynamic());
        verifyNoBusinessOperations();
    }

    @ParameterizedTest
    @MethodSource("unreadableWorkOrders")
    void readStillRejectsForeignDeletedMissingMismatchedAndUnknownStateRoots(PrdWorkOrder workOrder) {
        TenantHelper.setDynamic("TENANT-A");
        when(workOrders.selectById(11L)).thenReturn(workOrder);

        assertFalse(port.canReadDecision(ROOT, VIEWER));
        assertEquals("TENANT-A", TenantHelper.getDynamic());
        verify(workOrders).selectById(11L);
        verifyNoBusinessOperations();
    }

    static Stream<PrdWorkOrder> unreadableWorkOrders() {
        return Stream.of(null,
            workOrder(12L, TENANT, "0", "COMPLETED"),
            workOrder(11L, "TENANT-A", "0", "RELEASED"),
            workOrder(11L, "TENANT-A", "0", "COMPLETED"),
            workOrder(11L, TENANT, "1", "RELEASED"),
            workOrder(11L, TENANT, "1", "CLOSED"),
            workOrder(11L, TENANT, null, "COMPLETED"),
            workOrder(11L, null, "0", "COMPLETED"),
            workOrder(11L, TENANT, "0", null),
            workOrder(11L, TENANT, "0", ""),
            workOrder(11L, TENANT, "0", "UNKNOWN"));
    }

    @ParameterizedTest
    @MethodSource("insufficientPermissions")
    void historicalReadRequiresBothDecisionQueryAndBusinessReadPermissions(Set<String> permissions) {
        assertFalse(port.canReadDecision(ROOT, actor(permissions)));
        verifyNoInteractions(workOrders);
        verifyNoBusinessOperations();
    }

    static Stream<Set<String>> insufficientPermissions() {
        return Stream.of(Set.of(), Set.of("ai:decision:query"), Set.of("prd:work:query"),
            Set.of("ai:decision:run", "prd:work:query"),
            Set.of("ai:decision:execute", "prd:work:query"),
            Set.of("ai:decision:query", "so:orderheader:query"));
    }

    @Test
    void administratorMayReadTerminalHistoryButQueryViewerCannotLoadLiveSnapshot() {
        when(workOrders.selectById(11L)).thenReturn(workOrder(11L, TENANT, "0", "COMPLETED"));
        assertTrue(port.canReadDecision(ROOT, actor(Set.of("*:*:*"))));
        assertThrows(ServiceException.class, () -> port.loadDeliveryContext(ROOT, 30, VIEWER));
        verify(workOrders).selectById(11L);
        verifyNoMoreInteractions(workOrders);
        verifyNoBusinessOperations();
    }

    @Test
    void disabledTenantIsolationFailsClosedBeforeReadingRoot() {
        spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("false");
        assertFalse(port.canReadDecision(ROOT, VIEWER));
        verifyNoInteractions(workOrders);
        verifyNoBusinessOperations();
    }

    @Test
    void invalidIdentityOrBusinessReferenceFailsBeforeAnyRead() {
        assertFalse(port.canReadDecision(ROOT, null));
        assertFalse(port.canReadDecision(ROOT, new DecisionActor(null, 7L, "viewer", VIEWER.permissions())));
        assertFalse(port.canReadDecision(ROOT, new DecisionActor(" ", 7L, "viewer", VIEWER.permissions())));
        assertFalse(port.canReadDecision(ROOT, new DecisionActor(TENANT, 0L, "viewer", VIEWER.permissions())));
        assertFalse(port.canReadDecision(ROOT, new DecisionActor(TENANT, null, "viewer", VIEWER.permissions())));
        assertFalse(port.canReadDecision(ROOT, new DecisionActor(TENANT, 7L, "viewer", null)));
        assertFalse(port.canReadDecision(null, VIEWER));
        assertFalse(port.canReadDecision(new DecisionBusinessRef(null, 11L, null), VIEWER));
        assertFalse(port.canReadDecision(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, null, null), VIEWER));
        assertFalse(port.canReadDecision(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, -1L, null), VIEWER));
        verifyNoInteractions(workOrders);
        verifyNoBusinessOperations();
    }

    @Test
    void unreadableRootFailsClosedAndRestoresOuterTenant() {
        TenantHelper.setDynamic("TENANT-A");
        when(workOrders.selectById(11L)).thenThrow(new ServiceException("synthetic read failure"));
        assertFalse(port.canReadDecision(ROOT, VIEWER));
        assertEquals("TENANT-A", TenantHelper.getDynamic());
        verifyNoBusinessOperations();
    }

    @Test
    void directHistoricalVisibilityCannotBypassActorTenantContext() {
        assertFalse(TenantHelper.dynamic("TENANT-A", () -> assembler.existsForDecisionRead(ROOT, VIEWER)));
        assertNull(TenantHelper.getDynamic());
        verifyNoInteractions(workOrders, salesOrders, schedulePlans);
        verifyNoBusinessOperations();
    }

    @Test
    void nonWorkOrderReadRoutesKeepTheirOwnRootAndBusinessPermissionChecks() {
        var viewer = actor(Set.of("ai:decision:query", "so:orderheader:query", "prd:schedule:query", "prd:shortage:query"));
        var order = new SoSalesOrderHeader();
        order.setSoId(100L);
        when(salesOrders.selectById(100L)).thenReturn(order);
        var plan = new PrdSchedulePlan();
        plan.setPlanId(200L);
        when(schedulePlans.selectById(200L)).thenReturn(plan);
        var salesRef = new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 100L, null);
        var scheduleRef = new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 200L, null);
        var horizonRef = new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null);

        assertTrue(port.canReadDecision(salesRef, viewer));
        assertTrue(port.canReadDecision(scheduleRef, viewer));
        assertTrue(port.canReadDecision(horizonRef, viewer));
        assertFalse(port.canReadDecision(salesRef, VIEWER));
        assertFalse(port.canReadDecision(scheduleRef, VIEWER));
        assertFalse(port.canReadDecision(horizonRef, VIEWER));
        order.setSoId(101L);
        plan.setPlanId(201L);
        assertFalse(port.canReadDecision(salesRef, viewer), "销售订单仍须精确匹配根 ID");
        assertFalse(port.canReadDecision(scheduleRef, viewer), "排产方案仍须精确匹配根 ID");
        assertFalse(port.canReadDecision(new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, 1L, null), viewer));
        verifyNoInteractions(workOrders);
        verifyNoBusinessOperations();
    }

    private void verifyNoBusinessOperations() {
        verifyNoInteractions(shortage, schedules, freshness, actions, purchases, contracts);
    }

    private static DecisionActor actor(Set<String> permissions) {
        return new DecisionActor(TENANT, 7L, "viewer", permissions);
    }

    private static PrdWorkOrder workOrder(Long id, String tenant, String deleted, String status) {
        var workOrder = new PrdWorkOrder();
        workOrder.setWoId(id);
        workOrder.setTenantId(tenant);
        workOrder.setDelFlag(deleted);
        workOrder.setWoStatus(status);
        return workOrder;
    }
}
