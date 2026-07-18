package com.nexus.im.msgtype;

import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3-7: P1-2 消息类型策略模式回归测试。
 * <p>
 * 守护 P1-2 阶段从 ImChatServiceImpl 硬编码 SUPPORTED_MSG_TYPES Set 改造为策略模式：
 * <ul>
 *   <li>{@link ImMessageTypeRegistry} 自动收集所有 {@link ImMessageTypeHandler} Bean</li>
 *   <li>新增消息类型只需实现接口 + 注册为 Bean，无需修改调用方</li>
 *   <li>类型空白/大小写不敏感回退到 text</li>
 * </ul>
 * <p>
 * 任意重构若回退到硬编码 Set，对应用例立即失败。
 */
class ImMessageTypeRegistryTest {

    private final ImMessageTypeRegistry registry = new ImMessageTypeRegistry(List.of(
        new TextMessageHandler(),
        new ImageMessageHandler(),
        new FileMessageHandler()
    ));

    /**
     * P1-2: 不支持的消息类型 → 抛 ServiceException（替代原硬编码 Set.contains 校验）。
     */
    @Test
    void require_unknownType_throwsServiceException() {
        assertThatThrownBy(() -> registry.require("video"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不支持的消息类型");
    }

    /**
     * P1-2: 空白类型 → 回退到 text handler（容错策略）。
     */
    @Test
    void require_blankType_returnsTextHandler() {
        ImMessageTypeHandler h = registry.require("   ");
        assertThat(h.type()).isEqualTo("text");
    }

    /**
     * P1-2: null 类型 → 回退到 text handler。
     */
    @Test
    void require_nullType_returnsTextHandler() {
        ImMessageTypeHandler h = registry.require(null);
        assertThat(h.type()).isEqualTo("text");
    }

    /**
     * P1-2: 大写类型 → 归一化为小写后查表（"TEXT" → "text"）。
     */
    @Test
    void require_upperCaseType_normalizesToLowerCase() {
        ImMessageTypeHandler h = registry.require("TEXT");
        assertThat(h.type()).isEqualTo("text");
        // 同理：Image / FILE
        assertThat(registry.require("Image").type()).isEqualTo("image");
        assertThat(registry.require("FILE").type()).isEqualTo("file");
    }

    /**
     * P1-2: supportedTypes 返回所有已注册的类型（text/image/file）。
     */
    @Test
    void supportedTypes_includesTextImageFile() {
        assertThat(registry.supportedTypes()).containsExactlyInAnyOrder("text", "image", "file");
    }

    /**
     * P1-2: handlers Map 是不可变的（防止运行时被篡改）。
     */
    @Test
    void supportedTypes_isImmutable() {
        var types = registry.supportedTypes();
        assertThatThrownBy(() -> types.add("audio"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Handler normalize 校验 ====================

    /**
     * P1-2: TextMessageHandler.normalize 对空白内容抛异常。
     */
    @Test
    void textHandler_normalize_blank_throwsServiceException() {
        TextMessageHandler handler = new TextMessageHandler();
        assertThatThrownBy(() -> handler.normalize("   "))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能为空");
    }

    /**
     * P1-2: TextMessageHandler.normalize 对超长内容抛异常。
     */
    @Test
    void textHandler_normalize_tooLong_throwsServiceException() {
        TextMessageHandler handler = new TextMessageHandler();
        String tooLong = "a".repeat(2001);
        assertThatThrownBy(() -> handler.normalize(tooLong))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("2000");
    }

    /**
     * P1-2: ImageMessageHandler / FileMessageHandler 标记 requiresOss=true。
     */
    @Test
    void imageAndFileHandlers_requireOss() {
        assertThat(new ImageMessageHandler().requiresOss()).isTrue();
        assertThat(new FileMessageHandler().requiresOss()).isTrue();
        // TextMessageHandler 默认 requiresOss=false
        assertThat(new TextMessageHandler().requiresOss()).isFalse();
    }
}
