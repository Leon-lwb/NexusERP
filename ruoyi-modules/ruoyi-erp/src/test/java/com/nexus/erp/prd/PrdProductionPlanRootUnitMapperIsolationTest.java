package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedConstruction;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 同 JVM 先建立真实前一 H2 工厂及自定义生成器，再直接调用被审查夹具的实际生命周期。
 * 外层快照只清理探针自己产生的状态；恢复断言发生于外层清理前，不能掩盖被测夹具泄漏。
 */
@Isolated("同 JVM 验证 MyBatis-Plus 全局缓存和编号生成器")
class PrdProductionPlanRootUnitMapperIsolationTest {
    private PrdProductionPlanMapperGlobalState beforeProbe;
    private PrdProductionPlanMapperGlobalState beforeFixture;
    private JdbcTemplate priorJdbc;
    private SqlSessionFactory priorFactory;
    private IdentifierGenerator priorGenerator;

    interface FactoryProbeMapper {
        @Select("SELECT 42")
        int marker();
    }

    @BeforeEach
    void setUpPriorFactory() {
        beforeProbe = PrdProductionPlanMapperGlobalState.capture();
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:prior_factory_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        priorJdbc = new JdbcTemplate(source);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("仅探针前一工厂", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        priorGenerator = entity -> 424242L;
        GlobalConfig global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0"));
        global.setSqlInjector(new DefaultSqlInjector());
        global.setIdentifierGenerator(priorGenerator);
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        configuration.addMapper(BdMaterialMapper.class);
        configuration.addMapper(BdUnitOfMeasureMapper.class);
        configuration.addMapper(PrdWorkOrderMapper.class);
        configuration.addMapper(FactoryProbeMapper.class);
        priorFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        beforeFixture = PrdProductionPlanMapperGlobalState.capture();
        assertPriorFactory("夹具启动前");
    }

    @AfterEach
    void cleanOnlyProbeState() {
        try {
            if (priorJdbc != null) priorJdbc.execute("SHUTDOWN");
        } finally {
            if (beforeProbe != null) beforeProbe.close();
        }
    }

    @Test
    @DisplayName("实际夹具正常关闭后恢复前一工厂全部缓存与原生成器")
    void normalFixtureCloseRestoresPriorFactoryAndGenerator() {
        PrdProductionPlanRootUnitMapperTest fixture = new PrdProductionPlanRootUnitMapperTest();
        try {
            fixture.setUp();
            assertNotSame(priorFactory.getConfiguration(), TableInfoHelper.getTableInfo(BdMaterial.class).getConfiguration(),
                "RED/GREEN 都必须真的发生过前一 TableInfo 替换");
            assertNotEquals(424242L, IdWorker.getId(), "夹具构建必须真的替换过全局生成器");
            fixture.productionMappersPersistTrustedRootUnitWithTenantSql();
        } finally {
            fixture.tearDown();
        }
        beforeFixture.assertRestored();
        assertPriorFactory("正常关闭后");
    }

    @Test
    @DisplayName("真实工厂已构建后 setup 失败必须立即清理恢复而非等待下一测试")
    void setupFailureAfterFactoryBuildRestoresBeforeRethrow() {
        PrdProductionPlanRootUnitMapperTest fixture = new PrdProductionPlanRootUnitMapperTest();
        IllegalStateException failure = new IllegalStateException("探针：真实工厂构建后 setup 失败");
        try (MockedConstruction<SqlSessionTemplate> ignored = mockConstruction(SqlSessionTemplate.class,
            (template, context) -> when(template.getMapper(BdMaterialMapper.class)).thenThrow(failure))) {
            assertSame(failure, assertThrows(IllegalStateException.class, fixture::setUp));
            // 构造参数中的真实 build 已执行；这里只在其后的模板取 Mapper 边界注入失败。
            beforeFixture.assertRestored();
            assertPriorFactory("setup 失败返回后");
        } finally {
            fixture.tearDown();
        }
    }

    @Test
    @DisplayName("SHUTDOWN 清理抛错也必须 finally 恢复全部缓存及生成器")
    void cleanupFailureStillRestoresPriorFactoryAndGenerator() {
        PrdProductionPlanRootUnitMapperTest fixture = new PrdProductionPlanRootUnitMapperTest();
        JdbcTemplate actualJdbc = null;
        try {
            fixture.setUp();
            actualJdbc = (JdbcTemplate) ReflectionTestUtils.getField(fixture, "jdbc");
            JdbcTemplate failingShutdown = mock(JdbcTemplate.class);
            IllegalStateException failure = new IllegalStateException("探针：SHUTDOWN 清理失败");
            doThrow(failure).when(failingShutdown).execute("SHUTDOWN");
            // 只反射测试夹具自身字段，绝不反射生产服务或运行 JVM。
            ReflectionTestUtils.setField(fixture, "jdbc", failingShutdown);
            assertSame(failure, assertThrows(IllegalStateException.class, fixture::tearDown));
            beforeFixture.assertRestored();
            assertPriorFactory("清理失败返回后");
        } finally {
            if (actualJdbc != null) actualJdbc.execute("SHUTDOWN");
        }
    }

    private void assertPriorFactory(String stage) {
        for (Class<?> entity : new Class<?>[] {BdMaterial.class, BdUnitOfMeasure.class, PrdWorkOrder.class}) {
            assertSame(priorFactory, GlobalConfigUtils.currentSessionFactory(entity),
                stage + "：不能把关闭后的夹具工厂泄漏给后续同 JVM 测试");
        }
        assertEquals(424242L, IdWorker.getId(), stage + "：原自定义 IdentifierGenerator 行为必须不变");
        try (var session = priorFactory.openSession()) {
            assertEquals(42, session.getMapper(FactoryProbeMapper.class).marker(), "前一真实工厂必须继续可用");
        }
        System.out.println("同 JVM 前一工厂探针：" + stage + "，真实查询=42，原生成器=424242，工厂归属=3/3");
    }
}
