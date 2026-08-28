package com.nexus.common.ai.enums;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型能力枚举规范化单元测试
 * <p>验证能力列表按枚举声明顺序排序去重、未知能力拒绝、
 * 以及各模型类型的默认输入/输出能力模板。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiModelCapabilityEnumTest {

    @Test
    void canonicalizeMustSortAndDeduplicateKnownCapabilities() {
        assertEquals("TEXT,IMAGE", AiModelCapabilityEnum.canonicalize(List.of("IMAGE", "TEXT", "IMAGE")));
    }

    @Test
    void canonicalizeMustRejectUnknownCapabilities() {
        assertThrows(IllegalArgumentException.class,
            () -> AiModelCapabilityEnum.canonicalize(List.of("TEXT", "UNKNOWN")));
    }

    @Test
    void embeddingTemplateMustBeLockedToTextToVector() {
        assertEquals("TEXT", AiModelCapabilityEnum.defaultInput("EMBEDDING"));
        assertEquals("VECTOR", AiModelCapabilityEnum.defaultOutput("EMBEDDING"));
    }

    @Test
    void rerankTemplateMustBeLockedToTextToRanking() {
        assertEquals("TEXT", AiModelCapabilityEnum.defaultInput("RERANK"));
        assertEquals("RANKING", AiModelCapabilityEnum.defaultOutput("RERANK"));
    }

    @Test
    void csvCanonicalizeMustReturnNullWhenNotProvided() {
        assertNull(AiModelCapabilityEnum.canonicalize((String) null));
        assertNull(AiModelCapabilityEnum.canonicalize(" "));
    }

    @Test
    void csvCanonicalizeMustNormalizeMixedCaseAndSpaces() {
        assertEquals("TEXT,IMAGE", AiModelCapabilityEnum.canonicalize("image, text"));
    }

    @Test
    void parseMustReturnEmptySetWhenNotProvided() {
        assertTrue(AiModelCapabilityEnum.parse(null).isEmpty());
        assertTrue(AiModelCapabilityEnum.parse("").isEmpty());
    }

    @Test
    void parseMustContainAllRequestedCapabilities() {
        EnumSet<AiModelCapabilityEnum> parsed = AiModelCapabilityEnum.parse("TEXT,IMAGE");
        assertEquals(2, parsed.size());
        assertTrue(parsed.contains(AiModelCapabilityEnum.TEXT));
        assertTrue(parsed.contains(AiModelCapabilityEnum.IMAGE));
    }

    @Test
    void parseMustRejectUnknownCapability() {
        assertThrows(IllegalArgumentException.class, () -> AiModelCapabilityEnum.parse("TEXT,BOGUS"));
    }
}
