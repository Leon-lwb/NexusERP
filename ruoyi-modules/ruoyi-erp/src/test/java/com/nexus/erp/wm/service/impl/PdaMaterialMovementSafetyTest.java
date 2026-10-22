package com.nexus.erp.wm.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.controller.WmMaterialHandoverController;
import com.nexus.erp.wm.controller.WmMaterialReturnController;
import com.nexus.erp.wm.controller.serial.WmSerialBusinessAdapter;
import com.nexus.erp.wm.controller.serial.WmSerialBusinessAspect;
import com.nexus.erp.wm.controller.serial.WmSerialBusinessStore;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmMaterialHandoverBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReturnBo;
import com.nexus.erp.wm.domain.bo.pda.PdaHandoverConfirmBo;
import com.nexus.erp.wm.domain.bo.pda.PdaReturnConfirmBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.WmSerialInventoryRepository;
import com.nexus.erp.wm.service.WmSerialInventoryService;
import com.nexus.erp.wm.service.WmSerialSelectionService;
import com.nexus.erp.wm.strategy.impl.MiscIssueStrategy;
import com.nexus.erp.wm.strategy.impl.MiscRecptStrategy;
import com.nexus.erp.wm.trace.ErpTraceContext;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/** 真实Mapper、租户插件及事务；库存网关用同事务效果表记录，不连接业务数据库。 */
class PdaMaterialMovementSafetyTest {
    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private WmMaterialReturnMapper returns;
    private WmMaterialReturnLineMapper returnLines;
    private WmMaterialHandoverMapper handovers;
    private WmMaterialHandoverLineMapper handoverLines;
    private WmStorageLocationMapper locations;
    private WmWarehouseMapper warehouses;
    private IWmStockOperationService posting;
    private SqlSessionTemplate sessions;

