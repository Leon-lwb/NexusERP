package com.nexus.ai.controller;

import com.nexus.ai.service.facade.AiServiceFacade;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiSessionService;
import com.nexus.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatControllerSessionContractTest {
    @Test
    void missingAndForeignSessionsUseNotFoundInsteadOfInternalError() {
        IAiSessionService sessions = mock(IAiSessionService.class);
        AiMessageMapper messages = mock(AiMessageMapper.class);
        AiChatController controller = new AiChatController(mock(AiServiceFacade.class), messages, sessions);
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getTenantId).thenReturn("000007");
            assertEquals(404, controller.history(9007199254740993L).getCode());
            assertEquals(404, controller.getTokenUsage(9007199254740993L).getCode());
            AiSession foreign = new AiSession();
            foreign.setUserId(8L);
            when(sessions.selectById(9007199254740993L)).thenReturn(foreign);
            assertEquals(404, controller.history(9007199254740993L).getCode());
            assertEquals(404, controller.deleteSession(new Long[]{9007199254740993L}).getCode());
            AiSession rename = new AiSession();
            rename.setId(9007199254740993L);
            rename.setTitle("标题");
            assertEquals(404, controller.renameSession(rename).getCode());
            verifyNoInteractions(messages);
            verify(sessions, never()).deleteByIds(any());
            verify(sessions, never()).updateTitle(any(), any());
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"不存在", "其他用户", "其他租户"})
    void deniedHistoryAndTokenQueriesDoNotTouchMessageMapper(String scenario) throws Exception {
        AiServiceFacade facade = mock(AiServiceFacade.class);
        IAiSessionService sessions = mock(IAiSessionService.class);
        AiMessageMapper messages = mock(AiMessageMapper.class);
        AiChatController controller = new AiChatController(facade, messages, sessions);
        if (!"不存在".equals(scenario)) {
            AiSession foreign = new AiSession();
            foreign.setUserId("其他用户".equals(scenario) ? 8L : 7L);
            foreign.setTenantId("其他租户".equals(scenario) ? "000008" : "000007");
            when(sessions.selectById(9007199254740993L)).thenReturn(foreign);
        }
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getTenantId).thenReturn("000007");
            var mvc = MockMvcBuilders.standaloneSetup(controller).build();
            mvc.perform(get("/ai/chat/history/9007199254740993"))
                .andExpect(jsonPath("$.code").value(404));
            mvc.perform(get("/ai/chat/token-usage/9007199254740993"))
                .andExpect(jsonPath("$.code").value(404));
            verifyNoInteractions(messages, facade);
            verify(sessions, times(2)).selectById(9007199254740993L);
            verifyNoMoreInteractions(sessions);
        }
    }

    @Test
    void ownTenantHistoryStillReadsOnlyAfterOwnershipCheck() {
        IAiSessionService sessions = mock(IAiSessionService.class);
        AiMessageMapper messages = mock(AiMessageMapper.class);
        AiChatController controller = new AiChatController(mock(AiServiceFacade.class), messages, sessions);
        AiSession own = new AiSession();
        own.setUserId(7L);
        own.setTenantId("000007");
        when(sessions.selectById(9007199254740993L)).thenReturn(own);
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getTenantId).thenReturn("000007");
            assertEquals(200, controller.history(9007199254740993L).getCode());
            var order = inOrder(sessions, messages);
            order.verify(sessions).selectById(9007199254740993L);
            order.verify(messages).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
            verifyNoMoreInteractions(messages);
        }
    }

}
