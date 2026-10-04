package com.nexus.erp.prd;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.handler.InjectionMetaObjectHandler;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.bo.PrdMaterialRequirementBo;
import com.nexus.erp.prd.domain.bo.PrdMaterialRequirementBoToPrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.service.impl.PrdMaterialRequirementServiceImpl;
import com.nexus.erp.pu.mapper.ErpPurchaseAllocationLineageMapper;
import io.github.linpeilie.Converter;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;

/**
 * 两个实际编辑服务入口与真实内存 Mapper 的替换回归，不访问现有业务数据库。
 * 仅固定身份和容器取值边界，转换器、填充器、服务、租户插件及历史补读 SQL 均实际执行。
 * 不启动应用，不验证真实 MySQL、运行中 JVM 加载或服务事务代理。
 */
public class PrdMaterialRequirementReplacementTest {

    private static final String TENANT = "000000";
    private static final long REQUIREMENT_ID = 501L;
    private static final long ORIGINAL_ID = 101L;
    private static final long OTHER_TENANT_ID = 102L;
    private static final long ALREADY_RETIRED_ID = 103L;
    private static final long ACTOR_ID = 9L;
    private static final long ACTOR_DEPT = 8L;

    private enum Entry { BO, ENTITY }

    private MockedStatic<TenantHelper> tenantContext;
    private MockedStatic<LoginHelper> loginContext;
    private MockedStatic<SpringUtil> beanContext;
    private MockedStatic<MapstructUtils> conversionContext;
    private JdbcDataSource dataSource;
    private SqlSession session;
    private PrdMaterialRequirementDetailMapper detailMapper;
    private ErpPurchaseAllocationLineageMapper historyMapper;
    private PrdMaterialRequirementServiceImpl service;

