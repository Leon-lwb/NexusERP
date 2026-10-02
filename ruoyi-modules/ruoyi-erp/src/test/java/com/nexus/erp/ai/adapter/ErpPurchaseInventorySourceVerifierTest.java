package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseInventorySourceVerifier.InventorySnapshot;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper.DatabaseClock;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper.QuantityColumn;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 对真实认证器、真实事务资格与真实服务切面的离线回归，不用假 verified 或算法替身。
 * JDBC 与 Mapper 只提供不联网的来源夹具；事务由真实 Spring 管理器创建且始终回滚。
 * 仅测试入口映射同一持有者，生产认证器绝不取得连接、补绑资源或新开事务。
 */
@Tag("dev")
class ErpPurchaseInventorySourceVerifierTest {
    private static final String TENANT = "inventory-test";
    private static final Long MATERIAL = 9223372036854775000L;
    private static final Long UNIT = 27L;
    private static final Instant NOW = Instant.parse("2026-10-02T02:03:04.123456Z");

    @AfterEach
    void noTransactionOrRoutingResourcesLeak() {
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertNull(DynamicDataSourceContextHolder.peek());
    }

    @Test
    void realProxyReturnsExactImmutableSnapshotWithExplicitDatabaseClock() throws SQLException {
        Fixture f = new Fixture();
        InventorySnapshot result = f.read();
        assertAll(
            () -> assertEquals(TENANT, result.tenantId()),
            () -> assertEquals(MATERIAL, result.materialId()),
            () -> assertEquals(UNIT, result.baseUomId()),
            () -> assertEquals(4, result.quantityScale()),
            () -> assertEquals(NOW, result.snapshotAt()),
            () -> assertEquals("+08:00", result.databaseTimeZone()),
            () -> assertEquals(new BigDecimal("12.5000"), result.quantity()),
            () -> assertEquals(new BigDecimal("2.0000"), result.reserved()),
            () -> assertEquals(new BigDecimal("3.0000"), result.safety()),
            () -> assertEquals(new BigDecimal("0.0000"), result.frozenQuantity()),
            () -> assertEquals(Set.of(91L), result.stockIds()));
        assertTrue(result.sourceVersions().keySet().containsAll(Set.of(
            "bd_material:" + MATERIAL, "bd_unit_of_measure:27", "wm_material_stock:91",
            "wm_warehouse:8", "wm_storage_area:18", "wm_storage_location:28", "quantity-schema")));
        assertTrue(result.sourceVersions().get("wm_material_stock:91").contains("version=2"));
        assertThrows(UnsupportedOperationException.class, () -> result.stockIds().add(4L));
        assertThrows(UnsupportedOperationException.class, () -> result.sourceVersions().put("fake", "verified"));
        f.stock.setStockQuantity(new BigDecimal("999"));
        assertEquals(new BigDecimal("12.5000"), result.quantity());
        verify(f.qualification).requireParticipatingWritableErpTransaction();
        verify(f.erpConnection, times(1)).rollback();
        verify(f.erpConnection, never()).getMetaData();
        verify(f.erpConnection, never()).getCatalog();
    }

