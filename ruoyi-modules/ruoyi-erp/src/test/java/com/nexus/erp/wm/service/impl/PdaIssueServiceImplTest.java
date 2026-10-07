package com.nexus.erp.wm.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.mapper.PdaIssueSourceMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.service.IWmProductionIssueService;
import com.nexus.system.service.ISysConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 PDA 服务及来源门禁的回归；仅模拟持久化边界，不访问数据库或认证平台。 */
@Tag("dev")
@Tag("local")
class PdaIssueServiceImplTest {
    static final String TENANT = "000007";
    static final String GATE_KEY = "wm.issue.batch.qcGate";
    static final long ISSUE_ID = 10L;
    static final long LINE_ID = 1L;
    static final long MATERIAL_ID = 5L;
    static final long WAREHOUSE_ID = 201L;
    static final long LOCATION_ID = 301L;
    static final long BATCH_ID = 702L;
    static final String BATCH_CODE = "B20260920";
    final IWmProductionIssueService issueService = mock(IWmProductionIssueService.class);
    final WmProductionIssueLineMapper lineMapper = mock(WmProductionIssueLineMapper.class);
    final PdaIssueSourceMapper sourceMapper = mock(PdaIssueSourceMapper.class);
    final ISysConfigService configService = mock(ISysConfigService.class);
    final WmProductionIssue head = new WmProductionIssue();
    final WmProductionIssueLine target = new WmProductionIssueLine();
    final WmWarehouse warehouse = new WmWarehouse();
    final WmStorageLocation location = new WmStorageLocation();
    final WmStorageArea area = new WmStorageArea();
    java.sql.Date databaseToday = java.sql.Date.valueOf(LocalDate.now());
    final BdMaterial material = new BdMaterial();
    final WmBatch batch = new WmBatch();
    final WmMaterialStock stock = new WmMaterialStock();
    final DataSource sourceDataSource = mock(DataSource.class);
    final Connection sourceConnection = mock(Connection.class);
    PdaIssueServiceImpl service;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach
    void setUp() throws Exception {
        // 普通单测只模拟绑定的原连接，不把此夹具当真实 JDBC 事务验收。
        when(sourceConnection.getAutoCommit()).thenReturn(false);
        when(sourceConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        TransactionSynchronizationManager.bindResource(sourceDataSource, new ConnectionHolder(sourceConnection));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.REPEATABLE_READ.value());
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        head.setIssueId(ISSUE_ID); head.setTenantId(TENANT); head.setDelFlag("0");
        head.setWarehouseId(WAREHOUSE_ID); head.setStatus("CREATED");
        target.setLineId(LINE_ID); target.setIssueId(ISSUE_ID); target.setTenantId(TENANT); target.setDelFlag("0");
        target.setMaterialId(MATERIAL_ID); target.setRequiredQty(new BigDecimal("10"));
        target.setIssuedQty(BigDecimal.ZERO); target.setCurrentQty(BigDecimal.ZERO); target.setLineStatus("PENDING");
        warehouse.setWarehouseId(WAREHOUSE_ID); warehouse.setTenantId(TENANT); warehouse.setDelFlag("0");
        warehouse.setWarehouseCode("仓库一"); warehouse.setWarehouseName("一号仓库"); warehouse.setStatus(1L);
        area.setAreaId(401L); area.setWarehouseId(WAREHOUSE_ID); area.setTenantId(TENANT); area.setDelFlag("0");
        area.setAreaCode("库区一"); area.setEnableFlag(1L); area.setFrozenFlag(0L);
        location.setAreaId(401L); location.setEnableFlag(1L); location.setFrozenFlag(0L);
        location.setLocationId(LOCATION_ID); location.setWarehouseId(WAREHOUSE_ID); location.setTenantId(TENANT);
        location.setLocationCode("库位一"); location.setLocationName("一号库位"); location.setDelFlag("0"); location.setStatus(1L);
        material.setMaterialId(MATERIAL_ID); material.setTenantId(TENANT); material.setDelFlag("0");
        material.setStatus(1L); material.setIsBatchManage(1L);
        batch.setBatchId(BATCH_ID); batch.setBatchCode(BATCH_CODE); batch.setMaterialId(MATERIAL_ID);
        batch.setWarehouseId(WAREHOUSE_ID); batch.setTenantId(TENANT); batch.setDelFlag("0");
        batch.setStatus("ACTIVE"); batch.setQcStatus("QUALIFIED");
        stock.setStockId(801L); stock.setMaterialId(MATERIAL_ID); stock.setTenantId(TENANT); stock.setDelFlag("0");
        stock.setWarehouseId(WAREHOUSE_ID); stock.setWarehouseCode("仓库一");
        stock.setAreaId(401L); stock.setAreaCode("库区一");
        stock.setLocationId(LOCATION_ID); stock.setLocationCode("库位一");
        stock.setBatchId(BATCH_ID); stock.setBatchCode(BATCH_CODE);
        stock.setStockQuantity(new BigDecimal("10")); stock.setReservedQuantity(BigDecimal.ZERO); stock.setFrozenFlag(0L);
        when(sourceMapper.currentDatabaseDate()).thenAnswer(invocation -> databaseToday);
        when(sourceMapper.lockArea(TENANT, 401L)).thenReturn(area);
        when(sourceMapper.lockHead(TENANT, ISSUE_ID)).thenReturn(head);
        when(sourceMapper.lockLines(TENANT, ISSUE_ID)).thenReturn(List.of(target));
        when(sourceMapper.lockWarehouse(TENANT, WAREHOUSE_ID)).thenReturn(warehouse);
        when(sourceMapper.lockMaterial(TENANT, MATERIAL_ID)).thenReturn(material);
        when(sourceMapper.lockLocations(eq(TENANT), eq(WAREHOUSE_ID), any(), any())).thenReturn(List.of(location));
        when(sourceMapper.lockBatchById(eq(TENANT), anyLong())).thenReturn(batch);
        when(sourceMapper.lockBatchesByCode(eq(TENANT), anyString())).thenReturn(List.of(batch));
        when(sourceMapper.lockStocks(eq(TENANT), eq(MATERIAL_ID), eq(WAREHOUSE_ID), eq(LOCATION_ID), any())).thenReturn(List.of(stock));
        when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenReturn(1);
        when(configService.selectConfigByKey(GATE_KEY)).thenReturn("true");
        service = new PdaIssueServiceImpl(issueService, lineMapper, configService, sourceMapper,
            new PdaIssueSourceGuard(sourceMapper, sourceDataSource));
    }

