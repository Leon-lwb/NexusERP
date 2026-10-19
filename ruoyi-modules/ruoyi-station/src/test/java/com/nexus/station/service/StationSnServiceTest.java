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
    @Mock private com.nexus.mes.trace.MesSerialReferenceService serialReferences;
    @InjectMocks
    private StationSnService service;

    private MockedStatic<TenantHelper> tenantHelperMock;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        lenient().when(serialReferences.resolve(anyString())).thenAnswer(call->{String code=call.getArgument(0);ProSn row=proSnMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<ProSn>lambdaQuery().eq(ProSn::getSnCode,code));return row==null?null:new com.nexus.mes.trace.MesSerialReferenceService.Resolved("000000",row.getIdentityId(),row.getSnId(),code,row.getSnCode(),row.getMaterialId(),row);});
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

    @Test
    void formalCodesBindActualMappedIdsAndPreserveLocalAuditText() {
        var parent=sn(1L,"LOCAL-P",null);var component=sn(2L,"LOCAL-C",null);parent.setIdentityId(101L);component.setIdentityId(102L);
        doReturn(new com.nexus.mes.trace.MesSerialReferenceService.Resolved("000000",101L,1L,"ERP-P","LOCAL-P",null,parent)).when(serialReferences).resolve("ERP-P");
        doReturn(new com.nexus.mes.trace.MesSerialReferenceService.Resolved("000000",102L,2L,"ERP-C","LOCAL-C",null,component)).when(serialReferences).resolve("ERP-C");
        when(proSnMapper.update(any(),any())).thenReturn(1);var dto=new StationSnDtos.BindDto();dto.setParentSnCode("ERP-P");dto.setComponentSnCodes(List.of("ERP-C"));
        assertEquals(1,service.bind(dto).get("boundQty"));ArgumentCaptor<com.nexus.station.domain.MesSnBindLog> log=ArgumentCaptor.forClass(com.nexus.station.domain.MesSnBindLog.class);verify(bindLogMapper).insert(log.capture());assertEquals(1L,log.getValue().getParentSnId());assertEquals(2L,log.getValue().getComponentSnId());assertEquals("LOCAL-P",log.getValue().getParentSnCode());assertEquals("LOCAL-C",log.getValue().getComponentSnCode());verify(proSnMapper,never()).selectOne(any());
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
        when(proSnMapper.update(any(), any())).thenReturn(1);

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
        // 日志父SN与 CAS 写入的目标父SN一致
        assertEquals(parent.getSnId(), logCaptor.getValue().getParentSnId());
    }

    /**
     * MES-STATION-01: CAS 影响 0 行(并发下组件已被他人绑定)必须抛异常, 且不写 BIND 日志、不累加 boundQty。
     * <p>修复前: rows 未校验 → 返回 boundQty=1 并写入一条与实际 parent_sn_id 矛盾的 BIND 日志。</p>
     */
    @Test
    void bind_casAffectsZeroRowsThrowsAndWritesNoLog() {
        ProSn parent = sn(1L, "SN-P", null);
        ProSn comp = sn(2L, "SN-C1", null); // 读到未绑定(TOCTOU), 但 CAS 时已被并发绑走
        when(proSnMapper.selectOne(any())).thenReturn(parent, comp);
        when(proSnMapper.update(any(), any())).thenReturn(0);

        StationSnDtos.BindDto dto = new StationSnDtos.BindDto();
        dto.setParentSnCode("SN-P");
        dto.setComponentSnCodes(List.of("SN-C1"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bind(dto));
        assertTrue(ex.getMessage().contains("已被并发绑定"), "应提示并发绑定冲突: " + ex.getMessage());
        verify(bindLogMapper, never()).insert(any(com.nexus.station.domain.MesSnBindLog.class));
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
        when(proSnMapper.update(any(), any())).thenReturn(1);

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

    /**
     * 换板重绑同样按 CAS 影响行数判定: 0 行说明新组件已被并发绑定, 必须失败且不留 REBIND 日志。
     */
    @Test
    void unbind_rebindCasAffectsZeroRowsThrowsAndWritesNoLog() {
        ProSn comp = sn(2L, "SN-OLD", 1L);
        comp.setParentSnCode("SN-P");
        ProSn parent = sn(1L, "SN-P", null);
        ProSn newComp = sn(5L, "SN-NEW", null);
        when(proSnMapper.selectOne(any())).thenReturn(comp, newComp);
        when(proSnMapper.selectById(1L)).thenReturn(parent);
        // 第一次(解绑)成功, 第二次(换板重绑 CAS)0 行
        when(proSnMapper.update(any(), any())).thenReturn(1, 0);

        StationSnDtos.UnbindDto dto = new StationSnDtos.UnbindDto();
        dto.setComponentSnCode("SN-OLD");
        dto.setNewComponentSnCode("SN-NEW");
        dto.setReason("换板");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.unbind(dto));
        assertTrue(ex.getMessage().contains("已被并发绑定"), "应提示并发绑定冲突: " + ex.getMessage());
        // 只写了解绑那一条 UNBIND 日志, 不得出现 REBIND
        ArgumentCaptor<com.nexus.station.domain.MesSnBindLog> logCaptor =
            ArgumentCaptor.forClass(com.nexus.station.domain.MesSnBindLog.class);
        verify(bindLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("UNBIND", logCaptor.getValue().getAction());
    }

    @Test
    void unbindConcurrentParentChangeCannotEraseRelationshipOrWriteAudit() {
        ProSn component=sn(2L,"SN-C",1L);component.setParentSnCode("SN-P");
        when(proSnMapper.selectOne(any())).thenReturn(component);
        when(proSnMapper.selectById(1L)).thenReturn(sn(1L,"SN-P",null));
        when(proSnMapper.update(any(),any())).thenReturn(0);
        var dto=new StationSnDtos.UnbindDto();dto.setComponentSnCode("SN-C");dto.setReason("wrong component");
        assertThrows(ServiceException.class,()->service.unbind(dto));
        verify(bindLogMapper,never()).insert(any(com.nexus.station.domain.MesSnBindLog.class));
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
