package com.nexus.mes.pda.aspect;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.ServletUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.pda.domain.vo.PdaActionResultVo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * PDA 幂等切面 (MES 侧) — 见 {@link com.nexus.mes.pda.annotation.PdaIdempotent}
 * <p>
 * 幂等键按租户隔离: pda:idem:{tenantId}:{X-Idempotent-Key}
 */
@Slf4j
@Aspect
@Component
public class PdaIdempotentAspect {

    private static final String KEY_PREFIX = "pda:idem:";
    private static final String HEADER_NAME = "X-Idempotent-Key";
    private static final Duration TTL = Duration.ofHours(24);

    @Around("@annotation(com.nexus.mes.pda.annotation.PdaIdempotent)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        String key = ServletUtils.getRequest().getHeader(HEADER_NAME);
        if (StringUtils.isBlank(key)) {
            return point.proceed();
        }
        String redisKey = KEY_PREFIX + LoginHelper.getTenantId() + ":" + key;
        boolean first = RedisUtils.setObjectIfAbsent(redisKey, "1", TTL);
        if (first) {
            return point.proceed();
        }
        // 重复提交(如离线队列自动重放): 服务端已处理过, 直接返回成功
        log.info("[PDA幂等] 重复请求已忽略: {}", redisKey);
        return R.ok("重复提交，已处理", PdaActionResultVo.replayed());
    }
}
