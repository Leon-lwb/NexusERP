package com.nexus.mes.label.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelPrintTask;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 标签持久化测试公共夹具，仅使用随机命名的内存数据库和模拟打印机。
 */
class LabelDurableTestSupport {
    JdbcTemplate jdbc;
    TransactionTemplate business;
    LabelPrintTaskStore store;
    LabelPrinterTransport transport;
    LabelPrintDispatcher dispatcher;
    LabelPrintService service;
    AtomicInteger sends = new AtomicInteger();
    MockedStatic<SpringUtil> spring;
    MockedStatic<LoginHelper> login;

    @BeforeEach
    void setup() throws Exception {
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getProperty("tenant.enable")).thenReturn("true");
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isLogin).thenReturn(false);
        TenantHelper.setDynamic("000001");
        var source = new JdbcDataSource();
        source.setURL(
                "jdbc:h2:mem:label_"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        business = new TransactionTemplate(manager);
        for (String name : new String[] {"mes_label_print_log", "mes_label_print_task"}) {
            createTable(name);
        }
        MesProgressTransactionGuard guard = mock(MesProgressTransactionGuard.class);
        doAnswer(
                        inv -> {
                            assertTrue(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            assertEquals("mes", DynamicDataSourceContextHolder.peek());
                            return null;
                        })
                .when(guard)
                .requireMesConnection();
        store = new LabelPrintTaskStore(jdbc, guard);
        transport = mock(LabelPrinterTransport.class);
        when(transport.send(any()))
                .thenAnswer(
                        inv -> {
                            assertFalse(
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    "网络发送不得持有数据库事务");
                            sends.incrementAndGet();
                            return new LabelPrinterTransport.Result("SENT", null);
                        });
        dispatcher = new LabelPrintDispatcher(store, transport);
        service = new LabelPrintService(store, dispatcher);
    }

    @AfterEach
    void close() {
        TenantHelper.clearDynamic();
        jdbc.execute("DROP ALL OBJECTS");
        login.close();
        spring.close();
        DynamicDataSourceContextHolder.clear();
    }

    void createTable(String name) throws Exception {
        String ddl =
                table(Files.readString(installSchema()), name)
                        .replace(" USING BTREE", "")
                        .replaceAll("(?i) CHARACTER SET [a-zA-Z0-9_]+", "")
                        .replaceAll("(?i) COLLATE[ =]+[a-zA-Z0-9_]+", "")
                        .replaceAll("(?s)[)] ENGINE=.*", ")");
        jdbc.execute(ddl);
    }

    /** 从测试工作目录向上定位同一工作树的生成DDL，兼容Surefire模块目录。 */
    private static Path installSchema() throws IOException {
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            Path schema = directory.resolve("script/sql/ry-mes.sql");
            if (Files.isRegularFile(schema)) {
                return schema;
            }
        }
        throw new NoSuchFileException("无法从测试目录定位MES安装DDL: " + start);
    }

    int count(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + name, Integer.class);
    }

    MesLabelPrintTask onlyTask() {
        return store.find(
                jdbc.queryForObject("SELECT task_id FROM mes_label_print_task", Long.class));
    }

    MesLabelPrintLog record(String source) {
        MesLabelPrintLog record =
                MesLabelPrintLog.of(1L, "SN-1", 5L, "TPL", "synthetic", 9100, source);
        record.setTenantId("000001");
        record.setTemplateRevision(7L);
        record.setSnapshotMode("LEGACY_DIRECT");
        return record;
    }

    MesLabelPrintLog recordWithPayload(String source, String payload) {
        MesLabelPrintLog record = record(source);
        LabelPrintSnapshot.payload(record, payload, StandardCharsets.UTF_8);
        return record;
    }

    String table(String sql, String name) {
        var matcher =
                Pattern.compile(
                                "CREATE TABLE(?: IF NOT EXISTS)? `"
                                        + name
                                        + "` \\([\\s\\S]*?\\n\\)[^;]*;")
                        .matcher(sql);
        assertTrue(matcher.find(), name);
        return matcher.group();
    }
}
