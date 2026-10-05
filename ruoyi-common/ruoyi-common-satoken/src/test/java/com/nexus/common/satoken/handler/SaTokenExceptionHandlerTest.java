package com.nexus.common.satoken.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.nexus.common.core.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Proxy;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仅使用合成敏感标记验证日志脱敏，不加载应用、凭证或外部服务。
 */
@ResourceLock("SaTokenExceptionHandler日志捕获")
class SaTokenExceptionHandlerTest {

    private static final String TOKEN_SECRET = "SYNTHETIC_AUTH_TOKEN_DO_NOT_USE";
    private static final String MESSAGE_SECRET = "SYNTHETIC_AUTH_MESSAGE_DO_NOT_USE";
    private static final String PERMISSION_SECRET = "SYNTHETIC_AUTH_PERMISSION_DO_NOT_USE";
    private static final String ROLE_SECRET = "SYNTHETIC_AUTH_ROLE_DO_NOT_USE";
    private static final String LOGIN_TYPE_SECRET = "SYNTHETIC_AUTH_LOGIN_TYPE_DO_NOT_USE";
    private static final String TYPE_SECRET = "SYNTHETIC_AUTH_TYPE_DO_NOT_USE";
    private static final String PATH_SECRET = "SYNTHETIC_AUTH_PATH_DO_NOT_USE";
    private static final String QUERY_SECRET = "SYNTHETIC_AUTH_QUERY_DO_NOT_USE";
    private static final String CAUSE_SECRET = "SYNTHETIC_AUTH_CAUSE_DO_NOT_USE";
    private static final String[] SECRETS = {
        TOKEN_SECRET, MESSAGE_SECRET, PERMISSION_SECRET, ROLE_SECRET, LOGIN_TYPE_SECRET,
        TYPE_SECRET, PATH_SECRET, QUERY_SECRET, CAUSE_SECRET
    };
    private static final String FORBIDDEN_MESSAGE = "没有访问权限，请联系管理员授权";

    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
    private Logger logger;
    private Level originalLevel;
    private boolean originalAdditive;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureHandlerLogs() {
        logger = (Logger) LoggerFactory.getLogger(SaTokenExceptionHandler.class);
        originalLevel = logger.getLevel();
        originalAdditive = logger.isAdditive();
        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ERROR);
        // 合成攻击文本只进入内存捕获器，不传播到其他日志输出。
        logger.setAdditive(false);
    }

    @AfterEach
    void restoreHandlerLogger() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
        logger.setAdditive(originalAdditive);
    }

    static Stream<Arguments> loginCases() {
        String abnormal = "登录状态异常，请重新登录";
        return Stream.of(
            Arguments.of(NotLoginException.NOT_TOKEN, abnormal, "未提供登录凭证"),
            Arguments.of(NotLoginException.INVALID_TOKEN, abnormal, "登录凭证无效"),
            Arguments.of(NotLoginException.TOKEN_TIMEOUT, "登录已过期，请重新登录", "登录凭证已过期"),
            Arguments.of(NotLoginException.BE_REPLACED, "当前账号已在其他设备登录，您已被强制下线", "账号已在其他设备登录"),
            Arguments.of(NotLoginException.KICK_OUT, "账号已被管理员强制下线", "账号已被强制下线"),
            Arguments.of(NotLoginException.TOKEN_FREEZE, "登录已过期，请重新登录", "登录凭证已被冻结"),
            Arguments.of(NotLoginException.NO_PREFIX, abnormal, "登录凭证前缀不匹配"),
            Arguments.of("未知类型", abnormal, "登录状态异常"),
            Arguments.of(TYPE_SECRET + "\r\n伪造分类", abnormal, "登录状态异常"),
            Arguments.of("", abnormal, "登录状态异常")
        );
    }

    @ParameterizedTest
    @MethodSource("loginCases")
    @DisplayName("各登录分类保留原有401及固定中文响应")
    void loginResponsesKeepOriginalContract(String type, String expectedMessage, String reason) {
        R<Void> response = handler.handleNotLoginException(loginException(type), syntheticRequest());

        assertResponse(response, 401, expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("loginCases")
    @DisplayName("登录日志仅记录白名单中文原因且不携带合成敏感数据或异常")
    void loginLogsContainOnlyTrustedReason(String type, String expectedMessage, String reason) {
        handler.handleNotLoginException(loginException(type), requestWithoutSensitiveData());

        assertSafeLog("认证失败：" + reason + "，无法访问系统资源");
    }

    @Test
    @DisplayName("权限异常保留403及固定中文响应")
    void permissionResponseKeepsOriginalContract() {
        R<Void> response = handler.handleNotPermissionException(permissionException(), syntheticRequest());

        assertResponse(response, 403, FORBIDDEN_MESSAGE);
    }

    @Test
    @DisplayName("权限日志不记录恶意权限码、异常文本或异常原因")
    void permissionLogContainsOnlyFixedChineseMessage() {
        handler.handleNotPermissionException(permissionException(), requestWithoutSensitiveData());

        assertSafeLog("权限码校验失败");
    }

    @Test
    @DisplayName("角色异常保留403及固定中文响应")
    void roleResponseKeepsOriginalContract() {
        R<Void> response = handler.handleNotRoleException(roleException(), syntheticRequest());

        assertResponse(response, 403, FORBIDDEN_MESSAGE);
    }

    @Test
    @DisplayName("角色日志不记录恶意角色、异常文本或异常原因")
    void roleLogContainsOnlyFixedChineseMessage() {
        handler.handleNotRoleException(roleException(), requestWithoutSensitiveData());

        assertSafeLog("角色权限校验失败");
    }

    @ParameterizedTest
    @ValueSource(strings = {"登录", "权限", "角色"})
    @DisplayName("请求路径、路径参数和查询参数均不进入认证异常日志")
    void requestDataIsNeverLogged(String category) {
        switch (category) {
            case "登录" -> handler.handleNotLoginException(
                new NotLoginException("合成异常", "合成登录域", NotLoginException.NOT_TOKEN), syntheticRequest());
            case "权限" -> handler.handleNotPermissionException(new NotPermissionException("合成权限"), syntheticRequest());
            case "角色" -> handler.handleNotRoleException(new NotRoleException("合成角色"), syntheticRequest());
            default -> fail("测试分类未登记");
        }

        assertEquals(1, appender.list.size(), "必须保留一条认证异常日志");
        ILoggingEvent event = appender.list.get(0);
        assertAll(
            () -> assertNoSensitiveData(event.getFormattedMessage()),
            () -> assertNoSensitiveData(event.getMessage()),
            () -> assertFalse(event.getFormattedMessage().contains("/synthetic/"), "不得记录请求原始路径"),
            () -> assertFalse(event.getFormattedMessage().contains("access_token="), "不得记录凭证参数")
        );
    }

    @Test
    @DisplayName("最高优先级及三个异常处理映射保持不变")
    void advicePriorityAndExceptionMappingsRemainUnchanged() throws NoSuchMethodException {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, SaTokenExceptionHandler.class.getAnnotation(Order.class).value());
        assertExceptionMapping("handleNotLoginException", NotLoginException.class);
        assertExceptionMapping("handleNotPermissionException", NotPermissionException.class);
        assertExceptionMapping("handleNotRoleException", NotRoleException.class);
    }

    private static NotLoginException loginException(String type) {
        NotLoginException exception = NotLoginException.newInstance(
            LOGIN_TYPE_SECRET, type, "合成认证异常：" + MESSAGE_SECRET + "\r\n伪造日志", TOKEN_SECRET);
        return withSyntheticCause(exception);
    }

    private static NotPermissionException permissionException() {
        return withSyntheticCause(new NotPermissionException(
            PERMISSION_SECRET + "：" + MESSAGE_SECRET + "\r\n伪造权限日志", LOGIN_TYPE_SECRET));
    }

    private static NotRoleException roleException() {
        return withSyntheticCause(new NotRoleException(
            ROLE_SECRET + "：" + MESSAGE_SECRET + "\r\n伪造角色日志", LOGIN_TYPE_SECRET));
    }

    private static <T extends RuntimeException> T withSyntheticCause(T exception) {
        exception.initCause(new IllegalStateException("合成异常原因：" + CAUSE_SECRET));
        exception.addSuppressed(new IllegalArgumentException("合成抑制异常：" + TOKEN_SECRET));
        return exception;
    }

    private static HttpServletRequest syntheticRequest() {
        return request("/synthetic/" + PATH_SECRET + ";access_token=" + TOKEN_SECRET,
            "access_token=" + QUERY_SECRET);
    }

    private static HttpServletRequest requestWithoutSensitiveData() {
        return request("/synthetic/handler", null);
    }

    private static HttpServletRequest request(String uri, String query) {
        return (HttpServletRequest) Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getRequestURI" -> uri;
                case "getQueryString" -> query;
                case "getRequestURL" -> new StringBuffer("https://unit.invalid" + uri);
                default -> throw new UnsupportedOperationException("测试请求不支持此调用：" + method.getName());
            });
    }

    private void assertSafeLog(String expectedMessage) {
        assertEquals(1, appender.list.size(), "必须保留一条认证异常日志");
        ILoggingEvent event = appender.list.get(0);
        assertAll(
            () -> assertEquals(Level.ERROR, event.getLevel(), "保留既有错误日志级别"),
            () -> assertNoSensitiveData(event.getMessage()),
            () -> assertNoSensitiveData(event.getFormattedMessage()),
            () -> {
                if (event.getArgumentArray() != null) {
                    for (Object argument : event.getArgumentArray()) {
                        assertFalse(argument instanceof Throwable, "日志参数不得包含异常对象");
                        assertNoSensitiveData(String.valueOf(argument));
                    }
                }
            },
            () -> {
                if (event.getThrowableProxy() != null) {
                    assertNoSensitiveData(ThrowableProxyUtil.asString(event.getThrowableProxy()));
                }
                assertNull(event.getThrowableProxy(), "认证异常日志不得附带异常堆栈");
            },
            () -> assertEquals(expectedMessage, event.getFormattedMessage(), "日志只使用固定中文语义")
        );
    }

    private static void assertResponse(R<Void> response, int expectedCode, String expectedMessage) {
        assertAll(
            () -> assertEquals(expectedCode, response.getCode()),
            () -> assertEquals(expectedMessage, response.getMsg()),
            () -> assertNull(response.getData()),
            () -> assertNoSensitiveData(response.getMsg())
        );
    }

    private static void assertNoSensitiveData(String text) {
        for (String secret : SECRETS) {
            assertFalse(text.contains(secret), "日志或响应不得包含纯合成敏感标记：" + secret);
        }
        assertFalse(text.contains("\r"), "日志或响应不得包含注入的回车");
        assertFalse(text.contains("\n"), "日志或响应不得包含注入的换行");
    }

    private static void assertExceptionMapping(String methodName, Class<? extends RuntimeException> exceptionClass)
        throws NoSuchMethodException {
        ExceptionHandler annotation = SaTokenExceptionHandler.class
            .getDeclaredMethod(methodName, exceptionClass, HttpServletRequest.class).getAnnotation(ExceptionHandler.class);
        assertNotNull(annotation, "保留异常处理注解");
        assertArrayEquals(new Class<?>[]{exceptionClass}, annotation.value());
    }
}
