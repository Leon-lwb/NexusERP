package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签署二级认证调度器。
 * <p>原则：认证失败即拒绝签名，<b>不允许降级</b>。</p>
 *
 * @author nexus
 */
@Component
public class SignAuthVerifier {

    private final Map<String, SignCredentialChecker> checkers = new HashMap<>();

    public SignAuthVerifier(List<SignCredentialChecker> checkerList) {
        if (checkerList != null) {
            for (SignCredentialChecker c : checkerList) {
                this.checkers.put(c.authMethod(), c);
            }
        }
    }

    /**
     * 校验凭证。
     *
     * @param userId     签署人用户ID
     * @param authMethod 认证方式
     * @param credential 凭证
     */
    public void verify(Long userId, String authMethod, String credential) {
        if (StringUtils.isBlank(authMethod)) {
            throw new ServiceException("签署认证方式不能为空");
        }
        if (SignAuthMethod.LOGIN_ONLY.equals(authMethod)) {
            return;
        }
        SignCredentialChecker checker = checkers.get(authMethod);
        if (checker == null) {
            throw new ServiceException("不支持的签署认证方式：{}", authMethod);
        }
        if (StringUtils.isBlank(credential)) {
            throw new ServiceException("请提供签署认证凭证");
        }
        checker.verify(userId, credential);
    }
}
