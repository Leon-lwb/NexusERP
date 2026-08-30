package com.nexus.erp.wm.aspect;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.ServletUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * PDA 幂等切面 — 见 {@link com.nexus.erp.wm.annotation.PdaIdempotent}
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

    @Around("@annotation(com.nexus.erp.wm.annotation.PdaIdempotent)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        String key = ServletUtils.getRequest().getHeader(HEADER_NAME);
        if (StringUtils.isBlank(key)) {
            return point.proceed();
        }
        String redisKey = KEY_PREFIX + LoginHelper.getTenantId() + ":" + key;
        boolean first = RedisUtils.setObjectIfAbsent(redisKey, "1", TTL);
        if (!first) {
            // 重复提交(如离线队列自动重放): 服务端已处理过, 直接返回成功
            log.info("[PDA幂等] 重复请求已忽略: {}", redisKey);
            return R.ok("重复提交，已处理", PdaActionResultVo.replayed());
        }
        try {
            Object result = point.proceed();
            if (isFailure(result)) {
                // 业务失败释放幂等键, 允许修正后重试, 避免失败占位键导致 24h 内重试被误判为已处理
                RedisUtils.deleteObject(redisKey);
            }
            return result;
        } catch (Throwable e) {
            // 执行异常同样释放幂等键
            RedisUtils.deleteObject(redisKey);
            throw e;
        }
    }

    private boolean isFailure(Object result) {
        if (!(result instanceof R<?> r)) {
            return false;
        }
        if (r.getCode() != R.SUCCESS) {
            return true;
        }
        return r.getData() instanceof PdaActionResultVo vo && Boolean.FALSE.equals(vo.getOk());
    }
}
