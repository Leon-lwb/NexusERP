package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.handler.SaCheckPermissionHandler;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpLogic;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.mcp.service.IMcpMarketService;
import com.nexus.common.ai.domain.entity.AiMcpMarket;
import com.nexus.common.ai.domain.entity.AiMcpMarketTool;
import com.nexus.common.ai.mapper.AiMcpMarketMapper;
import com.nexus.common.ai.mapper.AiMcpMarketToolMapper;
import com.nexus.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 市场目录的权限和凭证边界测试，不连接数据库。 */
class McpMarketControllerTest {
    private final AiMcpMarketMapper mapper = mock(AiMcpMarketMapper.class);
    private final AiMcpMarketToolMapper tools = mock(AiMcpMarketToolMapper.class);
    private final McpMarketController controller = new McpMarketController(mapper, tools, mock(IMcpMarketService.class));

    @Test
    void marketToolListRequiresMarketQueryPermission() throws Exception {
        SaCheckPermission permission = McpMarketController.class
            .getMethod("getMarketTools", Long.class, PageQuery.class).getAnnotation(SaCheckPermission.class);
        assertNotNull(permission, "市场工具列表不可只依赖登录校验");
        assertArrayEquals(new String[]{"ai:mcp:market:query"}, permission.value());
    }

    @Test
    void marketToolPermissionIsEnforcedByTheRealSaTokenAnnotationHandler() throws Exception {
        var method = McpMarketController.class.getMethod("getMarketTools", Long.class, PageQuery.class);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        StpLogic logic = mock(StpLogic.class);
        doThrow(new NotPermissionException("ai:mcp:market:query"))
            .when(logic).checkPermissionAnd("ai:mcp:market:query");
        try (MockedStatic<SaManager> sa = mockStatic(SaManager.class)) {
            sa.when(() -> SaManager.getStpLogic(permission.type(), false)).thenReturn(logic);
            assertThrows(NotPermissionException.class,
                () -> new SaCheckPermissionHandler().checkMethod(permission, method));
        }
        verifyNoInteractions(mapper, tools);
    }

    @Test
    void marketToolMetadataIsMaskedWithoutMutatingTheStoredRecord() {
        AiMcpMarketTool stored = new AiMcpMarketTool();
        stored.setMarketId(1L);
        stored.setToolMetadata("{\"env\":{\"SERVICE_API_KEY\":\"test-only-secret\"}}");
        Page<AiMcpMarketTool> page = new Page<>();
        page.setRecords(List.of(stored));
        page.setTotal(1L);
        when(tools.selectPage(any(Page.class), any())).thenReturn(page);
        var result = controller.getMarketTools(1L, new PageQuery(10, 1));
        assertFalse(result.getRows().get(0).getToolMetadata().contains("test-only-secret"));
        assertTrue(stored.getToolMetadata().contains("test-only-secret"));
    }

    @Test
    void marketDetailDoesNotExposeAuthenticationOrUrlCredentials() {
        AiMcpMarket stored = new AiMcpMarket();
        stored.setUrl("https://example.test/catalog?token=test-url-secret");
        stored.setAuthConfig("{\"Authorization\":\"test-auth-secret\"}");
        when(mapper.selectById(1L)).thenReturn(stored);
        AiMcpMarket result = controller.getInfo(1L).getData();
        assertEquals("****", result.getAuthConfig());
        assertFalse(result.getUrl().contains("test-url-secret"));
        assertEquals("{\"Authorization\":\"test-auth-secret\"}", stored.getAuthConfig());
    }

    @Test
    void marketCreationDiscardsClientSuppliedTenant() throws Exception {
        AiMcpMarket request = new ObjectMapper().readValue(
            "{\"name\":\"demo\",\"url\":\"https://93.184.216.34/catalog\",\"tenantId\":\"123456\"}", AiMcpMarket.class);
        controller.add(request);
        verify(mapper).insert(any(AiMcpMarket.class));
        assertNull(request.getTenantId(), "租户应由当前上下文/插件注入，不能来自请求体");
    }

    @Test
    void marketEditPreservesMaskedAuthButDiscardsClientTenant() throws Exception {
        AiMcpMarket stored = new AiMcpMarket();
        stored.setId(1L);
        stored.setUrl("https://93.184.216.34/catalog?token=test-only-secret");
        stored.setAuthConfig("{\"Authorization\":\"test-only-secret\"}");
        when(mapper.selectById(1L)).thenReturn(stored);
        AiMcpMarket request = new ObjectMapper().readValue(
            "{\"id\":\"1\",\"url\":\"https://93.184.216.34/catalog?token=****\",\"authConfig\":\"****\",\"tenantId\":\"123456\"}", AiMcpMarket.class);
        controller.edit(request);
        assertNull(request.getTenantId());
        assertEquals(stored.getAuthConfig(), request.getAuthConfig());
        assertEquals(stored.getUrl(), request.getUrl());
    }

    @Test
    void marketCreationRejectsLoopbackBeforeInsert() {
        AiMcpMarket request = new AiMcpMarket();
        request.setName("demo");
        request.setUrl("http://127.0.0.1:8080/private");
        assertThrows(RuntimeException.class, () -> controller.add(request));
        verify(mapper, never()).insert(any(AiMcpMarket.class));
    }
}
