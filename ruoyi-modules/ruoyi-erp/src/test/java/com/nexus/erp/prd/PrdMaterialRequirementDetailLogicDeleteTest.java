package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * 私有内存 H2 的真实 Mapper、SQL 注入器与生产租户插件回归。
 * 只固定线程租户，不启动应用、不访问现有数据库；不代表现场 MySQL 或运行中服务已加载修复。
 */
class PrdMaterialRequirementDetailLogicDeleteTest {

    private static final String CURRENT_TENANT = "000000";
    private static final String OTHER_TENANT = "000001";
    private static final long REQUIREMENT_ID = 501L;
    private static final long CURRENT_DETAIL_ID = 101L;
    private static final long OTHER_DETAIL_ID = 102L;
    private static final long RETIRED_DETAIL_ID = 103L;

    private MockedStatic<TenantHelper> tenantContext;
    private JdbcDataSource dataSource;
    private SqlSession session;
    private PrdMaterialRequirementDetailMapper mapper;
    private MybatisConfiguration configuration;
    private SqlRecorder sqlRecorder;

    @BeforeEach
    void setUp() throws SQLException {
        tenantContext = mockStatic(TenantHelper.class);
        tenantContext.when(TenantHelper::getTenantId).thenReturn(CURRENT_TENANT);
        tenantContext.when(TenantHelper::isEnable).thenReturn(true);

        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mrp_detail_logic_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // 沿用现有明细表字段和主键，仅在每个用例独享的内存库建立夹具。
            statement.execute("""
                CREATE TABLE prd_material_requirement_detail (
                    detail_id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(20) DEFAULT '000000', requirement_id BIGINT NOT NULL,
                    material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(200),
                    material_spec VARCHAR(500), unit VARCHAR(32), source_type VARCHAR(20),
                    bom_quantity DECIMAL(18,4), loss_rate DECIMAL(18,6),
                    required_quantity DECIMAL(18,4), issued_quantity DECIMAL(18,4),
                    stock_quantity DECIMAL(18,4), shortage_quantity DECIMAL(18,4),
                    operation_step BIGINT, workstation_id BIGINT, workstation_name VARCHAR(100),
                    lead_time BIGINT, required_date TIMESTAMP, status BIGINT, remark VARCHAR(500),
                    create_dept BIGINT, create_by BIGINT, create_time TIMESTAMP,
                    update_by BIGINT, update_time TIMESTAMP, del_flag CHAR(1) DEFAULT '0',
                    backflush_flag INT, actual_consumed_qty DECIMAL(14,4)
                )
                """);
            statement.execute("""
                INSERT INTO prd_material_requirement_detail
                    (detail_id, tenant_id, requirement_id, material_id, required_quantity, issued_quantity, del_flag)
                VALUES (101, '000000', 501, 701, 10, 0, '0'),
                       (102, '000001', 501, 702, 20, 0, '0'),
                       (103, '000000', 502, 703, 30, 0, '1')
                """);
        }

        configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("仅内存回归", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        globalConfig.setSqlInjector(new DefaultSqlInjector());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);

