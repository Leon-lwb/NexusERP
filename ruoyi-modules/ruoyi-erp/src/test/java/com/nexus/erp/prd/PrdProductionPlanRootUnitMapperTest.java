package com.nexus.erp.prd;

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
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.IPrdMaterialRequirementService;
import com.nexus.erp.prd.service.impl.PrdProductionPlanServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 私有内存 H2 中执行真实生成方法、材料/单位/工单 Mapper、SQL 注入器、生产租户插件与 Spring 事务代理。
 * 计划/BOM/需求和编号仅用明确合法内存夹具；零行在写入边界定点注入，不吞异常、不伪造 Mapper 返回工单。
 * 表按完整实体元数据建测试夹具，不改仓库 Schema、不连接既有数据库；不代表 MySQL、完整来源认证或运行态验收。
 */
@Isolated("夹具修改 MyBatis-Plus 全局元数据及编号生成器，必须独占执行并在关闭后恢复")
class PrdProductionPlanRootUnitMapperTest {
    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionTemplate sessions;
    private BdMaterialMapper materials;
    private BdUnitOfMeasureMapper units;
    private PrdWorkOrderMapper workOrders;
    private PrdProductionPlanMapper plans;
    private BdBomMapper boms;
    private IPrdMaterialRequirementService requirements;
    private AutoCodeUtil codes;
    private MockedStatic<TenantHelper> tenant;
    private SqlRecorder recorder;
    private PrdProductionPlanMapperGlobalState globalState;

