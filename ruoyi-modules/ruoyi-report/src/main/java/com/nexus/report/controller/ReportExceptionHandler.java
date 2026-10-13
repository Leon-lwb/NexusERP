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

/** Report errors precede generic advice; authentication advice retains highest precedence. */
@Order(0)
@RestControllerAdvice(basePackages="com.nexus.report")
public class ReportExceptionHandler {
    public record Failure(int code,String msg,String errorCode) { }
    @ExceptionHandler(ReportFault.class)
    public ResponseEntity<Failure> report(ReportFault fault){return ResponseEntity.status(fault.status()).body(new Failure(fault.status(),fault.getMessage(),fault.code()));}
    @ExceptionHandler(DatasetAccessException.class)
    public ResponseEntity<Failure> dataset(DatasetAccessException fault){int status=fault.code().contains("DENIED")?403:400;return ResponseEntity.status(status).body(new Failure(status,"报表数据查询被拒绝或已停止",fault.code()));}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,BindException.class,ConstraintViolationException.class})
    public ResponseEntity<Failure> request(Exception fault){return ResponseEntity.badRequest().body(new Failure(400,"报表请求参数无效","INVALID_PARAMETER"));}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Failure> unexpected(Exception fault){return ResponseEntity.internalServerError().body(new Failure(500,"报表处理失败，请稍后重试","REPORT_EXECUTION_FAILED"));}
}
