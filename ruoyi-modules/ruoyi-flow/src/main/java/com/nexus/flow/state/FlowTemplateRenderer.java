package com.nexus.flow.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染器：解析 {{path}} 变量引用
 * <p>缺失语义分层（对齐 n8n）：
 * <ul>
 *   <li>路径中节点不存在/未执行 → 抛 {@link IllegalArgumentException}（由 {@link FlowState#get} 抛出）</li>
 *   <li>已存在节点的属性缺失 → 渲染为空串（不报错）</li>
 * </ul>
 * </p>
 *
 * @author NexusERP
 */
public class FlowTemplateRenderer {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{([^{}]+)\\}\\}");

    /**
     * 渲染模板（不转义反斜杠）
     *
     * @param template      模板字符串（如 "请分析：{{nodes.node_rag_1.output.content}}"）
     * @param state         流程状态
     * @param contextLabel  上下文标签（错误信息中标识，如 "节点 node_llm_1 的 promptTemplate"）
     * @return 渲染结果
     */
    public static String render(String template, FlowState state, String contextLabel) {
        if (template == null) {
            return null;
        }
        if (template.isBlank()) {
            return template;
        }
        Matcher matcher = EXPRESSION_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(template, last, matcher.start());
            String path = matcher.group(1).trim();
            try {
                JsonNode value = state.get(path);
                if (value == null || value.isMissingNode() || value.isNull()) {
                    // 属性缺失 → 空串
                    sb.append("");
                } else if (value.isValueNode()) {
                    sb.append(value.asText());
                } else {
                    // 对象/数组 → JSON 字符串
                    sb.append(value.toString());
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(contextLabel + " 中变量 {{" + path + "}} 解析失败: " + e.getMessage());
            }
            last = matcher.end();
        }
        sb.append(template.substring(last));
        return sb.toString();
    }

    /**
     * 判断字符串是否包含表达式
     *
     * @param text 文本
     * @return 是否包含 {{ }}
     */
    public static boolean containsExpression(String text) {
        return text != null && EXPRESSION_PATTERN.matcher(text).find();
    }
}
