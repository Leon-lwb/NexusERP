package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.engineering.service.*;
import com.nexus.ai.tool.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.engineering.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.*;

class EngineeringToolRoutingTest {
    @Test
    void engineeringCategoryNeverLeaksIntoQueryOrAllRoutes() {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var provider = new EasyedaEngineeringToolProvider();
        assertEquals("ENGINEERING", provider.getCategories());
        var context = mock(ApplicationContext.class);
        var tool = mock(EasyedaEngineeringTool.class);
        when(context.getBean(EasyedaEngineeringTool.class)).thenReturn(tool);
        var row = new AiTool();
        row.setToolName("easyeda_engineering");
        row.setToolType("BUILTIN");
        row.setStatus("0");
        when(f.tools.selectList(any())).thenReturn(List.of(row));
        var registry = new BuiltinToolRegistry(List.of(provider), f.tools, context);
        registry.init();
        assertTrue(registry.getEnabledBuiltinToolObjectsByCategories("QUERY,ALL").isEmpty());
        assertEquals(
                List.of(tool), registry.getEnabledBuiltinToolObjectsByCategories("ENGINEERING"));
        when(f.tools.selectList(any())).thenReturn(List.of());
        assertTrue(registry.getEnabledBuiltinToolObjectsByCategories("ENGINEERING").isEmpty());
    }

    @Test
    void disabledNoDeviceNoPermissionOrForeignSessionCannotAttachTools() {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var tool = new EasyedaEngineeringTool(f.tasks, f.catalog);
        var factory =
                new EngineeringAgentFactory(
                        f.properties, f.access, f.repository, f.sessions, tool, f.catalog);
        var request = new AiChatRequest();
        request.setContent("帮我创建PCB草稿");
        request.setEngineeringRequestId("server-request");
        assertNull(factory.capture(request, 7L, 9L, "000001"));
        var c = f.pair();
        request.setEngineeringDeviceId(Long.valueOf(c.deviceId()));
        f.properties.setEnabled(false);
        assertNull(factory.capture(request, 7L, 9L, "000001"));
        f.properties.setEnabled(true);
        when(f.tools.engineeringEnabled(anyString())).thenReturn(0L);
        assertThrows(EngineeringException.class, () -> factory.capture(request, 7L, 9L, "000001"));
        when(f.tools.engineeringEnabled(anyString())).thenReturn(1L);
        assertThrows(EngineeringException.class, () -> factory.capture(request, 7L, 9L, "000001"));
        var session = new AiSession();
        session.setId(9L);
        session.setUserId(7L);
        session.setTenantId("000001");
        when(f.sessions.selectOne(any())).thenReturn(session);
        assertNotNull(factory.capture(request, 7L, 9L, "000001"));
        request.setContent("查询库存预警");
        assertNull(factory.capture(request, 7L, 9L, "000001"));
        assertEquals(0, f.repository.count("select count(*) from ai_engineering_task"));
    }

    @Test
    void modelCompletionTextWithoutActualReceiptIsRejected() {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var factory =
                new EngineeringAgentFactory(
                        f.properties,
                        f.access,
                        f.repository,
                        f.sessions,
                        new EasyedaEngineeringTool(f.tasks, f.catalog),
                        f.catalog);
        var context = new EngineeringToolContext.Context(f.actor, 8L, 9L, "request");
        assertFalse(factory.verifiedResult(context, "PCB已经画好并生成成功").contains("生成成功"));
        var forged = factory.verifiedResult(context, "PCB设计完成，预览：/ai/engineering/task/123");
        assertFalse(forged.contains("/ai/engineering/task/123"));
        assertTrue(forged.contains("尚未创建"));
        assertTrue(factory.verifiedResult(context, "请提供需要的模板。").contains("尚未创建"));
    }
}
