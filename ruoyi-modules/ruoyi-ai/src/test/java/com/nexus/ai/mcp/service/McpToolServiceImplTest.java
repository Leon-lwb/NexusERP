package com.nexus.ai.mcp.service;

import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP 工具启停白名单校验单元测试
 * <p>toggleToolStatus 直接落库前端提交的 status 值：字典化迁移(2026-08-27)后
 * ai_tool.status 只认 0/1（字典 sys_normal_disable），旧前端仍可能提交
 * ENABLED/DISABLED 串——放行会写入任何读取端都匹配不到的脏状态，
 * 必须在入口拒绝；合法值 0/1 原样透传并刷新 MCP 连接。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class McpToolServiceImplTest {

    private final AiToolMapper aiToolMapper = mock(AiToolMapper.class);
    private final McpClientManager mcpClientManager = mock(McpClientManager.class);

    private McpToolServiceImpl newService() {
        return new McpToolServiceImpl(aiToolMapper, mock(BuiltinToolRegistry.class), mcpClientManager);
    }

    @Test
    void toggleMustRejectLegacyEnabledDisabledStrings() {
        McpToolServiceImpl service = newService();
        ServiceException enabled = assertThrows(ServiceException.class,
            () -> service.toggleToolStatus(1L, "ENABLED"));
        ServiceException disabled = assertThrows(ServiceException.class,
            () -> service.toggleToolStatus(1L, "DISABLED"));
        assertTrue(enabled.getMessage().contains("0=启用"));
        assertTrue(disabled.getMessage().contains("0=启用"));
        verify(aiToolMapper, never()).updateById(any(AiTool.class));
        verify(mcpClientManager, never()).refreshClient(1L);
    }

    @Test
    void toggleMustAcceptActiveAndDisabledCodesOnly() {
        McpToolServiceImpl service = newService();
        when(aiToolMapper.updateById(any(AiTool.class))).thenReturn(1);

        assertEquals(1, service.toggleToolStatus(1L, AiToolStatusContract.ACTIVE));
        assertEquals(1, service.toggleToolStatus(1L, AiToolStatusContract.DISABLED));
        assertThrows(ServiceException.class, () -> service.toggleToolStatus(1L, null));
        assertThrows(ServiceException.class, () -> service.toggleToolStatus(1L, ""));

        // 合法值各落库一次并刷新连接；非法值已在前置测试断言不落库
        verify(aiToolMapper, times(2)).updateById(any(AiTool.class));
        verify(mcpClientManager, times(2)).refreshClient(1L);
    }
}
