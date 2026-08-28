package com.nexus.ai.service.model;

import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.ai.enums.AiModelCapabilityEnum;
import com.nexus.common.ai.enums.AiModelProtocolEnum;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * 模型配置草稿校验器（厂商协议策略维度的落库前校验，纯 Java 类可直接 new）
 * <p>校验规则：
 * <ul>
 *   <li>厂商类型必须已注册策略，协议缺失时<b>直接写入草稿</b>（{@code draft.setApiProtocol(默认协议)}）
 *       并返回解析出的协议——设计取舍：草稿校验发生在 Controller 层预检/保存前，
 *       把默认协议"补全进草稿"让前端拿到回显值，避免二次保存时仍为空</li>
 *   <li>协议已填时必须是合法协议码（{@link AiModelProtocolEnum#isKnown}）且在厂商允许集合内</li>
 *   <li>EMBEDDING 锁定 input=TEXT / output=VECTOR（缺失自动补齐，冲突报错），
 *       且 embeddingDimension 必须为正整数</li>
 *   <li>RERANK 锁定 input=TEXT / output=RANKING（缺失自动补齐，冲突报错）</li>
 *   <li>maxTokens 与 contextWindow 同时非空时 maxTokens 不得越界（纵深防御，
 *       AiModelServiceImpl.normalizeAndValidate 保存时亦校验）</li>
 * </ul>
 * 校验失败抛 {@link ServiceException}（中文提示，与 Task 3 保存链路风格一致）。
 * 无 Spring 依赖：测试按 Task 3 纯重载模式直接 {@code new AiModelDraftValidator(new AiProviderPolicyRegistry())}。</p>
 *
 * @author nexus-school
 * @since 2.0.0
 */
@RequiredArgsConstructor
public class AiModelDraftValidator {

    private final AiProviderPolicyRegistry registry;

    /**
     * 解析并校验模型草稿（会<b>写回</b>草稿：补全缺失的 apiProtocol 与 EMBEDDING/RERANK 锁定能力，
     * 方法名 resolveAndValidate 即提示此副作用——调用方保存草稿可直接落库回显值）
     *
     * @param draft        模型草稿（apiProtocol / inputCapabilities / outputCapabilities 可能被补全写回）
     * @param providerType 厂商类型（ai_provider.provider_type）
     * @return 解析后的协议码（草稿未填时为厂商默认协议，已填时为规范化后的协议码）
     * @throws ServiceException 厂商未注册 / 协议非法或越权 / 能力锁定冲突 / 向量维度缺失 / maxTokens 越界
     */
    public String resolveAndValidate(AiModelBo draft, String providerType) {
        String protocol = resolveProtocol(draft, providerType);
        lockCapabilities(draft);
        validateEmbeddingDimension(draft);
        validateMaxTokens(draft);
        return protocol;
    }

    /**
     * 解析协议：未填 → 写入厂商默认协议；已填 → 校验合法性与厂商策略
     */
    private String resolveProtocol(AiModelBo draft, String providerType) {
        if (!registry.isRegistered(providerType)) {
            throw new ServiceException("未知厂商类型: " + providerType
                + "，请核对厂商配置或改用自定义 API 接入");
        }
        String raw = draft.getApiProtocol();
        if (raw == null || raw.isBlank()) {
            String defaultProtocol = registry.defaultProtocol(providerType);
            draft.setApiProtocol(defaultProtocol);
            return defaultProtocol;
        }
        String protocol = raw.trim().toUpperCase(Locale.ROOT);
        if (!AiModelProtocolEnum.isKnown(protocol)) {
            throw new ServiceException("未知API协议: " + raw + "，请从协议下拉框中选择");
        }
        if (!registry.allows(providerType, protocol)) {
            throw new ServiceException("厂商 " + providerType + " 不支持协议 " + protocol
                + "，允许的协议: " + registry.forProvider(providerType).allowedProtocols());
        }
        draft.setApiProtocol(protocol);
        return protocol;
    }

    /**
     * 能力锁定：EMBEDDING→input TEXT/output VECTOR，RERANK→input TEXT/output RANKING
     * <p>未填（null/空白）自动补齐锁定值；已填但与锁定值冲突（含多余能力）则报错。
     * 其他模型类型不校验能力组合（沿用 Task 3 保存链路的通用规范化）。</p>
     */
    private void lockCapabilities(AiModelBo draft) {
        String type = nullSafe(draft.getModelType()).toUpperCase(Locale.ROOT);
        switch (type) {
            case "EMBEDDING" -> {
                draft.setInputCapabilities(lockInput(draft));
                draft.setOutputCapabilities(lockOutput(draft, AiModelCapabilityEnum.VECTOR.getCode()));
            }
            case "RERANK" -> {
                draft.setInputCapabilities(lockInput(draft));
                draft.setOutputCapabilities(lockOutput(draft, AiModelCapabilityEnum.RANKING.getCode()));
            }
            default -> {
                // 非 EMBEDDING/RERANK 类型不做能力锁定
            }
        }
    }

    /**
     * 输入能力锁定为 TEXT：空白补齐，与锁定值不一致（多余/缺失能力）报错
     */
    private String lockInput(AiModelBo draft) {
        return requireExact(draft.getInputCapabilities(), AiModelCapabilityEnum.TEXT.getCode(),
            "该类模型的输入能力锁定为 " + AiModelCapabilityEnum.TEXT.getCode());
    }

    /**
     * 输出能力锁定为指定值（EMBEDDING→VECTOR，RERANK→RANKING）：空白补齐，不一致报错
     */
    private String lockOutput(AiModelBo draft, String locked) {
        return requireExact(draft.getOutputCapabilities(), locked,
            "该类模型的输出能力锁定为 " + locked);
    }

    /**
     * 能力串必须恰好等于锁定值：空白补齐，其余（多余/缺失能力）报错
     */
    private String requireExact(String provided, String locked, String conflictMessage) {
        if (provided == null || provided.isBlank()) {
            return locked;
        }
        String canonical;
        try {
            canonical = AiModelCapabilityEnum.canonicalize(provided);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
        if (!locked.equals(canonical)) {
            throw new ServiceException(conflictMessage + "，实际填写: " + canonical);
        }
        return canonical;
    }

    /**
     * EMBEDDING 模型必须有正向量维度
     */
    private void validateEmbeddingDimension(AiModelBo draft) {
        if (!"EMBEDDING".equalsIgnoreCase(nullSafe(draft.getModelType()))) {
            return;
        }
        if (draft.getEmbeddingDimension() == null || draft.getEmbeddingDimension() <= 0) {
            throw new ServiceException("向量模型必须配置正向量维度（embeddingDimension）");
        }
    }

    /**
     * maxTokens 不得超上下文窗口（纵深防御，保存链路已有同款校验）
     */
    private void validateMaxTokens(AiModelBo draft) {
        if (draft.getMaxTokens() != null && draft.getContextWindow() != null
            && draft.getMaxTokens() > draft.getContextWindow()) {
            throw new ServiceException("最大输出 Token（" + draft.getMaxTokens()
                + "）不能超过上下文窗口（" + draft.getContextWindow() + "）");
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}
