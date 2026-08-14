package com.nexus.flow.node.ai;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.flow.state.FlowTemplateRenderer;
import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.BuiltinToolRegistry;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ERP 业务工具节点执行器
 * <p>把既有 {@code @Tool} 注解方法暴露为画布节点：下拉选工具名 → 渲染参数 schema → 反射调用。
 * 复用 BuiltinToolRegistry 注册的工具（库存/销售/采购/生产等 ERP 查询）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpToolNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BuiltinToolRegistry builtinToolRegistry;
    private final ApplicationContext applicationContext;

    @Override
    public String getNodeType() {
        return "erp_tool";
    }

    @Override
    public String getDisplayName() {
        return "ERP 业务工具";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        // 工具名下拉：所有已注册工具 provider
        List<Map<String, String>> options = new ArrayList<>();
        for (BuiltinToolProvider provider : builtinToolRegistry.getAllProviders()) {
            options.add(Map.of("name", provider.getDisplayName() + "（" + provider.getToolName() + "）", "value", provider.getToolName()));
        }
        NodeParamSchema toolName = new NodeParamSchema("toolName", "工具", "options", "", true);
        toolName.setOptions(options);
        // args：携带全量工具参数定义（toolArgs: toolName → [{name,type}]），前端选中工具后渲染参数字段表单
        NodeParamSchema args = new NodeParamSchema("args", "参数", "json", "{}", false);
        args.setEditor("toolArgs");
        args.setAttrs(Map.of("toolArgs", collectToolArgs()));
        return List.of(
            toolName,
            args
        );
    }

    /**
     * 收集所有工具的参数定义（反射 @Tool 方法的 @P 注解）
     * <p>参数名用 @P.value()（中文描述；编译后 p.getName() 为 arg0，不可用作键）。
     * 类型映射：String→string、int/long→number、boolean→boolean</p>
     */
    private Map<String, Object> collectToolArgs() {
        Map<String, Object> toolArgs = new java.util.LinkedHashMap<>();
        for (BuiltinToolProvider provider : builtinToolRegistry.getAllProviders()) {
            List<Map<String, Object>> argList = new ArrayList<>();
            Class<?> toolClass = provider.getToolClass();
            // 取第一个 @Tool 方法（与 execute 的 fallback 逻辑一致）
            for (Method m : toolClass.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                for (Parameter p : m.getParameters()) {
                    Map<String, Object> arg = new java.util.LinkedHashMap<>();
                    String paramName = p.getName();
                    if (p.isAnnotationPresent(P.class)) {
                        paramName = p.getAnnotation(P.class).value();
                    }
                    arg.put("name", paramName);
                    Class<?> type = p.getType();
                    String typeName = type == String.class ? "string"
                        : (type == int.class || type == Integer.class || type == long.class || type == Long.class) ? "number"
                        : (type == boolean.class || type == Boolean.class) ? "boolean" : "string";
                    arg.put("type", typeName);
                    argList.add(arg);
                }
                break;
            }
            toolArgs.put(provider.getToolName(), argList);
        }
        return toolArgs;
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("toolName") == null || config.get("toolName").asText().isBlank()) {
            throw new IllegalArgumentException("ERP 工具节点缺少 toolName 配置");
        }
        String toolName = config.get("toolName").asText();
        if (!builtinToolRegistry.hasTool(toolName)) {
            throw new IllegalArgumentException("ERP 工具不存在: " + toolName);
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) throws Exception {
        JsonNode config = ctx.getConfig();
        String toolName = config.get("toolName").asText();
        String label = "节点 " + ctx.getNodeName();

        BuiltinToolProvider provider = builtinToolRegistry.getProvider(toolName);
        if (provider == null) {
            throw new IllegalArgumentException("ERP 工具不存在: " + toolName);
        }
        Class<?> toolClass = provider.getToolClass();
        Object toolBean = applicationContext.getBean(toolClass);
        if (toolBean == null) {
            throw new IllegalArgumentException("ERP 工具 Bean 不可用: " + toolName);
        }

        // 找到 @Tool 方法：优先与 args 参数名匹配的方法 → 零参方法 → 第一个（工具类可能含多个 @Tool 方法）
        JsonNode args = config.get("args");
        Method toolMethod = null;
        Method zeroArgMethod = null;
        for (Method m : toolClass.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Tool.class)) {
                continue;
            }
            if (toolMethod == null) {
                toolMethod = m;
            }
            if (m.getParameterCount() == 0) {
                zeroArgMethod = m;
            }
            // args 键与参数名全匹配 → 直接命中
            if (args != null && args.isObject()) {
                boolean allMatch = true;
                for (Parameter p : m.getParameters()) {
                    String paramName = p.getName();
                    if (p.isAnnotationPresent(P.class)) {
                        paramName = p.getAnnotation(P.class).value();
                    }
                    if (!args.has(paramName)) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch && m.getParameterCount() > 0) {
                    toolMethod = m;
                    break;
                }
            }
        }
        // 没有 args 匹配时优先零参方法（如 queryLowStockMaterials）
        if (zeroArgMethod != null && (args == null || !args.isObject() || args.isEmpty())) {
            toolMethod = zeroArgMethod;
        }
        if (toolMethod == null) {
            throw new IllegalArgumentException("ERP 工具 " + toolName + " 没有 @Tool 方法");
        }

        // 渲染参数（args JSON: 参数名 → 值，值支持 {{}} 渲染后按参数类型转换）
        Object[] paramValues = new Object[toolMethod.getParameterCount()];
        Parameter[] parameters = toolMethod.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            String paramName = p.getName();
            if (p.isAnnotationPresent(P.class)) {
                paramName = p.getAnnotation(P.class).value();
            }
            Object rawValue = null;
            if (args != null && args.isObject() && args.has(paramName)) {
                JsonNode valueNode = args.get(paramName);
                if (valueNode.isValueNode()) {
                    rawValue = FlowTemplateRenderer.render(valueNode.asText(), ctx.getState(), label + " 的参数 " + paramName);
                } else {
                    rawValue = valueNode.toString();
                }
            }
            // 类型转换
            Class<?> type = p.getType();
            paramValues[i] = convert(type, rawValue);
        }

        // 反射调用
        log.info("[Flow] ERP 工具节点 {} 调用: {} args={}", ctx.getNodeName(), toolName, MAPPER.valueToTree(paramValues));
        Object result = toolMethod.invoke(toolBean, paramValues);

        ObjectNode output = MAPPER.createObjectNode();
        output.put("toolName", toolName);
        if (result == null) {
            output.putNull("result");
        } else if (result instanceof String s) {
            output.put("result", s);
        } else if (result instanceof Number num) {
            output.put("result", num.doubleValue());
        } else if (result instanceof Boolean b) {
            output.put("result", b);
        } else {
            output.set("result", MAPPER.valueToTree(result));
        }
        return output;
    }

    /**
     * 按参数类型转换字符串值
     */
    private Object convert(Class<?> type, Object rawValue) {
        String text = rawValue == null ? null : String.valueOf(rawValue);
        if (type == String.class) {
            return text == null ? "" : text;
        }
        if (type == int.class || type == Integer.class) {
            return text == null || text.isBlank() ? 0 : Integer.parseInt(text.trim());
        }
        if (type == long.class || type == Long.class) {
            return text == null || text.isBlank() ? 0L : Long.parseLong(text.trim());
        }
        if (type == double.class || type == Double.class) {
            return text == null || text.isBlank() ? 0.0 : Double.parseDouble(text.trim());
        }
        if (type == boolean.class || type == Boolean.class) {
            return text != null && Boolean.parseBoolean(text.trim());
        }
        // 其他类型：返回原文（String 转换交给调用方）
        return text;
    }
}
