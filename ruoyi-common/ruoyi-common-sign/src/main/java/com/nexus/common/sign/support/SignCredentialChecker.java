package com.nexus.common.sign.support;

/**
 * 签署凭证校验器 SPI。
 * <p>由具备相应数据的模块提供实现（如密码校验需 {@code sys_user}，实现放 ruoyi-system）。</p>
 *
 * @author nexus
 */
public interface SignCredentialChecker {

    /**
     * 支持的认证方式，取值见 {@code SignAuthMethod}。
     */
    String authMethod();

    /**
     * 校验凭证，失败抛 {@code ServiceException}。
     *
     * @param userId     签署人用户ID
     * @param credential 凭证（明文，仅本次请求内存中使用，不落库不记日志）
     */
    void verify(Long userId, String credential);
}
