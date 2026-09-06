package com.nexus.common.redis.handler;

import cn.hutool.http.HttpStatus;
import com.baomidou.lock.exception.LockFailureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Redis异常处理器
 * <p>早于 GlobalExceptionHandler 的 RuntimeException 兜底, 保证 Lock4j 异常语义(C2)。</p>
 *
 * @author AprilWind
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RestControllerAdvice
public class RedisExceptionHandler {

    /**
     * 分布式锁Lock4j异常
     */
    @ExceptionHandler(LockFailureException.class)
    public R<Void> handleLockFailureException(LockFailureException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("获取锁失败了'{}',发生Lock4j异常.", requestURI, e);
        return R.fail(HttpStatus.HTTP_UNAVAILABLE, "业务处理中，请稍后再试...");
    }

}
