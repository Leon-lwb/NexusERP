package com.nexus.erp.wm.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmStockTakingPlan;
import com.nexus.erp.wm.domain.bo.WmStockTakingPlanBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStockTakingLineMapper;
import com.nexus.erp.wm.mapper.WmStockTakingPlanMapper;

import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.util.*;

class WmStockTakingCountSafetyTest {
    private JdbcDataSource source;
    private JdbcTemplate jdbc;
    private WmStockTakingPlanMapper plans;
    private WmStockTakingLineMapper lines;
    private WmStockTakingPlanServiceImpl service;
    private WmStockOperationServiceImpl posting;
    @BeforeEach void setup() {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:counts_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"); jdbc = new JdbcTemplate(source);
        MybatisConfiguration c = new MybatisConfiguration(); c.setMapUnderscoreToCamelCase(true); c.setLogImpl(NoLoggingImpl.class); c.setEnvironment(new Environment("实盘内存回归", new SpringManagedTransactionFactory(), source));
        GlobalConfig g = new GlobalConfig(); g.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0")); g.setSqlInjector(new DefaultSqlInjector()); GlobalConfigUtils.setGlobalConfig(c, g);
        c.addMapper(WmStockTakingPlanMapper.class); c.addMapper(WmStockTakingLineMapper.class); createTable(WmStockTakingPlan.class); createTable(WmStockTakingLine.class);
        var s = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(c)); plans = s.getMapper(WmStockTakingPlanMapper.class); lines = s.getMapper(WmStockTakingLineMapper.class);
        posting = mock(WmStockOperationServiceImpl.class);
        var factory =
                new ProxyFactory(
                        new WmStockTakingPlanServiceImpl(
                                plans,
                                lines,
                                mock(WmMaterialStockMapper.class),
                                posting,
                                mock(WmServiceHelper.class)));
        factory.setProxyTargetClass(true);
        factory.addAdvice(
                new TransactionInterceptor(
                        new DataSourceTransactionManager(source),
                        new AnnotationTransactionAttributeSource()));
        service = (WmStockTakingPlanServiceImpl) factory.getProxy();
        WmStockTakingPlan p = new WmStockTakingPlan(); p.setPlanId(11L); p.setStatus("GENERATED"); p.setWarehouseId(2L); plans.insert(p);
        WmStockTakingLine l = new WmStockTakingLine(); l.setLineId(21L); l.setPlanId(11L); l.setMaterialId(23L); l.setBookQuantity(new BigDecimal("4")); l.setStatus("PENDING"); lines.insert(l);
    }
    @AfterEach void cleanup() throws Exception { try (var c = source.getConnection(); var s = c.createStatement()) { s.execute("SHUTDOWN"); } }
    @Test void generatedPlanSavesActualCountWithoutReplacingIdentityOrBookSnapshot() {
        WmStockTakingPlanBo bo = form("3"); bo.setWarehouseId(999L); bo.setStatus("POSTED"); bo.getWmStockTakingLineList().get(0).setMaterialId(999L); bo.getWmStockTakingLineList().get(0).setBookQuantity(new BigDecimal("999"));
        assertDoesNotThrow(() -> service.updateByBo(bo));
        WmStockTakingLine actual = lines.selectById(21L); assertEquals(23L, actual.getMaterialId()); assertEquals(new BigDecimal("4.0000"), actual.getBookQuantity()); assertEquals(new BigDecimal("3.0000"), actual.getActualQuantity()); assertEquals(new BigDecimal("-1.0000"), actual.getDifferenceQuantity()); assertEquals(1L, lines.selectCount(null)); assertEquals("GENERATED", plans.selectById(11L).getStatus()); assertEquals(2L, plans.selectById(11L).getWarehouseId());
    }
    @Test void anotherPlansLineCannotBeCounted() {
        WmStockTakingPlanBo bo = form("3"); bo.getWmStockTakingLineList().get(0).setLineId(99L);
        assertThrows(ServiceException.class, () -> service.updateByBo(bo)); assertNull(lines.selectById(21L).getActualQuantity());
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "0.00001"}) void invalidCountCannotBeRoundedOrPersisted(String value) {
        assertThrows(ServiceException.class, () -> service.updateByBo(form(value))); assertNull(lines.selectById(21L).getActualQuantity());
    }
    @Test void manualPostingCannotExecuteAnApprovingPlan() {
        jdbc.update("UPDATE wm_stock_taking_plan SET status='APPROVING' WHERE plan_id=11"); jdbc.update("UPDATE wm_stock_taking_line SET actual_quantity=3 WHERE line_id=21");
        assertThrows(RuntimeException.class, () -> service.postAdjustment(11L)); assertEquals("APPROVING", plans.selectById(11L).getStatus()); verifyNoInteractions(posting);
    }
    @Test void planWithApprovalFlowCannotUseManualPostingBeforeApproval() {
        jdbc.update("UPDATE wm_stock_taking_plan SET select_flow_id=31 WHERE plan_id=11"); jdbc.update("UPDATE wm_stock_taking_line SET actual_quantity=3 WHERE line_id=21");
        assertThrows(RuntimeException.class, () -> service.postAdjustment(11L)); assertEquals("GENERATED", plans.selectById(11L).getStatus()); verifyNoInteractions(posting);
    }
    private WmStockTakingPlanBo form(String actual) { WmStockTakingPlanBo b = new WmStockTakingPlanBo(); b.setPlanId(11L); WmStockTakingLine l = new WmStockTakingLine(); l.setLineId(21L); l.setPlanId(11L); l.setActualQuantity(new BigDecimal(actual)); b.setWmStockTakingLineList(List.of(l)); return b; }
    private void createTable(Class<?> entity) { var t = TableInfoHelper.getTableInfo(entity); List<String> columns = new ArrayList<>(); columns.add(t.getKeyColumn() + " BIGINT PRIMARY KEY"); t.getFieldList().forEach(f -> { Class<?> type = f.getPropertyType(); String sql = type == BigDecimal.class ? "DECIMAL(20,4)" : Date.class.isAssignableFrom(type) ? "TIMESTAMP" : Number.class.isAssignableFrom(type) ? "BIGINT" : "VARCHAR(500)"; columns.add(f.getColumn() + " " + sql + ("del_flag".equals(f.getColumn()) ? " DEFAULT '0'" : "")); }); jdbc.execute("CREATE TABLE " + t.getTableName() + "(" + String.join(",", columns) + ")"); }
}
