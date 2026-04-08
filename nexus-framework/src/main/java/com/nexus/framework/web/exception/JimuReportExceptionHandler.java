package com.nexus.framework.web.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 积木报表相关异常处理器
 * 用于处理积木报表静态资源找不到等问题
 */
@RestControllerAdvice
@Order(1)
public class JimuReportExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(JimuReportExceptionHandler.class);

    /**
     * 处理静态资源找不到的异常
     * 积木报表会请求一些不存在的source map文件，忽略这些错误
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNoResourceFoundException(NoResourceFoundException e) {
        String message = e.getMessage();
        // 只记录非source map文件的资源找不到错误
        if (message != null && !message.contains(".map")) {
            log.warn("静态资源未找到: {}", message);
        }
        // source map文件找不到不记录，避免日志污染
    }
}
