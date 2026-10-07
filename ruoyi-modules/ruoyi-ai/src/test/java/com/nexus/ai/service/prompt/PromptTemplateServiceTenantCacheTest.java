package com.nexus.ai.service.prompt;

import com.nexus.ai.service.query.ErpTableSchemaManager;
import com.nexus.ai.service.query.SqlExampleService;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import com.nexus.common.ai.service.IAiPromptTemplateService;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 仅在生产 TenantHelper getter 和持久化接口边界 mock，不使用真实 DB 或缓存内部反射。
 */
class PromptTemplateServiceTenantCacheTest {

    private final AtomicReference<String> currentTenant = new AtomicReference<>("TENANT-A");
    private final Map<String, Map<String, String>> storedTemplates = new HashMap<>();
    private MockedStatic<TenantHelper> tenants;
    private IAiPromptTemplateService persistence;
    private PromptTemplateService service;
    private ErpTableSchemaManager schemaManager;
    private BuiltinToolRegistry toolRegistry;
    private SqlExampleService exampleProvider;

    @BeforeEach
    void setUp() {
        tenants = mockStatic(TenantHelper.class);
        tenants.when(TenantHelper::getTenantId).thenAnswer(invocation -> currentTenant.get());
        persistence = mock(IAiPromptTemplateService.class);
        when(persistence.selectByTemplateKey(anyString())).thenAnswer(invocation -> {
            // 模拟实际查询边界按当前租户返回内容，DTO 的基线 Long tenantId 不参与缓存身份。
            String key = invocation.getArgument(0);
            String content = storedTemplates.getOrDefault(TenantHelper.getTenantId(), Map.of()).get(key);
            if (content == null) {
                return null;
            }
            AiPromptTemplate template = new AiPromptTemplate();
            template.setTemplateKey(key);
            template.setContent(content);
            return template;
        });
        schemaManager = mock(ErpTableSchemaManager.class);
        toolRegistry = mock(BuiltinToolRegistry.class);
        exampleProvider = mock(SqlExampleService.class);
        service = new PromptTemplateService(persistence, schemaManager, toolRegistry, exampleProvider);
    }

    @AfterEach
    void tearDown() {
        tenants.close();
    }

    @Test
    void sameTemplateKeyReturnsDifferentContentAcrossWarmTenantContexts() {
        put("TENANT-A", "shared", "A {{name}}");
        put("TENANT-B", "shared", "B {{name}}");
        assertEquals("A 首次", service.render("shared", Map.of("name", "首次")));
        currentTenant.set("TENANT-B");
        assertEquals("B 第二次", service.render("shared", Map.of("name", "第二次")));
        currentTenant.set("TENANT-A");
        assertEquals("A 再次", service.render("shared", Map.of("name", "再次")));
        verify(persistence, times(2)).selectByTemplateKey("shared");
    }

    @Test
    void tenantStringIsNotNormalizedOrConvertedToLong() {
        put("000123", "shared", "前导零租户");
        put("123", "shared", "另一租户");
        currentTenant.set("000123");
        assertEquals("前导零租户", render("shared"));
        currentTenant.set("123");
        assertEquals("另一租户", render("shared"));
        verify(persistence, times(2)).selectByTemplateKey("shared");
    }

    @Test
    void tenantAndTemplateKeyCannotAliasThroughStringDelimiters() {
        put("TENANT:A", "shared", "租户名称含分隔符");
        put("TENANT", "A:shared", "模板名称含分隔符");
        currentTenant.set("TENANT:A");
        assertEquals("租户名称含分隔符", render("shared"));
        currentTenant.set("TENANT");
        assertEquals("模板名称含分隔符", render("A:shared"));
    }

    @Test
    void missingTemplateNegativeCacheCannotHideAnotherTenantsTemplate() {
        put("TENANT-B", "shared", "B 的真实模板");
        assertEquals("", render("shared"));
        assertEquals("", render("shared"));
        currentTenant.set("TENANT-B");
        assertEquals("B 的真实模板", render("shared"));
        verify(persistence, times(2)).selectByTemplateKey("shared");
    }

