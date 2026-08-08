package com.nexus.ai.tool;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.ai.service.query.SqlSafetyValidator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 执行 SQL 查询工具
 * <p>
 * 供 AI Agent 调用，在 ERP 数据源上执行只读 SELECT 查询。
 * 安全校验全部委托 {@link SqlSafetyValidator}（与 flow 的 sql 节点共用同一链路）。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteSqlQueryTool {

    /** ERP 查询服务（执行只读 SQL，@DS("erp") 数据源） */
    private final ErpQueryService erpQueryService;
    /** AI 配置属性，提供 SQL 行数/超时限制 */
    private final AiProperties aiProperties;
    /** SQL 安全校验器（公共） */
    private final SqlSafetyValidator sqlSafetyValidator;

    /**
     * 执行 ERP 数据库查询（只读）
     * <p>安全校验委托 {@link SqlSafetyValidator}：SELECT 白名单 + 危险关键词黑名单 + 表名白名单 + 租户隔离 + EXPLAIN 预校验</p>
     *
     * @param sql SQL 查询语句
     * @return 查询结果（Markdown 表格格式）；校验失败返回错误信息供 Agent 修正重试
     */
    @Tool("执行 ERP 数据库查询（只读），返回查询结果。SQL 语法错误时会返回错误信息，请根据错误修正后重试")
    public String executeSqlQuery(@P("SQL SELECT 查询语句") String sql) {
        log.info("[AI-SQL] 收到查询请求: {}", sql);

        // 安全校验（失败返回可读错误，让 Agent 修正后重试）
        String cleanedSql;
        try {
            cleanedSql = sqlSafetyValidator.validateAndSanitize(sql, aiProperties.getSqlMaxRows());
        } catch (IllegalArgumentException e) {
            log.warn("[AI-SQL] SQL 校验被拒绝: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }

        // 通过 ErpQueryService 执行（走 @DS("erp") 数据源）
        try {
            log.info("[AI-SQL] 执行最终SQL: {}", cleanedSql);
            List<Map<String, Object>> results = erpQueryService.executeQuery(
                cleanedSql, aiProperties.getSqlMaxRows(), aiProperties.getSqlTimeout() / 1000);
            log.info("[AI-SQL] 查询成功，返回 {} 行", results.size());
            return formatResults(results);
        } catch (Exception e) {
            log.error("[AI-SQL] 执行 SQL 查询失败: {}", sql, e);
            return "Error: Query execution failed - " + e.getMessage();
        }
    }

    /**
     * 格式化查询结果为 Markdown 表格
     *
     * @param results 查询结果列表
     * @return Markdown 表格字符串
     */
    private String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "Query executed successfully, but no results returned.";
        }

        StringBuilder result = new StringBuilder();
        int displayRows = Math.min(results.size(), 10);

        List<String> columnNames = List.copyOf(results.get(0).keySet());
        int displayCols = Math.min(columnNames.size(), 8);
        List<String> displayColumns = columnNames.subList(0, displayCols);

        // 表头
        result.append("| ");
        for (String col : displayColumns) {
            result.append(col).append(" | ");
        }
        result.append("\n|");
        for (int i = 0; i < displayCols; i++) {
            result.append(" --- |");
        }
        result.append("\n");

        // 数据行
        for (int i = 0; i < displayRows; i++) {
            result.append("| ");
            Map<String, Object> row = results.get(i);
            for (String column : displayColumns) {
                Object value = row.get(column);
                String displayValue = (value == null) ? "-" : value.toString();
                if (displayValue.length() > 20) {
                    displayValue = displayValue.substring(0, 17) + "...";
                }
                result.append(displayValue).append(" | ");
            }
            result.append("\n");
        }

        result.append("\nTotal: ").append(results.size()).append(" rows");
        if (displayRows < results.size()) {
            result.append(" (displayed ").append(displayRows).append(" rows)");
        }
        return result.toString();
    }
}
