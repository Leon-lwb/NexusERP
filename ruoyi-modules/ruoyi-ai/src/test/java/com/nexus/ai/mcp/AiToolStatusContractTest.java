package com.nexus.ai.mcp;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AI 工具状态契约单元测试
 * <p>锁定 ai_tool 状态及模板默认值字段的系统字典语义
 * （status 0启用/1停用、is_default Y/N），防止 MCP 链路回退到
 * 迁移前的 ENABLED/DISABLED 串或 1/0 默认值字面量。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiToolStatusContractTest {

    @Test
    void enabledMcpToolCodeMustUseSystemActiveCode() {
        assertEquals("0", AiToolStatusContract.ACTIVE);
        assertEquals("1", AiToolStatusContract.DISABLED);
    }

    @Test
    void defaultFlagsMustUseSystemYesNoDictionaryValues() {
        assertEquals("Y", AiToolStatusContract.DEFAULT_YES);
        assertEquals("N", AiToolStatusContract.DEFAULT_NO);
    }
}
