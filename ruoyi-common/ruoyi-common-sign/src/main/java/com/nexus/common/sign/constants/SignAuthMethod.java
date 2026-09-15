package com.nexus.common.sign.constants;

import java.util.Set;

/**
 * 签署认证方式。
 *
 * @author nexus
 */
public final class SignAuthMethod {

    private SignAuthMethod() {
    }

    /** 仅校验登录态与操作权限 */
    public static final String LOGIN_ONLY = "LOGIN_ONLY";
    /** 登录密码再次录入 */
    public static final String PASSWORD = "PASSWORD";
    /** 短信验证码（P1 未启用，见下方 SUPPORTED 说明） */
    public static final String SMS = "SMS";
    /** 人脸 1:1（P3 预留，P1 不支持） */
    public static final String FACE = "FACE";

    /**
     * 本期对外提供的认证方式。
     * <p><b>不含 {@code SMS}</b>：本模块取不到当前登录用户的手机号
     * （{@code LoginUser} 无手机号字段，且 {@code sys_user} 属 ruoyi-system，
     * 按 {@code SignCredentialChecker} 的分层约定不得由 common 模块直读），
     * 因此没有「下发签署验证码」的接口，短信方式对用户不可用。
     * 常量与 {@code SmsSignCredentialChecker} 保留，待具备手机号来源的模块接入后重新纳入。</p>
     */
    private static final Set<String> SUPPORTED = Set.of(LOGIN_ONLY, PASSWORD);

    /** 是否为本期支持的认证方式 */
    public static boolean isSupported(String method) {
        return SUPPORTED.contains(method);
    }
}
