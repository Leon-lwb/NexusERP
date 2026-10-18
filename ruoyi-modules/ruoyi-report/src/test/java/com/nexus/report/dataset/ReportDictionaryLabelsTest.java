package com.nexus.report.dataset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.service.DictService;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.report.ReportActor;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 验证异步字典读取限定当前租户并在异常后恢复调用线程。 */
class ReportDictionaryLabelsTest {
    @Test
    void tenantSpecificNamesAreCopiedAndOuterContextIsRestored() {
        try (var spring = mockStatic(SpringUtil.class); var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            var dictionaries = mock(DictService.class);
            var mutable = new LinkedHashMap<String, String>();
            mutable.put("A", "租户甲名称");
            mutable.put("EMPTY", " ");
            mutable.put("NULL", null);
            when(dictionaries.getAllDictByDictType("business_type")).thenAnswer(invocation ->
                    "TENANT_A".equals(TenantHelper.getTenantId()) ? mutable : Map.of("A", "租户乙名称"));
            var service = new ReportDictionaryLabels(dictionaries);
            TenantHelper.dynamic("OUTER", () -> {
                var first = service.load(new ReportActor("TENANT_A", 1, 10, true, List.of()), Set.of("business_type"));
                var second = service.load(new ReportActor("TENANT_B", 2, 10, true, List.of()), Set.of("business_type"));
                assertEquals("OUTER", TenantHelper.getTenantId());
                assertEquals("租户甲名称", first.get("business_type").get("A"));
                assertEquals("租户乙名称", second.get("business_type").get("A"));
                mutable.put("A", "已修改");
                assertEquals("租户甲名称", first.get("business_type").get("A"));
                assertFalse(first.get("business_type").containsKey("EMPTY"));
                assertFalse(first.get("business_type").containsKey("NULL"));
                assertThrows(UnsupportedOperationException.class, () -> first.get("business_type").put("A", "覆盖"));
            });
            assertNull(TenantHelper.getDynamic());
        }
    }

    @Test
    void failedDictionaryReadsRestoreOuterTenantAndPropagateFailure() {
        try (var spring = mockStatic(SpringUtil.class); var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            var dictionaries = mock(DictService.class);
            when(dictionaries.getAllDictByDictType("business_type")).thenThrow(new IllegalStateException("字典读取失败"));
            var service = new ReportDictionaryLabels(dictionaries);
            TenantHelper.dynamic("OUTER", () -> {
                assertThrows(IllegalStateException.class, () -> service.load(
                        new ReportActor("TENANT_A", 1, 10, true, List.of()), Set.of("business_type")));
                assertEquals("OUTER", TenantHelper.getTenantId());
            });
            assertNull(TenantHelper.getDynamic());
        }
    }
}
