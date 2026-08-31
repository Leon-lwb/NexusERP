package com.nexus.ai.service.model;

import com.nexus.common.ai.domain.entity.AiModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型能力选择器单元测试（纯 Java，无 Spring 上下文）
 * <p>语义：每个 supports* 判定都要求「模型类型 + 输入/输出能力」同时满足；
 * 能力为空/非法时一律 false（不抛异常），保证运行时消费者对存量数据健壮。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiModelCapabilitySelectionTest {

    private static AiModel model(String modelType, String inputCapabilities,
                                 String outputCapabilities, String apiProtocol) {
        AiModel model = new AiModel();
        model.setId(1L);
        model.setModelName("test-model");
        model.setDisplayName("test-model");
        model.setModelType(modelType);
        model.setInputCapabilities(inputCapabilities);
        model.setOutputCapabilities(outputCapabilities);
        model.setApiProtocol(apiProtocol);
        return model;
    }

    // ------------------------------------------------------------------
    // 计划内三条测试
    // ------------------------------------------------------------------

    @Test
    void visionReviewRequiresVisionImageInputAndTextOutput() {
        AiModel model = model("VISION", "TEXT,IMAGE", "TEXT", "OPENAI_CHAT_COMPLETIONS");
        assertTrue(AiModelCapabilitySelection.supportsVisionReview(model));
    }

    @Test
    void visionReviewRejectsVisionWithoutImageCapability() {
        AiModel model = model("VISION", "TEXT", "TEXT", "OPENAI_CHAT_COMPLETIONS");
        assertFalse(AiModelCapabilitySelection.supportsVisionReview(model));
    }

    @Test
    void imageGenerationRequiresTextInputAndImageOutput() {
        AiModel model = model("IMAGE", "TEXT", "IMAGE", null);
        assertTrue(AiModelCapabilitySelection.supportsImageGeneration(model));
    }

    // ------------------------------------------------------------------
    // supportsChat：TEXT 或 VISION 类型 + TEXT 输入 + TEXT 输出
    // ------------------------------------------------------------------

    @Test
    void supportsChatAcceptsTextAndVisionModels() {
        assertTrue(AiModelCapabilitySelection.supportsChat(model("TEXT", "TEXT", "TEXT", null)));
        assertTrue(AiModelCapabilitySelection.supportsChat(model("VISION", "TEXT,IMAGE", "TEXT", null)));
    }

    @Test
    void supportsChatRequiresTextInputAndTextOutput() {
        assertFalse(AiModelCapabilitySelection.supportsChat(model("TEXT", "IMAGE", "TEXT", null)),
            "缺 TEXT 输入（纯图入）不支持对话");
        assertFalse(AiModelCapabilitySelection.supportsChat(model("TEXT", "TEXT", "IMAGE", null)),
            "缺 TEXT 输出不支持对话");
    }

    // ------------------------------------------------------------------
    // supportsEmbedding / supportsRerank：类型 + 输入 + 输出全匹配
    // ------------------------------------------------------------------

    @Test
    void supportsEmbeddingRequiresEmbeddingTypeTextInputVectorOutput() {
        assertTrue(AiModelCapabilitySelection.supportsEmbedding(model("EMBEDDING", "TEXT", "VECTOR", null)));
        assertFalse(AiModelCapabilitySelection.supportsEmbedding(model("EMBEDDING", "TEXT", "TEXT", null)),
            "输出非 VECTOR 不是合法向量模型");
        assertFalse(AiModelCapabilitySelection.supportsEmbedding(model("TEXT", "TEXT", "VECTOR", null)),
            "类型非 EMBEDDING 不支持向量化");
    }

    @Test
    void supportsRerankRequiresRerankTypeTextInputRankingOutput() {
        assertTrue(AiModelCapabilitySelection.supportsRerank(model("RERANK", "TEXT", "RANKING", null)));
        assertFalse(AiModelCapabilitySelection.supportsRerank(model("RERANK", "TEXT", "VECTOR", null)),
            "输出非 RANKING 不是合法重排模型");
        assertFalse(AiModelCapabilitySelection.supportsRerank(model("TEXT", "TEXT", "RANKING", null)),
            "类型非 RERANK 不支持重排");
    }

    // ------------------------------------------------------------------
    // supportsImageGeneration：IMAGE 类型 + TEXT 输入 + IMAGE 输出
    // ------------------------------------------------------------------

    @Test
    void supportsImageGenerationRequiresFullMatch() {
        assertFalse(AiModelCapabilitySelection.supportsImageGeneration(model("IMAGE", "IMAGE", "IMAGE", null)),
            "缺 TEXT 输入（无提示词入口）不支持文生图");
        assertFalse(AiModelCapabilitySelection.supportsImageGeneration(model("IMAGE", "TEXT", "TEXT", null)),
            "缺 IMAGE 输出不能文生图");
        assertFalse(AiModelCapabilitySelection.supportsImageGeneration(model("VISION", "TEXT,IMAGE", "TEXT", null)),
            "VISION（图理解）类型不是文生图模型");
    }

    // ------------------------------------------------------------------
    // 错误类型拒绝：IMAGE 类型永不做视觉复核
    // ------------------------------------------------------------------

    @Test
    void wrongTypeNeverSupportsVisionReview() {
        assertFalse(AiModelCapabilitySelection.supportsVisionReview(model("IMAGE", "TEXT,IMAGE", "TEXT", null)),
            "IMAGE（文生图）类型不做图像理解复核");
        assertFalse(AiModelCapabilitySelection.supportsVisionReview(model("TEXT", "TEXT,IMAGE", "TEXT", null)));
    }

    @Test
    void visionReviewRequiresTextInputAlongsideImageInput() {
        assertFalse(AiModelCapabilitySelection.supportsVisionReview(model("VISION", "IMAGE", "TEXT", null)),
            "视觉复核提示词需要 TEXT 输入，仅 IMAGE 输入不算完整视觉对话模型");
    }

    // ------------------------------------------------------------------
    // 健壮性：null 模型 / 空能力 / 非法能力 —— 一律 false，绝不 NPE
    // ------------------------------------------------------------------

    @Test
    void nullModelReturnsFalseForAllChecks() {
        assertFalse(AiModelCapabilitySelection.supportsChat(null));
        assertFalse(AiModelCapabilitySelection.supportsVisionReview(null));
        assertFalse(AiModelCapabilitySelection.supportsEmbedding(null));
        assertFalse(AiModelCapabilitySelection.supportsRerank(null));
        assertFalse(AiModelCapabilitySelection.supportsImageGeneration(null));
    }

    @Test
    void blankCapabilitiesReturnFalseWithoutThrowing() {
        AiModel nullCaps = model("VISION", null, null, null);
        assertFalse(AiModelCapabilitySelection.supportsVisionReview(nullCaps));
        AiModel blankCaps = model("TEXT", " ", "", null);
        assertFalse(AiModelCapabilitySelection.supportsChat(blankCaps));
        AiModel blankImage = model("IMAGE", "", " ", null);
        assertFalse(AiModelCapabilitySelection.supportsImageGeneration(blankImage));
    }

    @Test
    void invalidCapabilityCsvReturnsFalseInsteadOfThrowing() {
        AiModel bogus = model("VISION", "TEXT,BOGUS", "TEXT", null);
        assertFalse(AiModelCapabilitySelection.supportsVisionReview(bogus),
            "非法能力串（存量脏数据）按能力不足处理，不向上抛异常");
        AiModel bogusOutput = model("IMAGE", "TEXT", "not-a-capability", null);
        assertFalse(AiModelCapabilitySelection.supportsImageGeneration(bogusOutput));
    }

    // ------------------------------------------------------------------
    // 大小写宽容：类型与能力码均与字典一致的大小写混写
    // ------------------------------------------------------------------

    @Test
    void typeAndCapabilitiesAreCaseInsensitive() {
        assertTrue(AiModelCapabilitySelection.supportsVisionReview(model("vision", "text,image", "text", null)));
        assertTrue(AiModelCapabilitySelection.supportsChat(model(" Text ", "TEXT", "TEXT", null)));
    }
}