    @BeforeEach
    void setUp() throws SQLException {
        tenantContext = mockStatic(TenantHelper.class);
        tenantContext.when(TenantHelper::getTenantId).thenReturn(TENANT);
        tenantContext.when(TenantHelper::isEnable).thenReturn(true);
        LoginUser user = new LoginUser();
        user.setUserId(ACTOR_ID);
        user.setDeptId(ACTOR_DEPT);
        user.setTenantId(TENANT);
        loginContext = mockStatic(LoginHelper.class);
        loginContext.when(LoginHelper::getLoginUser).thenReturn(user);
        loginContext.when(LoginHelper::getUserId).thenReturn(ACTOR_ID);
        loginContext.when(LoginHelper::getTenantId).thenReturn(TENANT);

        PrdMaterialRequirementBoToPrdMaterialRequirementMapper converter = Mappers.getMapper(PrdMaterialRequirementBoToPrdMaterialRequirementMapper.class);
        beanContext = mockStatic(SpringUtil.class);
        beanContext.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(mock(Converter.class));
        // 静态容器边界不依赖测试执行顺序，BO 仍由真实生成的转换器转换。
        conversionContext = mockStatic(MapstructUtils.class);
        conversionContext.when(() -> MapstructUtils.convert(any(PrdMaterialRequirementBo.class), eq(PrdMaterialRequirement.class)))
            .thenAnswer(invocation -> converter.convert((PrdMaterialRequirementBo) invocation.getArgument(0)));

        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mrp_replacement_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;BUILTIN_ALIAS_OVERRIDE=TRUE");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // H2 的内置返回整数会在毫秒投影溢出，仅在夹具内补齐 MySQL 的十进制返回类型。
            statement.execute("CREATE ALIAS UNIX_TIMESTAMP FOR \"com.nexus.erp.prd.PrdMaterialRequirementReplacementTest.unixTimestamp\"");
            statement.execute("""
                CREATE TABLE prd_material_requirement (
                    requirement_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), requirement_no VARCHAR(64),
                    plan_id BIGINT, plan_no VARCHAR(64), parent_requirement_id BIGINT, bom_id BIGINT,
                    requirement_date TIMESTAMP, total_material_types BIGINT, total_required_quantity DECIMAL(18,4),
                    status VARCHAR(32), planner_id BIGINT, planner_name VARCHAR(64), dept_id BIGINT,
                    dept_name VARCHAR(100), confirmed_time TIMESTAMP, issued_time TIMESTAMP, closed_time TIMESTAMP,
                    material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(200), material_spec VARCHAR(500),
                    remark VARCHAR(500), source_type VARCHAR(20), create_dept BIGINT, create_by BIGINT,
                    create_time TIMESTAMP, update_by BIGINT, update_time TIMESTAMP, del_flag CHAR(1) DEFAULT '0'
                )
                """);
            statement.execute("""
                CREATE TABLE prd_material_requirement_detail (
                    detail_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20) DEFAULT '000000', requirement_id BIGINT NOT NULL,
                    material_id BIGINT, material_code VARCHAR(64), material_name VARCHAR(200), material_spec VARCHAR(500),
                    unit VARCHAR(32), source_type VARCHAR(20), bom_quantity DECIMAL(18,4), loss_rate DECIMAL(18,6),
                    required_quantity DECIMAL(18,4), issued_quantity DECIMAL(18,4), stock_quantity DECIMAL(18,4),
                    shortage_quantity DECIMAL(18,4), operation_step BIGINT, workstation_id BIGINT,
                    workstation_name VARCHAR(100), lead_time BIGINT, required_date TIMESTAMP, status BIGINT,
                    remark VARCHAR(500), create_dept BIGINT, create_by BIGINT, create_time TIMESTAMP,
                    update_by BIGINT, update_time TIMESTAMP, del_flag CHAR(1) DEFAULT '0',
                    backflush_flag INT, actual_consumed_qty DECIMAL(14,4)
                )
                """);
            statement.execute("""
                INSERT INTO prd_material_requirement (requirement_id, tenant_id, requirement_no, status, del_flag)
                VALUES (501, '000000', 'MRP-501', 'DRAFT', '0')
                """);
            statement.execute("""
                INSERT INTO prd_material_requirement_detail
                    (detail_id, tenant_id, requirement_id, material_id, unit, required_quantity, issued_quantity,
                     source_type, status, backflush_flag, actual_consumed_qty, required_date,
                     create_dept, create_by, create_time, update_by, update_time, del_flag)
                VALUES (101, '000000', 501, 701, '4', 10, 0, 'PROCUREMENT', 1, 0, 0,
                        TIMESTAMP '2026-10-04 12:00:00', 41, 42, TIMESTAMP '2025-01-01 10:00:00',
                        43, TIMESTAMP '2025-01-02 10:00:00', '0'),
                       (102, '000001', 501, 702, '4', 20, 0, 'PROCUREMENT', 1, 0, 0,
                        TIMESTAMP '2026-10-04 12:00:00', 51, 52, TIMESTAMP '2025-01-01 10:00:00',
                        53, TIMESTAMP '2025-01-02 10:00:00', '0'),
                       (103, '000000', 501, 703, '4', 30, 0, 'PROCUREMENT', 1, 1, 0,
                        TIMESTAMP '2026-10-04 12:00:00', 61, 62, TIMESTAMP '2025-01-01 10:00:00',
                        63, TIMESTAMP '2025-01-02 10:00:00', '1')
                """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("仅内存编辑回归", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        GlobalConfig global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.ASSIGN_ID)
            .setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        global.setSqlInjector(new DefaultSqlInjector());
        global.setMetaObjectHandler(new InjectionMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        TenantProperties tenantProperties = new TenantProperties();
        tenantProperties.setExcludes(List.of());
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new PlusTenantLineHandler(tenantProperties)));
        configuration.addInterceptor(plugins);
        configuration.addMapper(PrdMaterialRequirementMapper.class);
        configuration.addMapper(PrdMaterialRequirementDetailMapper.class);
        configuration.addMapper(ErpPurchaseAllocationLineageMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        detailMapper = session.getMapper(PrdMaterialRequirementDetailMapper.class);
        historyMapper = session.getMapper(ErpPurchaseAllocationLineageMapper.class);
        service = new PrdMaterialRequirementServiceImpl(session.getMapper(PrdMaterialRequirementMapper.class),
            detailMapper, null, null, null, null, null);
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
            if (conversionContext != null) conversionContext.close();
            if (beanContext != null) beanContext.close();
            if (loginContext != null) loginContext.close();
            if (tenantContext != null) tenantContext.close();
        }
    }

