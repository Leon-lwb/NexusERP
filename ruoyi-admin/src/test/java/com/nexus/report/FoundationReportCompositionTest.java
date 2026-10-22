package com.nexus.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.erp.report.adapter.ErpReportLookupProvider;
import com.nexus.mes.report.adapter.MesReportLookupProvider;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.service.ReportTemplates;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import javax.sql.DataSource;

/** 在应用组合层验证ERP、MES目录与报表模板完全匹配，无需连接实际业务库。 */
class FoundationReportCompositionTest {
    /** 严格的一版编译器。 */
    private final ReportCompiler compiler = new ReportCompiler(new ObjectMapper());

    /** 用于装配构造的只读入口，本测试不调用取数。 */
    private final JdbcTemplate jdbc = new JdbcTemplate(mock(DataSource.class));

    /**
     * 加载应用装配的14类固定代码数据集。
     *
     * @return 数据集目录
     * @throws Exception 构造签名或类缺失
     */
    private List<ReportDatasetProvider> providers() throws Exception {
        List<ReportDatasetProvider> result = new ArrayList<>();
        for (String name :
                List.of(
                        "com.nexus.erp.report.adapter.MaterialCatalogDatasetProvider",
                        "com.nexus.erp.report.adapter.ClientCatalogDatasetProvider",
                        "com.nexus.erp.report.adapter.VendorCatalogDatasetProvider",
                        "com.nexus.erp.report.adapter.StockMovementDatasetProvider",
                        "com.nexus.erp.report.adapter.SalesOrderLinesDatasetProvider",
                        "com.nexus.erp.report.adapter.PurchaseOrderLinesDatasetProvider",
                        "com.nexus.mes.report.adapter.WorkOrderTrackingDatasetProvider",
                        "com.nexus.mes.report.adapter.IqcRecordsDatasetProvider",
                        "com.nexus.erp.report.adapter.InventoryBalanceDatasetProvider",
                        "com.nexus.erp.report.adapter.SalesExecutionDatasetProvider",
                        "com.nexus.erp.report.adapter.PurchaseExecutionDatasetProvider",
                        "com.nexus.erp.report.adapter.PurchaseOrderDocumentDatasetProvider",
                        "com.nexus.erp.report.adapter.DeliveryDocumentDatasetProvider",
                        "com.nexus.mes.report.adapter.QualityTraceDatasetProvider"))
            result.add(
                    (ReportDatasetProvider)
                            Class.forName(name)
                                    .getConstructor(JdbcTemplate.class)
                                    .newInstance(jdbc));
        return result;
    }

    /** 验证完整权限下17类基础报表编码唯一，全部可由现有一版引擎编译。 */
    @Test
    void allSeventeenFoundationTemplatesCompile() throws Exception {
        var actor = new ReportActor("A", 1, 10, true, List.of());
        Set<String> codes = new HashSet<>();
        for (var provider : providers())
            for (var dsl : ReportTemplates.foundation(provider.descriptor(), "1")) {
                assertTrue(codes.add(dsl.code()), dsl.code());
                assertEquals(1, dsl.schemaVersion());
                assertDoesNotThrow(
                        () -> compiler.compile(dsl, provider.descriptor(), actor), dsl.code());
            }
        assertEquals(17, codes.size());
    }

    /** 验证每个ID参数都有正确版本和类型的业务选择器，目录不会绑定猜测参数。 */
    @Test
    void everyBusinessIdParameterHasAnExactLookupBinding() throws Exception {
        var lookup = new ArrayList<ReportLookupBinding>();
        lookup.addAll(new ErpReportLookupProvider(jdbc).bindings());
        lookup.addAll(new MesReportLookupProvider(jdbc).bindings());
        var datasets = providers();
        for (var provider : datasets)
            for (var parameter : provider.descriptor().parameters())
                if (parameter.type() == ValueType.ID)
                    assertTrue(
                            lookup.stream()
                                    .anyMatch(
                                            b ->
                                                    b.datasetCode()
                                                                    .equals(
                                                                            provider.descriptor()
                                                                                    .code())
                                                            && b.providerVersion()
                                                                    == provider.descriptor()
                                                                            .version()
                                                            && b.parameter()
                                                                    .equals(parameter.name())),
                            provider.descriptor().code() + ":" + parameter.name());
        for (var binding : lookup) {
            var descriptor =
                    datasets.stream()
                            .map(ReportDatasetProvider::descriptor)
                            .filter(
                                    d ->
                                            d.code().equals(binding.datasetCode())
                                                    && d.version() == binding.providerVersion())
                            .findFirst()
                            .orElseThrow();
            assertTrue(
                    descriptor.parameters().stream()
                            .anyMatch(
                                    p ->
                                            p.name().equals(binding.parameter())
                                                    && (p.type() == ValueType.ID
                                                            || p.type() == ValueType.STRING)),
                    binding.datasetCode() + ":" + binding.parameter());
        }
    }

    /** 验证仅有列表业务权限时，模板不会通过页脚、公式或合计引用受限财务字段。 */
    @Test
    void fieldMaskedFoundationTemplatesStillCompile() throws Exception {
        for (var provider : providers()) {
            var descriptor = provider.descriptor();
            var actor =
                    new ReportActor(
                            "A",
                            7,
                            10,
                            false,
                            List.of(
                                    new ReportRole(
                                            2,
                                            Set.of(descriptor.permission()),
                                            ReportScope.all(7))));
            var fields =
                    descriptor.fields().stream()
                            .filter(
                                    f ->
                                            f.permission() == null
                                                    || f.permission().isBlank()
                                                    || actor.has(f.permission()))
                            .toList();
            var names = fields.stream().map(DatasetField::name).toList();
            var masked =
                    new DatasetDescriptor(
                            descriptor.code(),
                            descriptor.name(),
                            descriptor.version(),
                            descriptor.permission(),
                            descriptor.document(),
                            fields,
                            descriptor.parameters(),
                            descriptor.defaultColumns().stream().filter(names::contains).toList());
            for (var dsl : ReportTemplates.foundation(masked, "1"))
                assertDoesNotThrow(() -> compiler.compile(dsl, descriptor, actor), dsl.code());
        }
    }
}