        sqlRecorder = new SqlRecorder();
        // 外层租户插件先改写真实 SQL，内层记录器再记录实际交给 JDBC 的语句。
        configuration.addInterceptor(sqlRecorder);
        TenantProperties tenantProperties = new TenantProperties();
        tenantProperties.setExcludes(List.of());
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new PlusTenantLineHandler(tenantProperties)));
        configuration.addInterceptor(plugins);
        configuration.addMapper(PrdMaterialRequirementDetailMapper.class);

        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(PrdMaterialRequirementDetailMapper.class);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try {
            if (session != null) {
                session.close();
            }
            if (dataSource != null) {
                try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("SHUTDOWN");
                }
            }
        } finally {
            if (tenantContext != null) {
                tenantContext.close();
            }
        }
    }

    @Test
    void tableInfoBindsExistingStringDeleteFlagAsLogicDelete() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(PrdMaterialRequirementDetail.class);
        assertNotNull(tableInfo);
        assertTrue(tableInfo.isWithLogicDelete(), "真实实体元数据必须启用逻辑删除");
        assertAll(
            () -> assertEquals("delFlag", tableInfo.getLogicDeleteFieldInfo().getProperty()),
            () -> assertEquals("del_flag", tableInfo.getLogicDeleteFieldInfo().getColumn()),
            () -> assertEquals(String.class, tableInfo.getLogicDeleteFieldInfo().getPropertyType()),
            () -> assertEquals("0", tableInfo.getLogicDeleteFieldInfo().getLogicNotDeleteValue()),
            () -> assertEquals("1", tableInfo.getLogicDeleteFieldInfo().getLogicDeleteValue())
        );
    }

    @Test
    void wrapperDeleteUsesUpdateAndRetainsOriginalSourceRow() throws SQLException {
        assertEquals(1, deleteCurrentRequirement());
        String actualSql = sqlRecorder.lastSql();
        assertAll(
            () -> assertTrue(actualSql.startsWith("UPDATE PRD_MATERIAL_REQUIREMENT_DETAIL"),
                "服务所用真实 Mapper 删除必须生成 UPDATE，而不是 DELETE：" + actualSql),
            () -> assertTrue(actualSql.contains("DEL_FLAG"), "实际 SQL 必须标记逻辑删除字段"),
            () -> assertTrue(actualSql.contains("REQUIREMENT_ID"), "来源父头条件不得丢失"),
            () -> assertTrue(actualSql.contains("TENANT_ID = '000000'"), "生产租户插件条件不得丢失"),
            () -> assertEquals("1", rawDeleteFlag(CURRENT_DETAIL_ID), "删除后账本来源物理行仍应存在"),
            () -> assertEquals(new BigDecimal("10.0000"), rawRequiredQuantity(CURRENT_DETAIL_ID),
                "退休仅标记删除，不能重写原始需求数量")
        );
    }

    @Test
    void mapperReadsAutomaticallyExcludeRetiredRowsAndOtherTenant() {
        List<PrdMaterialRequirementDetail> visible = mapper.selectList(Wrappers.lambdaQuery());
        assertAll(
            () -> assertEquals(List.of(CURRENT_DETAIL_ID), visible.stream()
                .map(PrdMaterialRequirementDetail::getDetailId).toList(), "默认读取只能返回当前租户未删明细"),
            () -> assertNull(mapper.selectById(RETIRED_DETAIL_ID), "按主键读取也必须自动隐藏已删行"),
            () -> assertNull(mapper.selectById(OTHER_DETAIL_ID), "按主键读取不能穿透其他租户")
        );
        assertTrue(sqlRecorder.statements.stream().allMatch(sql -> sql.contains("DEL_FLAG")
            && sql.contains("TENANT_ID = '000000'")), "每条真实读取 SQL 必须带逻辑删除和租户条件");
    }

    @Test
    void repeatedDeleteIsStateIdempotentAndKeepsOtherTenantSource() throws SQLException {
        assertEquals(1, deleteCurrentRequirement());
        assertEquals(0, deleteCurrentRequirement(), "重复删除不应再次修改已经退休的行");
        assertAll(
            () -> assertEquals("1", rawDeleteFlag(CURRENT_DETAIL_ID), "重复删除不能物理移除来源"),
            () -> assertEquals("0", rawDeleteFlag(OTHER_DETAIL_ID), "同父头 ID 的其他租户明细不得被删除"),
            () -> assertEquals(new BigDecimal("20.0000"), rawRequiredQuantity(OTHER_DETAIL_ID)),
            () -> assertTrue(sqlRecorder.statements.stream().allMatch(sql -> sql.startsWith("UPDATE ")
                && sql.contains("DEL_FLAG") && sql.contains("TENANT_ID = '000000'")),
                "两次删除都必须保留逻辑删除和租户条件")
        );
        tenantContext.when(TenantHelper::getTenantId).thenReturn(OTHER_TENANT);
        assertEquals(OTHER_DETAIL_ID, mapper.selectById(OTHER_DETAIL_ID).getDetailId());
    }

    @Test
    void hiddenOriginalDetailIdCannotBeReinsertedAsNewSource() throws SQLException {
        assertEquals(1, deleteCurrentRequirement());
        PrdMaterialRequirementDetail replacement = newDetail(CURRENT_DETAIL_ID, 999L, new BigDecimal("99.0000"));
        PersistenceException failure = assertThrows(PersistenceException.class, () -> mapper.insert(replacement),
            "正常编辑不得因逻辑读取隐藏旧行就复用原 detailId 覆写账本来源");
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertInstanceOf(SQLException.class, cause);
        assertEquals("23505", ((SQLException) cause).getSQLState(), "必须由实际主键唯一约束拒绝重插");
        assertAll(
            () -> assertEquals("1", rawDeleteFlag(CURRENT_DETAIL_ID)),
            () -> assertEquals(new BigDecimal("10.0000"), rawRequiredQuantity(CURRENT_DETAIL_ID))
        );
    }

    @Test
    void newDetailIdCanBeInsertedWithoutRevivingOldSource() throws SQLException {
        assertEquals(1, deleteCurrentRequirement());
        assertEquals(1, mapper.insert(newDetail(104L, REQUIREMENT_ID, new BigDecimal("12.0000"))));
        assertAll(
            () -> assertEquals("1", rawDeleteFlag(CURRENT_DETAIL_ID), "新增身份不得复活原明细"),
            () -> assertEquals("0", rawDeleteFlag(104L), "新明细仍使用既有表默认未删值"),
            () -> assertNull(mapper.selectById(CURRENT_DETAIL_ID)),
            () -> assertEquals(new BigDecimal("12.0000"), mapper.selectById(104L).getRequiredQuantity())
        );
    }

    private int deleteCurrentRequirement() {
        // 使用现有服务 deleteDetailByRequirementId 的真实 Mapper 调用形态。
        return mapper.delete(Wrappers.<PrdMaterialRequirementDetail>lambdaQuery()
            .eq(PrdMaterialRequirementDetail::getRequirementId, REQUIREMENT_ID));
    }

    private PrdMaterialRequirementDetail newDetail(long detailId, long requirementId, BigDecimal quantity) {
        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();
        detail.setDetailId(detailId);
        detail.setRequirementId(requirementId);
        detail.setMaterialId(701L);
        detail.setTenantId(CURRENT_TENANT);
        detail.setRequiredQuantity(quantity);
        return detail;
    }

    private String rawDeleteFlag(long detailId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT del_flag FROM prd_material_requirement_detail WHERE detail_id = ?")) {
            statement.setLong(1, detailId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private BigDecimal rawRequiredQuantity(long detailId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT required_quantity FROM prd_material_requirement_detail WHERE detail_id = ?")) {
            statement.setLong(1, detailId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getBigDecimal(1) : null;
            }
        }
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

        private String lastSql() {
            return statements.get(statements.size() - 1);
        }
    }
}