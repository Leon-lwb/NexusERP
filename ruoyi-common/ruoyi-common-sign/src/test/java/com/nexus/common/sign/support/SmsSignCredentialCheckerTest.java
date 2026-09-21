package com.nexus.common.sign.support;

import org.junit.jupiter.api.Tag;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 短信验证码签署凭证校验器测试。
 *
 * <p>其中「错误计数必须带 TTL」是 Task 9 修复的回归测试：计数器若不过期，
 * 锁定到期后残留的旧计数（&gt;= MAX_FAIL）会让用户下一次输错就被立刻重新锁定。</p>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class SmsSignCredentialCheckerTest {

    private static final Long UID = 7L;
    private static final String CODE_KEY = SmsSignCredentialChecker.CODE_KEY_PREFIX + UID;
    private static final String CNT_KEY = SmsSignCredentialChecker.CNT_KEY_PREFIX + UID;
    private static final String LOCK_KEY = SmsSignCredentialChecker.LOCK_KEY_PREFIX + UID;
    private static final String CODE = "123456";

    private static ApplicationContext originalContext;

    private SmsSignCredentialChecker checker;
    private MockedStatic<RedisUtils> redisMock;

    /**
     * {@code RedisUtils} 的静态字段 {@code CLIENT = SpringUtils.getBean(RedissonClient.class)} 在类初始化时
     * 就要求 Spring 容器存在，否则 Mockito 无法对其做静态替换
     * （报 "could not be initialized"）。单测无容器，这里给 Hutool 的 SpringUtil 装一个桩上下文，
     * 使其 {@code getBean(...)} 返回 null 完成初始化；CLIENT 后续也不会被真正使用（静态方法已被替换）。
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
        checker = new SmsSignCredentialChecker();
        redisMock = mockStatic(RedisUtils.class);
    }

    @AfterEach
    void tearDown() {
        if (redisMock != null) {
            redisMock.close();
        }
    }

    /** 未锁定 + 验证码存在 */
    private void givenLiveCode() {
        redisMock.when(() -> RedisUtils.hasKey(LOCK_KEY)).thenReturn(false);
        redisMock.when(() -> RedisUtils.getCacheObject(CODE_KEY)).thenReturn(CODE);
    }

    @Test
    @DisplayName("认证方式: SMS")
    void authMethod_isSms() {
        assertEquals(SignAuthMethod.SMS, checker.authMethod());
    }

    @Test
    @DisplayName("错误验证码: 计数 +1 且写入 TTL（Task 9 回归）")
    void wrongCode_incrementsCounterWithTtl() {
        givenLiveCode();
        redisMock.when(() -> RedisUtils.incrAtomicValue(CNT_KEY)).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> checker.verify(UID, "000000"));
        assertEquals("签署验证码错误", ex.getMessage());

        redisMock.verify(() -> RedisUtils.incrAtomicValue(CNT_KEY));
        // 计数必须带 TTL，否则锁定到期后残留计数会让用户再次被立即锁定
        redisMock.verify(() -> RedisUtils.setCacheObject(CNT_KEY, 1L, SmsSignCredentialChecker.LOCK_TTL));
        // 未达阈值不锁
        redisMock.verify(() -> RedisUtils.setCacheObject(LOCK_KEY, "1", SmsSignCredentialChecker.LOCK_TTL), never());
        redisMock.verify(() -> RedisUtils.deleteObject(anyString()), never());
    }

    @Test
    @DisplayName("错误验证码达上限: 写入锁定 key 且计数同样带 TTL")
    void wrongCodeReachingMaxFail_locksAccount() {
        givenLiveCode();
        redisMock.when(() -> RedisUtils.incrAtomicValue(CNT_KEY))
            .thenReturn((long) SmsSignCredentialChecker.MAX_FAIL);

        assertThrows(ServiceException.class, () -> checker.verify(UID, "000000"));

        redisMock.verify(() -> RedisUtils.setCacheObject(CNT_KEY, 5L, SmsSignCredentialChecker.LOCK_TTL));
        redisMock.verify(() -> RedisUtils.setCacheObject(LOCK_KEY, "1", SmsSignCredentialChecker.LOCK_TTL));
    }

    @Test
    @DisplayName("正确验证码: 验证码与错误计数一并删除")
    void correctCode_deletesCodeAndCounter() {
        givenLiveCode();

        assertDoesNotThrow(() -> checker.verify(UID, CODE));

        redisMock.verify(() -> RedisUtils.deleteObject(CODE_KEY));
        redisMock.verify(() -> RedisUtils.deleteObject(CNT_KEY));
        redisMock.verify(() -> RedisUtils.incrAtomicValue(anyString()), never());
    }

    @Test
    @DisplayName("验证码已过期: 抛异常且不计数")
    void expiredCode_throws() {
        redisMock.when(() -> RedisUtils.hasKey(LOCK_KEY)).thenReturn(false);
        redisMock.when(() -> RedisUtils.getCacheObject(CODE_KEY)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> checker.verify(UID, CODE));
        assertTrue(ex.getMessage().contains("失效"));

        redisMock.verify(() -> RedisUtils.incrAtomicValue(anyString()), never());
        redisMock.verify(() -> RedisUtils.deleteObject(anyString()), never());
    }

    @Test
    @DisplayName("锁定中: 先抛「次数过多」，不触碰验证码")
    void locked_rejectsBeforeTouchingCode() {
        redisMock.when(() -> RedisUtils.hasKey(LOCK_KEY)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class, () -> checker.verify(UID, CODE));
        assertTrue(ex.getMessage().contains("次数过多"));

        redisMock.verify(() -> RedisUtils.getCacheObject(anyString()), never());
        redisMock.verify(() -> RedisUtils.incrAtomicValue(anyString()), never());
        redisMock.verify(() -> RedisUtils.deleteObject(anyString()), never());
    }
}
