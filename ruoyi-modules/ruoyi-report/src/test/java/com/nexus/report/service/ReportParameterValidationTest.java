package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

/** 验证空筛选、必填业务对象和报表默认值的服务端规则。 */
class ReportParameterValidationTest {
    /** 真实参数解析入口不访问存储，其余服务依赖在本单元测试中不参与。 */
    private final ReportDefinitionService service =
            mock(ReportDefinitionService.class, CALLS_REAL_METHODS);

    /** 真实编译器校验所构造的固定契约。 */
    private final ReportCompiler compiler = new ReportCompiler(new ObjectMapper());

    /** 编译身份只用于契约测试，不连接真实用户数据。 */
    private final ReportActor actor = new ReportActor("A", 1, 10, true, List.of());

    /**
     * 构造经真实编译器校验的参数绑定。
     *
     * @param parameter 可信数据集参数
     * @param required 报表是否加强为必填
     * @param defaultValue 报表自己的默认值
     * @return 不读取业务数据的固定绑定
     */
    private ReportDefinitionService.Binding binding(
            DatasetParameter parameter, boolean required, String defaultValue) {
        var descriptor =
                new DatasetDescriptor(
                        "TEST",
                        "参数测试",
                        1,
                        "test:list",
                        false,
                        List.of(new DatasetField("code", "编码", ValueType.STRING)),
                        List.of(parameter),
                        List.of("code"));
        var dsl =
                new ReportDsl(
                        1,
                        "PARAM_REPORT",
                        "参数测试",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(new ReportDsl.Column("code", "编码", 100, 0, null, null)),
                        List.of(
                                new ReportDsl.Parameter(
                                        parameter.name(),
                                        parameter.label(),
                                        parameter.type().name(),
                                        required,
                                        defaultValue)),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        var provider = mock(ReportDatasetProvider.class);
        when(provider.descriptor()).thenReturn(descriptor);
        return new ReportDefinitionService.Binding(
                compiler.compile(dsl, descriptor, actor), null, null, provider, null);
    }

    /** 缺少必填单据时仅返回代码声明的中文条件名，不包含SQL或数据值。 */
    @Test
    void missingDocumentReturnsSpecificSafeInstruction() {
        var binding =
                binding(new DatasetParameter("orderId", "采购订单", ValueType.ID, true), true, null);
        var fault = assertThrows(ReportFault.class, () -> service.parameters(binding, Map.of()));
        assertEquals("INVALID_PARAMETER", fault.code());
        assertEquals(400, fault.status());
        assertEquals("请先填写必填条件：采购订单", fault.getMessage());
    }

    /** 报表对可选数据集参数加强的必填约束同样不可绕过。 */
    @Test
    void templateRequiredConstraintIsEnforcedOnServer() {
        var binding =
                binding(new DatasetParameter("orderId", "采购订单", ValueType.ID, false), true, null);
        assertThrows(ReportFault.class, () -> service.parameters(binding, Map.of("orderId", " ")));
    }

    /** 可选筛选允许留空，不因为没有选择业务对象而拒绝全部授权范围。 */
    @Test
    void optionalFiltersCanRemainEmpty() {
        var binding =
                binding(
                        new DatasetParameter("warehouseId", "仓库", ValueType.ID, false),
                        false,
                        null);
        assertTrue(service.parameters(binding, Map.of()).isEmpty());
    }

    /** 留空使用报表自己的默认值，不能被空字符串覆盖为其他数据集默认值。 */
    @Test
    void blankInputKeepsReportDefault() {
        var binding =
                binding(
                        new DatasetParameter("date", "截止日期", ValueType.DATE, false, "TODAY"),
                        false,
                        "2026-01-02");
        assertEquals("2026-01-02", service.parameters(binding, Map.of("date", " ")).get("date"));
    }

    /** 缺省日期标记仅由服务端解析，完整雪花ID仍是无损的有符号64位整数。 */
    @Test
    void trustedDefaultsAndSnowflakeIdsStillWork() {
        var date =
                binding(
                        new DatasetParameter("date", "截止日期", ValueType.DATE, true, "TODAY"),
                        true,
                        null);
        assertEquals(LocalDate.now().toString(), service.parameters(date, Map.of()).get("date"));
        var id = binding(new DatasetParameter("orderId", "采购订单", ValueType.ID, true), true, null);
        assertEquals(
                Long.MAX_VALUE,
                service.parameters(id, Map.of("orderId", "9223372036854775807")).get("orderId"));
    }

    /** 发布接口的参数提示合并可信默认标记，但不改写已发布DSL或提前计算日期。 */
    @Test
    void effectiveParametersInheritFixedDefaultsWithoutChangingDsl() {
        var binding =
                binding(
                        new DatasetParameter("toDate", "结束日期", ValueType.DATE, true, "TODAY"),
                        true,
                        null);
        var effective = service.effectiveParameters(binding);
        assertEquals("TODAY", effective.get(0).defaultValue());
        assertNull(binding.compiled().dsl().parameters().get(0).defaultValue());
        var own =
                binding(
                        new DatasetParameter("toDate", "结束日期", ValueType.DATE, true, "TODAY"),
                        true,
                        "2026-01-02");
        assertEquals("2026-01-02", service.effectiveParameters(own).get(0).defaultValue());
    }
}
