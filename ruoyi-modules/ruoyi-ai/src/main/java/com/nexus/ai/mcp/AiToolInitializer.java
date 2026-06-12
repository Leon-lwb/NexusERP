package com.nexus.ai.mcp;

import com.nexus.ai.mcp.service.McpToolServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AI 工具初始化器
 * <p>
 * 在应用启动完成后，将 BuiltinToolRegistry 中的内置工具同步到 ai_tool 表。
 * 只同步新增工具，不覆盖已有状态。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@Order(999)
@RequiredArgsConstructor
public class AiToolInitializer implements ApplicationRunner {

    private final McpToolServiceImpl mcpToolService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[MCP] 应用启动完成，开始同步内置工具到数据库...");
        try {
            mcpToolService.syncBuiltinTools();
            log.info("[MCP] 内置工具同步完成");
        } catch (Exception e) {
            log.error("[MCP] 内置工具同步失败", e);
        }
    }
}
