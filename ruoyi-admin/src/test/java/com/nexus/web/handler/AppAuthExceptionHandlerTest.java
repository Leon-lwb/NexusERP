package com.nexus.web.handler;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.exception.base.BaseException;
import com.nexus.common.core.exception.user.UserException;
import com.nexus.common.core.utils.MessageUtils;
import com.nexus.common.tenant.exception.TenantException;
import com.nexus.common.web.handler.GlobalExceptionHandler;
import com.nexus.web.controller.AppAuthController;
import com.nexus.web.controller.AuthController;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("local")
@Tag("dev")
@Tag("prod")
class AppAuthExceptionHandlerTest {

    @BeforeAll
    static void initializeMessageSource() throws ClassNotFoundException {
        // MessageUtils 的静态消息源需要先绑定；每个用例仍显式控制其业务文案。
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(MessageSource.class)).thenReturn(source);
            Class.forName(MessageUtils.class.getName());
        }
    }

    static Stream<Arguments> failureCases() {
        return Stream.of(
            Arguments.of("login", "user.not.exists", 10001, "账号不存在"),
            Arguments.of("login", "user.blocked", 10002, "账号已禁用"),
            Arguments.of("login", "user.password.not.match", 10003, "用户不存在/密码错误"),
            Arguments.of("login", "user.password.retry.limit.count", 10003, "密码输入错误2次"),
            Arguments.of("login", "user.jcaptcha.error", 10004, "验证码错误"),
            Arguments.of("login", "user.jcaptcha.expire", 10005, "验证码已失效"),
            Arguments.of("smsLogin", "sms.code.retry.limit.count", 10004, "短信验证码输入错误2次"),
            Arguments.of("smsLogin", "user.jcaptcha.expire", 10005, "验证码已失效"),
            Arguments.of("login", "user.password.retry.limit.exceed", 10008, "密码输入错误5次，账户锁定10分钟"),
            Arguments.of("smsLogin", "sms.code.retry.limit.exceed", 10008, "短信验证码输入错误5次，账户锁定10分钟"),
            Arguments.of("login", "tenant.number.not.blank", 10009, "租户编号不能为空"),
            Arguments.of("login", "tenant.not.exists", 10010, "租户不存在"),
            Arguments.of("login", "tenant.blocked", 10011, "租户已停用"),
            Arguments.of("login", "tenant.expired", 10012, "租户已过期")
        );
    }

    @ParameterizedTest
    @MethodSource("failureCases")
    void classifiesAppAuthenticationFailures(String endpoint, String reason, int code, String message) throws Exception {
        BaseException failure = reason.startsWith("tenant.")
            ? new TenantException(reason, 2) : new UserException(reason, 2);
        AppAuthController controller = mock(AppAuthController.class);
        if (endpoint.equals("login")) when(controller.login(anyMap())).thenThrow(failure);
        else when(controller.smsLogin(anyMap())).thenThrow(failure);
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            messages.when(() -> MessageUtils.message(reason, failure.getArgs())).thenReturn(message);
            mvc(controller).perform(post("/app/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.msg").value(message));
        }
    }

    @Test
    void leavesPcAuthenticationFailureResponseUnchanged() throws Exception {
        UserException failure = new UserException("user.jcaptcha.error");
        AuthController controller = mock(AuthController.class);
        when(controller.login(anyString())).thenThrow(failure);
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            messages.when(() -> MessageUtils.message(failure.getCode(), failure.getArgs())).thenReturn("验证码错误");
            mvc(controller).perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("验证码错误"));
        }
    }

    @Test
    void preservesExpectedValidationMessagesAsBusinessWarnings() throws Exception {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("用户密码长度必须在5到30个字符之间");
        AppAuthController controller = mock(AppAuthController.class);
        ConstraintViolationException failure = new ConstraintViolationException(Set.of(violation));
        when(controller.login(anyMap())).thenThrow(failure);
        mvc(controller).perform(post("/app/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(jsonPath("$.code").value(601))
            .andExpect(jsonPath("$.msg").value("用户密码长度必须在5到30个字符之间"));
    }

    @Test
    void preservesRateLimitWarnings() throws Exception {
        AppAuthController controller = mock(AppAuthController.class);
        when(controller.smsLogin(anyMap())).thenThrow(new ServiceException("请求过于频繁，请稍候再试"));
        mvc(controller).perform(post("/app/auth/smsLogin").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(jsonPath("$.code").value(601))
            .andExpect(jsonPath("$.msg").value("请求过于频繁，请稍候再试"));
    }

    @Test
    void keepsUnexpectedRuntimeFailuresGeneric() throws Exception {
        AppAuthController controller = mock(AppAuthController.class);
        when(controller.login(anyMap())).thenThrow(new IllegalStateException("internal diagnostic detail"));
        mvc(controller).perform(post("/app/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.msg").value("发生未知异常，请联系管理员"));
    }

    private static MockMvc mvc(Object controller) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(GlobalExceptionHandler.class);
            ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestControllerAdvice.class));
            // 开关用于对照原始全局 500 响应，正常运行自动发现 App 专用 advice。
            if (!Boolean.getBoolean("test.app-auth.legacy")) scanner.scan("com.nexus.web.handler");
            context.refresh();
            return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(context.getBeansWithAnnotation(RestControllerAdvice.class).values().toArray())
                .build();
        }
    }
}
