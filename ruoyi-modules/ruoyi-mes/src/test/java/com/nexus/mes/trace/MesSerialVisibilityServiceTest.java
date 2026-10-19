package com.nexus.mes.trace;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MesSerialVisibilityServiceTest {
    ProSnMapper mapper;
    MesSerialVisibilityService service;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "visibility-test"),
                ProSn.class);
        mapper = mock(ProSnMapper.class);
        var context = spy(new MesTraceContext(mock(MesProgressTransactionGuard.class)));
        doReturn("000001").when(context).tenant();
        service = new MesSerialVisibilityService(mapper, context);
    }

    ProSn row(long identity, long local, String tenant, String deleted) {
        var sn = new ProSn();
        sn.setIdentityId(identity);
        sn.setSnId(local);
        sn.setTenantId(tenant);
        sn.setDelFlag(deleted);
        return sn;
    }

    @Test
    void returnsActualScopedLocalIdsAndIntersectsTenantIdentityAndDeletion() {
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(
                        List.of(
                                row(900, 7, "000001", "0"),
                                row(901, 8, "000002", "0"),
                                row(902, 9, "000001", "1"),
                                row(903, 10, "000001", "0")));
        assertEquals(Map.of(900L, 7L), service.localSnIds("000001", Set.of(900L, 901L, 902L)));
        assertEquals(Set.of(900L), service.visibleIdentities("000001", Set.of(900L, 901L, 902L)));
    }

    @Test
    void duplicateCanonicalIdentityMappingsAreRejectedInsteadOfChoosingTheFirstCodeMatch() {
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(row(900, 7, "000001", "0"), row(900, 8, "000001", "0")));
        assertThrows(RuntimeException.class, () -> service.localSnIds("000001", Set.of(900L)));
        assertThrows(
                RuntimeException.class, () -> service.visibleIdentities("000001", Set.of(900L)));
    }

    @Test
    void pagingUsesAnExplicitTenantScopeAndIncludesEveryMappedIdentity() {
        var requested = LongStream.rangeClosed(1, 1001).boxed().collect(Collectors.toSet());
        when(mapper.selectList(any(Wrapper.class)))
                .thenAnswer(
                        invocation -> {
                            Wrapper<ProSn> wrapper = invocation.getArgument(0);
                            assertTrue(wrapper.getSqlSegment().contains("tenant_id"));
                            assertTrue(wrapper.getSqlSegment().contains("identity_id"));
                            var values =
                                    ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<
                                                            ?, ?, ?>)
                                                    wrapper)
                                            .getParamNameValuePairs()
                                            .values();
                            assertTrue(values.contains("000001"));
                            return values.stream()
                                    .filter(Long.class::isInstance)
                                    .map(Long.class::cast)
                                    .map(id -> row(id, id + 10000, "000001", "0"))
                                    .toList();
                        });
        var result = service.localSnIds("000001", requested);
        assertEquals(1001, result.size());
        assertEquals(11001L, result.get(1001L));
        verify(mapper, times(3)).selectList(any(Wrapper.class));
    }

    @Test
    void wrongTenantCannotQueryTheMapper() {
        assertThrows(RuntimeException.class, () -> service.localSnIds("000002", Set.of(900L)));
        verifyNoInteractions(mapper);
    }
}
