package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协议解析规则单元测试（纯 Java 构造工厂，只测 resolveProtocol 分支，无 Spring 上下文）
 * <p>覆盖：NULL 协议→厂商默认；非法串回退默认；已知但厂商不允许→运行时策略校验报错；
 * 未注册厂商且无协议→无法解析报错；大小写规范化。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class ChatServiceProviderFactoryTest {

    private final ChatServiceProviderFactory factory =
        new ChatServiceProviderFactory(new AiProviderPolicyRegistry());

    @Test
    void nullProtocolResolvesToProviderDefault() {
        AiModel config = model("DASHSCOPE", null);
        assertEquals("DASHSCOPE_NATIVE", factory.resolveProtocol(config));
    }

    @Test
    void unknownProtocolStringFallsBackToProviderDefault() {
        AiModel config = model("OPENCODE_GO", "BOGUS_PROTOCOL");
        assertEquals("OPENAI_CHAT_COMPLETIONS", factory.resolveProtocol(config));
    }

    @Test
    void knownButDisallowedProtocolIsRejectedAtRuntime() {
        // 设计 6.3 运行时策略校验：DASHSCOPE 行手填 OPENAI 协议不得静默放行
        AiModel config = model("DASHSCOPE", "OPENAI_CHAT_COMPLETIONS");
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> factory.resolveProtocol(config));
        assertTrue(e.getMessage().contains("不被厂商 DASHSCOPE 允许"),
            "异常应说明厂商策略不允许该协议，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("OPENAI_CHAT_COMPLETIONS"));
    }

    @Test
    void allowedProtocolIsNormalizedAndReturned() {
        AiModel config = model("OPENCODE_GO", " openai_responses ");
        assertEquals("OPENAI_RESPONSES", factory.resolveProtocol(config));
    }

    @Test
    void unregisteredProviderWithoutProtocolIsUnresolvable() {
        AiModel config = model("NOT_A_PROVIDER", null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> factory.resolveProtocol(config));
        assertTrue(e.getMessage().contains("请前往模型管理配置调用协议"),
            "异常应指引管理员配置协议，实际: " + e.getMessage());
    }

    @Test
    void unregisteredProviderWithKnownProtocolStillResolves() {
        // provider_type 缺失（如手工导入数据）但协议已显式配置 → 仍按协议路由，不强校验
        AiModel config = model(null, "GEMINI_GENERATE_CONTENT");
        assertEquals("GEMINI_GENERATE_CONTENT", factory.resolveProtocol(config));
    }

    private AiModel model(String providerType, String apiProtocol) {
        AiModel config = new AiModel();
        config.setId(1L);
        config.setModelName("test-model");
        config.setProviderType(providerType);
        config.setApiProtocol(apiProtocol);
        return config;
    }
}
