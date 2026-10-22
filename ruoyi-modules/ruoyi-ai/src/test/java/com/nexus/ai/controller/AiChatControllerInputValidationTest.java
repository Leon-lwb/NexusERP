package com.nexus.ai.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.facade.AiServiceFacade;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiSessionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

class AiChatControllerInputValidationTest {
    private final AiServiceFacade facade = mock(AiServiceFacade.class);
    private final AiMessageMapper messages = mock(AiMessageMapper.class);
    private final IAiSessionService sessions = mock(IAiSessionService.class);
    private final AiChatController controller = new AiChatController(facade, messages, sessions);
    private MockMvc mvc;

    @BeforeEach
    void prepare() { mvc = MockMvcBuilders.standaloneSetup(controller).build(); }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}"})
    void emptyRequestBodyIsRejectedBeforeAnyBusinessOperation(String body) throws Exception {
        mvc.perform(post("/ai/chat/send").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value("消息内容不能为空"));
        verifyNoInteractions(facade, messages, sessions);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u3000", "\u00A0"})
    void nullOrWhitespaceContentUsesSafeChineseValidationError(String content) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Collections.singletonMap("content", content));
        mvc.perform(post("/ai/chat/send").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value("消息内容不能为空"));
        verifyNoInteractions(facade, messages, sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "has spaces in request id", "request-id-with-\\n-control", "\"quoted-request-id-value\""})
    void invalidClientCorrelationNeverInvokesModel(String clientRequestId) throws Exception {
        String body =
                new ObjectMapper()
                        .writeValueAsString(
                                Map.of("content", "hello", "clientRequestId", clientRequestId));
        mvc.perform(post("/ai/chat/send").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(facade, messages, sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"中", "😀"})
    void oversizedContentIsRejectedWithoutInvokingFacade(String character) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Collections.singletonMap("content", character.repeat(2001)));
        mvc.perform(post("/ai/chat/send").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value("消息内容不能超过2000个字符"));
        verifyNoInteractions(facade, messages, sessions);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void validBoundaryIsForwardedUnchanged(int length) {
        AiChatRequest request = new AiChatRequest();
        request.setContent("中".repeat(length));
        when(facade.chat(request)).thenReturn(9007199254740993L);
        var result = controller.send(request);
        assertEquals(200, result.getCode());
        assertEquals(9007199254740993L, result.getData());
        verify(facade).chat(same(request));
        assertEquals("中".repeat(length), request.getContent());
        verifyNoInteractions(messages, sessions);
    }
}
