package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 短信验证码签署凭证校验器。
 * <p>验证码由 {@code EsSignatureController.sendSmsCode()} 发送并写入 Redis；
 * 校验成功后立即删除（防重放）。</p>
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsSignCredentialChecker implements SignCredentialChecker {

    /** 验证码 Redis key 前缀 */
    public static final String CODE_KEY_PREFIX = "es:sign:sms:";
    /** 验证码有效期 */
    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    /** 连续错误锁定阈值 */
    public static final int MAX_FAIL = 5;
    /** 锁定 key 前缀 */
    public static final String LOCK_KEY_PREFIX = "es:sign:sms:lock:";
    /** 锁定时长 */
    public static final Duration LOCK_TTL = Duration.ofMinutes(10);
    /** 错误次数计数器 key 前缀 */
    public static final String CNT_KEY_PREFIX = LOCK_KEY_PREFIX + "cnt:";

    @Override
    public String authMethod() {
        return SignAuthMethod.SMS;
    }

    @Override
    public void verify(Long userId, String credential) {
        if (Boolean.TRUE.equals(RedisUtils.hasKey(LOCK_KEY_PREFIX + userId))) {
            throw new ServiceException("验证码错误次数过多，请 10 分钟后重试");
        }
        String code = RedisUtils.getCacheObject(CODE_KEY_PREFIX + userId);
        if (StringUtils.isBlank(code)) {
            throw new ServiceException("验证码已失效，请重新获取");
        }
        if (!code.equals(credential)) {
            long failCount = RedisUtils.incrAtomicValue(CNT_KEY_PREFIX + userId);
            if (failCount >= MAX_FAIL) {
                RedisUtils.setCacheObject(LOCK_KEY_PREFIX + userId, "1", LOCK_TTL);
                log.warn("签署短信验证码连续错误达上限，用户 {} 锁定 {} 分钟", userId, LOCK_TTL.toMinutes());
            }
            throw new ServiceException("签署验证码错误");
        }
        // 校验通过：验证码一次性作废，同时清空错误计数
        RedisUtils.deleteObject(CODE_KEY_PREFIX + userId);
        RedisUtils.deleteObject(CNT_KEY_PREFIX + userId);
    }
}
