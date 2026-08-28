package com.nexus.ai.service.model;

import com.nexus.common.ai.enums.AiModelProtocolEnum;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 厂商协议策略注册表单元测试
 * <p>验证批准的协议分组：固定 OpenAI 兼容组、固定原生协议组、多协议组（OpenCode Go / 自定义 API），
 * 以及未注册厂商类型的兜底行为（allows=false / defaultProtocol 抛出明确异常）。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiProviderPolicyRegistryTest {

    private final AiProviderPolicyRegistry registry = new AiProviderPolicyRegistry();

    @Test
    void opencodeGoAllowsAllThreeApprovedProtocols() {
        assertTrue(registry.allows("OPENCODE_GO", "OPENAI_CHAT_COMPLETIONS"));
        assertTrue(registry.allows("OPENCODE_GO", "OPENAI_RESPONSES"));
        assertTrue(registry.allows("OPENCODE_GO", "ANTHROPIC_MESSAGES"));
    }

    @Test
    void fixedProviderRejectsAnotherProtocol() {
        assertEquals("DASHSCOPE_NATIVE", registry.defaultProtocol("DASHSCOPE"));
        assertFalse(registry.allows("DASHSCOPE", "ANTHROPIC_MESSAGES"));
    }

    @Test
    void customApiDefaultsToOpenAiChatCompletionsButAllowsGenericProtocols() {
        assertEquals("OPENAI_CHAT_COMPLETIONS", registry.defaultProtocol("CUSTOM_API"));
        assertTrue(registry.allows("CUSTOM_API", "OPENAI_RESPONSES"));
    }

    @Test
    void fixedOpenAiCompatibleGroupDefaultsToChatCompletionsAndRejectsNative() {
        String[] openAiCompatible = {"ARK", "DEEPSEEK", "MOONSHOT", "TENCENT_HUNYUAN", "MINIMAX",
            "XIAOMI", "SILICONFLOW", "OPENAI", "MISTRAL", "XAI", "OPENROUTER"};
        for (String providerType : openAiCompatible) {
            assertEquals("OPENAI_CHAT_COMPLETIONS", registry.defaultProtocol(providerType),
                "providerType=" + providerType);
            assertTrue(registry.allows(providerType, "OPENAI_CHAT_COMPLETIONS"), "providerType=" + providerType);
            assertFalse(registry.allows(providerType, "DASHSCOPE_NATIVE"), "providerType=" + providerType);
            assertFalse(registry.allows(providerType, "OPENAI_RESPONSES"), "providerType=" + providerType);
        }
    }

    @Test
    void nativeProvidersLockToTheirOwnProtocol() {
        assertEquals("ZHIPU_NATIVE", registry.defaultProtocol("ZHIPU"));
        assertFalse(registry.allows("ZHIPU", "OPENAI_CHAT_COMPLETIONS"));
        assertEquals("OLLAMA_NATIVE", registry.defaultProtocol("OLLAMA"));
        assertFalse(registry.allows("OLLAMA", "OPENAI_RESPONSES"));
        assertEquals("ANTHROPIC_MESSAGES", registry.defaultProtocol("ANTHROPIC"));
        assertFalse(registry.allows("ANTHROPIC", "OPENAI_CHAT_COMPLETIONS"));
        assertEquals("GEMINI_GENERATE_CONTENT", registry.defaultProtocol("GEMINI"));
        assertFalse(registry.allows("GEMINI", "OPENAI_CHAT_COMPLETIONS"));
    }

    @Test
    void multiProtocolProvidersDefaultToOpenAiChatCompletions() {
        assertEquals("OPENAI_CHAT_COMPLETIONS", registry.defaultProtocol("OPENCODE_GO"));
        assertEquals("OPENAI_CHAT_COMPLETIONS", registry.defaultProtocol("CUSTOM_API"));
        assertFalse(registry.allows("OPENCODE_GO", "GEMINI_GENERATE_CONTENT"));
        assertFalse(registry.allows("CUSTOM_API", "DASHSCOPE_NATIVE"));
    }

    @Test
    void forProviderReturnsExactImmutablePolicy() {
        AiProviderPolicyRegistry.ProviderPolicy policy = registry.forProvider("OPENCODE_GO");
        assertEquals("OPENAI_CHAT_COMPLETIONS", policy.defaultProtocol());
        assertEquals(java.util.Set.of("OPENAI_CHAT_COMPLETIONS", "OPENAI_RESPONSES", "ANTHROPIC_MESSAGES"),
            policy.allowedProtocols());
        assertThrows(UnsupportedOperationException.class,
            () -> policy.allowedProtocols().add("DASHSCOPE_NATIVE"));
    }

    @Test
    void caseAndWhitespaceAreNormalized() {
        assertTrue(registry.allows("opencode_go", "openai_responses"));
        assertEquals("ANTHROPIC_MESSAGES", registry.defaultProtocol(" anthropic "));
    }

    @Test
    void unknownProviderTypeIsNotRegisteredAndFailsFast() {
        assertFalse(registry.isRegistered("NOT_A_PROVIDER"));
        assertFalse(registry.allows("NOT_A_PROVIDER", "OPENAI_CHAT_COMPLETIONS"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> registry.defaultProtocol("NOT_A_PROVIDER"));
        assertTrue(e.getMessage().contains("NOT_A_PROVIDER"),
            "异常信息应包含未知的厂商类型，实际: " + e.getMessage());
    }

    @Test
    void registryPoliciesStayInSyncWithProtocolEnum() {
        // 守护注册表与 AiModelProtocolEnum 的漂移：新增枚举协议必须进至少一个策略组，
        // 策略组里也不允许出现枚举不认识的协议码
        Set<String> protocolsUsedByPolicies = new HashSet<>();
        for (String providerType : registry.registeredProviderTypes()) {
            AiProviderPolicyRegistry.ProviderPolicy policy = registry.forProvider(providerType);
            assertTrue(AiModelProtocolEnum.isKnown(policy.defaultProtocol()),
                "厂商 " + providerType + " 的默认协议不在枚举内: " + policy.defaultProtocol());
            assertTrue(policy.allowedProtocols().contains(policy.defaultProtocol()),
                "厂商 " + providerType + " 的默认协议必须在其允许集合内");
            for (String protocol : policy.allowedProtocols()) {
                assertTrue(AiModelProtocolEnum.isKnown(protocol),
                    "厂商 " + providerType + " 允许了枚举不认识的协议: " + protocol);
                protocolsUsedByPolicies.add(protocol);
            }
        }
        for (AiModelProtocolEnum protocol : AiModelProtocolEnum.values()) {
            assertTrue(protocolsUsedByPolicies.contains(protocol.getCode()),
                "枚举协议未出现在任何策略组，运行时将无法路由: " + protocol.getCode());
        }
    }
}