    @AfterEach
    void tearDown() { tenant.close(); releaseDirectTransaction(); }

    /** 真实事务代理测试开始前移除普通单测的连接夹具，不冒充实际事务元数据。 */
    void releaseDirectTransaction() {
        TransactionSynchronizationManager.unbindResourceIfPossible(sourceDataSource);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    @Test
    void pickRejectsChangingAllocatedBatchBeforeAnyWrite() {
        target.setBatchId(701L); target.setBatchCode("批次A");
        target.setLocationId(LOCATION_ID); target.setLocationCode("库位一");
        assertBlocked(bo());
        assertEquals(701L, target.getBatchId(), "不得改变已分配批次身份");
        assertEquals("批次A", target.getBatchCode(), "不得覆盖已分配批次编码");
    }

    @Test
    void pickBindsUniqueRealSourceTogether() {
        PdaActionResultVo result = service.pick(ISSUE_ID, bo());
        assertAll("未分配行绑定唯一真实库存",
            () -> assertEquals(Boolean.TRUE, result.getOk(), "唯一合格库存应允许拣货"),
            () -> assertEquals(stock.getBatchId(), target.getBatchId(), "必须绑定真实批次标识，不能只写编码"),
            () -> assertEquals(stock.getBatchCode(), target.getBatchCode(), "批次编码须与真实标识对应"),
            () -> assertEquals(stock.getWarehouseId(), target.getWarehouseId(), "必须绑定真实仓库标识"),
            () -> assertEquals(stock.getWarehouseCode(), target.getWarehouseCode(), "仓库编码须与标识对应"),
            () -> assertEquals(stock.getLocationId(), target.getLocationId(), "必须绑定真实库位标识"),
            () -> assertEquals(stock.getLocationCode(), target.getLocationCode(), "库位编码须与标识对应"),
            () -> assertEquals(new BigDecimal("2"), target.getCurrentQty(), "仅目标行携带本次正数数量"));
        verify(issueService, times(1).description("正控应只执行一次扣库")).execute(ISSUE_ID);
        verify(sourceMapper, description("必须锁定相同批次的真实执行库存范围"))
            .lockStocks(TENANT, MATERIAL_ID, WAREHOUSE_ID, LOCATION_ID, BATCH_ID);
        verify(issueService, never().description("不得用普通快照替代当前头锁" )).selectById(anyLong());
    }

    @Test void pickBlocksPendingBatch() { batch.setQcStatus("PENDING"); assertBlocked(bo()); }
    @Test void pickBlocksNullQcStatusBatch() { batch.setQcStatus(null); assertBlocked(bo()); }
    @Test void pickBlocksUnqualifiedBatch() { batch.setQcStatus("UNQUALIFIED"); assertBlocked(bo()); }
    @Test void pickBlocksWhenBatchRecordMissing() {
        when(sourceMapper.lockBatchesByCode(TENANT, BATCH_CODE)).thenReturn(List.of()); assertBlocked(bo());
    }
    @Test void pickAllowsQualifiedBatch() {
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, bo()).getOk(), "质检合格且真实库存有效应放行");
        verify(issueService, description("合格库存应执行扣库")).execute(ISSUE_ID);
    }
    @Test void pickSkipsGateWhenConfigDisabled() {
        when(configService.selectConfigByKey(GATE_KEY)).thenReturn("false"); batch.setQcStatus("PENDING");
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, bo()).getOk(), "关闭门禁仅跳过质检，不跳过来源验证");
        verify(sourceMapper, description("关闭门禁仍须解析真实批次")).lockBatchesByCode(TENANT, BATCH_CODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"空量", "零量", "负量", "超领", "已领未知", "已领负数", "应领未知", "应领负数", "欠领为零", "已关闭",
        "未知租户", "头跨租户", "头删除", "头身份不符", "头完成", "头仓库未知", "行跨租户", "行删除", "行串单", "行缺失", "行重复",
        "物料未知", "物料缺失", "物料跨租户", "物料删除", "物料停用", "批次管理未知",
        "仓库缺失", "仓库停用", "仓库删除", "仓库跨租户", "行跨仓库", "行仓库编码不符",
        "库位缺失", "库位重复", "库位停用", "库位冻结", "库位启用未知", "库位冻结未知", "库位删除", "库位跨租户", "库位跨仓库", "已绑库位标识冲突", "已绑库位编码冲突",
        "批次缺失", "批次重复", "批次停用", "批次删除", "批次跨租户", "批次跨物料", "批次跨仓库", "批次编码不符", "已绑批次标识冲突", "批次过期",
        "库存缺失", "库存重复", "库存跨租户", "库存跨物料", "库存跨仓库", "库存跨库位", "库存跨批次", "库存批次编码不符", "库存库位编码不符", "库存仓库编码不符",
        "库存删除", "库存冻结", "冻结未知", "库存过期", "全预留", "库存不足", "账面未知", "预留未知", "负预留"})
    void pickRejectsUnsafeBoundariesWithoutWriting(String boundary) {
        PdaPickBo request = bo();
        switch (boundary) {
            case "空量" -> request.setQty(null);
            case "零量" -> request.setQty(BigDecimal.ZERO);
            case "负量" -> request.setQty(new BigDecimal("-1"));
            case "超领" -> target.setIssuedQty(new BigDecimal("9"));
            case "已领未知" -> target.setIssuedQty(null);
            case "已领负数" -> target.setIssuedQty(new BigDecimal("-1"));
            case "应领未知" -> target.setRequiredQty(null);
            case "应领负数" -> target.setRequiredQty(new BigDecimal("-1"));
            case "欠领为零" -> target.setShortQty(BigDecimal.ZERO);
            case "已关闭" -> target.setLineStatus("SHORT_CLOSED");
            case "未知租户" -> tenant.when(TenantHelper::getTenantId).thenReturn(null);
            case "头跨租户" -> head.setTenantId("000008");
            case "头删除" -> head.setDelFlag("2");
            case "头身份不符" -> head.setIssueId(11L);
            case "头完成" -> head.setStatus("COMPLETED");
            case "头仓库未知" -> head.setWarehouseId(null);
            case "行跨租户" -> target.setTenantId("000008");
            case "行删除" -> target.setDelFlag("2");
            case "行串单" -> target.setIssueId(11L);
            case "行缺失" -> request.setLineId(99L);
            case "行重复" -> when(sourceMapper.lockLines(TENANT, ISSUE_ID)).thenReturn(List.of(target, target));
            case "物料未知" -> target.setMaterialId(null);
            case "物料缺失" -> when(sourceMapper.lockMaterial(TENANT, MATERIAL_ID)).thenReturn(null);
            case "物料跨租户" -> material.setTenantId("000008");
            case "物料删除" -> material.setDelFlag("2");
            case "物料停用" -> material.setStatus(0L);
            case "批次管理未知" -> material.setIsBatchManage(null);
            case "仓库缺失" -> when(sourceMapper.lockWarehouse(TENANT, WAREHOUSE_ID)).thenReturn(null);
            case "仓库停用" -> warehouse.setStatus(0L);
            case "仓库删除" -> warehouse.setDelFlag("2");
            case "仓库跨租户" -> warehouse.setTenantId("000008");
            case "行跨仓库" -> target.setWarehouseId(202L);
            case "行仓库编码不符" -> target.setWarehouseCode("另一个仓库");
            case "库位缺失" -> when(sourceMapper.lockLocations(eq(TENANT), eq(WAREHOUSE_ID), any(), any())).thenReturn(List.of());
            case "库位重复" -> when(sourceMapper.lockLocations(eq(TENANT), eq(WAREHOUSE_ID), any(), any())).thenReturn(List.of(location, location));
            case "库位停用" -> location.setEnableFlag(0L);
            case "库位冻结" -> location.setFrozenFlag(1L);
            case "库位启用未知" -> location.setEnableFlag(null);
            case "库位冻结未知" -> location.setFrozenFlag(null);
            case "库位删除" -> location.setDelFlag("2");
            case "库位跨租户" -> location.setTenantId("000008");
            case "库位跨仓库" -> location.setWarehouseId(202L);
            case "已绑库位标识冲突" -> target.setLocationId(302L);
            case "已绑库位编码冲突" -> target.setLocationCode("另一个库位");
            case "批次缺失" -> when(sourceMapper.lockBatchesByCode(TENANT, BATCH_CODE)).thenReturn(List.of());
            case "批次重复" -> when(sourceMapper.lockBatchesByCode(TENANT, BATCH_CODE)).thenReturn(List.of(batch, batch));
            case "批次停用" -> batch.setStatus("INACTIVE");
            case "批次删除" -> batch.setDelFlag("2");
            case "批次跨租户" -> batch.setTenantId("000008");
            case "批次跨物料" -> batch.setMaterialId(6L);
            case "批次跨仓库" -> batch.setWarehouseId(202L);
            case "批次编码不符" -> batch.setBatchCode("其他批次");
            case "已绑批次标识冲突" -> target.setBatchId(701L);
            case "批次过期" -> batch.setExpiryDate(yesterday());
            case "库存缺失" -> when(sourceMapper.lockStocks(eq(TENANT), eq(MATERIAL_ID), eq(WAREHOUSE_ID), eq(LOCATION_ID), any())).thenReturn(List.of());
            case "库存重复" -> when(sourceMapper.lockStocks(eq(TENANT), eq(MATERIAL_ID), eq(WAREHOUSE_ID), eq(LOCATION_ID), any())).thenReturn(List.of(stock, stock));
            case "库存跨租户" -> stock.setTenantId("000008");
            case "库存跨物料" -> stock.setMaterialId(6L);
            case "库存跨仓库" -> stock.setWarehouseId(202L);
            case "库存跨库位" -> stock.setLocationId(302L);
            case "库存跨批次" -> stock.setBatchId(701L);
            case "库存批次编码不符" -> stock.setBatchCode("其他批次");
            case "库存库位编码不符" -> stock.setLocationCode("另一个库位");
            case "库存仓库编码不符" -> stock.setWarehouseCode("另一个仓库");
            case "库存删除" -> stock.setDelFlag("2");
            case "库存冻结" -> stock.setFrozenFlag(1L);
            case "冻结未知" -> stock.setFrozenFlag(null);
            case "库存过期" -> stock.setExpireDate(yesterday());
            case "全预留" -> stock.setReservedQuantity(stock.getStockQuantity());
            case "库存不足" -> stock.setStockQuantity(BigDecimal.ONE);
            case "账面未知" -> stock.setStockQuantity(null);
            case "预留未知" -> stock.setReservedQuantity(null);
            case "负预留" -> stock.setReservedQuantity(new BigDecimal("-1"));
            default -> fail("未覆盖的测试边界：" + boundary);
        }
        assertBlocked(request);
    }

    @Test void enabledUnfrozenLocationWithDisplayStatusZeroIsValid() {
        location.setStatus(0L);
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, bo()).getOk(), "库位展示状态为零不代表停用，真实启用未冻结来源应放行");
        verify(issueService, description("真实可用库位应按原流程扣库")).execute(ISSUE_ID);
    }
    @ParameterizedTest
    @ValueSource(strings = {"库区缺失", "库区停用", "库区冻结", "库区启用未知", "库区冻结未知", "库区跨租户", "库区删除", "库区跨仓库",
        "库区身份不符", "库位无父库区", "库存跨库区", "库存无库区", "库存库区编码不符"})
    void parentAreaChainAndAvailabilityCannotBeBypassed(String boundary) {
        switch (boundary) {
            case "库区缺失" -> when(sourceMapper.lockArea(TENANT, 401L)).thenReturn(null);
            case "库区停用" -> area.setEnableFlag(0L);
            case "库区冻结" -> area.setFrozenFlag(1L);
            case "库区启用未知" -> area.setEnableFlag(null);
            case "库区冻结未知" -> area.setFrozenFlag(null);
            case "库区跨租户" -> area.setTenantId("000008");
            case "库区删除" -> area.setDelFlag("2");
            case "库区跨仓库" -> area.setWarehouseId(202L);
            case "库区身份不符" -> area.setAreaId(402L);
            case "库位无父库区" -> location.setAreaId(null);
            case "库存跨库区" -> stock.setAreaId(402L);
            case "库存无库区" -> stock.setAreaId(null);
            case "库存库区编码不符" -> stock.setAreaCode("其他库区");
            default -> fail("未覆盖的库区边界：" + boundary);
        }
        assertBlocked(bo());
    }
    @Test void databaseNextDayRatherThanJvmTodayControlsStockExpiry() {
        databaseToday = java.sql.Date.valueOf(LocalDate.now().plusDays(1));
        stock.setExpireDate(java.sql.Date.valueOf(LocalDate.now()));
        assertBlocked(bo());
    }
    @Test void databaseNextDayRatherThanJvmTodayControlsBatchExpiry() {
        databaseToday = java.sql.Date.valueOf(LocalDate.now().plusDays(1));
        batch.setExpiryDate(java.sql.Date.valueOf(LocalDate.now()));
        assertBlocked(bo());
    }
    @Test void databasePriorDayAllowsDateStillValidThereDespiteJvmNextDay() {
        databaseToday = java.sql.Date.valueOf(LocalDate.now().minusDays(1));
        stock.setExpireDate(databaseToday); batch.setExpiryDate(databaseToday);
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, bo()).getOk(), "数据库当日仍有效的来源不能被 JVM 跨时区当日提前拦截");
    }
    @Test void unknownDatabaseDateIsNotSafeToIssue() { databaseToday = null; assertBlocked(bo()); }

    @Test void emptyScanStillChecksQcOfAllocatedBatchId() {
        target.setBatchId(BATCH_ID); target.setLocationId(LOCATION_ID);
        batch.setQcStatus("PENDING"); PdaPickBo request = bo(); request.setBatchCode(null); request.setLocationCode(null);
        assertBlocked(request);
        verify(sourceMapper, description("空扫码应按已分配标识锁定真实批次")).lockBatchById(TENANT, BATCH_ID);
    }
    @Test void emptyScanAllowsQualifiedAllocatedSource() {
        target.setBatchId(BATCH_ID); target.setLocationId(LOCATION_ID);
        PdaPickBo request = bo(); request.setBatchCode(null); request.setLocationCode(null);
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, request).getOk(), "已明确绑定的真实合格来源可不重扫编码");
        assertEquals(BATCH_CODE, target.getBatchCode(), "空扫码仍补齐真实批次编码");
    }
    @Test void emptyScanCannotInventSourceForBatchManagedMaterial() {
        PdaPickBo request = bo(); request.setBatchCode(null); assertBlocked(request);
    }
    @Test void genuineNonBatchNullSourceIsAllowedWithoutQcBypass() {
        material.setIsBatchManage(0L); stock.setBatchId(null); stock.setBatchCode(null);
        PdaPickBo request = bo(); request.setBatchCode(null);
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, request).getOk(), "明确原事务中真实非批次物料的唯一库存应允许领料");
        verify(sourceMapper, description("空批次标识必须锁定引擎可能扣减的完整库存范围"))
            .lockStocks(TENANT, MATERIAL_ID, WAREHOUSE_ID, LOCATION_ID, null);
        assertNull(target.getBatchId(), "不得为真实非批次库存捏造批次标识");
    }
    @Test void zeroBatchIdentityCannotPretendToBeSupportedNonBatchSource() {
        material.setIsBatchManage(0L); stock.setBatchId(0L); stock.setBatchCode(null);
        PdaPickBo request = bo(); request.setBatchCode(null); assertBlocked(request);
    }
    @ParameterizedTest @ValueSource(ints = {0, 2})
    void nonBatchNullScopeRequiresKnownPhysicalRepeatableReadTransaction(int isolation) throws Exception {
        material.setIsBatchManage(0L); stock.setBatchId(null); stock.setBatchCode(null);
        // 线程元数据不能覆盖真实连接的未知或较弱隔离条件。
        when(sourceConnection.getTransactionIsolation()).thenReturn(isolation);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Isolation.REPEATABLE_READ.value());
        PdaPickBo request = bo(); request.setBatchCode(null); assertBlocked(request);
    }
    @Test void originalTransactionMustRemainRequiredAndExplicitlyRepeatableRead() throws Exception {
        Transactional transaction = PdaIssueServiceImpl.class.getMethod("pick", Long.class, PdaPickBo.class)
            .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRED, transaction.propagation(), "不能另开事务破坏原身份绑定与扣库的原子性");
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation(), "空批次完整范围锁须有明确的可重复读事务条件");
        assertArrayEquals(new Class<?>[]{Exception.class}, transaction.rollbackFor(), "原事务须保留所有异常回滚");
    }
    @Test void databaseDateAtTodayMustBeSupportedAndNotExpired() {
        stock.setExpireDate(java.sql.Date.valueOf(LocalDate.now()));
        batch.setExpiryDate(java.sql.Date.valueOf(LocalDate.now()));
        var result = assertDoesNotThrow(() -> service.pick(ISSUE_ID, bo()), "数据库日期类型不能在来源门禁中触发异常");
        assertEquals(Boolean.TRUE, result.getOk(), "当日到期与既有执行引擎的日期边界一致，不应提前拒绝");
    }
    @Test void nonBatchBranchCannotConsumeInventoryWithRealBatchIdentity() {
        material.setIsBatchManage(0L); PdaPickBo request = bo(); request.setBatchCode(null); assertBlocked(request);
    }
    @Test void nonBatchBranchCannotSelectOneFromMixedExecutionRange() {
        material.setIsBatchManage(0L); stock.setBatchId(null); stock.setBatchCode(null);
        when(sourceMapper.lockStocks(eq(TENANT), eq(MATERIAL_ID), eq(WAREHOUSE_ID), eq(LOCATION_ID), any()))
            .thenReturn(List.of(stock, new WmMaterialStock()));
        PdaPickBo request = bo(); request.setBatchCode(null); assertBlocked(request);
    }
    @Test void disabledQcGateCannotAllowMissingSource() {
        when(configService.selectConfigByKey(GATE_KEY)).thenReturn("false");
        when(sourceMapper.lockBatchesByCode(TENANT, BATCH_CODE)).thenReturn(List.of()); assertBlocked(bo());
    }
    @Test void currentLockedRowsControlQuantityAndNotEarlierSnapshots() {
        target.setIssuedQty(new BigDecimal("9")); assertBlocked(bo());
        verify(lineMapper, never().description("数量必须使用锁内重读，不得再查旧快照")).selectList(any());
    }
    @Test void exactAvailableQuantityCanBePickedAndOtherLinesAreCleared() {
        stock.setReservedQuantity(new BigDecimal("8"));
        WmProductionIssueLine other = new WmProductionIssueLine();
        other.setLineId(2L); other.setIssueId(ISSUE_ID); other.setTenantId(TENANT); other.setDelFlag("0");
        other.setCurrentQty(new BigDecimal("7"));
        when(sourceMapper.lockLines(TENANT, ISSUE_ID)).thenReturn(List.of(target, other));
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, bo()).getOk(), "可用量恰好满足且未超过应领时应允许");
        assertEquals(BigDecimal.ZERO, other.getCurrentQty(), "其他行必须清零，不能意外随目标行扣库");
        var order = inOrder(sourceMapper, lineMapper, issueService);
        order.verify(sourceMapper).lockHead(TENANT, ISSUE_ID);
        order.verify(sourceMapper).lockLines(TENANT, ISSUE_ID);
        order.verify(sourceMapper).lockStocks(TENANT, MATERIAL_ID, WAREHOUSE_ID, LOCATION_ID, BATCH_ID);
        order.verify(lineMapper).updateById(target); order.verify(lineMapper).updateById(other);
        order.verify(issueService).execute(ISSUE_ID);
    }
    @Test void failedLineWriteMustThrowBeforeExecution() {
        when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.pick(ISSUE_ID, bo()), "写入失败必须抛异常以回滚整个事务");
        verify(issueService, never().description("明细写入失败不得执行扣库")).execute(anyLong());
    }
    @Test void executionFailureMustPropagateToOriginalTransaction() {
        doThrow(new ServiceException("模拟执行失败")).when(issueService).execute(ISSUE_ID);
        assertThrows(ServiceException.class, () -> service.pick(ISSUE_ID, bo()), "不能吞掉扣库异常破坏原事务回滚");
    }
    @Test void unknownTenantDoesNotQueryOrWriteAnySource() {
        tenant.when(TenantHelper::getTenantId).thenReturn(" "); assertBlocked(bo());
        verifyNoInteractions(sourceMapper);
    }
    @Test void missingHeadKeepsExistingRefusal() {
        when(sourceMapper.lockHead(TENANT, ISSUE_ID)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.pick(ISSUE_ID, bo()), "不存在的领料头仍应拒绝");
        verify(lineMapper, never().description("不存在的单头不得写行")).updateById(any(WmProductionIssueLine.class));
    }

    @Test void sourceBindingDoesNotRewriteOriginalRequirementAndWorkOrderKeys() {
        target.setRequirementDetailId(901L); head.setWoId(902L);
        assertEquals(Boolean.TRUE, service.pick(ISSUE_ID, bo()).getOk(), "唯一真实库存的来源绑定应允许继续原流程");
        assertEquals(901L, target.getRequirementDetailId(), "扫码绑定库存不能改换原物料需求来源行");
        assertEquals(902L, head.getWoId(), "扫码绑定库存不能改换原工单头来源");
    }
    @Test void emptyBatchScanWithExistingControllerLocationInputStillEnforcesQc() {
        target.setBatchId(BATCH_ID); target.setLocationId(LOCATION_ID); batch.setQcStatus("PENDING");
        PdaPickBo request = bo(); request.setBatchCode(null); assertBlocked(request);
    }

    private void assertBlocked(PdaPickBo request) {
        PdaActionResultVo result = service.pick(ISSUE_ID, request);
        assertAll("拒绝不安全来源且不写不执行",
            () -> assertEquals(Boolean.FALSE, result.getOk(), "来源或数量不安全必须拒绝：" + result.getMessage()),
            () -> assertNotNull(result.getMessage(), "拒绝必须说明原因"),
            () -> verify(lineMapper, never().description("拒绝时不得写明细")).updateById(any(WmProductionIssueLine.class)),
            () -> verify(issueService, never().description("拒绝时不得调用实际扣库")).execute(anyLong()));
    }
    PdaPickBo bo() {
        PdaPickBo request = new PdaPickBo(); request.setLineId(LINE_ID); request.setQty(new BigDecimal("2"));
        request.setLocationCode("库位一"); request.setBatchCode(BATCH_CODE); return request;
    }
    private static Date yesterday() {
        return Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
