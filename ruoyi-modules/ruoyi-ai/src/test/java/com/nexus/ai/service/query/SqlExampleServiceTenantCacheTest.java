package com.nexus.ai.service.query;

import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.common.ai.domain.bo.AiSqlExampleBo;
import com.nexus.common.ai.domain.vo.AiSqlExampleVo;
import com.nexus.common.ai.service.IAiSqlExampleService;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 使用真实 String getter 边界模拟两个租户，查询仅调用 mock 持久化接口，不启动数据库。
 */
class SqlExampleServiceTenantCacheTest {

    private final AtomicReference<String> currentTenant = new AtomicReference<>("TENANT-A");
    private final Map<String, String> revisions = new HashMap<>();
    private MockedStatic<TenantHelper> tenants;
    private IAiSqlExampleService persistence;
    private SqlExampleService service;

    @BeforeEach
    void setUp() {
        tenants = mockStatic(TenantHelper.class);
        tenants.when(TenantHelper::getTenantId).thenAnswer(invocation -> currentTenant.get());
        persistence = mock(IAiSqlExampleService.class);
        when(persistence.selectList(any(AiSqlExampleBo.class))).thenAnswer(invocation -> {
            AiSqlExampleBo request = invocation.getArgument(0);
            assertEquals(AiToolStatusContract.ACTIVE, request.getStatus());
            String tenantId = TenantHelper.getTenantId();
            String scope = request.getCategory() == null ? "全部" : request.getCategory();
            AiSqlExampleVo example = new AiSqlExampleVo();
            example.setCategory(request.getCategory());
            example.setQuestion(tenantId + " " + scope + " " + revisions.getOrDefault(tenantId, "旧版"));
            // 合成展示文本仅验证租户缓存隔离，不执行 SQL，也不改 Long DTO 的契约。
            example.setSqlTemplate("SELECT '" + tenantId + "'");
            return List.of(example);
        });
        service = new SqlExampleService(persistence);
    }

    @AfterEach
    void tearDown() {
        tenants.close();
    }

    @Test
    void allExamplesStayTenantScopedWhenBothTenantCachesAreWarm() {
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        currentTenant.set("TENANT-B");
        assertEquals("TENANT-B 全部 旧版", question(service.getAllEnabledExamples()));
        currentTenant.set("TENANT-A");
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        verify(persistence, times(2)).selectList(any(AiSqlExampleBo.class));
    }

    @Test
    void sameCategoryReturnsDifferentTenantContentWithoutReusingHotCache() {
        assertEquals("TENANT-A REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        currentTenant.set("TENANT-B");
        List<AiSqlExampleVo> examples = service.getExamplesByCategory("REVIEW");
        assertEquals("TENANT-B REVIEW 旧版", question(examples));
        assertTrue(service.formatExamplesForPrompt(examples).contains("SELECT 'TENANT-B'"));
        currentTenant.set("TENANT-A");
        assertEquals("TENANT-A REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        verify(persistence, times(2)).selectList(any(AiSqlExampleBo.class));
    }

    @Test
    void tenantStringKeepsLeadingZerosAndNonnumericNamesUnchanged() {
        currentTenant.set("000123");
        assertEquals("000123 全部 旧版", question(service.getAllEnabledExamples()));
        currentTenant.set("123");
        assertEquals("123 全部 旧版", question(service.getAllEnabledExamples()));
        currentTenant.set("TENANT-B");
        assertEquals("TENANT-B 全部 旧版", question(service.getAllEnabledExamples()));
        verify(persistence, times(3)).selectList(any(AiSqlExampleBo.class));
    }

    @Test
    void allExamplesAndLiteralAllCategoryUseDifferentCacheScopes() {
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        assertEquals("TENANT-A all 旧版", question(service.getExamplesByCategory("all")));
        assertEquals("TENANT-A REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        verify(persistence, times(3)).selectList(any(AiSqlExampleBo.class));
    }

    @Test
    void evictCacheOnlyRemovesCurrentTenantsAllAndCategoryScopes() {
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        assertEquals("TENANT-A REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        currentTenant.set("TENANT-B");
        assertEquals("TENANT-B 全部 旧版", question(service.getAllEnabledExamples()));
        assertEquals("TENANT-B REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        revisions.put("TENANT-A", "新版");
        revisions.put("TENANT-B", "新版");
        currentTenant.set("TENANT-A");
        service.evictCache();
        assertEquals("TENANT-A 全部 新版", question(service.getAllEnabledExamples()));
        assertEquals("TENANT-A REVIEW 新版", question(service.getExamplesByCategory("REVIEW")));
        currentTenant.set("TENANT-B");
        assertEquals("TENANT-B 全部 旧版", question(service.getAllEnabledExamples()));
        assertEquals("TENANT-B REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        verify(persistence, times(6)).selectList(any(AiSqlExampleBo.class));
    }

    @Test
    void explicitEvictAllCacheRemainsAvailableWithoutTenantContext() throws Exception {
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        currentTenant.set("TENANT-B");
        assertEquals("TENANT-B REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        revisions.put("TENANT-A", "新版");
        revisions.put("TENANT-B", "新版");
        currentTenant.set(null);
        // 基线没有该公共入口：仅反射调用 public API 让 RED 可编译，不访问缓存内部字段。
        Method globalEviction = assertDoesNotThrow(() -> SqlExampleService.class.getMethod("evictAllCache"),
            "应提供显式全局清理入口，当前租户清理不能替代它");
        globalEviction.invoke(service);
        currentTenant.set("TENANT-A");
        assertEquals("TENANT-A 全部 新版", question(service.getAllEnabledExamples()));
        currentTenant.set("TENANT-B");
        assertEquals("TENANT-B REVIEW 新版", question(service.getExamplesByCategory("REVIEW")));
        verify(persistence, times(4)).selectList(any(AiSqlExampleBo.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingTenantFailsBeforeAnyExampleQuery(String missingTenant) {
        currentTenant.set(missingTenant);
        IllegalStateException failure = assertThrows(IllegalStateException.class, service::getAllEnabledExamples);
        assertTrue(failure.getMessage().contains("租户上下文缺失"));
        assertThrows(IllegalStateException.class, () -> service.getExamplesByCategory("REVIEW"));
        assertThrows(IllegalStateException.class, service::evictCache);
        verifyNoInteractions(persistence);
        currentTenant.set("TENANT-A");
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        verify(persistence).selectList(any(AiSqlExampleBo.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingTenantCannotReadOrEvictAlreadyWarmExampleCache(String missingTenant) {
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        assertEquals("TENANT-A REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        currentTenant.set(missingTenant);
        assertThrows(IllegalStateException.class, service::getAllEnabledExamples);
        assertThrows(IllegalStateException.class, () -> service.getExamplesByCategory("REVIEW"));
        assertThrows(IllegalStateException.class, service::evictCache);
        revisions.put("TENANT-A", "新版");
        currentTenant.set("TENANT-A");
        assertEquals("TENANT-A 全部 旧版", question(service.getAllEnabledExamples()));
        assertEquals("TENANT-A REVIEW 旧版", question(service.getExamplesByCategory("REVIEW")));
        verify(persistence, times(2)).selectList(any(AiSqlExampleBo.class));
        verifyNoMoreInteractions(persistence);
    }

    private String question(List<AiSqlExampleVo> examples) {
        assertEquals(1, examples.size());
        return examples.get(0).getQuestion();
    }
}
