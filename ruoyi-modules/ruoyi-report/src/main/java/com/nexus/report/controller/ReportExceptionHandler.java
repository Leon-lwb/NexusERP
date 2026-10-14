package com.nexus.report.controller;

import com.nexus.common.report.DatasetAccessException;
import com.nexus.report.domain.ReportFault;

import jakarta.validation.ConstraintViolationException;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 报表异常优先于通用处理器，认证异常处理器继续保留最高优先级。 */
@Order(0)
@RestControllerAdvice(basePackages = "com.nexus.report")
public class ReportExceptionHandler {
    /**
     * Failure，负责当前模块对应的契约或处理职责。
     *
     * @param code 当前契约的code配置或状态
     * @param msg 当前契约的msg配置或状态
     * @param errorCode 当前契约的errorCode配置或状态
     */
    public record Failure(int code, String msg, String errorCode) {}

    /**
     * 装配，复用统一权限和存储契约。
     *
     * @param fault 当前操作的 {@code fault} 输入
     * @return 当前处理返回的固定结果
     */
    @ExceptionHandler(ReportFault.class)
    public ResponseEntity<Failure> report(ReportFault fault) {
        return ResponseEntity.status(fault.status())
                .body(new Failure(fault.status(), fault.getMessage(), fault.code()));
    }

    /**
     * 完成 {@code dataset} 对应的报表异常Handler处理。
     *
     * @param fault 当前操作的 {@code fault} 输入
     * @return 当前处理返回的固定结果
     */
    @ExceptionHandler(DatasetAccessException.class)
    public ResponseEntity<Failure> dataset(DatasetAccessException fault) {
        int status = fault.code().contains("DENIED") ? 403 : 400;
        return ResponseEntity.status(status)
                .body(new Failure(status, "报表数据查询被拒绝或已停止", fault.code()));
    }

    /**
     * 完成 {@code request} 对应的报表异常Handler处理。
     *
     * @param fault 当前操作的 {@code fault} 输入
     * @return 当前处理返回的固定结果
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        BindException.class,
        ConstraintViolationException.class
    })
    public ResponseEntity<Failure> request(Exception fault) {
        return ResponseEntity.badRequest().body(new Failure(400, "报表请求参数无效", "INVALID_PARAMETER"));
    }

    /**
     * 完成 {@code unexpected} 对应的报表异常Handler处理。
     *
     * @param fault 当前操作的 {@code fault} 输入
     * @return 当前处理返回的固定结果
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Failure> unexpected(Exception fault) {
        return ResponseEntity.internalServerError()
                .body(new Failure(500, "报表处理失败，请稍后重试", "REPORT_EXECUTION_FAILED"));
    }
}
