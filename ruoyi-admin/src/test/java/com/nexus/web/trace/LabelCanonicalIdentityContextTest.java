package com.nexus.web.trace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.erp.wm.trace.ErpSerialIdentityService;
import com.nexus.erp.wm.trace.ErpTraceContext;
import com.nexus.mes.label.domain.MesLabelPrintTask;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.listener.SnLabelPrintEventListener;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.LabelPrintDispatcher;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelPrintTaskStore;
import com.nexus.mes.label.service.LabelPrinterTransport;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 使用真实ERP身份实现、动态数据源与事务代理验证校准读取不会占用或替换MES业务连接。
 */
class LabelCanonicalIdentityContextTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate business;
    private LabelPrintTaskStore store;
    private LabelPrinterTransport transport;
    private LabelPrintDispatcher dispatcher;
    private LabelPrintService service;
    private final AtomicInteger sends = new AtomicInteger();
    private MockedStatic<SpringUtil> properties;
    private MockedStatic<LoginHelper> login;
    private JdbcTemplate erp;
    private DynamicRoutingDataSource routing;
    private SnLabelPrintEventListener listener;
    private Object mesTransaction;
    private int reads;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MesLabelTemplate.class);
    }

    @BeforeEach
    void routedIdentityFixture() throws Exception {
        initializeLabelFixture();
        var erpSource = new JdbcDataSource();
        erpSource.setURL(
                "jdbc:h2:mem:label_identity_"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        erp = new JdbcTemplate(erpSource);
        erp.execute(
                "CREATE TABLE wm_sn(tenant_id VARCHAR(20),sn_id BIGINT,sn_code"
                        + " VARCHAR(100),material_id BIGINT,batch_id BIGINT,batch_code"
                        + " VARCHAR(100),del_flag CHAR(1))");
        erp.update("INSERT INTO wm_sn VALUES('000001',1001,'ERP-FORMAL',100,NULL,NULL,'0')");
        routing = new DynamicRoutingDataSource(List.of());
        routing.setPrimary("mes");
        routing.setStrict(true);
        routing.addDataSource("mes", jdbc.getDataSource());
        routing.addDataSource("erp", erpSource);
        jdbc = new JdbcTemplate(routing);
        var transactions = new DataSourceTransactionManager(routing);
        business = new TransactionTemplate(transactions);
        var lookupJdbc =
                new JdbcTemplate(routing) {
                    @Override
                    public List<Map<String, Object>> queryForList(String sql, Object... args) {
                        assertEquals("erp", DynamicDataSourceContextHolder.peek());
                        assertTrue(
                                TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                        assertNotSame(
                                mesTransaction,
                                TransactionSynchronizationManager.getResource(routing));
                        assertEquals("000001", TenantHelper.getTenantId());
                        try {
                            assertEquals(
                                    erpSource.getURL().split(";")[0],
                                    DataSourceUtils.getConnection(routing).getMetaData().getURL());
                        } catch (SQLException failure) {
                            throw new IllegalStateException(failure);
                        }
                        reads++;
                        return super.queryForList(sql, args);
                    }
                };
        var registry =
                new ProxyFactory(
                        new ErpSerialIdentityService(
                                lookupJdbc,
                                new ObjectMapper(),
                                new ErpTraceContext(mock(SqlSessionFactory.class)),
                                transactions));
        registry.setProxyTargetClass(true);
        registry.addAdvisor(
                new DynamicDataSourceAnnotationAdvisor(
                        new DynamicDataSourceAnnotationInterceptor(true, null), DS.class));
        registry.addAdvice(
                new TransactionInterceptor(
                        transactions, new AnnotationTransactionAttributeSource()));
        var renderer = new LabelRenderService((SerialIdentityPort) registry.getProxy());
        var guard = mock(MesProgressTransactionGuard.class);
        doAnswer(
                        call -> {
                            assertEquals("mes", DynamicDataSourceContextHolder.peek());
                            assertFalse(
                                    TransactionSynchronizationManager
                                            .isCurrentTransactionReadOnly());
                            return null;
                        })
                .when(guard)
                .requireMesConnection();
        store = new LabelPrintTaskStore(jdbc, guard);
        dispatcher = new LabelPrintDispatcher(store, transport);
        service = new LabelPrintService(store, dispatcher);
        var templates = mock(MesLabelTemplateMapper.class);
        var template = new MesLabelTemplate();
        template.setTemplateId(5L);
        template.setTemplateRevision(7L);
        template.setLabelType("SN");
        template.setContent("{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\"}]}");
        when(templates.selectList(any())).thenReturn(List.of(template));
        var sns = mock(ProSnMapper.class);
        var sn = new ProSn();
        sn.setTenantId("000001");
        sn.setSnId(1L);
        sn.setIdentityId(1001L);
        sn.setMaterialId(100L);
        sn.setSnCode("LOCAL-OLD");
        when(sns.selectById(1L)).thenReturn(sn);
        listener = new SnLabelPrintEventListener(templates, sns, renderer, service);
    }

    @AfterEach
    void clearFixtures() {
        try {
            if (erp != null) {
                erp.execute("DROP ALL OBJECTS");
            }
            if (jdbc != null) {
                jdbc.execute("DROP ALL OBJECTS");
            }
        } finally {
            TenantHelper.clearDynamic();
            DynamicDataSourceContextHolder.clear();
            if (login != null) {
                login.close();
            }
            if (properties != null) {
                properties.close();
            }
        }
    }

    /**
     * Admin自含夹具只读主DDL并创建随机内存库，不依赖任何MES测试类或编译输出。
     */
    private void initializeLabelFixture() throws Exception {
        properties = mockStatic(SpringUtil.class);
        properties.when(() -> SpringUtil.getProperty("tenant.enable")).thenReturn("true");
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isLogin).thenReturn(false);
        TenantHelper.setDynamic("000001");
        var mes = new JdbcDataSource();
        mes.setURL(
                "jdbc:h2:mem:admin_label_"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(mes);
        String schema = Files.readString(installSchema());
        for (String name : new String[] {"mes_label_print_log", "mes_label_print_task"}) {
            var matcher =
                    Pattern.compile(
                                    "CREATE TABLE(?: IF NOT EXISTS)? `"
                                            + name
                                            + "` \\([\\s\\S]*?\\n\\)[^;]*;")
                            .matcher(schema);
            assertTrue(matcher.find(), name);
            jdbc.execute(
                    matcher.group()
                            .replace(" USING BTREE", "")
                            .replaceAll("(?i) CHARACTER SET [a-zA-Z0-9_]+", "")
                            .replaceAll("(?i) COLLATE[ =]+[a-zA-Z0-9_]+", "")
                            .replaceAll("(?s)[)] ENGINE=.*", ")"));
        }
        transport = mock(LabelPrinterTransport.class);
        when(transport.send(any()))
                .thenAnswer(
                        call -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            sends.incrementAndGet();
                            return new LabelPrinterTransport.Result("SENT", null);
                        });
    }

    private static Path installSchema() throws IOException {
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            Path schema = directory.resolve("script/sql/ry-mes.sql");
            if (Files.isRegularFile(schema)) {
                return schema;
            }
        }
        throw new NoSuchFileException("无法从admin测试目录定位同工作树MES安装DDL: " + start);
    }

    private int count(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + name, Integer.class);
    }

    private MesLabelPrintTask onlyTask() {
        return store.find(
                jdbc.queryForObject("SELECT task_id FROM mes_label_print_task", Long.class));
    }

    @Test
    void actualErpProxySuspendsReadOnlyLookupAndRestoresOriginalMesTaskTransaction() {
        DynamicDataSourceContextHolder.push("mes");
        try {
            business.executeWithoutResult(
                    tx -> {
                        mesTransaction = TransactionSynchronizationManager.getResource(routing);
                        listener.onSnCreated(new SnCreatedEvent(1L, "LOCAL-OLD", null, 100L));
                        assertRestoredMes();
                        assertEquals(1, count("mes_label_print_task"));
                        assertEquals(0, sends.get());
                    });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        assertEquals(1, reads);
        assertEquals("ERP-FORMAL", store.record(onlyTask()).getSnCode());
        assertEquals("PENDING", onlyTask().getStatus());
        assertNull(DynamicDataSourceContextHolder.peek());
        assertEquals(
                0,
                erp.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                + " table_name='mes_label_print_task'",
                        Integer.class));
    }

    @Test
    void absentFormalIdentityRestoresMesContextAndRollsBackBusinessAndTask() {
        erp.update("DELETE FROM wm_sn");
        jdbc.execute("CREATE TABLE synthetic_business(id BIGINT)");
        DynamicDataSourceContextHolder.push("mes");
        try {
            assertThrows(
                    ServiceException.class,
                    () ->
                            business.executeWithoutResult(
                                    tx -> {
                                        mesTransaction =
                                                TransactionSynchronizationManager.getResource(
                                                        routing);
                                        jdbc.update("INSERT INTO synthetic_business VALUES(1)");
                                        try {
                                            listener.onSnCreated(
                                                    new SnCreatedEvent(
                                                            1L, "LOCAL-OLD", null, 100L));
                                        } finally {
                                            assertRestoredMes();
                                        }
                                    }));
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        assertEquals(1, reads);
        assertEquals(0, count("synthetic_business"));
        assertEquals(0, count("mes_label_print_task"));
        assertEquals(0, sends.get());
        assertEquals("000001", TenantHelper.getTenantId());
    }

    private void assertRestoredMes() {
        assertSame(mesTransaction, TransactionSynchronizationManager.getResource(routing));
        assertEquals("mes", DynamicDataSourceContextHolder.peek());
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        assertEquals("000001", TenantHelper.getTenantId());
    }
}
