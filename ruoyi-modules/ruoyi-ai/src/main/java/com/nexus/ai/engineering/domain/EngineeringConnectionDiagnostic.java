package com.nexus.ai.engineering.domain;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import java.util.regex.Pattern;

/** 当前设备实际返回的有界连接摘要；不接收路径、凭证或原始 CLI 正文。 */
public record EngineeringConnectionDiagnostic(
        Boolean connected, String cliVersion, String editorVersion, String transportMode) {
    private static final Pattern VERSION = Pattern.compile(
            "^v?\\d{1,4}(?:\\.[0-9A-Za-z_-]{1,32}){0,5}(?:[-+][0-9A-Za-z.-]{1,32})?$");

    /** 验证公开摘要字段；错误响应不回显收到的内容。 */
    public EngineeringConnectionDiagnostic validated() {
        require(connected != null && version(cliVersion) && version(editorVersion)
                        && (transportMode == null || "CLI_COMPAT".equals(transportMode)),
                422, "INVALID_RESULT", "连接诊断内容无效");
        return this;
    }

    private static boolean version(String text) {
        return text == null || text.length() <= 80 && VERSION.matcher(text).matches();
    }
}
