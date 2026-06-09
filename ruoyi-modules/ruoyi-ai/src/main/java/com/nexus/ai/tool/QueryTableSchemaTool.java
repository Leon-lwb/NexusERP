package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询 ERP 表结构工具
 * <p>供 AI Agent 调用，获取指定表的 CREATE TABLE DDL（含字段注释）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTableSchemaTool {

    private final ErpQueryService erpQueryService;

    /**
     * 查询指定表的 CREATE TABLE 语句
     *
     * @param tableName 表名
     * @return CREATE TABLE DDL
     */
    /** DDL 最大返回长度（避免超出模型上下文限制） */
    private static final int MAX_DDL_LENGTH = 3000;

    @Tool("Query the CREATE TABLE statement (DDL) for a specific ERP table by table name")
    public String queryTableSchema(@P("Table name") String tableName) {
        log.info("[AI-Schema] 查询表结构: {}", tableName);

        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: Table name cannot be empty";
        }
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            return "Error: Invalid table name format";
        }
        if (!tableName.matches("^(bd_|so_|pu_|prd_|wm_|sch_).*")) {
            return "Error: Only ERP business tables are allowed (bd_*/so_*/pu_*/prd_*/wm_*/sch_*)";
        }
        try {
            String schema = erpQueryService.queryTableSchema(tableName);
            if (schema == null) {
                return "Table not found: " + tableName;
            }
            // 截断过长的 DDL，避免超出模型上下文限制
            if (schema.length() > MAX_DDL_LENGTH) {
                log.info("[AI-Schema] DDL 过长({}字符)，截断到 {} 字符", schema.length(), MAX_DDL_LENGTH);
                schema = schema.substring(0, MAX_DDL_LENGTH) + "\n... (truncated)";
            }
            return schema;
        } catch (Exception e) {
            log.error("[AI-Schema] 查询表结构失败: {}", tableName, e);
            return "Error: " + e.getMessage();
        }
    }
}
