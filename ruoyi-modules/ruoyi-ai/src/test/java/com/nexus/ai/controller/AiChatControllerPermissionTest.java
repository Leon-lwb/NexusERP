package com.nexus.ai.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import com.nexus.ai.service.facade.AiServiceFacade;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiSessionService;
import com.nexus.common.satoken.handler.SaTokenExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用Sa-Token实际注解分发器验证角色授权在控制器执行前生效。 */
class AiChatControllerPermissionTest {
    record Endpoint(HttpMethod method, String path, String body, String permission) { }

    static Stream<Endpoint> endpoints() {
        return Stream.of(
            new Endpoint(HttpMethod.POST, "/ai/chat/send", "{\"content\":\"hello\"}", "ai:assistant:send"),
            new Endpoint(HttpMethod.GET, "/ai/chat/history/91", "", "ai:assistant:query"),
            new Endpoint(HttpMethod.GET, "/ai/chat/sessions", "", "ai:assistant:query"),
            new Endpoint(HttpMethod.GET, "/ai/chat/token-usage/91", "", "ai:assistant:query"),
            new Endpoint(HttpMethod.DELETE, "/ai/chat/session/91", "", "ai:assistant:remove"),
            new Endpoint(HttpMethod.DELETE, "/ai/chat/message/92", "", "ai:assistant:remove"),
            new Endpoint(HttpMethod.PUT, "/ai/chat/session", "{\"id\":91,\"title\":\"new\"}", "ai:assistant:send"),
            new Endpoint(HttpMethod.PUT, "/ai/chat/message/92/feedback?feedback=LIKE", "", "ai:assistant:send"),
            new Endpoint(HttpMethod.GET, "/ai/chat/export/91", "", "ai:chat:export"));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void callerWithoutEndpointPermissionNeverReachesBusinessCode(Endpoint endpoint) throws Exception {
        var facade = mock(AiServiceFacade.class);
        var messages = mock(AiMessageMapper.class);
        var sessions = mock(IAiSessionService.class);
        var logic = mock(StpLogic.class);
        doThrow(new NotPermissionException(endpoint.permission())).when(logic).checkPermissionAnd(any(String[].class));
        try (var manager = mockStatic(SaManager.class)) {
            manager.when(() -> SaManager.getStpLogic(anyString(), eq(false))).thenReturn(logic);
            var mvc = MockMvcBuilders.standaloneSetup(new AiChatController(facade, messages, sessions))
                .setControllerAdvice(new SaTokenExceptionHandler()).addInterceptors(annotationInterceptor()).build();
            mvc.perform(request(endpoint.method(), endpoint.path()).contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(jsonPath("$.code").value(403));
            verify(logic).checkPermissionAnd(endpoint.permission());
            verifyNoInteractions(facade, messages, sessions);
        }
    }

    @Test
    void authorizedSendKeepsExistingSessionResponseContract() throws Exception {
        var facade = mock(AiServiceFacade.class);
        when(facade.chat(any())).thenReturn(9007199254740993L);
        var logic = mock(StpLogic.class);
        try (var manager = mockStatic(SaManager.class)) {
            manager.when(() -> SaManager.getStpLogic(anyString(), eq(false))).thenReturn(logic);
            var mvc = MockMvcBuilders.standaloneSetup(new AiChatController(facade, mock(AiMessageMapper.class), mock(IAiSessionService.class)))
                .setControllerAdvice(new SaTokenExceptionHandler()).addInterceptors(annotationInterceptor()).build();
            mvc.perform(post("/ai/chat/send").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello\"}"))
                .andExpect(jsonPath("$.code").value(200));
            verify(logic).checkPermissionAnd("ai:assistant:send");
            verify(facade).chat(any());
        }
    }

    private static HandlerInterceptor annotationInterceptor() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method) {
                    SaAnnotationStrategy.instance.checkMethodAnnotation.accept(method.getMethod());
                }
                return true;
            }
        };
    }
}
