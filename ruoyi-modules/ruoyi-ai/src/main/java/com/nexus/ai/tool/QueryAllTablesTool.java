package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询所有 ERP 业务表工具
 * <p>供 AI Agent 调用，获取数据库中所有可用的 ERP 业务表列表</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryAllTablesTool {

    private final ErpQueryService erpQueryService;

    /**
     * 查询所有可用的 ERP 业务表
     *
     * @return 表名列表
     */
    @Tool("Query all available ERP business tables in the database")
    public String queryAllTables() {
        log.info("[AI-Tables] 查询所有 ERP 业务表");
        try {
            List<String> tables = erpQueryService.queryErpTables();
            if (tables.isEmpty()) {
                return "No ERP business tables found.";
            }
            StringBuilder result = new StringBuilder();
            result.append("Found ").append(tables.size()).append(" ERP business tables:\n");
            for (String tableName : tables) {
                result.append("- ").append(tableName).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("查询 ERP 表列表失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
