package com.nexus.erp.wm;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
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
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.ProductionCompletionPostingMapper;
import com.nexus.erp.prd.service.ProductionCompletionPostingService;
import com.nexus.erp.prd.service.impl.PrdBackflushServiceImpl;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.vo.PutawayLocation;
import com.nexus.erp.wm.mapper.WmProductionRecptGuardMapper;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import com.nexus.erp.wm.service.WmProductionRecptWriteGuard;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 私有内存 H2 中证明真实生产入库实体、项目配置、Mapper 删除 SQL 与两层守卫的兼容关系。
 * 只模拟线程租户及下游倒冲、执行动作；TableInfo、租户插件、读取和 Spring 事务均为真实实现。
 * 不启动应用、不连接现有数据库，也不代表 MySQL 现场或运行中服务已加载修复。
 */
class WmProductionRecptDeleteFlagCompatibilityTest {
    private static final String TENANT = "000000";
    private static final long WORK_ORDER_ID = 201L;
    private static final long RETIRED_RECEIPT_ID = 101L;
    private static final long POSTED_RECEIPT_ID = 102L;
    private static final BigDecimal QUANTITY = new BigDecimal("4.0000");

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private MockedStatic<TenantHelper> tenant;
    private TableInfo tableInfo;
    private SqlRecorder sqlRecorder;
    private WmProductionRecptMapper receiptMapper;
    private WmProductionRecptGuardMapper guardMapper;
    private ProductionCompletionPostingMapper postingMapper;
    private WmProductionRecptWriteGuard guard;
    private DataSourceTransactionManager transactionManager;
    private TransactionTemplate transaction;
    private PrdBackflushServiceImpl backflush;
    private IWmProductionRecptService receipts;
    private IPutawayStrategyService putaway;
    private ProductionCompleteMessage message;