    @Test
    void databaseClockIsRefreshedAfterAllLocksAndExpiryDuringLockWaitRejectsPool() throws SQLException {
        Fixture f = new Fixture();
        when(f.mapper.selectDatabaseClock()).thenReturn(f.clock,
            new DatabaseClock(epoch(NOW.plusSeconds(2)), "+08:00"));
        f.stock.setExpireDate(Date.from(NOW.plusSeconds(1)));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().startsWith("采购库存来源未核验："));
    }

    @Test
    void quantitySchemaMustDescribeAlreadyLockedRowsRatherThanEarlierUnlockedTableState() throws SQLException {
        Fixture f = new Fixture();
        AtomicBoolean stockLocked = new AtomicBoolean(false);
        when(f.mapper.lockStocks(anyString(), anyLong())).thenAnswer(i -> { stockLocked.set(true); return f.stocks; });
        List<QuantityColumn> earlier = f.columns;
        when(f.mapper.selectQuantityColumns()).thenAnswer(i -> stockLocked.get()
            ? List.of(earlier.get(0), new QuantityColumn("wm_material_stock", "stock_quantity", "double", 12, 2), earlier.get(2))
            : earlier);
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertTrue(error.getMessage().startsWith("采购库存来源未核验："));
    }

    @Test
    void snapshotUsesDatabaseTimeAfterParentLocksRatherThanTimeBeforeWaiting() throws SQLException {
        Fixture f = new Fixture();
        when(f.mapper.selectDatabaseClock()).thenReturn(f.clock,
            new DatabaseClock(epoch(NOW.plusSeconds(2)), "+08:00"));
        assertEquals(NOW.plusSeconds(2), f.read().snapshotAt());
    }

    @Test
    void explicitZeroIsTrustedButEmptyStocksAreNeverDefaultedToZero() throws SQLException {
        Fixture f = new Fixture();
        f.stock.setStockQuantity(BigDecimal.ZERO);
        f.stock.setReservedQuantity(BigDecimal.ZERO);
        f.material.setSafetyStock(BigDecimal.ZERO);
        InventorySnapshot result = f.read();
        assertEquals(new BigDecimal("0.0000"), result.quantity());
        assertEquals(new BigDecimal("0.0000"), result.reserved());
        assertEquals(Set.of(91L), result.stockIds());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSources")
    void incompleteUnknownOrInconsistentSourceRejectsEntirePool(String name, Consumer<Fixture> corrupt)
        throws SQLException {
        Fixture f = new Fixture();
        corrupt.accept(f);
        ServiceException error = assertThrows(ServiceException.class, f::read, name);
        assertTrue(error.getMessage().startsWith("采购库存来源未核验："), error.getMessage());
    }

    private static Stream<Arguments> invalidSources() {
        return Stream.of(
            bad("数据库时钟缺失", f -> f.clock = null),
            bad("数据库时区未知", f -> f.clock = new DatabaseClock(epoch(NOW), null)),
            bad("数据库时区UTC", f -> f.clock = new DatabaseClock(epoch(NOW), "+00:00")),
            bad("数据库时区SYSTEM", f -> f.clock = new DatabaseClock(epoch(NOW), "SYSTEM")),
            bad("数据库时区命名非明确偏移", f -> f.clock = new DatabaseClock(epoch(NOW), "Asia/Shanghai")),
            bad("数据库秒NULL", f -> f.clock = new DatabaseClock(null, "+08:00")),
            bad("数据库秒负值", f -> f.clock = new DatabaseClock(BigDecimal.ONE.negate(), "+08:00")),
            bad("数据库秒不精确", f -> f.clock = new DatabaseClock(new BigDecimal("1790906584.1234567891"), "+08:00")),
            bad("真实列元数据NULL", f -> f.columns = null),
            bad("真实列元数据缺列", f -> f.columns = f.columns.subList(0, 2)),
            bad("真实列元数据重复", f -> f.columns = List.of(f.columns.get(0), f.columns.get(0), f.columns.get(2))),
            bad("库存DOUBLE禁止CAST升级", f -> f.columns = List.of(f.columns.get(0),
                new QuantityColumn("wm_material_stock", "stock_quantity", "double", 12, 2), f.columns.get(2))),
            bad("DECIMAL精度不符", f -> f.columns = List.of(f.columns.get(0),
                new QuantityColumn("wm_material_stock", "stock_quantity", "decimal", 20, 4), f.columns.get(2))),
            bad("物料缺失", f -> f.material = null),
            bad("物料混租户", f -> f.material.setTenantId("other")),
            bad("物料身份错", f -> f.material.setMaterialId(8L)),
            bad("物料停用", f -> f.material.setStatus(0L)),
            bad("物料状态未知", f -> f.material.setStatus(null)),
            bad("物料删除未知", f -> f.material.setDelFlag(null)),
            bad("物料明确删除", f -> f.material.setDelFlag("1")),
            bad("基本单位ID缺失", f -> f.material.setBaseUomId(null)),
            bad("安全库存NULL", f -> f.material.setSafetyStock(null)),
            bad("安全库存负值", f -> f.material.setSafetyStock(new BigDecimal("-1"))),
            bad("安全库存过精", f -> f.material.setSafetyStock(new BigDecimal("0.00001"))),
            bad("安全库存溢出", f -> f.material.setSafetyStock(new BigDecimal("100000000000000"))),
            bad("单位缺失", f -> f.unit = null),
            bad("单位混租户", f -> f.unit.setTenantId("other")),
            bad("单位身份错", f -> f.unit.setUomId(999L)),
            bad("单位停用", f -> f.unit.setStatus(0L)),
            bad("单位删除未知", f -> f.unit.setDelFlag("2")),
            bad("单位根类别未知", f -> f.unit.setCategory(null)),
            bad("单位精度NULL", f -> f.unit.setQuantityScale(null)),
            bad("单位精度负值", f -> f.unit.setQuantityScale(-1)),
            bad("单位精度超4", f -> f.unit.setQuantityScale(5)),
            bad("单位精度不符数量", f -> f.unit.setQuantityScale(0)),
            bad("单位精度不符预留", f -> { f.unit.setQuantityScale(0); f.stock.setStockQuantity(new BigDecimal("20"));
                f.stock.setReservedQuantity(new BigDecimal("1.5")); }),
            bad("单位精度不符安全", f -> { f.unit.setQuantityScale(0); f.stock.setStockQuantity(new BigDecimal("20"));
                f.material.setSafetyStock(new BigDecimal("1.5")); }),
            bad("单位版本NULL", f -> f.unit.setConversionVersion(null)),
            bad("单位版本空白", f -> f.unit.setConversionVersion(" ")),
            bad("单位核验日期NULL", f -> f.unit.setConversionVerifiedAt(null)),
            bad("单位核验日期未来", f -> f.unit.setConversionVerifiedAt(Date.from(NOW.plusSeconds(1)))),
            bad("单位核验日期零", f -> f.unit.setConversionVerifiedAt(new Date(0))),
            bad("根转换率NULL", f -> f.unit.setConversionRate(null)),
            bad("根转换率不是1", f -> f.unit.setConversionRate(new BigDecimal("2"))),
            bad("派生基本单位不猜测未独立核验的根", f -> { f.unit.setIsBase(0L); f.unit.setPrimaryId(7L); }),
            bad("未知基本单位旗号", f -> f.unit.setIsBase(null)),
            bad("基本单位错误祖先", f -> f.unit.setPrimaryId(7L)),
            bad("库存列表NULL", f -> f.stocks = null),
            bad("库存列表空", f -> f.stocks = List.of()),
            bad("库存含NULL行", f -> f.stocks = java.util.Arrays.asList(f.stock, null)),
            bad("库存重复行", f -> f.stocks = List.of(f.stock, f.stock)),
            bad("库存混租户", f -> f.stock.setTenantId("other")),
            bad("库存身份缺失", f -> f.stock.setStockId(null)),
            bad("库存身份非正", f -> f.stock.setStockId(0L)),
            bad("库存错物料", f -> f.stock.setMaterialId(8L)),
            bad("库存删除未知", f -> f.stock.setDelFlag(null)),
            bad("库存删除未知2", f -> f.stock.setDelFlag("2")),
            bad("库存版本NULL", f -> f.stock.setVersion(null)),
            bad("库存版本负值", f -> f.stock.setVersion(-1)),
            bad("库存单位NULL", f -> f.stock.setBaseUomId(null)),
            bad("库存异单位", f -> f.stock.setBaseUomId("28")),
            bad("库存单位前导零", f -> f.stock.setBaseUomId("027")),
            bad("库存单位空白", f -> f.stock.setBaseUomId("27 ")),
            bad("库存数量NULL", f -> f.stock.setStockQuantity(null)),
            bad("库存数量负值", f -> f.stock.setStockQuantity(new BigDecimal("-1"))),
            bad("库存数量过精", f -> f.stock.setStockQuantity(new BigDecimal("0.00001"))),
            bad("库存数量溢出", f -> f.stock.setStockQuantity(new BigDecimal("100000000000000"))),
            bad("库存预留NULL", f -> f.stock.setReservedQuantity(null)),
            bad("库存预留负值", f -> f.stock.setReservedQuantity(new BigDecimal("-1"))),
            bad("库存预留过精", f -> f.stock.setReservedQuantity(new BigDecimal("0.00001"))),
            bad("库存预留超在库", f -> f.stock.setReservedQuantity(new BigDecimal("13"))),
            bad("库存冻结NULL", f -> f.stock.setFrozenFlag(null)),
            bad("库存冻结未知2", f -> f.stock.setFrozenFlag(2L)),
            bad("库存入库时间缺失", f -> f.stock.setRecptDate(null)),
            bad("库存入库未来", f -> f.stock.setRecptDate(Date.from(NOW.plusSeconds(1)))),
            bad("库存有效期未知", f -> f.stock.setExpireDate(null)),
            bad("库存已过期", f -> f.stock.setExpireDate(Date.from(NOW.minusSeconds(1)))),
            bad("库存有效期零日期", f -> f.stock.setExpireDate(new Date(0))),
            bad("库存生产晚于入库", f -> f.stock.setProductionDate(Date.from(NOW))),
            bad("库存仓库缺失", f -> f.stock.setWarehouseId(null)),
            bad("库存库区缺失", f -> f.stock.setAreaId(null)),
            bad("库存库位ID0不能代表无库位", f -> f.stock.setLocationId(0L)),
            bad("仓库查询NULL", f -> f.warehouses = null),
            bad("仓库查询缺失", f -> f.warehouses = List.of()),
            bad("仓库查询重复", f -> f.warehouses = List.of(f.warehouse, f.warehouse)),
            bad("仓库查询多余ID", f -> f.warehouse.setWarehouseId(999L)),
            bad("仓库混租户", f -> f.warehouse.setTenantId("other")),
            bad("仓库停用", f -> f.warehouse.setStatus(0L)),
            bad("仓库状态未知", f -> f.warehouse.setStatus(null)),
            bad("仓库删除未知", f -> f.warehouse.setDelFlag(null)),
            bad("库区查询缺失", f -> f.areas = List.of()),
            bad("库区混租户", f -> f.area.setTenantId("other")),
            bad("库区父仓库不符", f -> f.area.setWarehouseId(9L)),
            bad("库区停用", f -> f.area.setEnableFlag(0L)),
            bad("库区启用未知", f -> f.area.setEnableFlag(null)),
            bad("库区冻结未知", f -> f.area.setFrozenFlag(2L)),
            bad("库区储位旗号未知", f -> f.area.setLocationFlag(null)),
            bad("库区储位旗号未知2", f -> f.area.setLocationFlag(2L)),
            bad("启用储位必须有储位", f -> f.stock.setLocationId(null)),
            bad("库区删除未知", f -> f.area.setDelFlag(null)),
            bad("储位查询缺失", f -> f.locations = List.of()),
            bad("储位查询NULL", f -> f.locations = null),
            bad("储位混租户", f -> f.location.setTenantId("other")),
            bad("储位父库区不符", f -> f.location.setAreaId(19L)),
            bad("储位父仓库不符", f -> f.location.setWarehouseId(9L)),
            bad("储位停用", f -> f.location.setEnableFlag(0L)),
            bad("储位冻结未知", f -> f.location.setFrozenFlag(null)),
            bad("储位删除未知", f -> f.location.setDelFlag("2")),
            bad("未启用储位但已有储位仍核验", f -> { f.area.setLocationFlag(0L); f.location.setEnableFlag(0L); }),
            bad("冻结库存不能掩盖NULL数量", f -> { f.stock.setFrozenFlag(1L); f.stock.setStockQuantity(null); }),
            bad("冻结父不能掩盖停用储位", f -> { f.area.setFrozenFlag(1L); f.location.setEnableFlag(0L); })
        );
    }

    @ParameterizedTest
    @MethodSource("frozenSources")
    void stockOrParentFreezeExcludesBothQuantityAndCorrespondingReservation(Consumer<Fixture> freeze)
        throws SQLException {
        Fixture f = new Fixture();
        freeze.accept(f);
        InventorySnapshot result = f.read();
        assertEquals(new BigDecimal("0.0000"), result.quantity());
        assertEquals(new BigDecimal("0.0000"), result.reserved());
        assertEquals(new BigDecimal("12.5000"), result.frozenQuantity());
        assertEquals(Set.of(91L), result.stockIds());
    }

    private static Stream<Consumer<Fixture>> frozenSources() {
        return Stream.of(f -> f.stock.setFrozenFlag(1L), f -> f.area.setFrozenFlag(1L),
            f -> f.location.setFrozenFlag(1L));
    }

    @Test
    void noLocationIsAllowedOnlyWhenAreaExplicitlyDisablesLocationManagement() throws SQLException {
        Fixture f = new Fixture();
        f.area.setLocationFlag(0L);
        f.stock.setLocationId(null);
        f.read();
        verify(f.mapper, never()).lockLocations(anyString(), anyList());
    }

    @Test
    void presentLocationIsStillVerifiedWhenLocationManagementIsDisabled() throws SQLException {
        Fixture f = new Fixture();
        f.area.setLocationFlag(0L);
        f.read();
        verify(f.mapper).lockLocations(TENANT, List.of(28L));
    }

    @Test
    void locksEachParentTableInAscendingDistinctIdOrderAndRetainsAllStockIds() throws SQLException {
        Fixture f = new Fixture();
        WmMaterialStock other = stock(40L, 7L, 17L, 27L);
        other.setFrozenFlag(1L);
        f.stocks = List.of(f.stock, other);
        f.warehouses = List.of(f.warehouse, warehouse(7L));
        f.areas = List.of(f.area, area(17L, 7L));
        f.locations = List.of(f.location, location(27L, 17L, 7L));
        InventorySnapshot result = f.read();
        var order = inOrder(f.mapper);
        order.verify(f.mapper).selectDatabaseClock();
        order.verify(f.mapper).lockMaterial(TENANT, MATERIAL);
        order.verify(f.mapper).lockUnit(TENANT, UNIT);
        order.verify(f.mapper).lockStocks(TENANT, MATERIAL);
        order.verify(f.mapper).lockWarehouses(TENANT, List.of(7L, 8L));
        order.verify(f.mapper).lockAreas(TENANT, List.of(17L, 18L));
        order.verify(f.mapper).lockLocations(TENANT, List.of(27L, 28L));
        order.verify(f.mapper).selectQuantityColumns();
        assertEquals(Set.of(40L, 91L), result.stockIds());
        assertEquals(new BigDecimal("12.5000"), result.quantity());
        assertEquals(new BigDecimal("2.0000"), result.reserved());
        assertEquals(new BigDecimal("12.5000"), result.frozenQuantity());
    }

    @Test
    void sourceFingerprintChangesWhenParentFreezeChangesWithoutStockVersionChange() throws SQLException {
        Fixture f = new Fixture();
        InventorySnapshot before = f.read();
        f.area.setFrozenFlag(1L);
        InventorySnapshot after = f.read();
        assertNotEquals(before.sourceVersions().get("wm_storage_area:18"), after.sourceVersions().get("wm_storage_area:18"));
        assertEquals(before.sourceVersions().get("wm_material_stock:91"), after.sourceVersions().get("wm_material_stock:91"));
    }

    @ParameterizedTest
    @MethodSource("invalidTenants")
    void tenantIsCheckedBeforeAllMappers(boolean enabled, String request, String current) throws SQLException {
        Fixture f = new Fixture();
        try (MockedStatic<TenantHelper> tenant = tenant(enabled, current)) {
            f.bound(false, () -> assertThrows(ServiceException.class, () -> f.proxy().verifyLocked(request, MATERIAL)));
        }
        verifyNoInteractions(f.mapper);
    }

    private static Stream<Arguments> invalidTenants() {
        return Stream.of(Arguments.of(false, TENANT, TENANT), Arguments.of(true, "other", TENANT),
            Arguments.of(true, null, TENANT), Arguments.of(true, " ", TENANT), Arguments.of(true, TENANT, null));
    }

    @Test
    void mandatoryProxyRejectsNoTransactionBeforeMapperAndDoesNotOpenConnection() throws SQLException {
        Fixture f = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(IllegalTransactionStateException.class, () -> f.proxy().verifyLocked(TENANT, MATERIAL));
        }
        verifyNoInteractions(f.mapper);
        verify(f.erp, never()).getConnection();
    }

    @Test
    void directInvocationCannotBypassPhysicalTransactionQualification() throws SQLException {
        Fixture f = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(ServiceException.class, () -> f.reader().verifyLocked(TENANT, MATERIAL));
        }
        verifyNoInteractions(f.mapper);
        verify(f.erp, never()).getConnection();
    }

    @Test
    void participatingGateFailureStopsBeforeAnyMapperAndOldStrictEntryIsNotCalled() throws SQLException {
        Fixture f = new Fixture();
        PuRequirementErpTransactionQualification gate = mock(PuRequirementErpTransactionQualification.class);
        doThrow(new ServiceException("测试门禁拒绝")).when(gate).requireParticipatingWritableErpTransaction();
        var reader = new ErpPurchaseInventorySourceVerifier(f.mapper, gate);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(ServiceException.class, () -> reader.verifyLocked(TENANT, MATERIAL));
        }
        verify(gate).requireParticipatingWritableErpTransaction();
        verify(gate, never()).requireWritableErpTransaction();
        verifyNoInteractions(f.mapper);
    }

    @Test
    void preboundExternallyForgedSqlSessionWithSameConnectionIsStillRejectedBeforeMapper() throws SQLException {
        Fixture f = new Fixture();
        org.apache.ibatis.session.SqlSession session = mock(org.apache.ibatis.session.SqlSession.class);
        when(session.getConnection()).thenReturn(f.erpConnection);
        var holder = new org.mybatis.spring.SqlSessionHolder(session, org.apache.ibatis.session.ExecutorType.SIMPLE, null);
        holder.setSynchronizedWithTransaction(true);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            f.bound(false, () -> {
                TransactionSynchronizationManager.bindResource(f.factory, holder);
                try {
                    assertThrows(ServiceException.class, () -> f.proxy().verifyLocked(TENANT, MATERIAL));
                    assertSame(holder, TransactionSynchronizationManager.getResource(f.factory));
                    return null;
                } finally {
                    TransactionSynchronizationManager.unbindResource(f.factory);
                }
            });
        }
        verifyNoInteractions(f.mapper);
    }

    @Test
    void readOnlyPhysicalTransactionIsRejectedBeforeAnyMapper() throws SQLException {
        Fixture f = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            f.bound(true, () -> assertThrows(ServiceException.class, () -> f.proxy().verifyLocked(TENANT, MATERIAL)));
        }
        verifyNoInteractions(f.mapper);
    }

    @Test
    void routerOnlyErpTransactionIsNotUpgradedByBindingOrOpeningAnotherConnection() throws SQLException {
        Fixture f = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            DynamicDataSourceContextHolder.push("erp");
            try {
                new TransactionTemplate(new DataSourceTransactionManager(f.routing)).execute(status -> {
                    assertThrows(ServiceException.class, () -> f.proxy().verifyLocked(TENANT, MATERIAL));
                    assertNull(TransactionSynchronizationManager.getResource(f.erp));
                    status.setRollbackOnly();
                    return null;
                });
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        }
        verifyNoInteractions(f.mapper);
        verify(f.erp, times(1)).getConnection();
    }

    @Test
    void masterBoundTransactionIsRejectedEvenThroughErpServiceProxy() throws SQLException {
        Fixture f = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            new TransactionTemplate(new DataSourceTransactionManager(f.routing)).execute(status -> {
                assertThrows(ServiceException.class, () -> f.proxy().verifyLocked(TENANT, MATERIAL));
                status.setRollbackOnly();
                return null;
            });
        }
        verifyNoInteractions(f.mapper);
        verify(f.erp, never()).getConnection();
    }

    @Test
    void mapperFailureIsSanitizedAndNeverAttachedAsCause() throws SQLException {
        Fixture f = new Fixture();
        when(f.mapper.lockStocks(anyString(), anyLong()))
            .thenThrow(new DataAccessResourceFailureException("secret-token-test JDBC password"));
        ServiceException error = assertThrows(ServiceException.class, f::read);
        assertFalse(error.toString().contains("secret-token-test"));
        assertNull(error.getCause());
        verify(f.mapper).lockStocks(TENANT, MATERIAL);
    }

    @Test
    void publicContractContainsOnlyIdentityInputAndImmutableOutput() throws ReflectiveOperationException {
        assertTrue(InventorySnapshot.class.isRecord());
        var method = ErpPurchaseInventorySourceVerifier.class.getMethod("verifyLocked", String.class, Long.class);
        assertEquals(InventorySnapshot.class, method.getReturnType());
        assertEquals("erp", method.getAnnotation(DS.class).value());
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,
            method.getAnnotation(org.springframework.transaction.annotation.Transactional.class).propagation());
    }

    private static Arguments bad(String name, Consumer<Fixture> change) {
        return Arguments.of(name, change);
    }

    private static BigDecimal epoch(Instant value) {
        return BigDecimal.valueOf(value.getEpochSecond()).add(BigDecimal.valueOf(value.getNano(), 9));
    }

    private static MockedStatic<TenantHelper> tenant(boolean enabled, String current) {
        MockedStatic<TenantHelper> mocked = mockStatic(TenantHelper.class);
        mocked.when(TenantHelper::isEnable).thenReturn(enabled);
        mocked.when(TenantHelper::getTenantId).thenReturn(current);
        return mocked;
    }

    private static WmMaterialStock stock(Long id, Long warehouse, Long area, Long location) {
        WmMaterialStock s = new WmMaterialStock();
        s.setTenantId(TENANT); s.setStockId(id); s.setMaterialId(MATERIAL); s.setBaseUomId(UNIT.toString());
        s.setWarehouseId(warehouse); s.setAreaId(area); s.setLocationId(location);
        s.setStockQuantity(new BigDecimal("12.5000")); s.setReservedQuantity(new BigDecimal("2.0000"));
        s.setFrozenFlag(0L); s.setVersion(2); s.setDelFlag("0");
        s.setProductionDate(Date.from(NOW.minusSeconds(100))); s.setRecptDate(Date.from(NOW.minusSeconds(50)));
        s.setExpireDate(Date.from(NOW.plusSeconds(3600)));
        return s;
    }

    private static WmWarehouse warehouse(Long id) {
        WmWarehouse w = new WmWarehouse();
        w.setTenantId(TENANT); w.setWarehouseId(id); w.setStatus(1L); w.setDelFlag("0");
        return w;
    }

    private static WmStorageArea area(Long id, Long warehouse) {
        WmStorageArea a = new WmStorageArea();
        a.setTenantId(TENANT); a.setAreaId(id); a.setWarehouseId(warehouse);
        a.setEnableFlag(1L); a.setFrozenFlag(0L); a.setLocationFlag(1L); a.setDelFlag("0");
        return a;
    }

    private static WmStorageLocation location(Long id, Long area, Long warehouse) {
        WmStorageLocation l = new WmStorageLocation();
        l.setTenantId(TENANT); l.setLocationId(id); l.setWarehouseId(warehouse); l.setAreaId(area);
        l.setEnableFlag(1L); l.setFrozenFlag(0L); l.setDelFlag("0");
        return l;
    }

    private static final class Fixture {
        final Connection erpConnection = connection();
        final HikariDataSource erp = source(erpConnection);
        final HikariDataSource master = source(connection());
        final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
        final ErpPurchaseInventorySourceMapper mapper = mock(ErpPurchaseInventorySourceMapper.class);
        final SqlSessionFactory factory;
        final PuRequirementErpTransactionQualification qualification;
        DatabaseClock clock = new DatabaseClock(epoch(NOW), "+08:00");
        List<QuantityColumn> columns = List.of(new QuantityColumn("bd_material", "safety_stock", "decimal", 18, 4),
            new QuantityColumn("wm_material_stock", "stock_quantity", "decimal", 18, 4),
            new QuantityColumn("wm_material_stock", "reserved_quantity", "decimal", 18, 4));
        BdMaterial material = new BdMaterial();
        BdUnitOfMeasure unit = new BdUnitOfMeasure();
        WmMaterialStock stock = stock(91L, 8L, 18L, 28L);
        WmWarehouse warehouse = warehouse(8L);
        WmStorageArea area = area(18L, 8L);
        WmStorageLocation location = location(28L, 18L, 8L);
        List<WmMaterialStock> stocks = List.of(stock);
        List<WmWarehouse> warehouses = List.of(warehouse);
        List<WmStorageArea> areas = List.of(area);
        List<WmStorageLocation> locations = List.of(location);

        Fixture() throws SQLException {
            routing.setPrimary("master"); routing.setStrict(true);
            routing.addDataSource("erp", erp); routing.addDataSource("master", master);
            Configuration configuration = new Configuration(new Environment("离线", new SpringManagedTransactionFactory(), routing));
            configuration.addMapper(PuRequirementCompetitionPoolMapper.class);
            configuration.addMapper(PuRequirementMaterialGuardMapper.class);
            configuration.addMapper(ErpPurchaseInventorySourceMapper.class);
            factory = new DefaultSqlSessionFactory(configuration);
            qualification = spy(new PuRequirementErpTransactionQualification(factory));
            material.setTenantId(TENANT); material.setMaterialId(MATERIAL); material.setBaseUomId(UNIT);
            material.setSafetyStock(new BigDecimal("3.0000")); material.setStatus(1L); material.setDelFlag("0");
            unit.setTenantId(TENANT); unit.setUomId(UNIT); unit.setCategory("QUANTITY"); unit.setIsBase(1L);
            unit.setConversionRate(BigDecimal.ONE); unit.setQuantityScale(4); unit.setConversionVersion("unit-check-1");
            unit.setConversionVerifiedAt(Date.from(NOW.minusSeconds(600))); unit.setStatus(1L); unit.setDelFlag("0");
            when(mapper.selectDatabaseClock()).thenAnswer(i -> clock);
            when(mapper.selectQuantityColumns()).thenAnswer(i -> columns);
            when(mapper.lockMaterial(anyString(), anyLong())).thenAnswer(i -> material);
            when(mapper.lockUnit(anyString(), anyLong())).thenAnswer(i -> unit);
            when(mapper.lockStocks(anyString(), anyLong())).thenAnswer(i -> stocks);
            when(mapper.lockWarehouses(anyString(), anyList())).thenAnswer(i -> warehouses);
            when(mapper.lockAreas(anyString(), anyList())).thenAnswer(i -> areas);
            when(mapper.lockLocations(anyString(), anyList())).thenAnswer(i -> locations);
        }

        ErpPurchaseInventorySourceVerifier reader() {
            return new ErpPurchaseInventorySourceVerifier(mapper, qualification);
        }

        ErpPurchaseInventorySourceVerifier proxy() {
            TransactionInterceptor transactions = new TransactionInterceptor();
            transactions.setTransactionManager(new DataSourceTransactionManager(routing));
            transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            ProxyFactory proxy = new ProxyFactory(reader());
            proxy.setProxyTargetClass(true);
            proxy.addAdvisor(new DynamicDataSourceAnnotationAdvisor(
                new DynamicDataSourceAnnotationInterceptor(true, new DsSpelExpressionProcessor()), DS.class));
            proxy.addAdvice(transactions);
            return (ErpPurchaseInventorySourceVerifier) proxy.getProxy();
        }

        InventorySnapshot read() {
            try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
                return bound(false, () -> proxy().verifyLocked(TENANT, MATERIAL));
            }
        }

        <T> T bound(boolean readOnly, Supplier<T> body) {
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(erp));
            transaction.setReadOnly(readOnly);
            return transaction.execute(status -> {
                Object holder = TransactionSynchronizationManager.getResource(erp);
                assertTrue(holder instanceof ConnectionHolder);
                TransactionSynchronizationManager.bindResource(routing, holder);
                try {
                    return body.get();
                } finally {
                    TransactionSynchronizationManager.unbindResource(routing);
                    status.setRollbackOnly();
                }
            });
        }

        private static HikariDataSource source(Connection connection) throws SQLException {
            HikariDataSource source = mock(HikariDataSource.class);
            when(source.getConnection()).thenReturn(connection);
            return source;
        }

        private static Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            AtomicBoolean auto = new AtomicBoolean(true);
            AtomicBoolean readOnly = new AtomicBoolean(false);
            when(connection.getAutoCommit()).thenAnswer(i -> auto.get());
            when(connection.isReadOnly()).thenAnswer(i -> readOnly.get());
            when(connection.isClosed()).thenReturn(false);
            doAnswer(i -> { auto.set(i.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
            doAnswer(i -> { readOnly.set(i.getArgument(0)); return null; }).when(connection).setReadOnly(anyBoolean());
            return connection;
        }
    }
}
