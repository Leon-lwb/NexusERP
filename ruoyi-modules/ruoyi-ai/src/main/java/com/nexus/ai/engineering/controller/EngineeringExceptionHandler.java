package com.nexus.ai.engineering.controller;

import com.nexus.common.ai.engineering.EngineeringException;
import com.nexus.common.core.domain.R;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.*;

/** 工程协议异常使用真实 HTTP 状态，响应不含凭证、内部路径及异常堆栈。 */
@RestControllerAdvice(basePackages = "com.nexus.ai.engineering.controller")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class EngineeringExceptionHandler {
    /** 返回稳定错误码、中文摘要和跟踪标识。 */
    @ExceptionHandler(EngineeringException.class)
    public ResponseEntity<R<Map<String, String>>> handle(
            EngineeringException e, HttpServletRequest request) {
        String trace =
                Objects.toString(
                        request.getAttribute("engineeringTraceId"), UUID.randomUUID().toString());
        var body = R.fail(e.getMessage(), Map.of("errorCode", e.code(), "traceId", trace));
        body.setCode(e.status());
        return ResponseEntity.status(e.status()).header("X-Nexus-Trace-Id", trace).body(body);
    }

    /** 隔离工程接口的输入及 IO 异常，避免全局处理器回显内部 SQL、对象地址和路径。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Map<String, String>>> unexpected(
            Exception error, HttpServletRequest request) {
        boolean input =
                error instanceof HttpMessageNotReadableException
                        || error instanceof MissingRequestHeaderException
                        || error instanceof MissingServletRequestPartException
                        || error instanceof MethodArgumentTypeMismatchException;
        return handle(
                new EngineeringException(
                        input ? 422 : 503,
                        input ? "INVALID_REQUEST" : "ENGINEERING_UNAVAILABLE",
                        input ? "工程请求格式无效" : "工程服务暂不可用，请稍后重试"),
                request);
    }
}