    @Test
    void evictCacheOnlyRemovesCurrentTenantAndExactTemplateKey() {
        put("TENANT-A", "shared", "A 旧版");
        put("TENANT-A", "other", "A 其他旧版");
        put("TENANT-B", "shared", "B 旧版");
        assertEquals("A 旧版", render("shared"));
        assertEquals("A 其他旧版", render("other"));
        currentTenant.set("TENANT-B");
        assertEquals("B 旧版", render("shared"));
        put("TENANT-A", "shared", "A 新版");
        put("TENANT-A", "other", "A 其他新版");
        put("TENANT-B", "shared", "B 新版");
        currentTenant.set("TENANT-A");
        service.evictCache("shared");
        assertEquals("A 新版", render("shared"));
        assertEquals("A 其他旧版", render("other"));
        currentTenant.set("TENANT-B");
        assertEquals("B 旧版", render("shared"));
        verify(persistence, times(3)).selectByTemplateKey("shared");
        verify(persistence).selectByTemplateKey("other");
    }

    @Test
    void evictAllCacheCanClearEveryTenantWithoutTenantContext() {
        put("TENANT-A", "shared", "A 旧版");
        put("TENANT-B", "shared", "B 旧版");
        assertEquals("A 旧版", render("shared"));
        currentTenant.set("TENANT-B");
        assertEquals("B 旧版", render("shared"));
        put("TENANT-A", "shared", "A 新版");
        put("TENANT-B", "shared", "B 新版");
        currentTenant.set(null);
        service.evictAllCache();
        currentTenant.set("TENANT-A");
        assertEquals("A 新版", render("shared"));
        currentTenant.set("TENANT-B");
        assertEquals("B 新版", render("shared"));
        verify(persistence, times(4)).selectByTemplateKey("shared");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingTenantFailsBeforeAnyPersistenceQuery(String missingTenant) {
        currentTenant.set(missingTenant);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> render("shared"));
        assertTrue(failure.getMessage().contains("租户上下文缺失"));
        assertThrows(IllegalStateException.class, () -> service.evictCache("shared"));
        verifyNoInteractions(persistence);
        currentTenant.set("TENANT-A");
        put("TENANT-A", "shared", "恢复后的 A 模板");
        assertEquals("恢复后的 A 模板", render("shared"));
        verify(persistence).selectByTemplateKey("shared");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingTenantCannotReadOrEvictAlreadyWarmTenantCache(String missingTenant) {
        put("TENANT-A", "shared", "A 已缓存");
        assertEquals("A 已缓存", render("shared"));
        currentTenant.set(missingTenant);
        assertThrows(IllegalStateException.class, () -> render("shared"));
        assertThrows(IllegalStateException.class, () -> service.evictCache("shared"));
        currentTenant.set("TENANT-A");
        put("TENANT-A", "shared", "A 磁盘新版");
        assertEquals("A 已缓存", render("shared"));
        verify(persistence).selectByTemplateKey("shared");
        verifyNoMoreInteractions(persistence);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void contextRenderingRequiresTenantBeforeConsultingOtherProviders(String missingTenant) {
        currentTenant.set(missingTenant);
        assertThrows(IllegalStateException.class, service::renderQueryAgentContext);
        assertThrows(IllegalStateException.class, service::renderReportAgentContext);
        assertThrows(IllegalStateException.class, service::renderSupervisorContext);
        verifyNoInteractions(persistence, schemaManager, toolRegistry, exampleProvider);
    }
    private String render(String key) {
        return service.render(key, Map.of());
    }

    private void put(String tenantId, String key, String content) {
        storedTemplates.computeIfAbsent(tenantId, ignored -> new HashMap<>()).put(key, content);
    }
}
