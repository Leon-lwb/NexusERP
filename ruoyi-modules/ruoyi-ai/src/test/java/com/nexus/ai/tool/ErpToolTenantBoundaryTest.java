package com.nexus.ai.tool;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 所有读 ERP 数据的内置工具共享同一租户边界；仅使用查询服务替身，不读写数据库。 */
class ErpToolTenantBoundaryTest {

    private static final List<Function<ErpQueryService, ErpToolHelper>> FACTORIES = List.of(
        ErpInventoryTool::new, ErpPurchaseTool::new, ErpProductionTool::new,
        ErpSalesTool::new, ErpShortageRiskTool::new, ApsScheduleTool::new);

    @AfterEach
    void 清理请求上下文() {
        AiRequestContext.clear();
    }

    @TestFactory
    Stream<DynamicTest> 缺失租户时所有业务工具均应停止查询() {
        return FACTORIES.stream().flatMap(factory -> {
            ErpQueryService query = mock(ErpQueryService.class);
            ErpToolHelper tool = factory.apply(query);
            return toolMethods(tool).map(method -> DynamicTest.dynamicTest(
                tool.getClass().getSimpleName() + "." + method.getName() + "：无租户拒绝",
                () -> {
                    AiRequestContext.clear();
                    clearInvocations(query);
                    String result = (String) method.invoke(tool, arguments(method));
                    assertTrue(result.contains("租户"), result);
                    verifyNoInteractions(query);
                }));
        });
    }

    @TestFactory
    Stream<DynamicTest> 所有业务工具必须绑定字符串而不是Long租户() {
        return FACTORIES.stream().flatMap(factory -> {
            ErpQueryService query = mock(ErpQueryService.class);
            ErpToolHelper tool = factory.apply(query);
            return toolMethods(tool).map(method -> DynamicTest.dynamicTest(
                tool.getClass().getSimpleName() + "." + method.getName() + "：精确字符串租户",
                () -> {
                    // 旧基线上只使用无前导零的值；主线程迁移 String setter 后仍直接使用原串。
                    setTenant("42");
                    clearInvocations(query);
                    method.invoke(tool, arguments(method));
                    var calls = mockingDetails(query).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().startsWith("execute"))
                        .toList();
                    assertFalse(calls.isEmpty(), "本案例必须真实进入查询服务边界，不能因其他参数错误而假通过");
                    for (var call : calls) {
                        String sql = (String) call.getArgument(0);
                        if (call.getMethod().getName().equals("executeParameterizedQuery")) {
                            Object[] params = call.getArgument(1);
                            // 业务参数可参数化、租户条件可由helper注入安全字符串字面量；两种路径均须精确保留。
                            assertTrue(Arrays.asList(params).contains("42") || sql.contains("tenant_id = '42'"),
                                sql + "\n参数：" + Arrays.toString(params));
                            assertFalse(Arrays.asList(params).contains(42L), Arrays.toString(params));
                        } else {
                            assertTrue(sql.contains("tenant_id = '42'"), sql);
                        }
                    }
                    AiRequestContext.clear();
                }));
        });
    }

    static void setTenant(String tenantId) throws Exception {
        Class<?> tenantType = AiRequestContext.class.getMethod("getTenantId").getReturnType();
        Object value = tenantType == String.class ? tenantId : Long.valueOf(tenantId);
        AiRequestContext.class.getMethod("set", tenantType, Long.class).invoke(null, value, 1L);
    }

    private static Stream<Method> toolMethods(Object tool) {
        return Arrays.stream(tool.getClass().getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Tool.class));
    }

    private static Object[] arguments(Method method) {
        return Arrays.stream(method.getParameterTypes()).map(type -> {
            if (type == String.class) return "MAT-1001";
            if (type == Integer.class || type == int.class) return 3;
            if (type == Long.class || type == long.class) return 1L;
            throw new IllegalStateException("测试尚未覆盖参数类型：" + type);
        }).toArray();
    }
}
