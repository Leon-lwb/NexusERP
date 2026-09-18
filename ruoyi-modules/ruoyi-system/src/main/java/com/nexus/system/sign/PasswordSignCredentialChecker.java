package com.nexus.system.sign;

import cn.hutool.crypto.digest.BCrypt;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.support.SignCredentialChecker;
import com.nexus.system.domain.SysUser;
import com.nexus.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 密码签署凭证校验器。
 * <p>用户登录密码再次录入校验，<b>不复用会话票据</b>；凭证不落库、不记日志。</p>
 *
 * <p><b>失败限速（与 SmsSignCredentialChecker 同一模式）：</b>签署二级认证是最应抵抗
 * 在线爆破的控制点 —— 会话被劫持后攻击者可无限次尝试受害者登录密码（单次 BCrypt ~100ms）。
 * 连续 {@link #MAX_FAIL} 次错误锁定 {@link #LOCK_TTL}，计数器与锁定同寿命，成功即清零。</p>
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordSignCredentialChecker implements SignCredentialChecker {

    /** 连续错误锁定阈值 */
    public static final int MAX_FAIL = 5;
    /** 锁定 key 前缀 */
    public static final String LOCK_KEY_PREFIX = "es:sign:pwd:lock:";
    /** 锁定时长 */
    public static final Duration LOCK_TTL = Duration.ofMinutes(10);
    /** 错误次数计数器 key 前缀 */
    public static final String CNT_KEY_PREFIX = LOCK_KEY_PREFIX + "cnt:";

    private final SysUserMapper userMapper;

    @Override
    public String authMethod() {
        return SignAuthMethod.PASSWORD;
    }

    @Override
    public void verify(Long userId, String credential) {
        if (Boolean.TRUE.equals(RedisUtils.hasKey(LOCK_KEY_PREFIX + userId))) {
            throw new ServiceException("签署密码错误次数过多，请 10 分钟后重试");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        if (user.getPassword() == null || !BCrypt.checkpw(credential, user.getPassword())) {
            String cntKey = CNT_KEY_PREFIX + userId;
            long failCount = RedisUtils.incrAtomicValue(cntKey);
            // 计数器与锁定同寿命：仅在成功校验时清零的话，锁定到期后残留的旧计数
            // （>= MAX_FAIL）会让用户下一次输错就被立刻重新锁定
            RedisUtils.setCacheObject(cntKey, failCount, LOCK_TTL);
            if (failCount >= MAX_FAIL) {
                RedisUtils.setCacheObject(LOCK_KEY_PREFIX + userId, "1", LOCK_TTL);
                log.warn("签署密码连续错误达上限，用户 {} 锁定 {} 分钟", userId, LOCK_TTL.toMinutes());
            }
            throw new ServiceException("签署密码错误");
        }
        // 校验通过：清空错误计数
        RedisUtils.deleteObject(CNT_KEY_PREFIX + userId);
    }
}
