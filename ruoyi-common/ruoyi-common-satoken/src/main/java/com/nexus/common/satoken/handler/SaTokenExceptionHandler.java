package com.nexus.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SaToken异常处理器
 * <p>
 * 必须以最高优先级注册：NotLoginException 等均继承自 RuntimeException，若不提升优先级，
 * 会被 GlobalExceptionHandler 的通用 RuntimeException 处理器抢先捕获，导致返回 500 且把
 * "token 已被冻结：eyJ..." 等 token 明文暴露给前端。提升后认证/鉴权异常统一返回 401/403。
 * <p>
 * 日志仅记录固定校验结果和白名单原因，不记录请求字段、异常文本或堆栈。
 *
 * @author Lion Li
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SaTokenExceptionHandler {

    /**
     * 权限码异常
     */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermissionException(NotPermissionException e, HttpServletRequest request) {
        log.error("权限码校验失败");
        return R.fail(HttpStatus.HTTP_FORBIDDEN, "没有访问权限，请联系管理员授权");
    }

    /**
     * 角色权限异常
     */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRoleException(NotRoleException e, HttpServletRequest request) {
        log.error("角色权限校验失败");
        return R.fail(HttpStatus.HTTP_FORBIDDEN, "没有访问权限，请联系管理员授权");
    }

    /**
     * 认证失败
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLoginException(NotLoginException e, HttpServletRequest request) {
        // 异常类型只参与白名单映射，未知类型统一归类，禁止原样写入日志。
        String reason = switch (e.getType()) {
            case NotLoginException.NOT_TOKEN -> "未提供登录凭证";
            case NotLoginException.INVALID_TOKEN -> "登录凭证无效";
            case NotLoginException.TOKEN_TIMEOUT -> "登录凭证已过期";
            case NotLoginException.BE_REPLACED -> "账号已在其他设备登录";
            case NotLoginException.KICK_OUT -> "账号已被强制下线";
            case NotLoginException.TOKEN_FREEZE -> "登录凭证已被冻结";
            case NotLoginException.NO_PREFIX -> "登录凭证前缀不匹配";
            default -> "登录状态异常";
        };
        log.error("认证失败：{}，无法访问系统资源", reason);
        String msg = switch (e.getType()) {
            case NotLoginException.TOKEN_TIMEOUT,
                 NotLoginException.TOKEN_FREEZE -> "登录已过期，请重新登录";
            case NotLoginException.BE_REPLACED -> "当前账号已在其他设备登录，您已被强制下线";
            case NotLoginException.KICK_OUT -> "账号已被管理员强制下线";
            default -> "登录状态异常，请重新登录";
        };
        return R.fail(HttpStatus.HTTP_UNAUTHORIZED, msg);
    }

}
