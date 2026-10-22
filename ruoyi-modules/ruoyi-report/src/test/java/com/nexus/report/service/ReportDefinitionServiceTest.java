package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.config.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.mapper.*;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;

/** 报表定义服务测试，负责当前模块对应的契约或处理职责。 */
class ReportDefinitionServiceTest {
    /** 保存当前 {@code root} 值。 */
    private final ReportActor root = new ReportActor("A", 1, 10, true, List.of());

    /** 保存当前 {@code basic} 值。 */
    private final ReportActor basic =
            new ReportActor(
                    "A",
                    7,
                    10,
                    false,
                    List.of(
                            new ReportRole(
                                    2,
                                    Set.of(
                                            "report:definition:add",
                                            "report:definition:edit",
                                            "report:definition:publish",
                                            "report:definition:list",
                                            "report:dataset:list",
                                            "wm:stock:list"),
                                    ReportScope.all(7))));

    /** 提供者，负责当前模块对应的契约或处理职责。 */
    private static final class Provider implements ReportDatasetProvider {
        /**
         * 读取固定代码契约描述。
         *
         * @return 当前处理返回的固定结果
         */
        public DatasetDescriptor descriptor() {
            return new DatasetDescriptor(
                    "TEST",
                    "测试",
                    1,
                    "wm:stock:list",
                    false,
                    List.of(
                            new DatasetField("code", "编码", ValueType.STRING),
                            new DatasetField(
                                    "amount",
                                    "金额",
                                    ValueType.DECIMAL,
                                    "wm:stock:query",
                                    List.of("currency")),
                            new DatasetField("currency", "币种", ValueType.STRING)),
                    List.of(),
                    List.of("code"));
        }

        /**
         * 执行受控只读查询。
         *
         * @param context 服务端执行上下文
         * @param query 已声明字段和筛选参数
         * @return 当前处理返回的固定结果
         */
        public DatasetResult query(ReportExecutionContext context, DatasetQuery query) {
            return new DatasetResult(List.of(Map.of("code", "001")), 1, Instant.now());
        }
    }

    /**
     * 验证 {@code store} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private JdbcReportStore store() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        try {
            var matcher =
                    Pattern.compile(
                                    "CREATE TABLE IF NOT EXISTS `rpt_[^`]+` \\([\\s\\S]*?\\)"
                                            + " ENGINE[^;]*;")
                            .matcher(
                                    Files.readString(
                                            Path.of(
                                                    "../../script/sql/update/2026-10-14-report-p0.sql")));
            while (matcher.find())
                jdbc.execute(
                        matcher.group()
                                .replaceAll("\\) ENGINE[^;]*;", ");")
                                .replaceAll("(?m)^  KEY `[^`]+` \\([^\\n]+\\),?\\n", "")
                                .replaceAll(",\\n\\)", "\n)"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new JdbcReportStore(jdbc, new ObjectMapper());
    }

    /**
     * 完成 {@code service} 对应的报表定义服务测试处理。
     *
     * @param store 租户化元数据或私有存储
     * @return 当前处理返回的固定结果
     */
    private ReportDefinitionService service(JdbcReportStore store) {
        return new ReportDefinitionService(
                store,
                new ReportProviderRegistry(List.of(new Provider())),
                new ReportAuthorizationPort() {
                    /**
                     * 重新解析当前账户和业务权限。
                     *
                     * @param tenant 当前租户标识
                     * @param user 当前操作的 {@code user} 输入
                     * @return 当前处理返回的固定结果
                     */
                    public ReportActor resolve(String tenant, long user) {
                        return user == 1 ? root : basic;
                    }

                    /**
                     * 校验授权主体属于当前租户。
                     *
                     * @param tenant 当前租户标识
                     * @param type 当前操作的 {@code type} 输入
                     * @param id 资源标识
                     * @return 当前检查或条件操作是否成功
                     */
                    public boolean subjectExists(String tenant, String type, long id) {
                        return tenant.equals("A") && id > 0;
                    }
                },
                new ReportCompiler(new ObjectMapper()));
    }

    /**
     * 完成 {@code dsl} 对应的报表定义服务测试处理。
     *
     * @param version 固定版本
     * @param field 当前操作的 {@code field} 输入
     * @return 当前处理返回的固定结果
     */
    private ReportDsl dsl(String version, String field) {
        return new ReportDsl(
                1,
                "TEST_REPORT",
                "测试",
                ReportDsl.Kind.TABLE,
                "TEST",
                version,
                List.of(new ReportDsl.Column(field, field, 100, 2, null, null)),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                true);
    }

    /** 验证 {@code fieldPrivilegesAreCheckedBeforeCreatingOrPublishingADefinition} 场景下的结果与边界条件。 */
    @Test
    void fieldPrivilegesAreCheckedBeforeCreatingOrPublishingADefinition() {
        var store = store();
        var service = service(store);
        var dataset = service.registerDataset(root, "TEST", 1);
        store.replaceAcl(
                root,
                "DATASET",
                dataset.id(),
                List.of(new ReportRecords.Grant("USER", "7", "VIEW")));
        var fault =
                assertThrows(
                        ReportFault.class,
                        () ->
                                service.create(
                                        basic, dsl(dataset.publishedVersion(), "amount"), null));
        assertEquals("SCOPE_DENIED", fault.code());
        var definition = service.create(basic, dsl(dataset.publishedVersion(), "code"), null);
        assertEquals("DRAFT", definition.status());
        service.publish(basic, definition.id(), 1);
        assertEquals("PUBLISHED", store.definition("A", definition.id()).orElseThrow().status());
    }

    /**
     * 验证 {@code datasetAclAndReportAclAreIndependentAndCrossTenantCannotUseAVersion} 场景下的结果与边界条件。
     */
    @Test
    void datasetAclAndReportAclAreIndependentAndCrossTenantCannotUseAVersion() {
        var store = store();
        var service = service(store);
        var dataset = service.registerDataset(root, "TEST", 1);
        assertThrows(
                ReportFault.class,
                () -> service.create(basic, dsl(dataset.publishedVersion(), "code"), null));
        var other = new ReportActor("B", 1, 10, true, List.of());
        assertThrows(
                ReportFault.class,
                () -> service.create(other, dsl(dataset.publishedVersion(), "code"), null));
    }

    /** 验证 {@code descriptorCatalogRejectsDuplicateProviderVersions} 场景下的结果与边界条件。 */
    @Test
    void descriptorCatalogRejectsDuplicateProviderVersions() {
        assertThrows(
                IllegalStateException.class,
                () -> new ReportProviderRegistry(List.of(new Provider(), new Provider())));
    }
}