    @ParameterizedTest
    @EnumSource(Entry.class)
    void twoRoundEditingCreatesFreshIdentitiesAndPreservesEveryHistoricalPayload(Entry entry) throws SQLException {
        Map<String, Object> original = rawRow(ORIGINAL_ID);
        Map<String, Object> otherTenant = rawRow(OTHER_TENANT_ID);
        Map<String, Object> alreadyRetired = rawRow(ALREADY_RETIRED_ID);
        PrdMaterialRequirementDetail firstRequest = currentRows().get(0);

        assertEquals(1, assertDoesNotThrow(() -> replace(entry, firstRequest), "原 ID 回传的无修改保存必须成功"));
        PrdMaterialRequirementDetail firstCurrent = currentRows().get(0);
        long firstId = firstCurrent.getDetailId();
        assertNotEquals(ORIGINAL_ID, firstId, "替换行必须获得新主键");
        assertEquals(ORIGINAL_ID, firstRequest.getDetailId(), "服务不能污染请求中用于定位历史的原主键");
        assertEquals(new BigDecimal("10.0000"), firstCurrent.getRequiredQuantity());
        assertRetiredWithoutPayloadChange(ORIGINAL_ID, original);
        Map<String, Object> firstSnapshot = rawRow(firstId);

        PrdMaterialRequirementDetail secondRequest = currentRows().get(0);
        secondRequest.setRequiredQuantity(new BigDecimal("12.0000"));
        assertEquals(1, assertDoesNotThrow(() -> replace(entry, secondRequest), "第二轮数量编辑必须成功"));
        List<PrdMaterialRequirementDetail> visible = currentRows();
        assertEquals(1, visible.size(), "当前查询只能展示第二轮新行");
        PrdMaterialRequirementDetail secondCurrent = visible.get(0);
        assertTrue(secondCurrent.getDetailId() > 0);
        assertNotEquals(ORIGINAL_ID, secondCurrent.getDetailId());
        assertNotEquals(firstId, secondCurrent.getDetailId());
        assertEquals("0", secondCurrent.getDelFlag());
        assertEquals(new BigDecimal("12.0000"), secondCurrent.getRequiredQuantity());
        assertRetiredWithoutPayloadChange(ORIGINAL_ID, original);
        assertRetiredWithoutPayloadChange(firstId, firstSnapshot);
        assertEquals(alreadyRetired, rawRow(ALREADY_RETIRED_ID), "先前退休来源及其标志不得被复活或重写");
        assertEquals(otherTenant, rawRow(OTHER_TENANT_ID), "两个替换入口均不得改动其他租户");

        var historical = historyMapper.selectRequirementDetails(TENANT, List.of(ORIGINAL_ID, firstId, OTHER_TENANT_ID));
        assertEquals(2, historical.size(), "真实历史补读仍能读出两代已删来源，但不跨租户");
        assertTrue(historical.stream().allMatch(row -> "1".equals(row.getDelFlag())
            && "4".equals(row.getUnit()) && new BigDecimal("10.0000").equals(row.getRequiredQuantity())));
    }

    @ParameterizedTest
    @EnumSource(Entry.class)
    void replacementIgnoresCallerTenantAuditAndRetiredFlag(Entry entry) throws SQLException {
        PrdMaterialRequirementDetail request = new PrdMaterialRequirementDetail();
        request.setRequirementId(999L);
        request.setMaterialId(701L);
        request.setRequiredQuantity(new BigDecimal("25.0000"));
        request.setUnit("4");
        request.setTenantId("000001");
        request.setDelFlag("1");
        request.setCreateDept(777L);
        request.setCreateBy(778L);
        request.setCreateTime(new Date(0));
        request.setUpdateBy(779L);
        request.setUpdateTime(new Date(0));
        request.setSearchValue("客户端旧搜索字段");
        request.setParams(Map.of("client", "客户端旧参数"));
        Map<String, Object> original = rawRow(ORIGINAL_ID);
        Map<String, Object> otherTenant = rawRow(OTHER_TENANT_ID);

        assertEquals(1, assertDoesNotThrow(() -> replace(entry, request)));
        List<PrdMaterialRequirementDetail> current = currentRows();
        assertEquals(1, current.size(), "客户端退休标志或租户不能使新替换行消失或串租户");
        PrdMaterialRequirementDetail inserted = current.get(0);
        assertAll(
            () -> assertTrue(inserted.getDetailId() > 0 && inserted.getDetailId() != ORIGINAL_ID),
            () -> assertEquals(REQUIREMENT_ID, inserted.getRequirementId()),
            () -> assertEquals(TENANT, inserted.getTenantId()),
            () -> assertEquals("0", inserted.getDelFlag()),
            () -> assertEquals(ACTOR_ID, inserted.getCreateBy()),
            () -> assertEquals(ACTOR_ID, inserted.getUpdateBy()),
            () -> assertEquals(ACTOR_DEPT, inserted.getCreateDept()),
            () -> assertTrue(inserted.getCreateTime().getTime() > 0),
            () -> assertEquals(inserted.getCreateTime(), inserted.getUpdateTime()),
            () -> assertNull(request.getDetailId(), "新行分配主键不能覆盖请求对象"),
            () -> assertEquals("1", request.getDelFlag(), "只规范新行，不篡改请求快照")
        );
        assertRetiredWithoutPayloadChange(ORIGINAL_ID, original);
        assertEquals(otherTenant, rawRow(OTHER_TENANT_ID));
    }

