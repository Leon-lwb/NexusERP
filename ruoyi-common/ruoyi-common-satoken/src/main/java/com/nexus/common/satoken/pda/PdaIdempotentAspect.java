package com.nexus.common.satoken.pda;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.ServletUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * PDA 幂等切面(全系统唯一实现, 原 ERP/MES 双套合并下沉, N4) — 见 {@link PdaIdempotent}
 * <p>
 * 幂等键 = {@code pda:idem:{tenantId}:{userId}:sha256(方法+URI|幂等标识)}(MES-PDA-02):
 * 幂等标识优先取 {@code X-Idempotent-Key} 请求头, 缺失时退化为"端点 + 请求参数指纹"兜底,
 * 且键内包含 userId 与端点 —— 同租户不同作业/不同用户不再互相吞掉。
 * <p>
 * 状态机 PENDING/DONE(MES-PDA-03): 首个请求执行前置 PENDING 占位, 业务成功(事务已提交)后才置 DONE;
 * 命中 DONE 返回重放成功, 命中 PENDING(首个请求仍在执行中)返回可读失败, <b>绝不提前返回成功</b>。
 */
@Slf4j
@Aspect
public class PdaIdempotentAspect {

    private static final String KEY_PREFIX = "pda:idem:";
    private static final String HEADER_NAME = "X-Idempotent-Key";
    /** 首个请求仍在执行中的占位状态 */
    private static final String STATE_PENDING = "PENDING";
    /** 首个请求已成功提交的状态 */
    private static final String STATE_DONE = "DONE";
    /** 已完成作业的重放窗口 */
    private static final Duration TTL = Duration.ofHours(24);
    /** 执行中占位窗口(短于 TTL: 进程崩溃后占位可自行过期, 不会永久吞掉重试) */
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    @Around("@annotation(com.nexus.common.satoken.pda.PdaIdempotent)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        HttpServletRequest request = ServletUtils.getRequest();
        String headerKey = request == null ? null : request.getHeader(HEADER_NAME);
        String endpoint = request == null
            ? point.getSignature().toShortString()
            : request.getMethod() + " " + request.getRequestURI();
        // 幂等标识: 优先客户端 header; 缺失时用端点+参数指纹兜底(不再"无 header 即完全无幂等")
        String identity = StringUtils.isNotBlank(headerKey) ? headerKey.trim() : argsFingerprint(point);
        String redisKey = KEY_PREFIX + currentTenantId() + ":" + currentUserId() + ":"
            + sha256Hex(endpoint + "|" + identity);

        boolean first = RedisUtils.setObjectIfAbsent(redisKey, STATE_PENDING, PENDING_TTL);
        if (!first) {
            Object state = RedisUtils.getCacheObject(redisKey);
            if (STATE_DONE.equals(state)) {
                // 首次请求已成功提交: 返回重放结果(与 MES/ERP ActionResultVo JSON 结构兼容)
                log.info("[PDA幂等] 重复请求命中已完成作业, 返回重放结果: {}", redisKey);
                return R.ok("重复提交，已处理", new PdaReplayResult());
            }
            // 首次请求仍在执行中: 不能返回成功, 否则首个请求失败即作业丢失且客户端不再重试
            log.warn("[PDA幂等] 相同作业正在处理中, 拒绝提前返回成功: {}", redisKey);
            return R.fail("相同作业正在处理中，请稍后查询处理结果");
        }
        try {
            Object result = point.proceed();
            if (isFailure(result)) {
                // 业务失败释放幂等键, 允许修正后重试, 避免失败占位键导致 24h 内重试被误判为已处理
                RedisUtils.deleteObject(redisKey);
                return result;
            }
            // 业务成功(内层事务已提交)后才置 DONE
            RedisUtils.setCacheObject(redisKey, STATE_DONE, TTL);
            return result;
        } catch (Throwable e) {
            // 执行异常同样释放幂等键
            RedisUtils.deleteObject(redisKey);
            throw e;
        }
    }

    /**
     * 请求参数指纹(header 缺失时的幂等标识兜底)
     * <p>跳过 Servlet / 校验 / 视图类参数(其 toString 不稳定), 其余参数取"类名=值"拼接后 SHA-256。</p>
     */
    private String argsFingerprint(ProceedingJoinPoint point) {
        StringBuilder sb = new StringBuilder();
        Object[] args = point.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                String className = arg.getClass().getName();
                if (className.startsWith("jakarta.servlet")
                    || className.startsWith("org.springframework.validation")
                    || className.startsWith("org.springframework.ui")) {
                    continue;
                }
                sb.append(className).append('=').append(arg).append(';');
            }
        }
        return sha256Hex(sb.toString());
    }

    private String currentUserId() {
        try {
            Long userId = LoginHelper.getUserId();
            return userId == null ? "-" : String.valueOf(userId);
        } catch (Exception e) {
            return "-";
        }
    }

    private String currentTenantId() {
        try {
            String tenantId = LoginHelper.getTenantId();
            return StringUtils.isBlank(tenantId) ? "-" : tenantId;
        } catch (Exception e) {
            return "-";
        }
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 极端情况下退化为 hashCode(仍可用, 仅键长不可控)
            return Integer.toHexString(raw.hashCode());
        }
    }

    private boolean isFailure(Object result) {
        if (!(result instanceof R<?> r)) {
            return false;
        }
        if (r.getCode() != R.SUCCESS) {
            return true;
        }
        return r.getData() instanceof PdaActionResult vo && Boolean.FALSE.equals(vo.getOk());
    }
}
