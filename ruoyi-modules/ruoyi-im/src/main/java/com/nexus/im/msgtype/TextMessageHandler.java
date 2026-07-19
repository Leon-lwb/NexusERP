package com.nexus.im.msgtype;

import cn.hutool.http.HtmlUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文本消息处理器
 * <p>P5-4 MEDIUM: 通过 {@code im.xss.sanitize.enabled}（默认 true）控制 XSS 标签清理，
 * 防止 {@code <script>} / {@code <iframe>} 等危险标签进入消息流。前端应同时使用 DOMPurify 做双重保险。
 *
 * @author nexus
 */
@Component
public class TextMessageHandler implements ImMessageTypeHandler {

    /**
     * P5-4 MEDIUM: XSS 清理开关，默认开启。
     * <p>禁用场景：仅在调试或特殊业务（如代码片段分享）下关闭，正常生产环境应保持开启。
     */
    @Value("${im.xss.sanitize.enabled:true}")
    private boolean xssSanitizeEnabled;

    @Override
    public String type() {
        return "text";
    }

    @Override
    public String normalize(String content) {
        return doNormalize(content);
    }

    /**
     * P5-4 MEDIUM: 重写 sanitize，开启时调用 Hutool {@link HtmlUtil#cleanHtmlTag} 移除所有 HTML 标签。
     * <p>设计理由：IM 文本消息应保持纯文本，不应包含任何 HTML 标签。
     * {@code cleanHtmlTag} 通过正则 {@code <[^>]*>} 删除标签，不影响普通文本（如 {@code <3} 表情符号）。
     */
    @Override
    public String sanitize(String value) {
        if (!xssSanitizeEnabled) {
            return value;
        }
        return HtmlUtil.cleanHtmlTag(value);
    }
}
