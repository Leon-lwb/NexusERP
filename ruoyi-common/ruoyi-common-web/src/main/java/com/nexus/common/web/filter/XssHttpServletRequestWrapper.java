package com.nexus.common.web.filter;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import com.nexus.common.core.utils.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * XSS过滤处理
 *
 * @author nexus
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {
    /**
     * @param request
     */
    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        if (value == null) {
            return null;
        }
        return cleanHtmlTagPreservingComparisons(value).trim();
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> valueMap = super.getParameterMap();
        if (MapUtil.isEmpty(valueMap)) {
            return valueMap;
        }
        // 避免某些容器不允许改参数的情况 copy一份重新改
        Map<String, String[]> map = new HashMap<>(valueMap.size());
        map.putAll(valueMap);
        for (Map.Entry<String, String[]> entry : map.entrySet()) {
            String[] values = entry.getValue();
            if (values != null) {
                int length = values.length;
                String[] escapseValues = new String[length];
                for (int i = 0; i < length; i++) {
                    // 防xss攻击和过滤前后空格
                    escapseValues[i] = cleanHtmlTagPreservingComparisons(values[i]).trim();
                }
                map.put(entry.getKey(), escapseValues);
            }
        }
        return map;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (ArrayUtil.isEmpty(values)) {
            return values;
        }
        int length = values.length;
        String[] escapseValues = new String[length];
        for (int i = 0; i < length; i++) {
            // 防xss攻击和过滤前后空格
            escapseValues[i] = cleanHtmlTagPreservingComparisons(values[i]).trim();
        }
        return escapseValues;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        // 非json类型，直接返回
        if (!isJsonRequest()) {
            return super.getInputStream();
        }

        // 为空，直接返回
        String json = StrUtil.str(IoUtil.readBytes(super.getInputStream(), false), StandardCharsets.UTF_8);
        if (StringUtils.isEmpty(json)) {
            return super.getInputStream();
        }

        // xss过滤
        json = cleanHtmlTagPreservingComparisons(json).trim();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        final ByteArrayInputStream bis = IoUtil.toStream(jsonBytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return true;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public int available() throws IOException {
                return jsonBytes.length;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return bis.read();
            }
        };
    }

    /**
     * 仅保留不可能是合法 HTML 标签的精确比较符 <> 和 <=>。
     * 先临时保护比较符，再对完整原文执行既有标签过滤，避免分段清洗绕过跨段标签。
     * 不解析或重序列化 JSON，中文、大 ID 与高精度数量字面量保持原样。
     */
    private static String cleanHtmlTagPreservingComparisons(String value) {
        if (!value.contains("<>") && !value.contains("<=>")) {
            return HtmlUtil.cleanHtmlTag(value);
        }
        // 每次生成且检查原文不存在的标记，不能把用户原有文本误还原成比较符。
        String marker;
        do {
            marker = "__XSS_COMPARE_" + UUID.randomUUID() + "__";
        } while (value.contains(marker));
        String notEqual = marker + "NE";
        String nullSafeEqual = marker + "NSE";
        String protectedValue = value.replace("<=>", nullSafeEqual).replace("<>", notEqual);
        return HtmlUtil.cleanHtmlTag(protectedValue)
            .replace(nullSafeEqual, "<=>")
            .replace(notEqual, "<>");
    }

    /**
     * 是否是Json请求
     */
    public boolean isJsonRequest() {
        String header = super.getHeader(HttpHeaders.CONTENT_TYPE);
        return StringUtils.startsWithIgnoreCase(header, MediaType.APPLICATION_JSON_VALUE);
    }
}
