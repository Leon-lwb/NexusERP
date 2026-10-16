package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

/** 与操作系统无关的相对路径校验；拒绝 URL、盘符、UNC、父目录、控制字符和 Windows 特殊名称。 */
public final class EngineeringPaths {
    private EngineeringPaths() {}

    /** 验证规范相对路径；逐字符校验，多个换行也不能绕过整串正则。 */
    public static String validate(String path) {
        require(
                path != null
                        && !path.isEmpty()
                        && path.length() <= 255
                        && !path.startsWith("/")
                        && path.codePoints().noneMatch(EngineeringPaths::unsafe),
                422,
                "ARTIFACT_REFERENCE_INVALID",
                "产物路径必须是安全的相对路径");
        for (String segment : path.split("/", -1))
            require(
                    !segment.isEmpty()
                            && !segment.equals(".")
                            && !segment.equals("..")
                            && !segment.endsWith(".")
                            && !segment.endsWith(" ")
                            && !segment.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"),
                    422,
                    "ARTIFACT_REFERENCE_INVALID",
                    "产物路径包含不允许的目录或文件名");
        return path;
    }

    private static boolean unsafe(int point) {
        return Character.isISOControl(point)
                || Character.getType(point) == Character.FORMAT
                || "\\:%?#<>\"|*".indexOf(point) >= 0;
    }

    /** 文件显示名称只能是一个安全路径段，防止响应头和下载名称注入。 */
    public static String filename(String value) {
        validate(value);
        require(!value.contains("/"), 422, "INVALID_REQUEST", "原文件名不能包含目录");
        return value;
    }

    /** 解析 OBJ/MTL 同组相对引用，不允许跨目录父级或外部资源。 */
    public static String reference(String containing, String relative) {
        validate(relative);
        int slash = containing.lastIndexOf('/');
        return validate((slash < 0 ? "" : containing.substring(0, slash + 1)) + relative);
    }
}
