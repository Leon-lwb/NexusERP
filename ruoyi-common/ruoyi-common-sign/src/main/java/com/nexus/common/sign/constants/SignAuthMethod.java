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
    /** 短信验证码 */
    public static final String SMS = "SMS";
    /** 人脸 1:1（P3 预留，P1 不支持） */
    public static final String FACE = "FACE";

    private static final Set<String> SUPPORTED = Set.of(LOGIN_ONLY, PASSWORD, SMS);

    /** 是否为本期支持的认证方式 */
    public static boolean isSupported(String method) {
        return SUPPORTED.contains(method);
    }
}
