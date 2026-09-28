package com.nexus.web.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.constant.GlobalConstants;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.web.service.SysLoginService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@Tag("local")
@Tag("dev")
@Tag("prod")
class SmsAuthStrategyTest {
    @Test
    void consumesValidSmsCodeAfterSuccessfulValidation() {
        String key = GlobalConstants.CAPTCHA_CODE_KEY + "13800138000";
        SmsAuthStrategy strategy = new SmsAuthStrategy(mock(SysLoginService.class), mock(SysUserMapper.class));
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            redis.when(() -> RedisUtils.getCacheObject(key)).thenReturn("1234");
            assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeMethod(strategy, "validateSmsCode", "tenant", "13800138000", "1234"));
            redis.verify(() -> RedisUtils.deleteObject(key));
        }
    }
}