    @BeforeEach
    void setUp() {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        DynamicDataSourceContextHolder.push("erp");
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:production_receipt_delete_" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(transactionManager);

        // 删除值来自项目实际公共配置，而不是测试自行把默认值硬设为一。
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("common-mybatis.yml"));
        Properties properties = yaml.getObject();
        assertNotNull(properties, "必须加载项目实际公共 MyBatis 配置");
        String deleted = properties.getProperty("mybatis-plus.global-config.dbConfig.logicDeleteValue");
        String active = properties.getProperty("mybatis-plus.global-config.dbConfig.logicNotDeleteValue");
        assertNotNull(deleted, "项目必须明确配置默认删除值");
        assertNotNull(active, "项目必须明确配置有效行标记");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setEnvironment(new Environment("仅私有内存回归",
            new SpringManagedTransactionFactory(), dataSource));
        GlobalConfig global = new GlobalConfig();
        global.setBanner(false);
        global.setDbConfig(new GlobalConfig.DbConfig()
            .setLogicDeleteValue(deleted).setLogicNotDeleteValue(active));
        global.setSqlInjector(new DefaultSqlInjector());
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        sqlRecorder = new SqlRecorder();
        configuration.addInterceptor(sqlRecorder);
        TenantProperties tenantProperties = new TenantProperties();
        tenantProperties.setExcludes(List.of());
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new PlusTenantLineHandler(tenantProperties)));
        configuration.addInterceptor(plugins);
        configuration.addMapper(WmProductionRecptMapper.class);
        configuration.addMapper(WmProductionRecptGuardMapper.class);
        configuration.addMapper(ProductionCompletionPostingMapper.class);
        tableInfo = TableInfoHelper.getTableInfo(WmProductionRecpt.class);
        assertNotNull(tableInfo, "必须由真实生产实体初始化 TableInfo");
        SqlSessionTemplate sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        receiptMapper = sessions.getMapper(WmProductionRecptMapper.class);
        guardMapper = sessions.getMapper(WmProductionRecptGuardMapper.class);
        postingMapper = sessions.getMapper(ProductionCompletionPostingMapper.class);
        guard = new WmProductionRecptWriteGuard(guardMapper);
        createPrivateTables();

        backflush = mock(PrdBackflushServiceImpl.class);
        receipts = mock(IWmProductionRecptService.class);
        putaway = mock(IPutawayStrategyService.class);
        PutawayLocation location = new PutawayLocation();
        location.setWarehouseId(3L); location.setWarehouseCode("WH-3"); location.setWarehouseName("测试成品仓");
        location.setLocationId(28L); location.setLocationCode("LOC-28"); location.setLocationName("测试库位");
        when(putaway.resolvePutaway(301L, QUANTITY)).thenReturn(location);
        doAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "倒冲替身必须处于真实上层事务");
            return null;
        }).when(backflush).backflushOnCompletion(WORK_ORDER_ID, QUANTITY);
        when(receipts.createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "入库替身必须处于真实上层事务");
                assertEquals(QUANTITY, invocation.getArgument(6), "替身不得更改实际本次数量");
                persistPostedEvidence();
                return POSTED_RECEIPT_ID;
            });
        message = new ProductionCompleteMessage();
        message.setTenantId(0L); message.setWoId(WORK_ORDER_ID); message.setWoNo("WO-201");
        message.setMaterialId(301L); message.setMaterialCode("FG-301"); message.setMaterialName("内存测试成品");
        message.setCompletionQty(QUANTITY);
    }

    @AfterEach
    void tearDown() {
        try {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "用例结束不得遗留事务");
            if (jdbc != null) jdbc.execute("SHUTDOWN");
        } finally {
            DynamicDataSourceContextHolder.poll();
            if (tenant != null) tenant.close();
        }
    }

    @Test
    void actualTableInfoAndMapperBatchDeleteProduceOneWithoutRewritingSource() {
        assertAll(
            () -> assertTrue(tableInfo.isWithLogicDelete(), "真实生产入库实体必须启用逻辑删除"),
            () -> assertEquals("delFlag", tableInfo.getLogicDeleteFieldInfo().getProperty(), "实体属性必须对应原删除字段"),
            () -> assertEquals("del_flag", tableInfo.getLogicDeleteFieldInfo().getColumn(), "原数据库列不能被替换"),
            () -> assertEquals(String.class, tableInfo.getLogicDeleteFieldInfo().getPropertyType(), "生产删除标记按字符串映射"),
            () -> assertEquals("0", tableInfo.getLogicDeleteFieldInfo().getLogicNotDeleteValue(), "有效行仍为零"),
            () -> assertEquals("1", tableInfo.getLogicDeleteFieldInfo().getLogicDeleteValue(), "项目实际映射默认删除值为一")
        );
        insertReceipt(RETIRED_RECEIPT_ID, TENANT, WORK_ORDER_ID, "0", "DRAFT", null, null);
        insertReceipt(111L, "000008", WORK_ORDER_ID, "0", "DRAFT", null, null);
        assertEquals(1, receiptMapper.deleteBatchIds(List.of(RETIRED_RECEIPT_ID, 111L)),
            "服务所用真实批量删除仅能退休当前租户原行");
        String sql = sqlRecorder.statements.stream().filter(s -> s.startsWith("UPDATE WM_PRODUCTION_RECPT"))
            .reduce((first, last) -> last).orElseThrow(() -> new AssertionError("必须实际执行逻辑删除更新 SQL"));
        assertAll(
            () -> assertTrue(sql.replaceAll("\\s+", "").contains("DEL_FLAG='1'"), "注入器实际 SQL 必须写入默认删除值一"),
            () -> assertTrue(sql.contains("TENANT_ID = '000000'"), "实际删除 SQL 必须保留生产租户插件条件"),
            () -> assertEquals("1", rawFlag(RETIRED_RECEIPT_ID), "原生读取必须证明物理行保留且删除值为一"),
            () -> assertEquals("0", rawFlag(111L), "不能删除其他租户的原行"),
            () -> assertNull(receiptMapper.selectById(RETIRED_RECEIPT_ID), "普通 Mapper 读取必须隐藏实际删除行")
        );
        WmProductionRecpt original = transaction.execute(status -> guardMapper.lockReceipts(TENANT, WORK_ORDER_ID).get(0));
        assertAll(
            () -> assertEquals(RETIRED_RECEIPT_ID, original.getRecptId(), "固定历史读取必须保留原单编号"),
            () -> assertEquals(TENANT, original.getTenantId(), "逻辑删除不得改变原租户"),
            () -> assertEquals(WORK_ORDER_ID, original.getWoId(), "逻辑删除不得改变原工单"),
            () -> assertEquals(QUANTITY, original.getQuantity(), "逻辑删除不得重写原数量四"),
            () -> assertEquals("DRAFT", original.getStatus(), "逻辑删除不得制造完成状态"),
            () -> assertNull(original.getBatchId(), "原无批次编号必须保持为空"),
            () -> assertNull(original.getBatchCode(), "原无批次编码必须保持为空"),
            () -> assertEquals(0L, count("wm_material_stock_tx"), "删除草稿不能生成流水")
        );
    }

    @ParameterizedTest(name = "{0}：守卫允许原零效果删除草稿退出新建范围")
    @ValueSource(strings = {"真实映射默认一", "历史删除二"})
    void zeroEffectDraftAllowsGuardRecreateScope(String mode) {
        retireDraft(mode);
        List<String> before = snapshot();
        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> {
            PrdWorkOrder wo = guard.lockWorkOrder(WORK_ORDER_ID);
            assertTrue(guard.receipts(wo).isEmpty(), "明确零效果删除草稿不得冒充有效单据");
            guard.requireNoEffects(wo);
        }), "实际默认删除值与历史删除值都应允许无流水的新建范围核验");
        assertEquals(before, snapshot(), "范围核验只能读取，不得改原草稿或数量");
    }

    @ParameterizedTest(name = "{0}：上层首次完工及原结果重放")
    @ValueSource(strings = {"真实映射默认一", "历史删除二"})
    void completionCreatesOnceAndReplaysOnlyOriginalReceipt(String mode) {
        retireDraft(mode);
        ProductionCompletionPostingService service = postingService(postingMapper);
        assertDoesNotThrow(() -> service.postCompletion(message), "明确删除草稿不得阻断首次真实形状的完工入库");
        List<String> posted = snapshot();
        assertDoesNotThrow(() -> service.postCompletion(message), "删除历史与完整原结果并存时只能验证原入库凭据");
        assertEquals(posted, snapshot(), "重放不得再造单据、批次、库存或流水");
        verify(backflush, times(1)).backflushOnCompletion(WORK_ORDER_ID, QUANTITY);
        verify(putaway, times(1)).resolvePutaway(301L, QUANTITY);
        verify(receipts, times(1)).createAndExecute(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
        verify(receipts, times(1)).execute(POSTED_RECEIPT_ID);
        assertEquals(2L, count("wm_production_recpt"), "只保留原删除草稿和唯一有效执行单");
        assertEquals(1L, count("wm_material_stock_tx"), "原工单只能拥有唯一正向入库流水");
        assertEquals("真实映射默认一".equals(mode) ? "1" : "2", rawFlag(RETIRED_RECEIPT_ID),
            "兼容不能通过改写历史删除标记实现");
    }

    @ParameterizedTest(name = "{0}：两层继续拒绝不明确或已有批次的删除行")
    @MethodSource("unsafeDeletedRows")
    void unsafeDeletedReceiptStillBlocksBothLayers(String description, String flag, String status,
                                                   Long batchId, String batchCode) {
        insertReceipt(RETIRED_RECEIPT_ID, TENANT, WORK_ORDER_ID, flag, status, batchId, batchCode);
        assertBothLayersBlock(guard, postingService(postingMapper));
    }

    static Stream<Arguments> unsafeDeletedRows() {
        List<Arguments> cases = new ArrayList<>();
        for (String flag : List.of("1", "2")) {
            cases.add(Arguments.of("删除" + flag + "已执行", flag, "EXECUTED", null, null));
            cases.add(Arguments.of("删除" + flag + "未知状态", flag, "UNKNOWN", null, null));
            cases.add(Arguments.of("删除" + flag + "空状态", flag, null, null, null));
            cases.add(Arguments.of("删除" + flag + "空串状态", flag, "", null, null));
            cases.add(Arguments.of("删除" + flag + "仅有批次编号", flag, "DRAFT", 401L, null));
            cases.add(Arguments.of("删除" + flag + "批次编号为零也非空", flag, "DRAFT", 0L, null));
            cases.add(Arguments.of("删除" + flag + "仅有批次编码", flag, "DRAFT", null, "B-401"));
            cases.add(Arguments.of("删除" + flag + "批次编码空串也非空", flag, "DRAFT", null, ""));
        }
        cases.add(Arguments.of("未知删除三", "3", "DRAFT", null, null));
        cases.add(Arguments.of("删除标记为空", null, "DRAFT", null, null));
        cases.add(Arguments.of("删除标记空串", "", "DRAFT", null, null));
        cases.add(Arguments.of("删除标记带空格", " 1", "DRAFT", null, null));
        cases.add(Arguments.of("未声明删除编码", "01", "DRAFT", null, null));
        return cases.stream();
    }

    @ParameterizedTest(name = "删除草稿{0}，流水删除标记{1}、方向{2}：两层不能忽略工单流水")
    @MethodSource("existingTransactions")
    void deletedDraftWithAnyTransactionStillBlocksBothLayers(String flag, String txFlag, int direction) {
        retireDraft("1".equals(flag) ? "真实映射默认一" : "历史删除二");
        jdbc.update("INSERT INTO wm_material_stock_tx (tx_id, tenant_id, del_flag, source_doc_type, source_doc_id, tx_type, tx_flag)"
            + " VALUES (501, ?, ?, 'PRD_WORK_ORDER', ?, 'PRODUCTION_IN', ?)", TENANT, txFlag, WORK_ORDER_ID, direction);
        assertBothLayersBlock(guard, postingService(postingMapper));
    }

    static Stream<Arguments> existingTransactions() {
        List<Arguments> cases = new ArrayList<>();
        for (String flag : List.of("1", "2")) {
            for (String txFlag : List.of("0", "1", "2")) cases.add(Arguments.of(flag, txFlag, 1));
            cases.add(Arguments.of(flag, "0", -1));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "删除{0}、{1}漂移：两层不得信任错误的读取结果")
    @MethodSource("sourceDrifts")
    void returnedTenantOrWorkOrderDriftStillBlocksBothLayers(String flag, String dimension) {
        retireDraft("1".equals(flag) ? "真实映射默认一" : "历史删除二");
        WmProductionRecpt drift = transaction.execute(status -> guardMapper.lockReceipts(TENANT, WORK_ORDER_ID).get(0));
        if ("租户".equals(dimension)) drift.setTenantId("000008");
        else drift.setWoId(202L);
        // 仅模拟固定读取返回漂移；默认删除标记的映射仍由真实 Mapper 删除独立证明。
        WmProductionRecptGuardMapper driftingGuard = spy(guardMapper);
        doReturn(List.of(drift)).when(driftingGuard).lockReceipts(TENANT, WORK_ORDER_ID);
        ProductionCompletionPostingMapper driftingPosting = spy(postingMapper);
        doReturn(List.of(drift)).when(driftingPosting).selectReceipts(TENANT, WORK_ORDER_ID);
        assertBothLayersBlock(new WmProductionRecptWriteGuard(driftingGuard), postingService(driftingPosting));
    }

    static Stream<Arguments> sourceDrifts() {
        return Stream.of(Arguments.of("1", "租户"), Arguments.of("1", "工单"),
            Arguments.of("2", "租户"), Arguments.of("2", "工单"));
    }

    @Test
    void activeZeroRemainsVisibleAndCannotAuthorizeAnotherCompletion() {
        insertReceipt(RETIRED_RECEIPT_ID, TENANT, WORK_ORDER_ID, "0", "DRAFT", null, null);
        List<WmProductionRecpt> active = transaction.execute(status -> guard.receipts(guard.lockWorkOrder(WORK_ORDER_ID)));
        assertEquals(List.of(RETIRED_RECEIPT_ID), active.stream().map(WmProductionRecpt::getRecptId).toList(),
            "零标记必须仍是有效原单，不能作为删除草稿隐藏");
        List<String> before = snapshot();
        assertThrows(ServiceException.class, () -> postingService(postingMapper).postCompletion(message),
            "有效但未执行的原草稿不能授权上层再次倒冲入库");
        assertEquals(before, snapshot(), "有效草稿拒绝后原数据不能改变");
        verifyNoInteractions(backflush, receipts, putaway);
    }

    @ParameterizedTest(name = "{0}：兼容不授权编辑或再次删除原删除单")
    @ValueSource(strings = {"真实映射默认一", "历史删除二"})
    void retiredDraftStillCannotBeEditedOrDeletedAgain(String mode) {
        retireDraft(mode);
        List<String> before = snapshot();
        assertThrows(ServiceException.class,
            () -> transaction.execute(status -> guard.lockReceipt(RETIRED_RECEIPT_ID)), "原删除单仍不能作为有效单编辑或执行");
        assertThrows(ServiceException.class,
            () -> transaction.executeWithoutResult(status -> guard.validateDelete(new Long[]{RETIRED_RECEIPT_ID})),
            "删除标记兼容不得授权再次删除原单");
        assertEquals(before, snapshot(), "拒绝再次编辑删除后不能改变历史原单");
    }

    private void assertBothLayersBlock(WmProductionRecptWriteGuard candidate, ProductionCompletionPostingService service) {
        List<String> before = snapshot();
        assertThrows(ServiceException.class, () -> transaction.executeWithoutResult(status -> {
            PrdWorkOrder wo = candidate.lockWorkOrder(WORK_ORDER_ID);
            candidate.receipts(wo);
            candidate.requireNoEffects(wo);
        }), "守卫不得把异常删除、批次或已有流水当作零效果草稿");
        assertThrows(ServiceException.class, () -> service.postCompletion(message), "上层不得用异常历史授权新倒冲或假成功");
        assertEquals(before, snapshot(), "两层拒绝均不得改变原单、流水或库存");
        verifyNoInteractions(backflush, receipts, putaway);
    }

    private void retireDraft(String mode) {
        boolean actualDefault = "真实映射默认一".equals(mode);
        insertReceipt(RETIRED_RECEIPT_ID, TENANT, WORK_ORDER_ID, actualDefault ? "0" : "2", "DRAFT", null, null);
        if (actualDefault) {
            assertEquals(1, receiptMapper.deleteBatchIds(List.of(RETIRED_RECEIPT_ID)), "必须执行既有服务同款真实 Mapper 批量删除");
            assertEquals("1", rawFlag(RETIRED_RECEIPT_ID), "反例必须来自实际删除结果一，不能来自替换模拟值");
        }
    }

    private ProductionCompletionPostingService postingService(ProductionCompletionPostingMapper mapper) {
        ProductionCompletionPostingService target = new ProductionCompletionPostingService(mapper, backflush, putaway, receipts);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(interceptor);
        return (ProductionCompletionPostingService) proxy.getProxy();
    }

    /** 仅按真实实体元数据建立随机命名内存库的夹具表，不创建或执行业务升级 DDL。 */
    private void createPrivateTables() {
        List<String> columns = new ArrayList<>();
        columns.add(tableInfo.getKeyColumn() + " BIGINT PRIMARY KEY");
        tableInfo.getFieldList().forEach(field -> columns.add(field.getColumn() + " " + sqlType(field.getPropertyType())));
        jdbc.execute("CREATE TABLE " + tableInfo.getTableName() + " (" + String.join(",", columns) + ")");
        jdbc.execute("CREATE TABLE prd_work_order (wo_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag VARCHAR(2),"
            + " wo_no VARCHAR(64), material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(128),"
            + " unit_id BIGINT, planned_qty DECIMAL(18,4), completed_qty DECIMAL(18,4),"
            + " wo_status VARCHAR(32), actual_end_date TIMESTAMP)");
        jdbc.update("INSERT INTO prd_work_order VALUES (?, ?, '0', 'WO-201', 301, 'FG-301', '内存测试成品',"
            + " 26, 6, 4, 'COMPLETED', CURRENT_TIMESTAMP)", WORK_ORDER_ID, TENANT);
        jdbc.execute("CREATE TABLE prd_work_report (tenant_id VARCHAR(20), wo_id BIGINT, report_status VARCHAR(32), reported_qty DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE wm_production_issue (tenant_id VARCHAR(20), wo_id BIGINT, status VARCHAR(32), remark VARCHAR(255))");
        jdbc.execute("CREATE TABLE wm_material_stock_tx (tx_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag VARCHAR(2),"
            + " source_doc_type VARCHAR(32), source_doc_id BIGINT, tx_type VARCHAR(32), tx_flag INTEGER,"
            + " material_stock_id BIGINT, material_id BIGINT, material_code VARCHAR(64), batch_id BIGINT, batch_code VARCHAR(64),"
            + " warehouse_id BIGINT, location_id BIGINT, tx_quantity DECIMAL(18,4), before_quantity DECIMAL(18,4),"
            + " after_quantity DECIMAL(18,4), tx_date TIMESTAMP)");
        jdbc.execute("CREATE TABLE wm_material_stock (stock_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), del_flag VARCHAR(2),"
            + " wo_id BIGINT, material_id BIGINT, material_code VARCHAR(64), batch_id BIGINT, batch_code VARCHAR(64),"
            + " warehouse_id BIGINT, location_id BIGINT)");
    }

    private String sqlType(Class<?> type) {
        if (type == String.class) return "VARCHAR(512)";
        if (type == Long.class) return "BIGINT";
        if (type == Integer.class) return "INTEGER";
        if (type == BigDecimal.class) return "DECIMAL(18,4)";
        if (type == Date.class) return "TIMESTAMP";
        throw new IllegalArgumentException("未声明的内存夹具字段类型：" + type);
    }

    private void insertReceipt(long id, String tenantId, long woId, String flag, String status, Long batchId, String batchCode) {
        jdbc.update("INSERT INTO wm_production_recpt (recpt_id, recpt_code, tenant_id, wo_id, wo_no, material_id, material_code,"
            + " quantity, warehouse_id, location_id, status, del_flag, batch_id, batch_code)"
            + " VALUES (?, ?, ?, ?, 'WO-201', 301, 'FG-301', 4, 3, 28, ?, ?, ?, ?)",
            id, "REC-" + id, tenantId, woId, status, flag, batchId, batchCode);
    }

    /** 下游替身只写私有内存证据；真实 Mapper 插入保证清除同事务读取缓存，不能伪造读视图。 */
    private void persistPostedEvidence() {
        jdbc.update("INSERT INTO wm_material_stock VALUES (601, ?, '0', ?, 301, 'FG-301', 401, 'B-401', 3, 28)", TENANT, WORK_ORDER_ID);
        jdbc.update("INSERT INTO wm_material_stock_tx VALUES (501, ?, '0', 'PRD_WORK_ORDER', ?, 'PRODUCTION_IN', 1,"
            + " 601, 301, 'FG-301', 401, 'B-401', 3, 28, 4, 0, 4, CURRENT_TIMESTAMP)", TENANT, WORK_ORDER_ID);
        WmProductionRecpt posted = new WmProductionRecpt();
        posted.setRecptId(POSTED_RECEIPT_ID); posted.setRecptCode("REC-102"); posted.setTenantId(TENANT);
        posted.setWoId(WORK_ORDER_ID); posted.setWoNo("WO-201"); posted.setMaterialId(301L); posted.setMaterialCode("FG-301");
        posted.setQuantity(QUANTITY); posted.setStatus("EXECUTED"); posted.setDelFlag("0");
        posted.setBatchId(401L); posted.setBatchCode("B-401"); posted.setWarehouseId(3L); posted.setLocationId(28L);
        assertEquals(1, receiptMapper.insert(posted), "下游替身必须仅生成唯一完整形状的内存凭据");
    }

    private String rawFlag(long id) {
        return jdbc.queryForObject("SELECT del_flag FROM wm_production_recpt WHERE recpt_id = ?", String.class, id);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private List<String> snapshot() {
        List<String> rows = new ArrayList<>();
        for (String table : List.of("wm_production_recpt", "wm_material_stock_tx", "wm_material_stock")) {
            rows.add(table + jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return rows;
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    private static final class SqlRecorder implements Interceptor {
        private final List<String> statements = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            StatementHandler statement = (StatementHandler) invocation.getTarget();
            statements.add(statement.getBoundSql().getSql().replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT));
            return invocation.proceed();
        }
    }
}
