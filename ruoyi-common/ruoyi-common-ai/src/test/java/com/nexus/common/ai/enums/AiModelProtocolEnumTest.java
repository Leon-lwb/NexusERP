package com.nexus.common.ai.enums;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型 API 协议枚举单元测试
 * <p>验证协议 code 合法性判断：已知 code（忽略大小写）为 true，
 * null/空白/未知值为 false。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiModelProtocolEnumTest {

    @Test
    void isKnownMustAcceptValidProtocolCodeIgnoringCase() {
        assertTrue(AiModelProtocolEnum.isKnown("OPENAI_CHAT_COMPLETIONS"));
        assertTrue(AiModelProtocolEnum.isKnown("anthropic_messages"));
        assertTrue(AiModelProtocolEnum.isKnown("Ollama_Native"));
    }

    @Test
    void isKnownMustRejectNullBlankAndUnknown() {
        assertFalse(AiModelProtocolEnum.isKnown(null));
        assertFalse(AiModelProtocolEnum.isKnown(" "));
        assertFalse(AiModelProtocolEnum.isKnown("HTTP"));
        assertFalse(AiModelProtocolEnum.isKnown("OPENAI"));
    }
}
