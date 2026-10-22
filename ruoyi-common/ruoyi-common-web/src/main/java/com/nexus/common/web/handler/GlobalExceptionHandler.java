package com.nexus.common.web.handler;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpStatus;

import com.fasterxml.jackson.core.JsonParseException;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.exception.SseException;
import com.nexus.common.core.exception.base.BaseException;
import com.nexus.common.core.utils.StreamUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;

// 同名类型说明：本文件同时使用 cn.hutool.http.HttpStatus 和 org.springframework.http.HttpStatus，后者保留全限定名以区分。

/**
 * 全局异常处理器
 *
 * @author Lion Li
 */
@Order(1)
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                       HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return R.fail(HttpStatus.HTTP_BAD_METHOD, e.getMessage());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.error(e.getMessage());
        Integer code = e.getCode();
        return ObjectUtil.isNotNull(code) ? R.fail(code, e.getMessage()) : R.fail(e.getMessage());
    }

    /**
     * 认证失败
     */
    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(SseException.class)
    public R<Void> handleNotLoginException(SseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.debug("请求地址'{}',认证失败'{}',无法访问系统资源", requestURI, e.getMessage());
        return R.fail(HttpStatus.HTTP_UNAUTHORIZED, "认证失败，无法访问系统资源");
    }

    /**
     * 上传文件超过大小限制
     * <p>由 multipart 解析器在进入 Controller <b>之前</b>抛出（如 Undertow 的
     * {@code FileTooLargeException}，Spring 的 {@code StandardMultipartHttpServletRequest}
     * 会按消息里的 size/exceeded 关键字统一成 {@link MaxUploadSizeExceededException}）。
     * 若不在此拦截，它会落到下面的 {@code Exception} 兜底处理器，变成「发生系统异常，请联系管理员」——
     * 业务层那条带具体字节数的友好提示（如文档签章的 {@code PDF 文件不能超过 10MB（当前 N 字节）}）
     * 也就永远执行不到。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.error("请求地址'{}',上传文件超过大小限制", request.getRequestURI());
        // 具体上限由容器/解析器给出：拿得到就报出来，拿不到（Spring 统一转换时可能是 -1）只给可操作建议
        long maxUploadSize = e.getMaxUploadSize();
        return R.fail(maxUploadSize > 0
            ? String.format("上传文件超过大小限制（单文件最大 %dMB），请压缩后重试", maxUploadSize / 1024 / 1024)
            : "上传文件超过大小限制，请压缩或拆分后重试");
    }

    /**
     * servlet异常
     */
    @ExceptionHandler(ServletException.class)
    public R<Void> handleServletException(ServletException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        return R.fail("发生未知异常，请联系管理员");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.error(e.getMessage());
        return R.fail(e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public R<Void> handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI);
        return R.fail(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求参数类型不匹配'{}',发生系统异常.", requestURI);
        return R.fail(String.format("请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'", e.getName(), e.getRequiredType().getName(), e.getValue()));
    }

    /**
     * 找不到路由
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}'不存在.", requestURI);
        return R.fail(HttpStatus.HTTP_NOT_FOUND, "请求地址不存在");
    }

    /**
     * 拦截未知的运行时异常
     */
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IOException.class)
    public void handleIoException(IOException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (requestURI.contains("sse")) {
            // sse 经常性连接中断 例如关闭浏览器 直接屏蔽
            return;
        }
        log.error("请求地址'{}',连接中断", requestURI, e);
    }

    /**
     * sse 连接超时异常 不需要处理
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleRuntimeException(AsyncRequestTimeoutException e) {
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public R<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        return R.fail("发生未知异常，请联系管理员");
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return R.fail("发生系统异常，请联系管理员");
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        log.error(e.getMessage());
        String message = StreamUtils.join(e.getAllErrors(), DefaultMessageSourceResolvable::getDefaultMessage, ", ");
        return R.fail(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> constraintViolationException(ConstraintViolationException e) {
        log.error(e.getMessage());
        String message = StreamUtils.join(e.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
        return R.fail(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error(e.getMessage());
        String message = StreamUtils.join(e.getBindingResult().getAllErrors(), DefaultMessageSourceResolvable::getDefaultMessage, ", ");
        return R.fail(message);
    }

    /**
     * 方法参数校验异常 用于处理 @Validated 注解
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public R<Void> handlerMethodValidationException(HandlerMethodValidationException e) {
        log.error(e.getMessage());
        String message = StreamUtils.join(e.getAllErrors(), MessageSourceResolvable::getDefaultMessage, ", ");
        return R.fail(message);
    }

    /**
     * JSON 解析异常（Jackson 在处理 JSON 格式出错时抛出）
     * 可能是请求体格式非法，也可能是服务端反序列化失败
     */
    @ExceptionHandler(JsonParseException.class)
    public R<Void> handleJsonParseException(JsonParseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}' 发生 JSON 解析异常: {}", requestURI, e.getMessage());
        return R.fail(HttpStatus.HTTP_BAD_REQUEST, "请求数据格式错误（JSON 解析失败）：" + e.getMessage());
    }

    /**
     * 请求体读取异常（通常是请求参数格式非法、字段类型不匹配等）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.error("请求地址'{}', 参数解析失败: {}", request.getRequestURI(), e.getMessage());
        return R.fail(HttpStatus.HTTP_BAD_REQUEST, "请求参数格式错误：" + e.getMostSpecificCause().getMessage());
    }

    /**
     * SpEL 表达式相关异常
     */
    @ExceptionHandler(ExpressionException.class)
    public R<Void> handleSpelException(ExpressionException e, HttpServletRequest request) {
        log.error("请求地址'{}'，SpEL解析异常: {}", request.getRequestURI(), e.getMessage());
        return R.fail(HttpStatus.HTTP_INTERNAL_ERROR, "SpEL解析失败：" + e.getMessage());
    }

}
