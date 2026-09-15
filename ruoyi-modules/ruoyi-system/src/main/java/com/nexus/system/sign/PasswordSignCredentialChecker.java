package com.nexus.system.sign;

import cn.hutool.crypto.digest.BCrypt;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.support.SignCredentialChecker;
import com.nexus.system.domain.SysUser;
import com.nexus.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 密码签署凭证校验器。
 * <p>用户登录密码再次录入校验，<b>不复用会话票据</b>；凭证不落库、不记日志。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
public class PasswordSignCredentialChecker implements SignCredentialChecker {

    private final SysUserMapper userMapper;

    @Override
    public String authMethod() {
        return SignAuthMethod.PASSWORD;
    }

    @Override
    public void verify(Long userId, String credential) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        if (user.getPassword() == null || !BCrypt.checkpw(credential, user.getPassword())) {
            throw new ServiceException("签署密码错误");
        }
    }
}
