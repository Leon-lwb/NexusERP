package com.nexus.flow.node.logic;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.config.AiProperties;
import com.nexus.flow.state.FlowTemplateRenderer;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.ai.service.query.SqlSafetyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SQL 数据库查询节点执行器
 * <p>在 ERP 数据源上执行只读 SELECT（安全链路复用 {@link SqlSafetyValidator}），
 * 输出结构化 JSON {columns, rows, count} 供下游 IF/CODE 直接引用</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SqlSafetyValidator sqlSafetyValidator;
    private final ErpQueryService erpQueryService;
    private final AiProperties aiProperties;

    @Override
    public String getNodeType() {
        return "sql";
    }

    @Override
    public String getDisplayName() {
        return "数据库查询";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of(
            new NodeParamSchema("sqlTemplate", "SQL 语句", "textarea", "", true),
            new NodeParamSchema("maxRows", "最大返回行数", "number", "50", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("sqlTemplate") == null || config.get("sqlTemplate").asText().isBlank()) {
            throw new IllegalArgumentException("SQL 节点缺少 sqlTemplate 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        String sqlTemplate = config.get("sqlTemplate").asText();
        String label = "节点 " + ctx.getNodeName();

        // 渲染模板变量（如 SELECT * FROM wm_material_stock WHERE material_code = '{{input.code}}'）
        String rawSql = FlowTemplateRenderer.render(sqlTemplate, ctx.getState(), label + " 的 sqlTemplate");

        // 安全校验 + 净化（SELECT 白名单/关键词黑名单/表名白名单/租户注入/LIMIT/EXPLAIN）
        // 显式传租户：节点在 ForkJoinPool 线程执行，AiRequestContext ThreadLocal 不可达
        int maxRows = config.has("maxRows") && config.get("maxRows").canConvertToInt()
            ? config.get("maxRows").asInt() : 50;
        Long tenantId = ctx.getTenantId() == null ? null : Long.valueOf(ctx.getTenantId());
        String safeSql = sqlSafetyValidator.validateAndSanitize(rawSql, maxRows, tenantId);

        // 执行（@DS("erp") 数据源）
        List<Map<String, Object>> rows = erpQueryService.executeQuery(
            safeSql, maxRows, aiProperties.getSqlTimeout() / 1000);

        // 组装结构化输出
        ObjectNode output = MAPPER.createObjectNode();
        ArrayNode columnsNode = output.putArray("columns");
        ArrayNode rowsNode = output.putArray("rows");
        if (!rows.isEmpty()) {
            rows.get(0).keySet().forEach(columnsNode::add);
        }
        for (Map<String, Object> row : rows) {
            ObjectNode rowNode = rowsNode.addObject();
            row.forEach((k, v) -> {
                if (v == null) {
                    rowNode.putNull(k);
                } else if (v instanceof Number num) {
                    rowNode.put(k, num.doubleValue());
                } else if (v instanceof Boolean b) {
                    rowNode.put(k, b);
                } else {
                    rowNode.put(k, v.toString());
                }
            });
        }
        output.put("count", rows.size());
        output.put("sql", safeSql);
        log.info("[Flow] SQL 节点 {} 执行成功: {} 行", ctx.getNodeName(), rows.size());
        return output;
    }
}