    @BeforeEach
    void setup() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:movement_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(dataSource);
        MybatisConfiguration config = new MybatisConfiguration();
        config.setEnvironment(new Environment("内存库存作业", new SpringManagedTransactionFactory(), dataSource));
        config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(NoLoggingImpl.class);
        GlobalConfig global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        global.setSqlInjector(new DefaultSqlInjector());
        GlobalConfigUtils.setGlobalConfig(config, global);
        MybatisPlusInterceptor plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() { return new StringValue("000000"); }
        }));
        config.addInterceptor(plugin);
        for (Class<?> mapper :
                List.of(
                        WmMaterialReturnMapper.class,
                        WmMaterialReturnLineMapper.class,
                        WmMaterialHandoverMapper.class,
                        WmMaterialHandoverLineMapper.class,
                        WmStorageLocationMapper.class,
                        WmWarehouseMapper.class,
                        WmMaterialStockMapper.class,
                        WmMaterialStockTxMapper.class,
                        BdMaterialMapper.class)) config.addMapper(mapper);
        for (Class<?> entity :
                List.of(
                        WmMaterialReturn.class,
                        WmMaterialReturnLine.class,
                        WmMaterialHandover.class,
                        WmMaterialHandoverLine.class,
                        WmStorageLocation.class,
                        WmWarehouse.class,
                        WmMaterialStock.class,
                        WmMaterialStockTx.class,
                        BdMaterial.class)) createTable(entity);
        sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        returns = sessions.getMapper(WmMaterialReturnMapper.class);
        returnLines = sessions.getMapper(WmMaterialReturnLineMapper.class);
        handovers = sessions.getMapper(WmMaterialHandoverMapper.class);
        handoverLines = sessions.getMapper(WmMaterialHandoverLineMapper.class);
        locations = sessions.getMapper(WmStorageLocationMapper.class);
        warehouses = sessions.getMapper(WmWarehouseMapper.class);
        jdbc.execute("CREATE TABLE posting_effect(id BIGINT AUTO_INCREMENT PRIMARY KEY, direction VARCHAR(10))");
        posting = mock(IWmStockOperationService.class);
        doAnswer(i -> { jdbc.update("INSERT INTO posting_effect(direction) VALUES('IN')"); return null; })
            .when(posting).postMiscRecpt(any(), any(), any());
        doAnswer(i -> { jdbc.update("INSERT INTO posting_effect(direction) VALUES('OUT')"); return null; })
            .when(posting).postMiscIssue(any(), any());
        WmWarehouse wh = new WmWarehouse(); wh.setWarehouseId(2L); wh.setWarehouseCode("WH002"); wh.setWarehouseName("半成品仓库"); warehouses.insert(wh);
        WmStorageLocation loc = new WmStorageLocation(); loc.setLocationId(20L); loc.setWarehouseId(2L); loc.setAreaId(5L); loc.setLocationCode("L020"); locations.insert(loc);
    }

    @AfterEach
    void cleanup() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
    }

    @ParameterizedTest @ValueSource(strings = {"RETURN", "HANDOVER"})
    void concurrentCompletionPostsOnlyOnceAcrossDifferentRequests(String kind) throws Exception {
        seed(kind, "2", "1");
        CountDownLatch oldHeaderReads = new CountDownLatch(2);
        WmMaterialReturnMapper returnSpy = spy(returns);
        doAnswer(i -> { WmMaterialReturn h = returns.selectById((Long)i.getArgument(0)); oldHeaderReads.countDown(); assertTrue(oldHeaderReads.await(3, TimeUnit.SECONDS)); return h; })
            .when(returnSpy).selectById(any());
        WmMaterialHandoverMapper handoverSpy = spy(handovers);
        doAnswer(i -> { WmMaterialHandover h = handovers.selectById((Long)i.getArgument(0)); oldHeaderReads.countDown(); assertTrue(oldHeaderReads.await(3, TimeUnit.SECONDS)); return h; })
            .when(handoverSpy).selectById(any());
        var returnService = proxy(new PdaReturnServiceImpl(returnSpy, returnLines, posting));
        var handoverService = proxy(new PdaHandoverServiceImpl(handoverSpy, handoverLines, posting, locations, warehouses));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Callable<PdaActionResultVo> action = () -> {
                try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
                    login.when(LoginHelper::getUserId).thenReturn(1L);
                    login.when(LoginHelper::getUsername).thenReturn("仓储管理员");
                    return "RETURN".equals(kind) ? returnService.finish(11L) : handoverService.sign(11L);
                }
            };
            Future<PdaActionResultVo> first = workers.submit(action);
            Future<PdaActionResultVo> second = workers.submit(action);
            assertTrue(first.get(10, TimeUnit.SECONDS).getOk());
            assertTrue(second.get(10, TimeUnit.SECONDS).getOk());
            assertEquals("RETURN".equals(kind) ? 1 : 2, jdbc.queryForObject("SELECT COUNT(*) FROM posting_effect", Integer.class),
                "不同用户或不同幂等键的完成请求也只能产生一次库存效果");
        } finally { workers.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(strings = {"RETURN", "HANDOVER"})
    void zeroPlanDoesNotAllowUnlimitedConfirmation(String kind) {
        seed(kind, "0", "0");
        assertFalse(confirm(kind, BigDecimal.ONE).getOk(), "计划为零的历史异常行不能绕过数量上限");
        assertEquals(0, actual(kind).signum());
    }

    @ParameterizedTest @ValueSource(strings = {"RETURN", "HANDOVER"})
    void invalidScanQuantityCannotReduceActualQuantity(String kind) {
        seed(kind, "2", "1");
        assertFalse(confirm(kind, new BigDecimal("-1")).getOk());
        assertEquals(0, BigDecimal.ONE.compareTo(actual(kind)));
    }

    @ParameterizedTest @ValueSource(strings = {"RETURN", "HANDOVER"})
    void corruptedActualQuantityCannotBePosted(String kind) {
        seed(kind, "2", "3");
        PdaActionResultVo result = "RETURN".equals(kind)
            ? proxy(new PdaReturnServiceImpl(returns, returnLines, posting)).finish(11L)
            : proxy(new PdaHandoverServiceImpl(handovers, handoverLines, posting, locations, warehouses)).sign(11L);
        assertFalse(result.getOk(), "完成时必须再次校验实际数量不超过计划，不能信任历史行");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posting_effect", Integer.class));
    }

    @ParameterizedTest @ValueSource(strings = {"RETURN", "HANDOVER"})
    void failedPostingRollsBackStateAndEveryInventoryEffect(String kind) {
        seed(kind, "2", "1");
        doAnswer(i -> { jdbc.update("INSERT INTO posting_effect(direction) VALUES('IN')"); throw new IllegalStateException("库存持久化失败"); })
            .when(posting).postMiscRecpt(any(), any(), any());
        assertThrows(IllegalStateException.class, () -> {
            if ("RETURN".equals(kind)) proxy(new PdaReturnServiceImpl(returns, returnLines, posting)).finish(11L);
            else proxy(new PdaHandoverServiceImpl(handovers, handoverLines, posting, locations, warehouses)).sign(11L);
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posting_effect", Integer.class));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM " + ("RETURN".equals(kind) ? "wm_material_return WHERE return_id=11" : "wm_material_handover WHERE handover_id=11"), String.class));
    }

    @Test
    void handoverBindsSourceWarehouseAndPreservesTargetArea() {
        seed("HANDOVER", "2", "1");
        assertTrue(proxy(new PdaHandoverServiceImpl(handovers, handoverLines, posting, locations, warehouses)).sign(11L).getOk());
        var outgoing = ArgumentCaptor.forClass(WmMiscIssueLine.class);
        verify(posting).postMiscIssue(outgoing.capture(), any());
        var json = new ObjectMapper().valueToTree(outgoing.getValue());
        assertEquals(1L, json.path("warehouseId").asLong(), "交接出库只能分配单头指定仓库，不能按全仓FIFO扣库存");
        var incoming = ArgumentCaptor.forClass(WmMiscRecpt.class);
        verify(posting).postMiscRecpt(any(), incoming.capture(), any());
        assertEquals(5L, incoming.getValue().getAreaId(), "线边库存必须保留真实目标库区");
    }

    @ParameterizedTest @ValueSource(strings = {"RETURN", "HANDOVER"})
    void realStrategiesAndSerialAspectUseActualMovementSourceWithoutSyntheticMiscRows(String kind) {
        var stocks = sessions.getMapper(WmMaterialStockMapper.class);
        var txs = sessions.getMapper(WmMaterialStockTxMapper.class);
        var materials = sessions.getMapper(BdMaterialMapper.class);
        var material = new BdMaterial();
        material.setMaterialId(23L);
        material.setStatus(1L);
        material.setIsBatchManage(0L);
        material.setIsSerialManage(0L);
        material.setTrackingStage("AUDIT");
        material.setTrackingVersion(1L);
        materials.insert(material);
        WmMaterialStock stock = new WmMaterialStock(); stock.setStockId(9L); stock.setMaterialId(23L); stock.setWarehouseId(1L); stock.setLocationId(1L); stock.setStockQuantity(new BigDecimal("4")); stock.setReservedQuantity(BigDecimal.ZERO); stock.setFrozenFlag(0L); stock.setVersion(0); stocks.insert(stock);
        seed(kind, "2", "1");
        var context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("000000");
        var json = new ObjectMapper();
        var repository = new WmSerialInventoryRepository(jdbc, stocks, txs, context, json);
        var serialTarget = new WmSerialInventoryService(repository);
        var selections = new WmSerialSelectionService(repository, serialTarget);
        var store = new WmSerialBusinessStore(jdbc, repository, selections);
        var adapter = new WmSerialBusinessAdapter(store);
        var aspect = new WmSerialBusinessAspect(adapter, json);
        var serialFactory = new AspectJProxyFactory(serialTarget);
        serialFactory.setProxyTargetClass(true);
        serialFactory.addAspect(aspect);
        var serial = (WmSerialInventoryService) serialFactory.getProxy();
        var valuation = mock(IInventoryValuationService.class);
        when(valuation.issueStockCost(any(), any())).thenReturn(BigDecimal.ZERO);
        var in = new MiscRecptStrategy(txs, stocks, valuation, materials);
        var out = new MiscIssueStrategy(txs, stocks, valuation);
        ReflectionTestUtils.setField(in, "serialInventory", serial);
        ReflectionTestUtils.setField(out, "serialInventory", serial);
        doAnswer(i -> { in.execute(i.getArgument(0), i.getArgument(1), i.getArgument(2)); return null; }).when(posting).postMiscRecpt(any(), any(), any());
        doAnswer(i -> { out.execute(i.getArgument(0), i.getArgument(1)); return null; }).when(posting).postMiscIssue(any(), any());
        var returnTarget = new PdaReturnServiceImpl(returns, returnLines, posting);
        ReflectionTestUtils.setField(returnTarget, "serialInventory", serial);
        assertDoesNotThrow(() -> assertTrue(("RETURN".equals(kind) ? proxy(returnTarget).finish(11L) : proxy(new PdaHandoverServiceImpl(handovers, handoverLines, posting, locations, warehouses)).sign(11L)).getOk()), "真实SN适配不能把退料或交接ID当成不存在的杂项单");
        var ledger = txs.selectList(null); assertEquals("RETURN".equals(kind) ? 1 : 2, ledger.size());
        assertTrue(ledger.stream().allMatch(tx -> ("WM_MATERIAL_" + kind).equals(tx.getSourceDocType()) && Long.valueOf(11).equals(tx.getSourceDocId())));
        assertEquals(0, new BigDecimal("RETURN".equals(kind) ? "5" : "4").compareTo(stocks.selectList(null).stream().map(WmMaterialStock::getStockQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @ParameterizedTest @ValueSource(strings = {"RETURN", "HANDOVER"})
    void pcCreateCannotAssignLinesToAnotherTenantOrForgeCreator(String kind) {
        var code = mock(AutoCodeUtil.class);
        when(code.genSerialCode(any(), any())).thenReturn("DOC202610220001");
        var helper =
                new WmServiceHelper(
                        code,
                        mock(IWfProcessService.class),
                        mock(WfMyBusinessServiceImpl.class),
                        mock(FlowableProcInsIdHelper.class));
        Long id;
        String table;
        if ("RETURN".equals(kind)) {
            var bo = new WmMaterialReturnBo();
            bo.setWarehouseId(2L);
            bo.setWarehouseCode("WH002");
            bo.setReturnType("GOOD");
            var line = new WmMaterialReturnLine(); line.setTenantId("000001"); line.setCreateBy(9999L); line.setMaterialId(23L); line.setLocationId(20L); line.setQuantity(BigDecimal.ONE); bo.setLines(List.of(line));
            new WmMaterialReturnController(returns, returnLines, helper).add(bo);
            id = line.getLineId();
            table = "wm_material_return_line";
        } else {
            var bo = new WmMaterialHandoverBo();
            bo.setWarehouseId(1L);
            bo.setWarehouseCode("WH001");
            var line = new WmMaterialHandoverLine(); line.setTenantId("000001"); line.setCreateBy(9999L); line.setMaterialId(23L); line.setToLocationId(20L); line.setQuantity(BigDecimal.ONE); bo.setLines(List.of(line));
            new WmMaterialHandoverController(handovers, handoverLines, locations, helper).add(bo);
            id = line.getLineId();
            table = "wm_material_handover_line";
        }
        assertEquals("000000", jdbc.queryForObject("SELECT tenant_id FROM " + table + " WHERE line_id=?", String.class, id), "请求体tenantId不能越出租户边界");
        assertNotEquals(9999L, jdbc.queryForObject("SELECT create_by FROM " + table + " WHERE line_id=?", Long.class, id), "审计身份不能由客户端伪造");
    }

    private PdaActionResultVo confirm(String kind, BigDecimal quantity) {
        if ("RETURN".equals(kind)) {
            PdaReturnConfirmBo bo = new PdaReturnConfirmBo(); bo.setLineId(12L); bo.setQty(quantity);
            return proxy(new PdaReturnServiceImpl(returns, returnLines, posting)).confirmLine(11L, bo);
        }
        PdaHandoverConfirmBo bo = new PdaHandoverConfirmBo(); bo.setLineId(12L); bo.setQty(quantity);
        return proxy(new PdaHandoverServiceImpl(handovers, handoverLines, posting, locations, warehouses)).confirmLine(11L, bo);
    }

    private BigDecimal actual(String kind) {
        return jdbc.queryForObject("SELECT actual_qty FROM " + ("RETURN".equals(kind) ? "wm_material_return_line" : "wm_material_handover_line") + " WHERE line_id=12", BigDecimal.class);
    }

    private void seed(String kind, String planned, String actual) {
        if ("RETURN".equals(kind)) {
            WmMaterialReturn h = new WmMaterialReturn(); h.setReturnId(11L); h.setStatus("DRAFT"); h.setReturnType("GOOD"); h.setWarehouseId(2L); returns.insert(h);
            WmMaterialReturnLine l = new WmMaterialReturnLine(); l.setLineId(12L); l.setReturnId(11L); l.setMaterialId(23L); l.setLocationId(20L); l.setQuantity(new BigDecimal(planned)); l.setActualQty(new BigDecimal(actual)); returnLines.insert(l);
        } else {
            WmMaterialHandover h = new WmMaterialHandover(); h.setHandoverId(11L); h.setStatus("DRAFT"); h.setWarehouseId(1L); handovers.insert(h);
            WmMaterialHandoverLine l = new WmMaterialHandoverLine(); l.setLineId(12L); l.setHandoverId(11L); l.setMaterialId(23L); l.setFromLocationId(1L); l.setToLocationId(20L); l.setQuantity(new BigDecimal(planned)); l.setActualQty(new BigDecimal(actual)); handoverLines.insert(l);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    private void createTable(Class<?> entity) {
        var table = TableInfoHelper.getTableInfo(entity);
        List<String> columns = new ArrayList<>();
        columns.add(table.getKeyColumn() + " BIGINT PRIMARY KEY");
        table.getFieldList().forEach(f -> {
            Class<?> type = f.getPropertyType();
            String sqlType = type == BigDecimal.class ? "DECIMAL(20,4)" : Date.class.isAssignableFrom(type) ? "TIMESTAMP" : Number.class.isAssignableFrom(type) ? "BIGINT" : "VARCHAR(500)";
            columns.add(f.getColumn() + " " + sqlType + ("del_flag".equals(f.getColumn()) ? " DEFAULT '0'" : ""));
        });
        jdbc.execute("CREATE TABLE " + table.getTableName() + "(" + String.join(",", columns) + ")");
    }
}
