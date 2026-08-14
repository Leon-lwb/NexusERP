package com.nexus.flow.expr;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.flow.state.FlowState;
import com.nexus.flow.state.FlowTemplateRenderer;

import java.util.List;

/**
 * IF 节点条件评估器
 * <p>结构化比较 DSL（对齐 n8n FilterValue 的简化版），支持 AND/OR 组合与 11 个操作符</p>
 *
 * @author NexusERP
 */
public class FlowConditionEvaluator {

    /**
     * 评估条件组
     *
     * @param logic     组合逻辑: AND/OR
     * @param conditions 条件列表
     * @param state     流程状态
     * @return 评估结果
     */
    public static boolean evaluate(String logic, List<Condition> conditions, FlowState state) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        boolean and = !"OR".equalsIgnoreCase(logic);
        if (and) {
            for (Condition c : conditions) {
                if (!evaluateOne(c, state)) {
                    return false;
                }
            }
            return true;
        } else {
            for (Condition c : conditions) {
                if (evaluateOne(c, state)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 评估单个条件
     *
     * @param c     条件
     * @param state 流程状态
     * @return 评估结果
     */
    private static boolean evaluateOne(Condition c, FlowState state) {
        // 变量路径支持 {{}} 包裹（兼容画布粘贴）与裸路径两种写法
        String path = c.variable;
        if (path != null && path.startsWith("{{") && path.endsWith("}}")) {
            path = path.substring(2, path.length() - 2).trim();
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("条件缺少 variable 路径");
        }
        JsonNode left = state.get(path);
        String leftStr = left == null || left.isMissingNode() || left.isNull() ? "" : left.asText();
        String rightStr = FlowTemplateRenderer.render(c.value == null ? "" : c.value, state, "条件值");

        return switch (c.operator.toUpperCase()) {
            case "EQ" -> leftStr.equals(rightStr);
            case "NE" -> !leftStr.equals(rightStr);
            case "GT" -> compareNumeric(leftStr, rightStr) > 0;
            case "GTE" -> compareNumeric(leftStr, rightStr) >= 0;
            case "LT" -> compareNumeric(leftStr, rightStr) < 0;
            case "LTE" -> compareNumeric(leftStr, rightStr) <= 0;
            case "CONTAINS" -> leftStr.contains(rightStr);
            case "STARTS_WITH" -> leftStr.startsWith(rightStr);
            case "ENDS_WITH" -> leftStr.endsWith(rightStr);
            case "IS_EMPTY" -> leftStr.isEmpty();
            case "IS_NOT_EMPTY" -> !leftStr.isEmpty();
            default -> throw new IllegalArgumentException("不支持的操作符: " + c.operator);
        };
    }

    /**
     * 数值比较（非数值按字符串比较回退）
     */
    private static int compareNumeric(String a, String b) {
        try {
            double da = Double.parseDouble(a.trim());
            double db = Double.parseDouble(b.trim());
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    /**
     * 条件结构
     *
     * @param variable 变量路径
     * @param operator 操作符
     * @param value    比较值（支持 {{}} 模板）
     */
    public record Condition(String variable, String operator, String value) {
    }
}
