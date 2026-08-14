package com.nexus.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 工具测试结果
 *
 * @author NexusERP
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolTestResult {

    /** 是否成功 */
    private boolean success;

    /** 消息 */
    private String message;

    /** 发现的工具数量 */
    private Integer toolCount;

    /** 工具名称列表 */
    private List<String> tools;

    /** 构建成功结果，携带发现的工具数量与名称列表。 */
    public static McpToolTestResult success(String message, int toolCount, List<String> tools) {
        return McpToolTestResult.builder()
            .success(true)
            .message(message)
            .toolCount(toolCount)
            .tools(tools)
            .build();
    }

    /** 构建失败结果，仅携带错误信息。 */
    public static McpToolTestResult fail(String message) {
        return McpToolTestResult.builder()
            .success(false)
            .message(message)
            .build();
    }
}
