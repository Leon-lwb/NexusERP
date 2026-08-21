package com.nexus.station.service;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.station.domain.dto.StationSnDtos;
import com.nexus.station.mapper.StationSnBindLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * StationSnService 单元测试 — 关键件绑定/解绑/防环(C5/C6)。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StationSnServiceTest {

    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private StationSnBindLogMapper bindLogMapper;
    @InjectMocks
    private StationSnService service;

    private MockedStatic<TenantHelper> tenantHelperMock;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        tenantHelperMock = mockStatic(TenantHelper.class);
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> snColumns = new HashMap<>();
        snColumns.put("SNID", new ColumnCache("sn_id", "sn_id"));
        snColumns.put("SNCODE", new ColumnCache("sn_code", "sn_code"));
        snColumns.put("PARENTSNID", new ColumnCache("parent_sn_id", "parent_sn_id"));
        snColumns.put("PARENTSNCODE", new ColumnCache("parent_sn_code", "parent_sn_code"));
        snColumns.put("SNSTATUS", new ColumnCache("sn_status", "sn_status"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(ProSn.class)).thenReturn(snColumns);
    }

    @AfterEach
    void tearDown() {
        if (tenantHelperMock != null) tenantHelperMock.close();
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    @Test
    void bind_componentsAttachedToParent() {
        ProSn parent = sn(1L, "SN-P", null);
        ProSn comp = sn(2L, "SN-C1", null);
        when(proSnMapper.selectOne(any())).thenReturn(parent, comp);

        StationSnDtos.BindDto dto = new StationSnDtos.BindDto();
        dto.setParentSnCode("SN-P");
        dto.setComponentSnCodes(List.of("SN-C1"));

        Map<String, Object> data = service.bind(dto);

        assertEquals(1, data.get("boundQty"));
        // 组件挂到父SN(条件含 isNull(parent_sn_id) 防并发重复绑)
        verify(proSnMapper).update(any(), any());
        ArgumentCaptor<com.nexus.station.domain.MesSnBindLog> logCaptor =
            ArgumentCaptor.forClass(com.nexus.station.domain.MesSnBindLog.class);
        verify(bindLogMapper).insert(logCaptor.capture());
        assertEquals("BIND", logCaptor.getValue().getAction());
        assertEquals("SN-P", logCaptor.getValue().getParentSnCode());
    }

    @Test
    void bind_rejectsAlreadyBoundComponent() {
        ProSn parent = sn(1L, "SN-P", null);
        ProSn comp = sn(2L, "SN-C1", 1L); // 已绑定
        comp.setParentSnCode("SN-OTHER");
        when(proSnMapper.selectOne(any())).thenReturn(parent, comp);

        StationSnDtos.BindDto dto = new StationSnDtos.BindDto();
        dto.setParentSnCode("SN-P");
        dto.setComponentSnCodes(List.of("SN-C1"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bind(dto));
        assertTrue(ex.getMessage().contains("禁止重复绑定"));
        verify(proSnMapper, never()).update(any(), any());
    }

    @Test
    void bind_rejectsCycle_ancestorChainHitsComponent() {
        // 链: SN-ROOT ← SN-MID ← SN-P(待绑父); 把 SN-ROOT 作为组件绑到 SN-P 下会成环
        ProSn root = sn(1L, "SN-ROOT", null);
        ProSn mid = sn(2L, "SN-MID", 1L);
        mid.setParentSnCode("SN-ROOT");
        ProSn parent = sn(3L, "SN-P", 2L);
        parent.setParentSnCode("SN-MID");
        when(proSnMapper.selectOne(any())).thenReturn(parent, root);
        when(proSnMapper.selectById(2L)).thenReturn(mid);
        when(proSnMapper.selectById(1L)).thenReturn(root);

        StationSnDtos.BindDto dto = new StationSnDtos.BindDto();
        dto.setParentSnCode("SN-P");
        dto.setComponentSnCodes(List.of("SN-ROOT"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bind(dto));
        assertTrue(ex.getMessage().contains("防环校验未通过"));
        verify(proSnMapper, never()).update(any(), any());
    }

    @Test
    void bind_rejectsScrappedSn() {
        ProSn parent = sn(1L, "SN-P", null);
        parent.setSnStatus("SCRAPPED");
        when(proSnMapper.selectOne(any())).thenReturn(parent);

        StationSnDtos.BindDto dto = new StationSnDtos.BindDto();
        dto.setParentSnCode("SN-P");
        dto.setComponentSnCodes(List.of("SN-C1"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bind(dto));
        assertTrue(ex.getMessage().contains("禁止绑定操作"));
    }

    @Test
    void unbind_withNewComponent_rebindsInOneStep() {
        ProSn comp = sn(2L, "SN-OLD", 1L);
        comp.setParentSnCode("SN-P");
        ProSn parent = sn(1L, "SN-P", null);
        ProSn newComp = sn(5L, "SN-NEW", null);
        when(proSnMapper.selectOne(any())).thenReturn(comp, newComp);
        when(proSnMapper.selectById(1L)).thenReturn(parent);

        StationSnDtos.UnbindDto dto = new StationSnDtos.UnbindDto();
        dto.setComponentSnCode("SN-OLD");
        dto.setNewComponentSnCode("SN-NEW");
        dto.setReason("换板");

        Map<String, Object> data = service.unbind(dto);

        assertTrue(data.isEmpty());
        // 解绑 + 重绑两次 update, UNBIND + REBIND 两条留痕
        verify(proSnMapper, times(2)).update(any(), any());
        ArgumentCaptor<com.nexus.station.domain.MesSnBindLog> logCaptor =
            ArgumentCaptor.forClass(com.nexus.station.domain.MesSnBindLog.class);
        verify(bindLogMapper, times(2)).insert(logCaptor.capture());
        List<com.nexus.station.domain.MesSnBindLog> logs = logCaptor.getAllValues();
        assertEquals("UNBIND", logs.get(0).getAction());
        assertEquals("REBIND", logs.get(1).getAction());
        assertEquals("SN-NEW", logs.get(1).getComponentSnCode());
        assertEquals("SN-OLD", logs.get(1).getNewComponentSnCode());
    }

    @Test
    void unbind_requiresReason() {
        StationSnDtos.UnbindDto dto = new StationSnDtos.UnbindDto();
        dto.setComponentSnCode("SN-OLD");

        assertThrows(ServiceException.class, () -> service.unbind(dto));
        verify(proSnMapper, never()).update(any(), any());
    }

    @Test
    void unbind_rejectsUnboundComponent() {
        ProSn comp = sn(2L, "SN-FREE", null);
        when(proSnMapper.selectOne(any())).thenReturn(comp);

        StationSnDtos.UnbindDto dto = new StationSnDtos.UnbindDto();
        dto.setComponentSnCode("SN-FREE");
        dto.setReason("误操作");

        assertThrows(ServiceException.class, () -> service.unbind(dto));
    }

    private ProSn sn(Long id, String code, Long parentId) {
        ProSn sn = new ProSn();
        sn.setSnId(id);
        sn.setSnCode(code);
        sn.setParentSnId(parentId);
        sn.setSnStatus("IN_PRODUCTION");
        return sn;
    }
}
