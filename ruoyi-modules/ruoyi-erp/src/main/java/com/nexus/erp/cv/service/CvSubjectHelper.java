package com.nexus.erp.cv.service;

import cn.hutool.core.util.StrUtil;

/**
 * 人脸底库 subject 命名工具
 * <p>vision 服务是单一全局底库,而 {@code sch_worker} 的唯一键是
 * {@code (tenant_id, employee_id)}(同工号在不同租户合法共存)。
 * 因此底库 subject 必须带租户维度,统一格式:</p>
 * <pre>T{tenantId}-EMP-{employeeId}   例: T000000-EMP-1001</pre>
 * <p>历史底库使用裸 {@code EMP-{employeeId}}(无租户维度)会跨租户覆盖/串库,
 * {@link #normalize(String, String)} 对旧格式做幂等补齐,并强制把 subject
 * 归一到<b>当前登录租户</b>——前端无法通过传参访问他租户底库。</p>
 *
 * @author nexus
 * @since 2026-09-20
 */
public final class CvSubjectHelper {

    /** 员工工号前缀(与 vision 服务约定的 subject 中段) */
    public static final String EMP_PREFIX = "EMP-";

    /** 租户前缀正则: T<数字>- */
    private static final String TENANT_PREFIX_REGEX = "^T\\d+-.*";

    private static final String DEFAULT_TENANT = "000000";

    private CvSubjectHelper() {
    }

    /**
     * 构造带租户维度的 subject
     *
     * @param tenantId   租户ID(空则按默认租户 000000)
     * @param employeeId 员工工号
     * @return T{tenantId}-EMP-{employeeId}
     */
    public static String build(String tenantId, String employeeId) {
        return "T" + normalizeTenant(tenantId) + "-" + EMP_PREFIX
            + (employeeId == null ? "" : employeeId.trim());
    }

    /**
     * 归一化前端传入的 subject:统一补/替换为当前租户前缀(幂等)。
     * <p>兼容三种输入:{@code EMP-1001}、{@code T000000-EMP-1001}、{@code T999-EMP-1001}(他租户→改绑当前租户)。</p>
     *
     * @param tenantId 当前租户ID
     * @param subject  前端传入的 subject
     * @return 归属当前租户的规范 subject;入参空白时原样返回
     */
    public static String normalize(String tenantId, String subject) {
        if (StrUtil.isBlank(subject)) {
            return subject;
        }
        String tenant = normalizeTenant(tenantId);
        String trimmed = subject.trim();
        if (trimmed.matches(TENANT_PREFIX_REGEX)) {
            // 已是带租户格式: 丢弃原租户段,改绑当前租户(防跨租户操作)
            return "T" + tenant + "-" + trimmed.substring(trimmed.indexOf('-') + 1);
        }
        return "T" + tenant + "-" + trimmed;
    }

    /**
     * 从 subject 中提取员工工号(用于反查 sch_worker)
     *
     * @param subject 底库 subject(带租户或历史裸格式)
     * @return 工号;无法解析返回 null
     */
    public static String employeeId(String subject) {
        if (StrUtil.isBlank(subject)) {
            return null;
        }
        String trimmed = subject.trim();
        if (trimmed.matches(TENANT_PREFIX_REGEX)) {
            trimmed = trimmed.substring(trimmed.indexOf('-') + 1);
        }
        String employeeId = StrUtil.removePrefix(trimmed, EMP_PREFIX);
        return StrUtil.isBlank(employeeId) ? null : employeeId;
    }

    private static String normalizeTenant(String tenantId) {
        return StrUtil.isBlank(tenantId) ? DEFAULT_TENANT : tenantId.trim();
    }
}
