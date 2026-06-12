package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpTableSchemaManager;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查询所有 ERP 业务表工具
 * <p>供 AI Agent 调用，获取数据库中所有可用的 ERP 业务表列表（从缓存获取）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryAllTablesTool {

    private final ErpTableSchemaManager erpTableSchemaManager;

    /**
     * 查询所有可用的 ERP 业务表
     *
     * @return 表名列表（含中文注释）
     */
    @Tool("查询数据库中所有可用的 ERP 业务表")
    public String queryAllTables() {
        log.info("[AI-Tables] 查询所有 ERP 业务表（从缓存）");
        try {
            String summary = erpTableSchemaManager.getAllTablesSummary();
            if (summary.isEmpty()) {
                return "No ERP business tables found.";
            }
            int tableCount = erpTableSchemaManager.getTableCount();
            return "Found " + tableCount + " ERP business tables:\n" + summary;
        } catch (Exception e) {
            log.error("查询 ERP 表列表失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
