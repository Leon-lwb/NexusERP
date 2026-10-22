package com.nexus.system.sign;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.extra.spring.SpringUtil;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.system.domain.SysUser;
import com.nexus.system.mapper.SysUserMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

/**
 * 密码签署凭证校验器测试 —— 失败限速（在线爆破防护）。
 *
 * <p>签署二级认证是最应抵抗在线爆破的控制点：会话被劫持后攻击者可无限次尝试
 * 受害者登录密码。规则与 {@code SmsSignCredentialCheckerTest} 同构：连续 5 次错误
 * 锁 10 分钟、计数器带 TTL、成功即清零。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class PasswordSignCredentialCheckerTest {

    private static final Long UID = 7L;
    private static final String CNT_KEY = PasswordSignCredentialChecker.CNT_KEY_PREFIX + UID;
    private static final String LOCK_KEY = PasswordSignCredentialChecker.LOCK_KEY_PREFIX + UID;

    private static ApplicationContext originalContext;

    @Mock
    private SysUserMapper userMapper;

    private PasswordSignCredentialChecker checker;
    private MockedStatic<RedisUtils> redisMock;

    /**
     * {@code RedisUtils} 的静态字段初始化要求 Spring 容器存在（同
     * {@code SmsSignCredentialCheckerTest} 的说明），单测无容器，装一个桩上下文。
     */
    @BeforeAll
    static void stubSpringContextForRedisUtils() {
        originalContext = SpringUtil.getApplicationContext();
        new SpringUtil().setApplicationContext(mock(ApplicationContext.class));
    }

    @AfterAll
    static void restoreSpringContext() {
        new SpringUtil().setApplicationContext(originalContext);
    }

    @BeforeEach
    void setUp() {
        checker = new PasswordSignCredentialChecker(userMapper);
        redisMock = mockStatic(RedisUtils.class);
    }

    @AfterEach
    void tearDown() {
        if (redisMock != null) {
            redisMock.close();
        }
    }

    /** 未锁定 + 用户密码为给定 BCrypt 串 */
    private void givenPassword(String rawPassword) {
        redisMock.when(() -> RedisUtils.hasKey(LOCK_KEY)).thenReturn(false);
        SysUser user = new SysUser();
        user.setUserId(UID);
        user.setPassword(BCrypt.hashpw(rawPassword));
        when(userMapper.selectById(UID)).thenReturn(user);
    }

    @Test
    @DisplayName("认证方式: PASSWORD")
    void authMethod_isPassword() {
        assertEquals(SignAuthMethod.PASSWORD, checker.authMethod());
    }

    @Test
    @DisplayName("正确密码: 通过且清空错误计数")
    void correctPassword_passesAndClearsCounter() {
        givenPassword("admin123");

        assertDoesNotThrow(() -> checker.verify(UID, "admin123"));
        verify(userMapper).selectById(UID);
        redisMock.verify(() -> RedisUtils.deleteObject(CNT_KEY));
    }

    @Test
    @DisplayName("错误密码: 计数 +1 且写入 TTL")
    void wrongPassword_incrementsCounterWithTtl() {
        givenPassword("admin123");
        redisMock.when(() -> RedisUtils.incrAtomicValue(CNT_KEY)).thenReturn(1L);

        ServiceException e = assertThrows(ServiceException.class, () -> checker.verify(UID, "wrong"));
        assertEquals("签署密码错误", e.getMessage());
        redisMock.verify(() -> RedisUtils.incrAtomicValue(CNT_KEY));
        // 计数必须带 TTL，否则锁定到期后残留计数会让用户再次被立即锁定
        redisMock.verify(() -> RedisUtils.setCacheObject(CNT_KEY, 1L, PasswordSignCredentialChecker.LOCK_TTL));
    }

    @Test
    @DisplayName("连续错误达到上限: 写入锁定 key")
    void fifthFailure_writesLock() {
        givenPassword("admin123");
        redisMock.when(() -> RedisUtils.incrAtomicValue(CNT_KEY)).thenReturn((long) PasswordSignCredentialChecker.MAX_FAIL);

        assertThrows(ServiceException.class, () -> checker.verify(UID, "wrong"));
        redisMock.verify(() -> RedisUtils.setCacheObject(LOCK_KEY, "1", PasswordSignCredentialChecker.LOCK_TTL));
    }

    @Test
    @DisplayName("已锁定: 即使密码正确也拒绝")
    void locked_rejectsEvenCorrectPassword() {
        redisMock.when(() -> RedisUtils.hasKey(LOCK_KEY)).thenReturn(true);

        ServiceException e = assertThrows(ServiceException.class, () -> checker.verify(UID, "admin123"));
        assertEquals("签署密码错误次数过多，请 10 分钟后重试", e.getMessage());
    }

    @Test
    @DisplayName("用户不存在: 拒绝")
    void userMissing_rejected() {
        redisMock.when(() -> RedisUtils.hasKey(LOCK_KEY)).thenReturn(false);
        when(userMapper.selectById(any(Long.class))).thenReturn(null);

        assertThrows(ServiceException.class, () -> checker.verify(UID, "x"));
    }

    @Test
    @DisplayName("无密码用户(外部账号): 拒绝且计入失败")
    void nullPasswordHash_rejected() {
        redisMock.when(() -> RedisUtils.hasKey(LOCK_KEY)).thenReturn(false);
        SysUser user = new SysUser();
        user.setUserId(UID);
        user.setPassword(null);
        when(userMapper.selectById(UID)).thenReturn(user);

        assertThrows(ServiceException.class, () -> checker.verify(UID, "x"));
    }
}
