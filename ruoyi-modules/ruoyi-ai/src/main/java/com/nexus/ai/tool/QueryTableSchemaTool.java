package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.ai.service.query.ErpTableSchemaManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询 ERP 表结构工具
 * <p>供 AI Agent 调用，获取指定表的 CREATE TABLE DDL（优先从缓存获取）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTableSchemaTool {

    private final ErpTableSchemaManager erpTableSchemaManager;
    private final ErpQueryService erpQueryService;

    /** DDL 最大返回长度（避免超出模型上下文限制） */
    private static final int MAX_DDL_LENGTH = 3000;

    @Tool("查询指定 ERP 表的建表语句（DDL）和字段说明")
    public String queryTableSchema(@P("表名") String tableName) {
        log.info("[AI-Schema] 查询表结构: {}", tableName);

        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: Table name cannot be empty";
        }
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            return "Error: Invalid table name format";
        }

        try {
            // 优先从缓存获取 DDL
            ErpTableSchemaManager.TableStructure schema = erpTableSchemaManager.getTableSchema(tableName);
            String ddl = null;
            if (schema != null && schema.getDdl() != null) {
                ddl = schema.getDdl();
                log.debug("[AI-Schema] 从缓存获取 DDL: {}", tableName);
            } else {
                // 缓存未命中，动态查库验证表是否存在并获取 DDL
                if (!erpQueryService.tableExists(tableName)) {
                    return "Error: Table '" + tableName + "' does not exist. Please call queryAllTables first to see all available tables.";
                }
                ddl = erpQueryService.queryTableSchema(tableName);
                log.debug("[AI-Schema] 从数据库获取 DDL: {}", tableName);
            }

            if (ddl == null) {
                return "Table not found: " + tableName;
            }

            // 截断过长的 DDL，避免超出模型上下文限制
            if (ddl.length() > MAX_DDL_LENGTH) {
                log.info("[AI-Schema] DDL 过长({}字符)，截断到 {} 字符", ddl.length(), MAX_DDL_LENGTH);
                ddl = ddl.substring(0, MAX_DDL_LENGTH) + "\n... (truncated)";
            }
            return ddl;
        } catch (Exception e) {
            log.error("[AI-Schema] 查询表结构失败: {}", tableName, e);
            return "Error: " + e.getMessage();
        }
    }
}