    @Test
    void ordinaryInsertStillKeepsAnExplicitNewSourceIdentity() {
        PrdMaterialRequirementDetail detail = new PrdMaterialRequirementDetail();
        detail.setDetailId(104L);
        detail.setRequirementId(REQUIREMENT_ID);
        detail.setTenantId(TENANT);
        detail.setDelFlag("0");
        detail.setRequiredQuantity(new BigDecimal("3.0000"));
        assertEquals(1, service.insertPrdMaterialRequirementDetails(List.of(detail)));
        assertEquals(104L, detailMapper.selectById(104L).getDetailId(), "不能泛化改变普通插入路径的身份约定");
    }

    @Test
    void customHistorySqlCanReadRetiredRowsWithLogicDeleteEnabled() {
        assertNull(detailMapper.selectById(ALREADY_RETIRED_ID));
        var history = historyMapper.selectRequirementDetails(TENANT, List.of(ALREADY_RETIRED_ID, OTHER_TENANT_ID));
        assertEquals(1, history.size());
        assertEquals(ALREADY_RETIRED_ID, history.get(0).getDetailId());
        assertEquals("1", history.get(0).getDelFlag());
        assertEquals(new BigDecimal("30.0000"), history.get(0).getRequiredQuantity());
    }

    /** 仅用于内存夹具的时间函数类型适配，不替换被测生产 SQL。 */
    public static BigDecimal unixTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : BigDecimal.valueOf(timestamp.getTime(), 3);
    }
    private int replace(Entry entry, PrdMaterialRequirementDetail detail) {
        if (entry == Entry.BO) {
            PrdMaterialRequirementBo bo = new PrdMaterialRequirementBo();
            bo.setRequirementId(REQUIREMENT_ID);
            bo.setRemark("正常编辑回归");
            bo.setPrdMaterialRequirementDetailList(List.of(detail));
            return service.updateByBo(bo);
        }
        PrdMaterialRequirement entity = new PrdMaterialRequirement();
        entity.setRequirementId(REQUIREMENT_ID);
        entity.setRemark("正常兼容编辑回归");
        entity.setPrdMaterialRequirementDetailList(List.of(detail));
        return service.updatePrdMaterialRequirement(entity);
    }

    private List<PrdMaterialRequirementDetail> currentRows() {
        return detailMapper.selectList(Wrappers.<PrdMaterialRequirementDetail>lambdaQuery()
            .eq(PrdMaterialRequirementDetail::getRequirementId, REQUIREMENT_ID));
    }

    private void assertRetiredWithoutPayloadChange(long id, Map<String, Object> before) throws SQLException {
        Map<String, Object> expected = new LinkedHashMap<>(before);
        expected.put("DEL_FLAG", "1");
        assertEquals(expected, rawRow(id), "旧行除逻辑删除标志外的完整载荷和审计信息必须保留");
    }

    private Map<String, Object> rawRow(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT * FROM prd_material_requirement_detail WHERE detail_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "历史物理行必须存在");
                Map<String, Object> row = new LinkedHashMap<>();
                var columns = rows.getMetaData();
                for (int column = 1; column <= columns.getColumnCount(); column++) {
                    row.put(columns.getColumnLabel(column), rows.getObject(column));
                }
                return row;
            }
        }
    }
}