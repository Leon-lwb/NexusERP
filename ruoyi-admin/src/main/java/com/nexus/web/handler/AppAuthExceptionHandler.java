package com.nexus.web.handler;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.exception.base.BaseException;
import com.nexus.web.controller.AppAuthController;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** App 认证业务错误保留可区分的错误码；PC 认证接口仍使用其原有响应契约。 */
@Order(0)
@Slf4j
@RestControllerAdvice(assignableTypes = AppAuthController.class)
public class AppAuthExceptionHandler {

    private static final Map<String, Integer> ERROR_CODES = Map.ofEntries(
        Map.entry("user.not.exists", 10001),
        Map.entry("user.password.delete", 10002),
        Map.entry("user.blocked", 10002),
        Map.entry("user.password.not.match", 10003),
        Map.entry("user.password.retry.limit.count", 10003),
        Map.entry("user.jcaptcha.error", 10004),
        Map.entry("sms.code.retry.limit.count", 10004),
        Map.entry("user.jcaptcha.expire", 10005),
        Map.entry("user.password.retry.limit.exceed", 10008),
        Map.entry("sms.code.retry.limit.exceed", 10008),
        Map.entry("tenant.number.not.blank", 10009),
        Map.entry("tenant.not.exists", 10010),
        Map.entry("tenant.blocked", 10011),
        Map.entry("tenant.expired", 10012)
    );

    @ExceptionHandler(BaseException.class)
    public R<Void> handleAuthenticationFailure(BaseException exception) {
        String reason = exception.getCode();
        Integer code = reason == null ? null : ERROR_CODES.get(reason);
        log.warn("App认证校验未通过: reason={}", reason);
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "登录失败，请检查登录信息";
        return code == null ? R.warn(message) : R.fail(code, message);
    }

    @ExceptionHandler(ServiceException.class)
    public R<Void> handleBusinessFailure(ServiceException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "登录失败，请稍后重试";
        Integer code = exception.getCode();
        return code == null ? R.warn(message) : R.fail(code, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleValidationFailure(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.joining("；"));
        return R.warn(message.isBlank() ? "登录信息格式不正确，请检查后重试" : message);
    }
}
