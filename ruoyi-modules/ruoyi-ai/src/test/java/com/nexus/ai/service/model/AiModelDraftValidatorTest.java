package com.nexus.ai.service.model;

import com.nexus.common.ai.domain.bo.AiModelBo;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 模型草稿校验器单元测试（纯 Java 构造，无 Spring 上下文，沿用 Task 3 纯重载测试模式）
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiModelDraftValidatorTest {

    private final AiModelDraftValidator validator =
        new AiModelDraftValidator(new AiProviderPolicyRegistry());

    @Test
    void missingProtocolIsFilledWithProviderDefault() {
        AiModelBo draft = textDraft();
        assertEquals("OPENAI_CHAT_COMPLETIONS", validator.resolveAndValidate(draft, "OPENCODE_GO"));
        assertEquals("OPENAI_CHAT_COMPLETIONS", draft.getApiProtocol());
    }

    @Test
    void providedProtocolIsNormalizedAndValidated() {
        AiModelBo draft = textDraft();
        draft.setApiProtocol(" openai_responses ");
        assertEquals("OPENAI_RESPONSES", validator.resolveAndValidate(draft, "OPENCODE_GO"));
        assertEquals("OPENAI_RESPONSES", draft.getApiProtocol());
    }

    @Test
    void protocolNotAllowedForProviderIsRejected() {
        AiModelBo draft = textDraft();
        draft.setApiProtocol("ANTHROPIC_MESSAGES");
        ServiceException e = assertThrows(ServiceException.class,
            () -> validator.resolveAndValidate(draft, "DASHSCOPE"));
        assertTrue(e.getMessage().contains("不支持协议"));
    }

    @Test
    void unknownProviderAndUnknownProtocolAreRejected() {
        ServiceException e1 = assertThrows(ServiceException.class,
            () -> validator.resolveAndValidate(textDraft(), "NOT_A_PROVIDER"));
        assertTrue(e1.getMessage().contains("未知厂商类型"));
        AiModelBo draft = textDraft();
        draft.setApiProtocol("BOGUS_PROTOCOL");
        ServiceException e2 = assertThrows(ServiceException.class,
            () -> validator.resolveAndValidate(draft, "OPENAI"));
        assertTrue(e2.getMessage().contains("未知API协议"));
    }

    @Test
    void embeddingLocksCapabilitiesAndRequiresPositiveDimension() {
        AiModelBo draft = textDraft();
        draft.setModelType("EMBEDDING");
        draft.setEmbeddingDimension(1024);
        validator.resolveAndValidate(draft, "OPENAI");
        assertEquals("TEXT", draft.getInputCapabilities());
        assertEquals("VECTOR", draft.getOutputCapabilities());

        draft.setEmbeddingDimension(null);
        ServiceException e = assertThrows(ServiceException.class,
            () -> validator.resolveAndValidate(draft, "OPENAI"));
        assertTrue(e.getMessage().contains("向量维度"));

        draft.setEmbeddingDimension(-1);
        assertThrows(ServiceException.class, () -> validator.resolveAndValidate(draft, "OPENAI"));
    }

    @Test
    void embeddingConflictingCapabilityIsRejected() {
        AiModelBo draft = textDraft();
        draft.setModelType("EMBEDDING");
        draft.setEmbeddingDimension(1024);
        draft.setOutputCapabilities("TEXT");
        ServiceException e = assertThrows(ServiceException.class,
            () -> validator.resolveAndValidate(draft, "OPENAI"));
        assertTrue(e.getMessage().contains("输出能力锁定为 VECTOR"));
    }

    @Test
    void rerankLocksTextAndRanking() {
        AiModelBo draft = textDraft();
        draft.setModelType("RERANK");
        validator.resolveAndValidate(draft, "OPENAI");
        assertEquals("TEXT", draft.getInputCapabilities());
        assertEquals("RANKING", draft.getOutputCapabilities());
    }

    @Test
    void maxTokensBeyondContextWindowIsRejected() {
        AiModelBo draft = textDraft();
        draft.setContextWindow(4096);
        draft.setMaxTokens(8192);
        ServiceException e = assertThrows(ServiceException.class,
            () -> validator.resolveAndValidate(draft, "OPENAI"));
        assertTrue(e.getMessage().contains("上下文窗口"));
    }

    private AiModelBo textDraft() {
        AiModelBo draft = new AiModelBo();
        draft.setModelName("test-model");
        draft.setModelType("TEXT");
        return draft;
    }
}
