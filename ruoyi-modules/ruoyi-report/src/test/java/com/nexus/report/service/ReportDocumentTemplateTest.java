package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.mapper.JdbcReportStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** 验证新增ERP单据模板的业务绑定、必填参数和安全默认列。 */
class ReportDocumentTemplateTest {
    /** 验证销售单模板展示单号、客户、日期、交期和币种，金额不进入默认输出。 */
    @Test
    void salesDocumentGetsBusinessBandsAndCompilesWithSafeDefaults() {
        var descriptor = sales();
        var template = ReportTemplates.initial(descriptor, "99", "BASE_ERP_SALES_ORDER_DOCUMENT");
        assertEquals(ReportDsl.Kind.DOCUMENT, template.kind());
        String header = text(template.headerRows());
        assertTrue(header.contains("销售单号：{{orderNo}}"));
        for (String field : List.of("clientName", "orderDate", "promiseDate", "currency")) {
            assertTrue(header.contains("{{" + field + "}}"), "缺少页眉业务字段：" + field);
        }
        assertTrue(text(template.footerRows()).contains("客户确认："));
        assertSafe(template, descriptor, "orderId");
    }

    /** 验证收货模板展示供应商、采购来源、仓库和日期，页脚为收货确认。 */
    @Test
    void receiveDocumentGetsReceiptBandsWithoutMoneyFields() {
        var descriptor = receive();
        var template = ReportTemplates.initial(descriptor, "99", "BASE_ERP_PURCHASE_RECEIVE_DOCUMENT");
        assertEquals(ReportDsl.Kind.DOCUMENT, template.kind());
        String header = text(template.headerRows());
        assertTrue(header.contains("收货单号：{{receiveNo}}"));
        for (String field : List.of("vendorName", "purchaseOrderNo", "warehouseName", "receiveDate")) {
            assertTrue(header.contains("{{" + field + "}}"), "缺少页眉业务字段：" + field);
        }
        assertTrue(text(template.footerRows()).contains("收货确认："));
        assertSafe(template, descriptor, "receiveId");
    }

    /** 验证正常基础模板目录为两个新单据各生成稳定且独立的基础编码。 */
    @Test
    void foundationAddsIndependentDocumentCodesAndPreservesPublishedDatasetReference() {
        for (var descriptor : List.of(sales(), receive())) {
            var templates = ReportTemplates.foundation(descriptor, "99");
            assertEquals(1, templates.size());
            assertEquals("BASE_" + descriptor.code(), templates.get(0).code());
            assertEquals("99", templates.get(0).datasetVersionId());
            assertEquals(descriptor.code(), templates.get(0).datasetCode());
        }
    }

    /** 验证显式初始化增量加入收货模板，并完整保留同编码人工修改的销售模板。 */
    @Test
    void bootstrapAddsMissingDocumentsAndKeepsExistingSameCodeUntouched() throws Exception {
        var actor = new ReportActor("A", 7, 10, true, List.of());
        var store = store();
        var firstService = service(store, actor, List.of(sales()));
        var original = firstService.bootstrap(actor).get(0);
        var originalDsl = new ReportCompiler(new ObjectMapper()).parse(original.json());
        var customized = new ReportDsl(
                originalDsl.schemaVersion(),
                originalDsl.code(),
                "人工维护的销售模板",
                originalDsl.kind(),
                originalDsl.datasetCode(),
                originalDsl.datasetVersionId(),
                originalDsl.columns(),
                originalDsl.parameters(),
                originalDsl.page(),
                originalDsl.headerRows(),
                originalDsl.footerRows(),
                originalDsl.summaries(),
                originalDsl.repeatHeader());
        var saved = firstService.save(actor, original.id(), original.revision(), customized);
        firstService.publish(actor, saved.id(), saved.revision());
        var preserved = firstService.detail(actor, saved.id());
        var publishedVersions = firstService.versions(actor, saved.id());

        var upgradedService = service(store, actor, List.of(sales(), receive()));
        var upgraded = upgradedService.bootstrap(actor);
        assertEquals(2, upgraded.size());
        assertEquals(preserved, upgradedService.detail(actor, saved.id()));
        assertEquals(publishedVersions, upgradedService.versions(actor, saved.id()));
        var receipt = upgraded.stream()
                .filter(definition -> definition.code().equals("BASE_ERP_PURCHASE_RECEIVE_DOCUMENT"))
                .findFirst()
                .orElseThrow();
        assertEquals("PUBLISHED", receipt.status());
        assertEquals("DOCUMENT", receipt.kind());
        var receiptDsl = new ReportCompiler(new ObjectMapper()).parse(receipt.json());
        assertTrue(text(receiptDsl.headerRows()).contains("收货单号：{{receiveNo}}"));
        assertEquals(upgraded, upgradedService.bootstrap(actor));
    }