    @BeforeEach
    void setUp() {
        globalState = PrdProductionPlanMapperGlobalState.capture();
        try {
            initializeFixture();
        } catch (RuntimeException | Error failure) {
            // 构建到一半失败也立即恢复，保留原异常；JUnit 再次调用清理时允许幂等返回。
            try {
                tearDown();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void initializeFixture() {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:root_unit_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("仅内存工单单位回归", new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        GlobalConfig global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        global.setSqlInjector(new DefaultSqlInjector());
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        recorder = new SqlRecorder();
        configuration.addInterceptor(recorder);
        TenantProperties properties = new TenantProperties();
        properties.setExcludes(List.of());
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties)));
        configuration.addInterceptor(plugins);
        configuration.addMapper(BdMaterialMapper.class);
        configuration.addMapper(BdUnitOfMeasureMapper.class);
        configuration.addMapper(PrdWorkOrderMapper.class);
        createFixtureTable(BdMaterial.class);
        createFixtureTable(BdUnitOfMeasure.class);
        createFixtureTable(PrdWorkOrder.class);
        sessions = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        materials = sessions.getMapper(BdMaterialMapper.class);
        units = sessions.getMapper(BdUnitOfMeasureMapper.class);
        workOrders = sessions.getMapper(PrdWorkOrderMapper.class);
        jdbc.execute("""
            INSERT INTO bd_material(material_id,tenant_id,material_code,material_name,material_spec,base_uom_id,status,del_flag)
            VALUES(3001,'000000','FG-TRUSTED','可信根成品','标准规格',7001,1,'0')
            """);
        jdbc.execute("""
            INSERT INTO bd_unit_of_measure(uom_id,tenant_id,uom_code,uom_name,primary_id,is_base,conversion_rate,
                status,del_flag,quantity_scale,conversion_version,conversion_verified_at)
            VALUES(7001,'000000','SET-ROOT','套',7001,1,1,1,'0',4,'fixture-unit-v1',TIMESTAMP '2026-10-01 00:00:00')
            """);
        plans = mock(PrdProductionPlanMapper.class);
        boms = mock(BdBomMapper.class);
        requirements = mock(IPrdMaterialRequirementService.class);
        codes = mock(AutoCodeUtil.class);
        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanId(1001L);
        plan.setPlanName("可信根计划");
        plan.setTenantId("000000");
        plan.setBomId(4001L);
        plan.setTotalQuantity(new BigDecimal("10.2500"));
        BdBom bom = new BdBom();
        bom.setBomId(4001L);
        bom.setTenantId("000000");
        bom.setParentMaterialId(3001L);
        when(plans.selectById(1001L)).thenReturn(plan);
        when(boms.selectById(4001L)).thenReturn(bom);
        when(requirements.selectPrdMaterialRequirementByRequirementId(2001L)).thenReturn(requirement(2001L));
        AtomicInteger serial = new AtomicInteger();
        when(codes.genSerialCode("PRD_WORK_ORDER_CODE", null)).thenAnswer(inv -> "WO-H2-" + serial.incrementAndGet());
    }

    @AfterEach
    void tearDown() {
        try {
            if (sessions != null) sessions.clearCache();
        } finally {
            sessions = null;
            try {
                if (jdbc != null) jdbc.execute("SHUTDOWN");
            } finally {
                jdbc = null;
                dataSource = null;
                try {
                    if (tenant != null) tenant.close();
                } finally {
                    tenant = null;
                    // 即使清理数据库或静态桩失败，也不能把关闭的 H2 工厂和生成器泄漏给后续测试。
                    try {
                        if (globalState != null) globalState.close();
                    } finally {
                        globalState = null;
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("实际 Mapper 查询含租户和逻辑删除边界，实际工单落库具有完整根单位")
    void productionMappersPersistTrustedRootUnitWithTenantSql() {
        assertEquals(1, service(workOrders).generateWorkOrders(1001L, List.of(2001L)));
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM prd_work_order WHERE source_id=2001");
        assertAll(
            () -> assertEquals(7001L, ((Number) row.get("UNIT_ID")).longValue()),
            () -> assertEquals("套", row.get("UNIT_NAME")),
            () -> assertEquals("FG-TRUSTED", row.get("MATERIAL_CODE")),
            () -> assertEquals("可信根成品", row.get("MATERIAL_NAME")),
            () -> assertEquals("标准规格", row.get("MATERIAL_SPEC")),
            () -> assertEquals("000000", row.get("TENANT_ID")),
            () -> assertEquals("0", row.get("DEL_FLAG")),
            () -> assertEquals(0, new BigDecimal("10.2500").compareTo((BigDecimal) row.get("PLANNED_QTY")))
        );
        assertScopedRead("BD_MATERIAL", "MATERIAL_ID");
        assertScopedRead("BD_UNIT_OF_MEASURE", "UOM_ID");
        verify(codes).genSerialCode("PRD_WORK_ORDER_CODE", null);
    }

    enum InvalidStoredSource {
        MATERIAL_OTHER_TENANT, MATERIAL_DELETED, UNIT_OTHER_TENANT, UNIT_DELETED,
        SAME_NAME_OTHER_ID, PRIMARY_DRIFT, UNVERIFIED_SCALE
    }

    @ParameterizedTest(name = "真实 SQL 来源拒绝：{0}")
    @EnumSource(InvalidStoredSource.class)
    @DisplayName("真实 SQL 不得放行外租户删除或同名错误主键单位")
    void actualSqlRejectsWrongSourceWithoutNumberingOrInsert(InvalidStoredSource reason) {
        switch (reason) {
            case MATERIAL_OTHER_TENANT -> jdbc.execute("UPDATE bd_material SET tenant_id='000001'");
            case MATERIAL_DELETED -> jdbc.execute("UPDATE bd_material SET del_flag='1'");
            case UNIT_OTHER_TENANT -> jdbc.execute("UPDATE bd_unit_of_measure SET tenant_id='000001'");
            case UNIT_DELETED -> jdbc.execute("UPDATE bd_unit_of_measure SET del_flag='1'");
            case SAME_NAME_OTHER_ID -> jdbc.execute("UPDATE bd_unit_of_measure SET uom_id=7002,primary_id=7002");
            case PRIMARY_DRIFT -> jdbc.execute("UPDATE bd_unit_of_measure SET primary_id=7002");
            case UNVERIFIED_SCALE -> jdbc.execute("UPDATE bd_unit_of_measure SET quantity_scale=NULL");
        }
        assertThrows(ServiceException.class, () -> service(workOrders).generateWorkOrders(1001L, List.of(2001L)));
        verifyNoInteractions(codes);
        assertEquals(0, rowCount(), "真实内存表不能留部分出生工单");
        assertTrue(recorder.statements.stream().noneMatch(sql -> sql.startsWith("INSERT")), "拒绝前不能进入插入 SQL");
    }

    @Test
    @DisplayName("历史缺单位工单保留原行，当前单位不可用也不破坏幂等跳过")
    void existingHistoricalOrderIsUnchangedWithoutNewUnitRead() {
        persistHistory(2001L);
        Map<String, Object> before = history();
        jdbc.execute("UPDATE bd_material SET status=0");
        jdbc.execute("UPDATE bd_unit_of_measure SET del_flag='1'");
        assertEquals(0, service(workOrders).generateWorkOrders(1001L, List.of(2001L)));
        assertEquals(before, history(), "历史工单全部字段必须不变，包括缺失单位、原状态和数量");
        assertEquals(1, rowCount());
        verifyNoInteractions(codes);
        assertTrue(recorder.statements.stream().noneMatch(sql -> sql.contains("FROM BD_MATERIAL")
            || sql.contains("FROM BD_UNIT_OF_MEASURE") || sql.startsWith("UPDATE") || sql.startsWith("INSERT")),
            "已生成来源应在新检查前跳过，不能查新单位后追改历史");
    }

    @Test
    @DisplayName("真实 Mapper 的混合批次仅为未生成来源出生，原工单全部字段不变")
    void mixedBatchPersistsOnlyNewSourceAndPreservesHistory() {
        persistHistory(2001L);
        Map<String, Object> before = history();
        addSecondRequirement();
        assertEquals(1, service(workOrders).generateWorkOrders(1001L, List.of(2001L, 2002L)));
        assertEquals(before, history());
        assertEquals(2, rowCount());
        assertEquals(7001L, jdbc.queryForObject("SELECT unit_id FROM prd_work_order WHERE source_id=2002", Long.class));
        verify(codes).genSerialCode("PRD_WORK_ORDER_CODE", null);
    }

    @ParameterizedTest(name = "第 {0} 个插入返回零行")
    @ValueSource(ints = {1, 2})
    @DisplayName("零行异常经原事务代理回滚当批真实写入，并保留历史工单")
    void zeroRowInsertRollsBackRealBatchWritesAndPreservesHistory(int failureAt) {
        persistHistory(2009L);
        Map<String, Object> before = history();
        addSecondRequirement();
        PrdWorkOrderMapper failingBoundary = mock(PrdWorkOrderMapper.class);
        when(failingBoundary.selectCount(any())).thenAnswer(inv -> workOrders.selectCount(inv.getArgument(0)));
        AtomicInteger writes = new AtomicInteger();
        when(failingBoundary.insert(any(PrdWorkOrder.class))).thenAnswer(inv -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "必须真正经过 Spring 写事务");
            if (writes.incrementAndGet() == failureAt) return 0;
            return workOrders.insert(inv.getArgument(0, PrdWorkOrder.class));
        });
        ServiceException error = assertThrows(ServiceException.class,
            () -> service(failingBoundary).generateWorkOrders(1001L, List.of(2001L, 2002L)));
        assertEquals("生产工单写入失败，必须且只能新增一行", error.getMessage());
        assertEquals(1, rowCount(), "当批前项实际 INSERT 必须回滚，不得返回生成成功或留半批");
        assertEquals(before, history(), "事务失败不得重写既有历史");
        assertEquals(failureAt - 1L, recorder.statements.stream().filter(sql -> sql.startsWith("INSERT")).count(),
            "仅失败前项执行真实 INSERT，零行仅在持久化边界注入，不假称 MySQL 自然零行");
        verify(failingBoundary, times(failureAt)).insert(any(PrdWorkOrder.class));
    }

    private PrdProductionPlanServiceImpl service(PrdWorkOrderMapper orders) {
        PrdProductionPlanServiceImpl target = new PrdProductionPlanServiceImpl(
            plans, boms, materials, null, null, requirements, null, null, null, codes, orders, null, units);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        return (PrdProductionPlanServiceImpl) proxy.getProxy();
    }

    private void addSecondRequirement() {
        when(requirements.selectPrdMaterialRequirementByRequirementId(2002L)).thenReturn(requirement(2002L));
    }

    private static PrdMaterialRequirement requirement(long id) {
        PrdMaterialRequirement row = new PrdMaterialRequirement();
        row.setRequirementId(id);
        row.setTenantId("000000");
        row.setPlanId(1001L);
        row.setBomId(4001L);
        row.setMaterialId(3001L);
        row.setRequirementNo("MR-H2-" + id);
        row.setMaterialCode("不可信缓存编码");
        row.setMaterialName("不可信缓存名称");
        row.setMaterialSpec("不可信缓存规格");
        row.setTotalRequiredQuantity(new BigDecimal("999"));
        return row;
    }

    private void persistHistory(long sourceId) {
        jdbc.update("""
            INSERT INTO prd_work_order(wo_id,tenant_id,wo_no,plan_id,source_type,source_id,material_id,material_code,
                unit_id,unit_name,planned_qty,completed_qty,scrap_qty,wo_status,del_flag)
            VALUES(9001,'000000','WO-HISTORY',1001,'MATERIAL_REQUIREMENT',?,3001,'HISTORICAL-CODE',
                NULL,'历史单位文本',9,2,1,'RELEASED','0')
            """, sourceId);
    }

    private Map<String, Object> history() {
        return jdbc.queryForMap("SELECT * FROM prd_work_order WHERE wo_id=9001");
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM prd_work_order", Integer.class);
    }

    private void assertScopedRead(String table, String key) {
        String sql = recorder.statements.stream().filter(s -> s.contains("FROM " + table + " ")).findFirst().orElseThrow();
        assertAll(
            () -> assertTrue(sql.contains(key + " = ?"), "必须按真实主键读，而非同名搜索：" + sql),
            () -> assertTrue(sql.contains("TENANT_ID = '000000'"), "生产租户插件条件不能丢失：" + sql),
            () -> assertTrue(sql.contains("DEL_FLAG = '0'"), "真实实体逻辑删除条件不能丢失：" + sql)
        );
        System.out.println("真实 Mapper 已执行来源 SQL（仅 H2）: " + sql);
    }

    private void createFixtureTable(Class<?> entity) {
        // 完整展开现有实体所有映射列；私有夹具不模拟 MySQL DDL、锁行为或生产约束验收。
        TableInfo info = TableInfoHelper.getTableInfo(entity);
        List<String> columns = new ArrayList<>();
        columns.add(info.getKeyColumn() + " BIGINT PRIMARY KEY");
        info.getFieldList().forEach(field -> {
            Class<?> type = field.getPropertyType();
            String sqlType = type == BigDecimal.class ? "DECIMAL(24,8)" : Date.class.isAssignableFrom(type)
                ? "TIMESTAMP" : Number.class.isAssignableFrom(type) ? "BIGINT" : "VARCHAR(600)";
            String suffix = "del_flag".equals(field.getColumn()) ? " DEFAULT '0'" : "";
            columns.add(field.getColumn() + " " + sqlType + suffix);
        });
        jdbc.execute("CREATE TABLE " + info.getTableName() + "(" + String.join(",", columns) + ")");
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    private static class SqlRecorder implements Interceptor {
        private final List<String> statements = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            StatementHandler handler = (StatementHandler) invocation.getTarget();
            statements.add(handler.getBoundSql().getSql().replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT));
            return invocation.proceed();
        }
    }
}
