package com.nexus.erp.wm.trace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.SerialVisibilityPort;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.*;

class ScopedMaterialCodeResolverTest {
    @Test
    void mesOnlyRoleSeesOnlyMappedAuthorizedIdentitiesRatherThanTheFirstMatch() {
        var core = mock(ErpCodeResolverService.class);
        var documents = mock(ErpDocumentCodeResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SerialVisibilityPort> provider = mock(ObjectProvider.class);
        var visibility = mock(SerialVisibilityPort.class);
        when(provider.getIfAvailable()).thenReturn(visibility);
        var first =
                new CodeCandidate(CodeObjectType.SN, 1L, null, "HIDDEN", 4L, "M", null, null, null);
        var second =
                new CodeCandidate(
                        CodeObjectType.SN, 2L, null, "VISIBLE", 4L, "M", null, null, null);
        when(core.resolve(anyString(), any(), anySet()))
                .thenReturn(CodeResolution.ambiguous(List.of(first, second)));
        when(visibility.visibleIdentities("000001", Set.of(1L, 2L))).thenReturn(Set.of(2L));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            auth.when(() -> StpUtil.hasPermission("mes:pro:sn:query")).thenReturn(true);
            var request =
                    new CodeResolveRequest("ALIAS", "QUERY", null, null, null, null, null, null);
            var result =
                    new ScopedMaterialCodeResolver(
                                    core,
                                    documents,
                                    provider,
                                    new StructuredLabelResolver(new ObjectMapper()))
                            .resolve("000001", request, Set.of(CodeObjectType.SN));
            assertEquals(ResolveStatus.RESOLVED, result.status());
            assertEquals(2L, result.identityId());
            assertEquals(1, result.candidates().size());
        }
    }

    @Test
    void identicalDocumentCodeAndFormalSnRemainAmbiguous() {
        var core = mock(ErpCodeResolverService.class);
        var documents = mock(ErpDocumentCodeResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SerialVisibilityPort> provider = mock(ObjectProvider.class);
        when(core.resolve(anyString(), any(), anySet()))
                .thenReturn(
                        CodeResolution.resolved(
                                new CodeCandidate(
                                        CodeObjectType.SN,
                                        1L,
                                        null,
                                        "SAME",
                                        4L,
                                        "M",
                                        null,
                                        null,
                                        null)));
        var document =
                new CodeCandidate(
                        CodeObjectType.DOCUMENT,
                        9L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "WM_MATERIAL_RECEIVE",
                        "SAME");
        when(documents.resolve(anyString(), any())).thenReturn(List.of(document));
        try (var auth = mockStatic(StpUtil.class)) {
            var result =
                    new ScopedMaterialCodeResolver(
                                    core,
                                    documents,
                                    provider,
                                    new StructuredLabelResolver(new ObjectMapper()))
                            .resolve(
                                    "000001",
                                    new CodeResolveRequest(
                                            "SAME", "QUERY", null, null, null, null, null, null),
                                    Set.of(CodeObjectType.SN, CodeObjectType.DOCUMENT));
            assertEquals(ResolveStatus.AMBIGUOUS, result.status());
            assertNull(result.identityId());
            assertEquals("WM_MATERIAL_RECEIVE", result.candidates().get(1).documentType());
        }
    }

    @Test
    void aRegisteredJsonCarrierKeepsItsDeclaredUnitAsThePrimaryObject() {
        var core = mock(ErpCodeResolverService.class);
        var documents = mock(ErpDocumentCodeResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SerialVisibilityPort> provider = mock(ObjectProvider.class);
        String raw = "{\"materialCode\":\"M4\"}";
        var unit =
                new CodeCandidate(CodeObjectType.SN, 1L, null, "SN1", 4L, "M4", null, null, null);
        when(core.resolve(anyString(), any(), anySet()))
                .thenAnswer(
                        invocation -> {
                            var request = invocation.<CodeResolveRequest>getArgument(1);
                            return request.rawCode().equals(raw)
                                    ? CodeResolution.resolved(unit)
                                    : CodeResolution.resolved(
                                            new CodeCandidate(
                                                    CodeObjectType.MATERIAL,
                                                    null,
                                                    null,
                                                    null,
                                                    4L,
                                                    "M4",
                                                    null,
                                                    null,
                                                    null));
                        });
        try (var auth = mockStatic(StpUtil.class)) {
            var result =
                    new ScopedMaterialCodeResolver(
                                    core,
                                    documents,
                                    provider,
                                    new StructuredLabelResolver(new ObjectMapper()))
                            .resolve(
                                    "000001",
                                    new CodeResolveRequest(
                                            raw, "QUERY", null, null, null, null, null, null),
                                    Set.of(CodeObjectType.SN, CodeObjectType.MATERIAL));
            assertEquals(CodeObjectType.SN, result.objectType());
            assertEquals(1L, result.identityId());
        }
    }

    @Test
    void registeredCarrierAndParsedSerialAssertionsCannotIdentifyDifferentUnits() {
        var core = mock(ErpCodeResolverService.class);
        var documents = mock(ErpDocumentCodeResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SerialVisibilityPort> provider = mock(ObjectProvider.class);
        String raw = "{\"snCode\":\"SN2\"}";
        when(core.resolve(anyString(), any(), anySet()))
                .thenAnswer(
                        invocation -> {
                            var request = invocation.<CodeResolveRequest>getArgument(1);
                            return CodeResolution.resolved(
                                    new CodeCandidate(
                                            CodeObjectType.SN,
                                            request.rawCode().equals(raw) ? 1L : 2L,
                                            null,
                                            request.rawCode().equals(raw) ? "SN1" : "SN2",
                                            4L,
                                            "M4",
                                            null,
                                            null,
                                            null));
                        });
        try (var auth = mockStatic(StpUtil.class)) {
            var result =
                    new ScopedMaterialCodeResolver(
                                    core,
                                    documents,
                                    provider,
                                    new StructuredLabelResolver(new ObjectMapper()))
                            .resolve(
                                    "000001",
                                    new CodeResolveRequest(
                                            raw, "QUERY", null, null, null, null, null, null),
                                    Set.of(CodeObjectType.SN));
            assertEquals(ResolveStatus.INVALID, result.status());
        }
    }
}
