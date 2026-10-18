package com.nexus.report.dataset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.report.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** 验证字典翻译只作用于授权投影，并在单次读取期间固定名称。 */
class ReportDictionaryDatasetProviderTest {
    private final ReportActor actor = new ReportActor("TENANT_A", 1, 10, true, List.of());
    private final ReportExecutionContext context = new ReportExecutionContext(actor, List.of(ReportScope.all(1)));
    private final DatasetQuery query = new DatasetQuery(Map.of(), Set.of("code", "id", "amount"), 1, 20, false, null, false);
    private final ReportDictionaryLabels labels = mock(ReportDictionaryLabels.class);

    @Test
    void queryPreservesRawCodesExactValuesAndMetadata() {
        var original = new LinkedHashMap<String, Object>();
        original.put("code", "ENTERPRISE");
        original.put("id", "9223372036854775807");
        original.put("amount", "9007199254740993.005");
        var source = provider(List.of(original));
        when(labels.load(actor, Set.of("bd_client_type")))
                .thenReturn(Map.of("bd_client_type", Map.of("ENTERPRISE", "企业")));
        var decorated = new ReportDictionaryDatasetProvider(source, labels);
        var result = decorated.query(context, query);
        var code = TypedValue.of(ValueType.STRING, result.rows().get(0).get("code"));
        assertEquals("ENTERPRISE", code.value());
        assertEquals("企业", code.displayText());
        assertEquals("ENTERPRISE", original.get("code"));
        assertEquals("9223372036854775807", result.rows().get(0).get("id"));
        assertEquals("9007199254740993.005", result.rows().get(0).get("amount"));
        assertEquals(1, result.total());
        assertEquals(Instant.EPOCH, result.dataTime());
        assertSame(source.descriptor(), decorated.descriptor());
        assertEquals(source.executionIdentity(), decorated.executionIdentity());
        assertEquals(source.requiredPermissions(), decorated.requiredPermissions());
        verify(labels).load(actor, Set.of("bd_client_type"));
        verifyNoMoreInteractions(labels);
    }

    @Test
    void streamingFreezesOneDictionaryCopyWithoutPerRowQueries() {
        var mutable = new HashMap<>(Map.of("ENTERPRISE", "企业"));
        when(labels.load(actor, Set.of("bd_client_type"))).thenReturn(Map.of("bd_client_type", mutable));
        var decorated = new ReportDictionaryDatasetProvider(
                provider(List.of(Map.of("code", "ENTERPRISE"), Map.of("code", "ENTERPRISE"))), labels);
        var names = new ArrayList<String>();
        decorated.stream(context, query, row -> {
            names.add(TypedValue.of(ValueType.STRING, row.get("code")).displayText());
            mutable.put("ENTERPRISE", "修改后的企业名称");
        });
        assertEquals(List.of("企业", "企业"), names);
        verify(labels, times(1)).load(actor, Set.of("bd_client_type"));
    }

    @Test
    void unknownEmptyAndNullValuesKeepTheirMeaning() {
        var nullRow = new HashMap<String, Object>();
        nullRow.put("code", null);
        when(labels.load(actor, Set.of("bd_client_type")))
                .thenReturn(Map.of("bd_client_type", Map.of("ENTERPRISE", " ")));
        var result = new ReportDictionaryDatasetProvider(
                provider(List.of(Map.of("code", "UNKNOWN"), Map.of("code", "ENTERPRISE"), nullRow,
                        Map.of("code", TypedValue.of(ValueType.STRING, null)))), labels)
                .query(context, query);
        assertEquals("UNKNOWN", TypedValue.of(ValueType.STRING, result.rows().get(0).get("code")).displayText());
        assertEquals("ENTERPRISE", TypedValue.of(ValueType.STRING, result.rows().get(1).get("code")).displayText());
        assertNull(TypedValue.of(ValueType.STRING, result.rows().get(2).get("code")).value());
        assertNull(TypedValue.of(ValueType.STRING, result.rows().get(3).get("code")).value());
    }

    @Test
    void unselectedFieldsAndEmptyResultsDoNotReadDictionaries() {
        var decorated = new ReportDictionaryDatasetProvider(provider(List.of(Map.of("id", "123"))), labels);
        decorated.query(context, new DatasetQuery(Map.of(), Set.of("id"), 1, 20, false, null, false));
        new ReportDictionaryDatasetProvider(provider(List.of()), labels).stream(context, query, row -> fail("空结果不应产生行"));
        verifyNoInteractions(labels);
    }

    @Test
    void deniedProviderCannotTriggerDictionaryReads() {
        var source = provider(List.of());
        doThrow(new DatasetAccessException("SCOPE_DENIED", "拒绝")).when(source).query(context, query);
        doThrow(new DatasetAccessException("SCOPE_DENIED", "拒绝")).when(source).stream(eq(context), eq(query), any());
        var decorated = new ReportDictionaryDatasetProvider(source, labels);
        assertThrows(DatasetAccessException.class, () -> decorated.query(context, query));
        assertThrows(DatasetAccessException.class, () -> decorated.stream(context, query, row -> fail("不可访问")));
        verifyNoInteractions(labels);
    }

    /** 构造固定字段的合成提供者，真实权限拒绝仍由被装饰提供者负责。 */
    @SuppressWarnings("unchecked")
    private ReportDatasetProvider provider(List<Map<String, Object>> rows) {
        var source = mock(ReportDatasetProvider.class);
        when(source.descriptor()).thenReturn(new DatasetDescriptor(
                "DICTIONARY_TEST", "字典回归", 1, "biz:list", false,
                List.of(new DatasetField("code", "类型", ValueType.STRING),
                        new DatasetField("hidden", "敏感类型", ValueType.STRING, "biz:hidden", List.of()),
                        new DatasetField("id", "标识", ValueType.ID),
                        new DatasetField("amount", "金额", ValueType.DECIMAL)),
                List.of(), List.of("code")));
        when(source.displayDictionaries()).thenReturn(Map.of("code", "bd_client_type", "hidden", "sensitive_type"));
        when(source.query(any(), any())).thenReturn(new DatasetResult(rows, rows.size(), Instant.EPOCH));
        when(source.executionIdentity()).thenReturn("fixed-v1");
        when(source.requiredPermissions()).thenReturn(Set.of("biz:list", "biz:header"));
        doAnswer(invocation -> {
            rows.forEach((Consumer<Map<String, Object>>) invocation.getArgument(2));
            return null;
        }).when(source).stream(any(), any(), any());
        return source;
    }
}