    /**
     * 以真实报表九表结构创建隔离H2，仅写入合成元数据。
     *
     * @return 当前测试私有的报表元数据存储
     * @throws Exception 安装结构定位或建表失败
     */
    private JdbcReportStore store() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("script/sql/update/2026-10-14-report-p0.sql"))) {
            root = root.getParent();
        }
        assertNotNull(root, "报表测试需要仓库安装结构");
        var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS `rpt_[^`]+` \\([\\s\\S]*?\\) ENGINE[^;]*;")
                .matcher(Files.readString(root.resolve("script/sql/update/2026-10-14-report-p0.sql")));
        int tables = 0;
        while (matcher.find()) {
            jdbc.execute(matcher.group().replaceAll("\\) ENGINE[^;]*;", ");")
                    .replaceAll("(?m)^  KEY `[^`]+` \\([^\\n]+\\),?\\n", "")
                    .replaceAll(",\\n\\)", "\n)"));
            tables++;
        }
        assertEquals(9, tables);
        return new JdbcReportStore(jdbc, new ObjectMapper());
    }

    /**
     * 装配实际初始化服务，使用固定合成契约并禁止读取业务事实。
     *
     * @param store 当前测试的私有H2存储
     * @param actor 当前合成管理员
     * @param descriptors 本轮已安装的固定契约
     * @return 无外部服务依赖的实际报表定义服务
     */
    private ReportDefinitionService service(
            JdbcReportStore store, ReportActor actor,
            List<DatasetDescriptor> descriptors) {
        var providers = descriptors.stream()
                .<ReportDatasetProvider>map(descriptor -> new ReportDatasetProvider() {
            /**
             * 读取固定合成单据契约。
             *
             * @return 不可变单据描述
             */
            @Override
            public DatasetDescriptor descriptor() {
                return descriptor;
            }

            /**
             * 初始化仅创建模板，任何业务查询均使测试失败。
             *
             * @param context 当前合成身份
             * @param query 查询参数
             * @return 本测试禁止返回业务数据
             */
            @Override
            public DatasetResult query(ReportExecutionContext context, DatasetQuery query) {
                throw new AssertionError("初始化不得查询业务事实");
            }
        }).toList();
        var authorization = new ReportAuthorizationPort() {
            /**
             * 返回本测试固定管理员，保持初始化时的实时身份解析。
             *
             * @param tenant 当前租户
             * @param user 当前用户
             * @return 合成管理员
             */
            @Override
            public ReportActor resolve(String tenant, long user) {
                assertEquals(actor.tenantId(), tenant);
                assertEquals(actor.userId(), user);
                return actor;
            }

            /**
             * 验证授权主体只属于合成租户。
             *
             * @param tenant 当前租户
             * @param type 授权主体类型
             * @param identifier 授权主体标识
             * @return 主体属于本测试租户时为真
             */
            @Override
            public boolean subjectExists(String tenant, String type, long identifier) {
                return actor.tenantId().equals(tenant) && identifier > 0;
            }
        };
        return new ReportDefinitionService(store, new ReportProviderRegistry(providers), authorization,
                new ReportCompiler(new ObjectMapper()));
    }

    /**
     * 验证模板列宽、必填参数、金额隔离和严格编译结果。
     *
     * @param template 待校验的单据模板
     * @param descriptor 仅含安全字段的合成契约
     * @param parameter 必填单据参数
     */
    private void assertSafe(ReportDsl template, DatasetDescriptor descriptor, String parameter) {
        assertEquals(
                descriptor.defaultColumns(),
                template.columns().stream().map(ReportDsl.Column::field).toList());
        assertTrue(template.columns().stream().noneMatch(column ->
                column.field().contains("Amount") || column.field().contains("Price")));
        assertEquals(parameter, template.parameters().get(0).name());
        assertTrue(template.parameters().get(0).required());
        assertNull(template.parameters().get(0).defaultValue());
        assertEquals(
                template.page().usableWidth(),
                template.columns().stream().mapToDouble(ReportDsl.Column::width).sum(),
                0.001);
        var actor = new ReportActor("A", 7, 10, false, List.of(new ReportRole(2,
                Set.of(descriptor.permission()), ReportScope.all(7))));
        assertDoesNotThrow(
                () -> new ReportCompiler(new ObjectMapper()).compile(template, descriptor, actor));
    }

    /**
     * 构造销售单安全字段契约，模拟金额字段被裁剪后的模板输入。
     *
     * @return 销售单据合成契约
     */
    private DatasetDescriptor sales() {
        var fields = detailFields();
        for (String name : List.of("orderNo", "clientName", "currency")) {
            fields.add(new DatasetField(name, name, ValueType.STRING));
        }
        for (String name : List.of("orderDate", "promiseDate")) {
            fields.add(new DatasetField(name, name, ValueType.DATE));
        }
        return new DatasetDescriptor("ERP_SALES_ORDER_DOCUMENT", "销售订单打印", 1, "so:orderheader:query", true,
                fields, List.of(new DatasetParameter("orderId", "销售订单", ValueType.ID, true, null)),
                List.of("materialCode", "materialName", "specification", "quantity", "unitName"));
    }

    /**
     * 构造无金额字段的采购收货单契约。
     *
     * @return 收货单据合成契约
     */
    private DatasetDescriptor receive() {
        var fields = detailFields();
        for (String name : List.of(
                "receiveNo", "vendorName", "purchaseOrderNo", "warehouseName", "batchCode")) {
            fields.add(new DatasetField(name, name, ValueType.STRING));
        }
        fields.add(new DatasetField("receiveDate", "收货日期", ValueType.DATE));
        return new DatasetDescriptor("ERP_PURCHASE_RECEIVE_DOCUMENT", "采购收货单打印", 1, "wm:receive:query", true,
                fields, List.of(new DatasetParameter("receiveId", "采购收货单", ValueType.ID, true, null)),
                List.of(
                        "materialCode", "materialName", "specification", "quantity", "unitName", "batchCode"));
    }

    /**
     * 创建通用安全物料明细字段。
     *
     * @return 可追加单据页眉字段的合成投影
     */
    private List<DatasetField> detailFields() {
        var fields = new ArrayList<DatasetField>();
        for (String name : List.of("materialCode", "materialName", "specification", "unitName")) {
            fields.add(new DatasetField(name, name, ValueType.STRING));
        }
        fields.add(new DatasetField(
                "quantity", "数量", ValueType.DECIMAL, null,
                List.of("materialCode", "unitName"), true));
        return fields;
    }

    /**
     * 提取页眉或页脚的纯文本以检查业务字段绑定。
     *
     * @param rows 模板单据行
     * @return 所有固定单元格文本
     */
    private String text(List<ReportDsl.BandRow> rows) {
        return rows.stream().flatMap(row -> row.cells().stream()).map(ReportDsl.Cell::text)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
