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
 * <p>供 AI Agent 调用，获取指定表的结构化摘要（含字段类型、枚举值、外键关系）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTableSchemaTool {

    /** ERP 表结构缓存管理器，提供结构化表摘要 */
    private final ErpTableSchemaManager erpTableSchemaManager;
    /** ERP 查询服务，验证表存在性与回退 DDL */
    private final ErpQueryService erpQueryService;

    /**
     * 查询指定 ERP 表的结构信息
     * <p>返回结构化摘要（含字段类型、枚举值、外键关系），比原始 DDL 信息密度更高</p>
     *
     * @param tableName 表名
     * @return 表结构摘要
     */
    @Tool("查询指定 ERP 表的结构信息，包含字段类型、注释、枚举值和外键关系")
    public String queryTableSchema(@P("表名") String tableName) {
        log.info("[AI-Schema] 查询表结构: {}", tableName);

        if (tableName == null || tableName.trim().isEmpty()) {
            return "错误：表名不能为空";
        }
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            return "错误：表名格式无效";
        }

        try {
            // 优先从缓存获取结构化摘要
            ErpTableSchemaManager.TableStructure schema = erpTableSchemaManager.getTableSchema(tableName);
            if (schema != null) {
                log.debug("[AI-Schema] 从缓存获取表结构: {}", tableName);
                return erpTableSchemaManager.formatEnhancedTableSummary(schema);
            }

            // 缓存未命中，验证表是否存在
            if (!erpQueryService.tableExists(tableName)) {
                return "错误：表 '" + tableName + "' 不存在。请先调用 queryAllTables 查看所有可用表。";
            }

            // 刷新缓存后重试
            erpTableSchemaManager.refreshTableSchema(tableName);
            schema = erpTableSchemaManager.getTableSchema(tableName);
            if (schema != null) {
                return erpTableSchemaManager.formatEnhancedTableSummary(schema);
            }

            // 最终降级：返回原始 DDL
            String ddl = erpQueryService.queryTableSchema(tableName);
            if (ddl != null && ddl.length() > 3000) {
                ddl = ddl.substring(0, 3000) + "\n... (truncated)";
            }
            return ddl != null ? ddl : "未找到表: " + tableName;
        } catch (Exception e) {
            log.error("[AI-Schema] 查询表结构失败: {}", tableName, e);
            return "错误：" + e.getMessage();
        }
    }
}
