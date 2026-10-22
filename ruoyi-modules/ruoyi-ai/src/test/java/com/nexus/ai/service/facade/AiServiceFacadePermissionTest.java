package com.nexus.ai.service.facade;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 即使通过内部入口调用门面，也必须在读模型和写消息前检查发送权限。 */
class AiServiceFacadePermissionTest {
    @Test
    void deniedInternalCallerDoesNotResolveModelOrPersistMessages() throws Exception {
        var constructor = AiServiceFacade.class.getDeclaredConstructors()[0];
        List<Object> dependencies = new ArrayList<>();
        for (Class<?> type : constructor.getParameterTypes()) dependencies.add(mock(type));
        var facade = (AiServiceFacade) constructor.newInstance(dependencies.toArray());
        var request = new AiChatRequest();
        request.setContent("hello");
        try (var auth = mockStatic(StpUtil.class); var login = mockStatic(LoginHelper.class); var sse = mockStatic(AiSseMessageHelper.class)) {
            auth.when(() -> StpUtil.checkPermission("ai:assistant:send")).thenThrow(new NotPermissionException("ai:assistant:send"));
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getTenantId).thenReturn("000007");
            assertThrows(NotPermissionException.class, () -> facade.chat(request));
            for (Object dependency : dependencies) verifyNoInteractions(dependency);
            sse.verifyNoInteractions();
        }
    }
}
