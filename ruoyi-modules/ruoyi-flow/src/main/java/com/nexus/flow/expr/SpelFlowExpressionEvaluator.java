package com.nexus.flow.expr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.flow.state.FlowState;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CODE 节点 SpEL 只读执行器
 * <p>安全约束（MVP 决策，替代 GraalJS）：
 * <ul>
 *   <li>{@link SimpleEvaluationContext#forReadOnlyDataBinding()} — 无 MethodResolver/TypeLocator，不能调方法、不能 new/静态引用</li>
 *   <li>表达式长度上限（默认 2000 字符）</li>
 *   <li>单条表达式 500ms 超时</li>
 *   <li>异常转中文可读错误</li>
 * </ul>
 * </p>
 * <p>root 为 FlowState 的 Map 视图（SpEL 用点号访问 Map 键），
 * 表达式示例：nodes['node_rag_1']['output']['chunks'][0]、input.amount > 1000 ? 'HIGH' : 'LOW'</p>
 *
 * @author NexusERP
 */
public class SpelFlowExpressionEvaluator {

    /** 单条表达式超时（毫秒） */
    private static final long EXPRESSION_TIMEOUT_MS = 500;

    /** 默认表达式长度上限 */
    private static final int DEFAULT_EXPRESSION_LIMIT = 2000;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /** 每实例一个超时执行器（节点并发场景由引擎串行保证） */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spel-flow-evaluator");
        t.setDaemon(true);
        return t;
    });

    /**
     * 执行一组表达式
     *
     * @param expressions 目标字段 -> SpEL 表达式
     * @param state       流程状态（转为 Map 视图作为 SpEL root）
     * @param contextLabel 上下文标签（错误信息）
     * @return 输出 JSON
     */
    public ObjectNode evaluate(Map<String, String> expressions, FlowState state, String contextLabel) {
        ObjectNode output = MAPPER.createObjectNode();
        if (expressions == null || expressions.isEmpty()) {
            throw new IllegalArgumentException(contextLabel + " 缺少 expressions 配置");
        }
        Map<String, Object> root = toMapView(state);
        for (Map.Entry<String, String> entry : expressions.entrySet()) {
            String field = entry.getKey();
            String expr = entry.getValue();
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException(contextLabel + " 的 expressions 包含空字段名");
            }
            if (expr == null || expr.isBlank()) {
                output.put(field, "");
                continue;
            }
            if (expr.length() > DEFAULT_EXPRESSION_LIMIT) {
                throw new IllegalArgumentException(contextLabel + " 中字段 " + field + " 的表达式超过长度上限 " + DEFAULT_EXPRESSION_LIMIT);
            }
            Object result = evaluateOne(expr, root);
            output.set(field, MAPPER.valueToTree(result));
        }
        return output;
    }

    /**
     * 执行单条表达式（带超时）
     */
    private Object evaluateOne(String expression, Map<String, Object> root) {
        Future<Object> future =
                executor.submit(
                        () -> {
                            try {
                                // 只读安全模式：仅注册 DataBinding(JavaBean 属性) + 宽松 MapAccessor(Map 键访问)，
                                // 不注册 MethodResolver/TypeLocator —— 不能调方法、不能 new/静态引用（MVP 安全决策）
                                // 宽松 MapAccessor: 缺失键返回 null 而非抛 EL1008E（对齐 n8n 属性缺失→空串语义）
                                StandardEvaluationContext ctx = new StandardEvaluationContext();
                                ctx.setPropertyAccessors(
                                        List.of(
                                                DataBindingPropertyAccessor.forReadOnlyAccess(),
                                                new LenientMapAccessor()));
                                ctx.setMethodResolvers(List.of());
                                ctx.setTypeLocator(
                                        typeName -> {
                                            throw new SpelEvaluationException(
                                                    SpelMessage.TYPE_NOT_FOUND, typeName);
                                        });
                                return PARSER.parseExpression(expression).getValue(ctx, root);
                            } catch (Exception e) {
                                throw new SpelEvaluationRuntimeException(expression, e);
                            }
                        });
        try {
            return future.get(EXPRESSION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalArgumentException("表达式执行超时（500ms）: " + expression);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("表达式执行被中断: " + expression);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SpelEvaluationRuntimeException se) {
                throw new IllegalArgumentException("表达式执行失败: " + se.getMessage());
            }
            throw new IllegalArgumentException("表达式执行失败: " + expression + "（" + cause.getMessage() + "）");
        }
    }

    /**
     * 将 FlowState 转为 Map 视图（SpEL 可访问）
     * <p>结构: {input: {...}, nodes: {nodeId: {output: {...}}}}，值为 LinkedHashMap/List/标量</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMapView(FlowState state) {
        Map<String, Object> root = new HashMap<>();
        root.put("input", jsonToJava(state.getInput()));
        JsonNode nodes = state.getRoot().get("nodes");
        if (nodes != null && nodes.isObject()) {
            Map<String, Object> nodeMap = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = nodes.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                JsonNode output = e.getValue().get("output");
                Map<String, Object> entry = new HashMap<>();
                entry.put("output", jsonToJava(output));
                nodeMap.put(e.getKey(), entry);
            }
            root.put("nodes", nodeMap);
        }
        return root;
    }

    /**
     * JSON 转 Java 对象（Map/List/标量）
     */
    private Object jsonToJava(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), jsonToJava(e.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(n -> list.add(jsonToJava(n)));
            return list;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.asLong() : node.asDouble();
        }
        return node.asText();
    }

    /**
     * 表达式执行运行时异常（内部包装，保留原始信息）
     */
    private static class SpelEvaluationRuntimeException extends RuntimeException {
        public SpelEvaluationRuntimeException(String expression, Throwable cause) {
            super(expression + "（" + cause.getMessage() + "）", cause);
        }
    }

    /**
     * 宽松 MapAccessor：缺失键返回 null（而非抛 EL1008E），对齐 n8n 属性缺失→空串语义
     * <p>继承 {@link MapAccessor} 仅覆盖 read：键不存在时返回 {@code null}，
     * 使 SpEL 把缺失属性视为空值（表达式渲染为 ""），流程可继续而非节点失败</p>
     */
    private static class LenientMapAccessor extends MapAccessor {

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canRead(EvaluationContext context, Object target, String name)
                throws AccessException {
            return target instanceof Map;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public TypedValue read(EvaluationContext context, Object target, String name)
                throws AccessException {
            Map map = (Map) target;
            if (!map.containsKey(name)) {
                // 缺失键 → null（SpEL 视为空值）
                return TypedValue.NULL;
            }
            return new TypedValue(map.get(name));
        }
    }
}
