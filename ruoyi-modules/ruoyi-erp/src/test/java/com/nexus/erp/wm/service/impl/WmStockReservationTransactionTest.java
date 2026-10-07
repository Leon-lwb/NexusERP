package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 私有 H2、真实生产 Mapper 和租户插件的预留事务回归。
 * 非空仓库列沿用实际 MySQL 合同；不连接本地业务库，不代表 MySQL 并发或运行态已验收。
 */
class WmStockReservationTransactionTest {
    private static final String TENANT = "000000";
    private static final String SOURCE = "WM_PRODUCTION_ISSUE";
    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionTemplate sessions;
    private WmMaterialStockMapper stocks;
    private WmMaterialStockTxMapper transactions;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach
    void setup() {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reservation_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("仅内存预留回归", new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        GlobalConfig global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        global.setSqlInjector(new DefaultSqlInjector());
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        TenantProperties properties = new TenantProperties();
        properties.setExcludes(List.of());
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties)));
        configuration.addInterceptor(plugins);
        configuration.addMapper(WmMaterialStockMapper.class);
        configuration.addMapper(WmMaterialStockTxMapper.class);
        createTable(WmMaterialStock.class);
        createTable(WmMaterialStockTx.class);
        sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        stocks = sessions.getMapper(WmMaterialStockMapper.class);
        transactions = sessions.getMapper(WmMaterialStockTxMapper.class);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "预留入口不得泄漏事务状态");
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty(), "预留入口不得泄漏连接或会话");
            try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        } finally {
            tenant.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reserveAndReleasePreserveNativeDimensionsAndNeutralStock(boolean managedBatch) {
        WmMaterialStock stock = stock(managedBatch);
        assertEquals(1, stocks.insert(stock));
        var service = service(transactions);

        assertEquals(0, new BigDecimal("4").compareTo(assertDoesNotThrow(
            () -> service.reserve(23L, 3L, new BigDecimal("4"), SOURCE, 41L, 42L),
            "合法实际库存预留必须携带非空仓库维度而不是触发插入异常")));
        WmMaterialStock reserved = stocks.selectById(9L);
        assertEquals(new BigDecimal("4.0000"), reserved.getReservedQuantity(), "预留四件必须真实落库");
        assertEquals(new BigDecimal("10.0000"), reserved.getStockQuantity(), "预留不得改变账面库存数量");
        assertEquals(new BigDecimal("1014.0000"), reserved.getTotalCost(), "预留不得改变库存成本");
        assertDimensions(stock, transactions.selectList(null).get(0), "RESERVE");

        assertEquals(0, new BigDecimal("4").compareTo(service.unreserve(SOURCE, 41L, 42L)));
        WmMaterialStock released = stocks.selectById(9L);
        assertEquals(new BigDecimal("0.0000"), released.getReservedQuantity(), "释放四件后预留必须归零");
        assertEquals(new BigDecimal("10.0000"), released.getStockQuantity(), "释放不得改变账面库存数量");
        assertEquals(new BigDecimal("1014.0000"), released.getTotalCost(), "释放不得改变库存成本");
        var rows = transactions.selectList(null);
        assertEquals(2, rows.size(), "预留与释放各自必须只有一条持久流水");
        assertDimensions(stock, rows.stream().filter(tx -> "UNRESERVE".equals(tx.getTxType())).findFirst().orElseThrow(), "UNRESERVE");
        assertEquals(0, BigDecimal.ZERO.compareTo(service.unreserve(SOURCE, 41L, 42L)), "已释放来源再次释放应为零");
        assertEquals(2, transactions.selectList(null).size(), "重复释放不得再写流水");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVE", "UNRESERVE"})
    void zeroTransactionInsertRollsBackOriginalReservationChange(String action) {
        seed(action);
        List<Map<String, Object>> before = snapshot();
        WmMaterialStockTxMapper failed = spy(transactions);
        doReturn(0).when(failed).insert(any(WmMaterialStockTx.class));
        assertThrows(ServiceException.class, () -> execute(service(failed), action), "流水写入零行必须明确失败");
        assertEquals(before, snapshot(), "流水写入零行必须回滚同一原事务的预留量和版本");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVE", "UNRESERVE"})
    void throwingTransactionInsertRollsBackOriginalReservationChange(String action) {
        seed(action);
        List<Map<String, Object>> before = snapshot();
        WmMaterialStockTxMapper failed = spy(transactions);
        doThrow(new IllegalStateException("模拟库存流水持久化失败")).when(failed).insert(any(WmMaterialStockTx.class));
        assertThrows(IllegalStateException.class, () -> execute(service(failed), action), "数据库异常必须保留失败语义");
        assertEquals(before, snapshot(), "持久化异常必须回滚原库存预留量和版本");
    }

    @Test
    void actualTenantPluginPreventsReservingOtherTenantStock() {
        assertEquals(1, stocks.insert(stock(false)));
        jdbc.update("INSERT INTO wm_material_stock(stock_id,tenant_id,material_id,warehouse_id,stock_quantity,reserved_quantity,frozen_flag,version,del_flag) VALUES(99,'000001',23,3,20,0,0,1,'0')");
        assertEquals(0, new BigDecimal("4").compareTo(service(transactions).reserve(23L, 3L, new BigDecimal("4"), SOURCE, 41L, 42L)));
        assertEquals(new BigDecimal("0.0000"), jdbc.queryForObject("SELECT reserved_quantity FROM wm_material_stock WHERE stock_id=99", BigDecimal.class), "实际租户插件不能预留其他租户库存");
        assertEquals(1, transactions.selectList(null).size(), "仅本租户唯一真实库存生成预留流水");
    }

    private void seed(String action) {
        WmMaterialStock stock = stock(false);
        if ("UNRESERVE".equals(action)) stock.setReservedQuantity(new BigDecimal("4"));
        assertEquals(1, stocks.insert(stock));
        if ("UNRESERVE".equals(action)) {
            WmMaterialStockTx original = new WmMaterialStockTx();
            original.setTenantId(TENANT);
            original.setTxType("RESERVE");
            original.setMaterialId(23L);
            original.setMaterialStockId(9L);
            original.setWarehouseId(3L);
            original.setTxQuantity(new BigDecimal("4"));
            original.setSourceDocType(SOURCE);
            original.setSourceDocId(41L);
            original.setSourceDocLineId(42L);
            assertEquals(1, transactions.insert(original));
        }
    }

    private void execute(WmStockReservationServiceImpl service, String action) {
        if ("RESERVE".equals(action)) service.reserve(23L, 3L, new BigDecimal("4"), SOURCE, 41L, 42L);
        else service.unreserve(SOURCE, 41L, 42L);
    }

    private List<Map<String, Object>> snapshot() {
        // JdbcTemplate 每次在事务外取得连接，断言真实已提交库存与全部流水，而不是模拟对象状态。
        List<Map<String, Object>> snapshot = new ArrayList<>(jdbc.queryForList("SELECT * FROM wm_material_stock ORDER BY stock_id"));
        snapshot.addAll(jdbc.queryForList("SELECT * FROM wm_material_stock_tx ORDER BY tx_id"));
        return snapshot;
    }

    private WmStockReservationServiceImpl service(WmMaterialStockTxMapper txMapper) {
        ProxyFactory proxy = new ProxyFactory(new WmStockReservationServiceImpl(stocks, txMapper));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        return (WmStockReservationServiceImpl) proxy.getProxy();
    }

    private WmMaterialStock stock(boolean batch) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(9L); stock.setTenantId(TENANT);
        stock.setMaterialId(23L); stock.setMaterialCode("IF20260914023");
        stock.setMaterialName("不锈钢手术剪"); stock.setMaterialSpec("标准型");
        stock.setBaseUomId("26"); stock.setBaseUomName("件");
        stock.setWarehouseId(3L); stock.setWarehouseCode("WH003"); stock.setWarehouseName("成品仓");
        stock.setAreaId(9L); stock.setAreaCode("A0009"); stock.setAreaName("成品一区");
        stock.setLocationId(28L); stock.setLocationCode("L028"); stock.setLocationName("成品一号储位");
        stock.setBatchId(batch ? 51L : null); stock.setBatchCode(batch ? "B202610070001" : null);
        stock.setSnId(batch ? 61L : null); stock.setSnCode(batch ? "SN202610070001" : null);
        stock.setRecptDate(new Date(1791331200000L));
        stock.setStockQuantity(new BigDecimal("10")); stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setFrozenFlag(0L); stock.setVersion(1); stock.setDelFlag("0");
        stock.setUnitCost(new BigDecimal("101.4")); stock.setTotalCost(new BigDecimal("1014"));
        return stock;
    }

    private void assertDimensions(WmMaterialStock stock, WmMaterialStockTx tx, String type) {
        assertAll(
            () -> assertEquals(stock.getTenantId(), tx.getTenantId(), "流水租户必须来自真实库存"),
            () -> assertEquals(stock.getStockId(), tx.getMaterialStockId(), "流水必须保留真实库存主键"),
            () -> assertEquals(stock.getMaterialId(), tx.getMaterialId(), "流水物料必须来自真实库存"),
            () -> assertEquals(stock.getMaterialCode(), tx.getMaterialCode(), "物料编码不得丢失"),
            () -> assertEquals(stock.getMaterialName(), tx.getMaterialName(), "物料名称不得丢失"),
            () -> assertEquals(stock.getMaterialSpec(), tx.getMaterialSpec(), "规格不得丢失"),
            () -> assertEquals(stock.getBaseUomId(), tx.getBaseUomId(), "基本单位归属不得丢失"),
            () -> assertEquals(stock.getBaseUomName(), tx.getBaseUomName(), "基本单位名称不得丢失"),
            () -> assertEquals(stock.getWarehouseId(), tx.getWarehouseId(), "非空仓库维度不得丢失"),
            () -> assertEquals(stock.getWarehouseCode(), tx.getWarehouseCode(), "仓库编码不得丢失"),
            () -> assertEquals(stock.getWarehouseName(), tx.getWarehouseName(), "仓库名称不得丢失"),
            () -> assertEquals(stock.getAreaId(), tx.getAreaId(), "库区不得丢失"),
            () -> assertEquals(stock.getAreaCode(), tx.getAreaCode(), "库区编码不得丢失"),
            () -> assertEquals(stock.getAreaName(), tx.getAreaName(), "库区名称不得丢失"),
            () -> assertEquals(stock.getLocationId(), tx.getLocationId(), "储位不得丢失"),
            () -> assertEquals(stock.getLocationCode(), tx.getLocationCode(), "储位编码不得丢失"),
            () -> assertEquals(stock.getLocationName(), tx.getLocationName(), "储位名称不得丢失"),
            () -> assertEquals(stock.getBatchId(), tx.getBatchId(), "批次主键及合法空值不得伪造"),
            () -> assertEquals(stock.getBatchCode(), tx.getBatchCode(), "批次编码及合法空值不得伪造"),
            () -> assertEquals(stock.getSnId(), tx.getSnId(), "序列号主键及合法空值不得伪造"),
            () -> assertEquals(stock.getSnCode(), tx.getSnCode(), "序列号及合法空值不得伪造"),
            () -> assertEquals(stock.getRecptDate(), tx.getRecptDate(), "实际到货日期不得丢失"),
            () -> assertEquals(type, tx.getTxType()),
            () -> assertEquals(0, tx.getTxFlag(), "预留释放流水必须数量中性"),
            () -> assertEquals(new BigDecimal("4.0000"), tx.getTxQuantity(), "预留释放均保留正数净额"),
            () -> assertEquals(new BigDecimal("10.0000"), tx.getBeforeQuantity()),
            () -> assertEquals(tx.getBeforeQuantity(), tx.getAfterQuantity(), "预留释放不能伪造出入库"),
            () -> assertEquals(SOURCE, tx.getSourceDocType()),
            () -> assertEquals(41L, tx.getSourceDocId()),
            () -> assertEquals(42L, tx.getSourceDocLineId())
        );
    }

    private void createTable(Class<?> entity) {
        // 从真实实体展开完整映射字段；仅固定现场已核实的非空主键、物料和仓库列合同。
        TableInfo info = TableInfoHelper.getTableInfo(entity);
        List<String> columns = new ArrayList<>();
        columns.add(info.getKeyColumn() + " BIGINT PRIMARY KEY");
        info.getFieldList().forEach(field -> {
            Class<?> type = field.getPropertyType();
            String sqlType = type == BigDecimal.class ? "DECIMAL(20,4)" :
                Date.class.isAssignableFrom(type) ? "TIMESTAMP" :
                Number.class.isAssignableFrom(type) ? "BIGINT" : "VARCHAR(500)";
            String suffix = List.of("material_id", "warehouse_id", "material_stock_id", "tx_type").contains(field.getColumn()) ? " NOT NULL" : "";
            if ("del_flag".equals(field.getColumn())) suffix += " DEFAULT '0'";
            columns.add(field.getColumn() + " " + sqlType + suffix);
        });
        jdbc.execute("CREATE TABLE " + info.getTableName() + "(" + String.join(",", columns) + ")");
    }
}
