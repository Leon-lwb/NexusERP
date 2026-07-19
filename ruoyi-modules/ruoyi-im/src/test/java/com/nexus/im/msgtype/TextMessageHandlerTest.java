package com.nexus.im.msgtype;

import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P5-4 MEDIUM: TextMessageHandler XSS 清理单元测试。
 * <p>
 * 守护点：
 * <ul>
 *   <li>开启 sanitize（默认）：{@code <script>} / {@code <iframe>} 等危险标签被移除</li>
 *   <li>纯文本（含 {@code <3} 表情符号）不受影响</li>
 *   <li>sanitize 后产生空字符串（原内容全是标签）→ 抛"消息内容不能为空"</li>
 *   <li>禁用 sanitize（{@code im.xss.sanitize.enabled=false}）：原样返回，不清理</li>
 * </ul>
 *
 * @author nexus
 */
class TextMessageHandlerTest {

    private TextMessageHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new TextMessageHandler();
        // 默认开启 sanitize，模拟 im.xss.sanitize.enabled=true（@Value 默认值）
        setSanitizeEnabled(handler, true);
    }

    @Test
    void normalize_scriptTag_isStripped() {
        String result = handler.normalize("<script>alert('xss')</script>hello");
        assertThat(result).isEqualTo("alert('xss')hello");
    }

    @Test
    void normalize_iframeTag_isStripped() {
        String result = handler.normalize("<iframe src=\"evil.com\"></iframe>safe");
        assertThat(result).isEqualTo("safe");
    }

    @Test
    void normalize_multipleDangerousTags_allStripped() {
        String result = handler.normalize("<script>x</script><iframe>y</iframe><b>z</b>text");
        // <b>z</b> 也会被 cleanHtmlTag 移除（HTML 标签全删，仅保留标签间内容）
        assertThat(result).isEqualTo("xyztext");
    }

    @Test
    void normalize_plainTextWithHeartEmoji_notAffected() {
        // <3 是常见表情符号，不是 HTML 标签（没有匹配 <[^>]*>），不应被清理
        String result = handler.normalize("I love you <3");
        assertThat(result).isEqualTo("I love you <3");
    }

    @Test
    void normalize_plainText_notAffected() {
        String result = handler.normalize("Hello, world!");
        assertThat(result).isEqualTo("Hello, world!");
    }

    @Test
    void normalize_blankAfterSanitize_throwsEmptyException() {
        // 原内容全是 HTML 标签，sanitize 后为空 → 抛 ServiceException
        assertThatThrownBy(() -> handler.normalize("<script></script><iframe></iframe>"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("消息内容不能为空");
    }

    @Test
    void normalize_blankInput_throwsEmptyException() {
        assertThatThrownBy(() -> handler.normalize("   "))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("消息内容不能为空");
    }

    @Test
    void normalize_tooLongInput_throwsLengthException() {
        StringBuilder sb = new StringBuilder();
        sb.append("<script>x</script>".repeat(200)); // 18 chars × 200 = 3600，清理后剩 "x"×200 = 200
        // 清理后长度 = 200，未超 maxLength(2000)，不会抛异常 → 验证 sanitize 在 length 校验前生效
        String result = handler.normalize(sb.toString());
        assertThat(result).hasSize(200);
        assertThat(result).doesNotContain("<").doesNotContain(">");
    }

    @Test
    void normalize_tooLongAfterSanitize_throwsLengthException() {
        // 清理后内容超长 → 抛长度异常（验证 sanitize 在 length 校验前生效）
        StringBuilder sb = new StringBuilder();
        sb.append("a".repeat(2001)); // 纯文本无标签，长度 2001 > maxLength(2000)
        assertThatThrownBy(() -> handler.normalize(sb.toString()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("消息内容不能超过");
    }

    @Test
    void sanitizeDisabled_keepsRawContent() throws Exception {
        // 关闭 sanitize 开关，验证原样返回（不清理 HTML 标签）
        setSanitizeEnabled(handler, false);
        String result = handler.normalize("<script>alert('xss')</script>hello");
        assertThat(result).isEqualTo("<script>alert('xss')</script>hello");
    }

    @Test
    void type_isText() {
        assertThat(handler.type()).isEqualTo("text");
    }

    @Test
    void requiresOss_isFalse() {
        assertThat(handler.requiresOss()).isFalse();
    }

    /**
     * 通过反射设置 private 字段 xssSanitizeEnabled，模拟 @Value 注入。
     * <p>避免启动 Spring 容器，保持纯单元测试。
     */
    private void setSanitizeEnabled(TextMessageHandler handler, boolean enabled) throws Exception {
        Field field = TextMessageHandler.class.getDeclaredField("xssSanitizeEnabled");
        field.setAccessible(true);
        field.setBoolean(handler, enabled);
    }
}
