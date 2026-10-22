package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.*;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class InventoryMonitoringSafetyTest {
    private JdbcDataSource source;
    private JdbcTemplate jdbc;
    private BdMaterialMapper materials;
    private BdUnitOfMeasureMapper units;
    private WmMaterialStockMapper stocks;
    private WmMaterialStockTxMapper transactions;
    private WmStockAlertMapper alerts;
    private WmStockReconciliationMapper reconciliations;

    @BeforeEach void setup() {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:monitor_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        MybatisConfiguration config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.setLogImpl(NoLoggingImpl.class);
        config.setEnvironment(new Environment("库存监测内存回归", new SpringManagedTransactionFactory(), source));
        GlobalConfig global = new GlobalConfig(); global.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0")); global.setSqlInjector(new DefaultSqlInjector()); GlobalConfigUtils.setGlobalConfig(config, global);
        for (Class<?> mapper : List.of(BdMaterialMapper.class, BdUnitOfMeasureMapper.class, WmMaterialStockMapper.class, WmMaterialStockTxMapper.class, WmStockAlertMapper.class, WmStockReconciliationMapper.class)) config.addMapper(mapper);
        for (Class<?> entity : List.of(BdMaterial.class, BdUnitOfMeasure.class, WmMaterialStock.class, WmMaterialStockTx.class, WmStockAlert.class, WmStockReconciliation.class)) createTable(entity);
        var sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        materials = sessions.getMapper(BdMaterialMapper.class); units = sessions.getMapper(BdUnitOfMeasureMapper.class); stocks = sessions.getMapper(WmMaterialStockMapper.class); transactions = sessions.getMapper(WmMaterialStockTxMapper.class); alerts = sessions.getMapper(WmStockAlertMapper.class); reconciliations = sessions.getMapper(WmStockReconciliationMapper.class);
        BdMaterial material = new BdMaterial(); material.setMaterialId(23L); material.setMaterialCode("IF20261004005"); material.setMaterialName("平垫圈 12 A级 镀锌"); material.setStatus(1L); materials.insert(material);
    }
    @AfterEach void cleanup() throws Exception { try (var c = source.getConnection(); var s = c.createStatement()) { s.execute("SHUTDOWN"); } }

    @Test void maximumOnlyThresholdIsIncludedInActualAlertScan() {
        BdMaterial material = materials.selectById(23L); material.setMaxStock(new BigDecimal("10")); materials.updateById(material);
        stock("12", false);
        alertService().checkStockAlerts();
        var found = alerts.selectList(null); assertEquals(1, found.size(), "只配最高库存的物料同样必须扫描"); assertEquals("ABOVE_MAX", found.get(0).getAlertType());
    }
    @Test void activeAlertRefreshesThresholdSnapshotWhenMaterialSettingChanges() {
        BdMaterial material = materials.selectById(23L); material.setSafetyStock(new BigDecimal("8")); materials.updateById(material); stock("5", false);
        alertService().checkStockAlerts();
        material.setSafetyStock(new BigDecimal("10")); materials.updateById(material); alertService().checkStockAlerts();
        var found = alerts.selectList(null); assertEquals(1, found.size()); assertEquals(0, new BigDecimal("10").compareTo(found.get(0).getSafetyStock()), "活动预警阈值必须与本次实际扫描保持一致");
    }
    @Test void frozenStockRemainsPartOfAccountingReconciliation() {
        stock("5", true); transaction("5"); reconciliationService(reconciliations).reconcileByMaterial(23L);
        var found = reconciliations.selectList(null); assertEquals(1, found.size()); assertEquals("MATCH", found.get(0).getStatus(), "冻结限制可用量，不能把账面库存抹去"); assertEquals(0, new BigDecimal("5").compareTo(found.get(0).getBookQty()));
    }
    @Test void nativeFourDecimalDifferenceMustNotBeReportedAsMatched() {
        stock("5.0001", false); transaction("5"); reconciliationService(reconciliations).reconcileByMaterial(23L);
        assertEquals("DIFF", reconciliations.selectList(null).get(0).getStatus(), "数据库支持的0.0001差异也必须可见");
    }
    @Test void fullReconciliationFindsLedgerWhoseStockRowIsMissing() {
        transaction("5"); WmStockReconciliationMapper sink = spy(reconciliations);
        doAnswer(i -> { for (Object row : (Collection<?>) i.getArgument(0)) reconciliations.insert((WmStockReconciliation)row); return true; }).when(sink).insertBatch(anyCollection());
        reconciliationService(sink).reconcile();
        var found = reconciliations.selectList(null); assertEquals(1, found.size(), "只有流水而没有库存行也必须核对"); assertEquals("DIFF", found.get(0).getStatus()); assertEquals(0, new BigDecimal("-5").compareTo(found.get(0).getDiffQty()));
    }
    private WmStockAlertServiceImpl alertService() { return new WmStockAlertServiceImpl(alerts, materials, units, stocks); }
    private StockReconciliationServiceImpl reconciliationService(WmStockReconciliationMapper mapper) { return new StockReconciliationServiceImpl(mapper, stocks, transactions, materials, units); }
    private void stock(String quantity, boolean frozen) { WmMaterialStock s = new WmMaterialStock(); s.setStockId(9L); s.setMaterialId(23L); s.setWarehouseId(1L); s.setStockQuantity(new BigDecimal(quantity)); s.setReservedQuantity(BigDecimal.ZERO); s.setFrozenFlag(frozen ? 1L : 0L); stocks.insert(s); }
    private void transaction(String quantity) { WmMaterialStockTx tx = new WmMaterialStockTx(); tx.setTxId(10L); tx.setMaterialId(23L); tx.setMaterialStockId(9L); tx.setWarehouseId(1L); tx.setTxType("MISC_IN"); tx.setTxFlag(1); tx.setTxQuantity(new BigDecimal(quantity)); transactions.insert(tx); }
    private void createTable(Class<?> entity) { var t = TableInfoHelper.getTableInfo(entity); List<String> columns = new ArrayList<>(); columns.add(t.getKeyColumn() + " BIGINT PRIMARY KEY"); t.getFieldList().forEach(f -> { Class<?> type = f.getPropertyType(); String sql = type == BigDecimal.class ? "DECIMAL(20,4)" : Date.class.isAssignableFrom(type) ? "TIMESTAMP" : Number.class.isAssignableFrom(type) ? "BIGINT" : "VARCHAR(500)"; columns.add(f.getColumn() + " " + sql + ("del_flag".equals(f.getColumn()) ? " DEFAULT '0'" : "")); }); jdbc.execute("CREATE TABLE " + t.getTableName() + "(" + String.join(",", columns) + ")"); }
}
