package com.nexus.ai.service.model;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 厂商类型 → 调用协议策略注册表（后端可信事实源）
 * <p>字典 ai_model_api_protocol 只提供 UI 选项，本注册表是协议分组的唯一权威：
 * 哪些厂商允许哪些协议（{@link #allows}）、每厂商的默认协议（{@link #defaultProtocol}，
 * 用于 api_protocol 为 NULL 的存量数据回填推断）。启动时在构造器中构建不可变状态。</p>
 * <p>批准的协议分组：
 * <ul>
 *   <li>固定 OpenAI Chat Completions 组：ARK、DEEPSEEK、MOONSHOT、TENCENT_HUNYUAN、MINIMAX、
 *       XIAOMI、SILICONFLOW、OPENAI、MISTRAL、XAI、OPENROUTER</li>
 *   <li>固定原生协议组：DASHSCOPE→DASHSCOPE_NATIVE、ZHIPU→ZHIPU_NATIVE、OLLAMA→OLLAMA_NATIVE、
 *       ANTHROPIC→ANTHROPIC_MESSAGES、GEMINI→GEMINI_GENERATE_CONTENT</li>
 *   <li>多协议组：OPENCODE_GO、CUSTOM_API → {OPENAI_CHAT_COMPLETIONS, OPENAI_RESPONSES,
 *       ANTHROPIC_MESSAGES}，默认 OPENAI_CHAT_COMPLETIONS</li>
 * </ul>
 * 协议码值与 {@link com.nexus.common.ai.enums.AiModelProtocolEnum} 一致。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@Component
public class AiProviderPolicyRegistry {

    /** OpenAI Chat Completions 协议码（字典 ai_model_api_protocol） */
    public static final String OPENAI_CHAT_COMPLETIONS = "OPENAI_CHAT_COMPLETIONS";
    /** OpenAI Responses 协议码 */
    public static final String OPENAI_RESPONSES = "OPENAI_RESPONSES";
    /** Anthropic Messages 协议码 */
    public static final String ANTHROPIC_MESSAGES = "ANTHROPIC_MESSAGES";
    /** Gemini generateContent 协议码 */
    public static final String GEMINI_GENERATE_CONTENT = "GEMINI_GENERATE_CONTENT";
    /** DashScope 原生协议码 */
    public static final String DASHSCOPE_NATIVE = "DASHSCOPE_NATIVE";
    /** 智谱原生协议码 */
    public static final String ZHIPU_NATIVE = "ZHIPU_NATIVE";
    /** Ollama 原生协议码 */
    public static final String OLLAMA_NATIVE = "OLLAMA_NATIVE";

    /** 多协议组（OpenCode Go / 自定义 API）允许的通用协议集合 */
    private static final Set<String> GENERIC_PROTOCOLS = Set.of(
        OPENAI_CHAT_COMPLETIONS, OPENAI_RESPONSES, ANTHROPIC_MESSAGES);

    /** 厂商类型 → 协议策略（不可变，构造期构建完毕） */
    private final Map<String, ProviderPolicy> policies;

    public AiProviderPolicyRegistry() {
        this.policies = buildPolicies();
    }

    /**
     * 厂商是否允许使用指定协议
     *
     * @param providerType 厂商类型（ai_provider.provider_type，大小写/空白宽容）
     * @param protocol     协议码（字典 ai_model_api_protocol，大小写/空白宽容）
     * @return 厂商已注册且协议在其允许集合内时 true；未注册厂商一律 false
     */
    public boolean allows(String providerType, String protocol) {
        ProviderPolicy policy = policies.get(normalizeProviderType(providerType));
        return policy != null && policy.allowedProtocols().contains(normalizeProtocol(protocol));
    }

    /**
     * 厂商的默认调用协议（用于 api_protocol 为 NULL 的存量模型回填推断）
     *
     * @param providerType 厂商类型
     * @return 默认协议码
     * @throws IllegalArgumentException 厂商类型未注册（防止静默回退到错误协议）
     */
    public String defaultProtocol(String providerType) {
        ProviderPolicy policy = forProvider(providerType);
        return policy.defaultProtocol();
    }

    /**
     * 取厂商的完整协议策略（Task 5 厂商策略端点使用）
     *
     * @param providerType 厂商类型
     * @return 不可变策略
     * @throws IllegalArgumentException 厂商类型未注册
     */
    public ProviderPolicy forProvider(String providerType) {
        ProviderPolicy policy = policies.get(normalizeProviderType(providerType));
        if (policy == null) {
            throw new IllegalArgumentException("未知厂商类型: " + providerType
                + "，请在厂商管理中核对 provider_type 或改用 CUSTOM_API 自定义接入");
        }
        return policy;
    }

    /**
     * 厂商类型是否已注册策略
     */
    public boolean isRegistered(String providerType) {
        return providerType != null && !providerType.isBlank()
            && policies.containsKey(normalizeProviderType(providerType));
    }

    /**
     * 已注册的全部厂商类型（不可变，策略端点展示与枚举漂移守护测试用）
     */
    public Set<String> registeredProviderTypes() {
        return policies.keySet();
    }

    /**
     * 厂商协议策略（值对象，allowedProtocols 不可变）
     *
     * @param defaultProtocol  默认协议码
     * @param allowedProtocols 允许的协议码集合（构造后不可变）
     */
    public record ProviderPolicy(String defaultProtocol, Set<String> allowedProtocols) {
        public ProviderPolicy {
            allowedProtocols = Set.copyOf(allowedProtocols);
        }
    }

    /**
     * 构建全部厂商策略（LinkedHashMap 保序便于日志排查，构建后整体冻结）
     */
    private static Map<String, ProviderPolicy> buildPolicies() {
        Map<String, ProviderPolicy> mutable = new java.util.LinkedHashMap<>();
        // 固定 OpenAI Chat Completions 组（全部 OpenAI 兼容厂商共用同一协议实现）
        Set<String> openAiCompatibleProviders = Set.of(
            "ARK", "DEEPSEEK", "MOONSHOT", "TENCENT_HUNYUAN", "MINIMAX",
            "XIAOMI", "SILICONFLOW", "OPENAI", "MISTRAL", "XAI", "OPENROUTER");
        for (String providerType : openAiCompatibleProviders) {
            mutable.put(providerType, fixed(OPENAI_CHAT_COMPLETIONS));
        }
        // 固定原生协议组（一一绑定厂商自有协议）
        mutable.put("DASHSCOPE", fixed(DASHSCOPE_NATIVE));
        mutable.put("ZHIPU", fixed(ZHIPU_NATIVE));
        mutable.put("OLLAMA", fixed(OLLAMA_NATIVE));
        mutable.put("ANTHROPIC", fixed(ANTHROPIC_MESSAGES));
        mutable.put("GEMINI", fixed(GEMINI_GENERATE_CONTENT));
        // 多协议组：允许三种通用协议，默认 OpenAI Chat Completions
        mutable.put("OPENCODE_GO", multiProtocol());
        mutable.put("CUSTOM_API", multiProtocol());
        return java.util.Collections.unmodifiableMap(mutable);
    }

    /** 单协议厂商策略 */
    private static ProviderPolicy fixed(String protocol) {
        return new ProviderPolicy(protocol, Set.of(protocol));
    }

    /** 多协议厂商策略（OpenCode Go / 自定义 API） */
    private static ProviderPolicy multiProtocol() {
        return new ProviderPolicy(OPENAI_CHAT_COMPLETIONS, GENERIC_PROTOCOLS);
    }

    private static String normalizeProviderType(String providerType) {
        return providerType == null ? "" : providerType.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeProtocol(String protocol) {
        return protocol == null ? "" : protocol.trim().toUpperCase(Locale.ROOT);
    }
}
