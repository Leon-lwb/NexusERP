package com.nexus.common.core.utils.sql;

import cn.hutool.core.exceptions.UtilException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.nexus.common.core.utils.StringUtils;

/**
 * sql操作工具类
 *
 * @author nexus
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SqlUtil {

    /**
     * 定义常用的 sql关键字
     */
    public static final String SQL_REGEX = "|%0A|and |extractvalue|updatexml|sleep|information_schema|exec |insert |select |delete |update |drop |count |chr |mid |master |truncate |char |declare |or |union |like |+|/*|user()";

    /**
     * 仅支持字母、数字、下划线、空格、逗号、小数点（支持多个字段排序）
     */
    public static final String SQL_PATTERN = "[a-zA-Z0-9_\\ \\,\\.]+";

    /**
     * 检查字符，防止注入绕过
     */
    public static String escapeOrderBySql(String value) {
        if (StringUtils.isNotEmpty(value) && !isValidOrderBySql(value)) {
            throw new IllegalArgumentException("参数不符合规范，不能进行查询");
        }
        return value;
    }

    /**
     * 验证 order by 语法是否符合规范
     */
    public static boolean isValidOrderBySql(String value) {
        return value.matches(SQL_PATTERN);
    }

    /**
     * SQL关键字检查
     */
    public static void filterKeyword(String value) {
        if (StringUtils.isEmpty(value)) {
            return;
        }

        // ==================== 核心增强：自动拦截单引号 ====================
        // 单引号是 SQL 注入的最基本载体，直接禁止
        if (value.contains("'")) {
            throw new UtilException("请求参数包含非法字符【'】，已禁止执行");
        }

        // ==================== 原有逻辑增强 ====================
        // 先去除所有空白字符再匹配，防止 "se lect" 等空白绕过
        String normalizedValue = value.replaceAll("\\p{Z}|\\s", "");
        String[] sqlKeywords = StringUtils.split(SQL_REGEX, "\\|");
        for (String sqlKeyword : sqlKeywords) {
            if (StringUtils.indexOf(normalizedValue, sqlKeyword) > -1) {
                throw new UtilException("请求参数包含敏感关键词'" + sqlKeyword + "'，可能存在安全风险");
            }
        }
    }
}
