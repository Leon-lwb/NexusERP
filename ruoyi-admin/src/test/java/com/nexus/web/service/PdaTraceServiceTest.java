package com.nexus.web.service;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.vo.WmTraceResultVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.IWmTraceService;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.web.controller.AppPdaTraceController;
import com.nexus.web.domain.vo.PdaManufacturingTraceVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdaTraceServiceTest {
    @Mock WmSnMapper snMapper;
    @Mock WmBatchMapper batchMapper;
    @Mock BdMaterialMapper materialMapper;
    @Mock IWmTraceService warehouse;
    @Mock ILtLotService lots;
    @Mock PdaManufacturingTraceService manufacturing;
    @InjectMocks PdaTraceService service;
    private MockedStatic<StpUtil> permissions;
    private MockedStatic<TenantHelper> tenant;
    private final Set<String> granted = new HashSet<>();

    @BeforeAll
    static void tables() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "pda-trace");
        for (Class<?> type : List.of(WmSn.class, WmBatch.class, BdMaterial.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach
    void context() {
        permissions = mockStatic(StpUtil.class);
        permissions.when(() -> StpUtil.hasPermission(anyString()))
            .thenAnswer(call -> granted.contains(call.getArgument(0)));
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn("000007");
    }

    @AfterEach
    void clear() {
        permissions.close();
        tenant.close();
    }

    @Test
    void manufacturingPermissionCanEnterTraceEndpoint() throws Exception {
        var check = AppPdaTraceController.class.getMethod("trace", String.class)
            .getAnnotation(SaCheckPermission.class);
        assertEquals(SaMode.OR, check.mode());
        assertTrue(List.of(check.value()).contains("mes:pro:trace:query"));
        assertTrue(List.of(check.value()).containsAll(List.of("wm:trace:query", "mes:lt:lot:trace")));
    }

    @Test
    void warehousePermissionDoesNotReadMesLots() {
        granted.add("wm:trace:query");
        service.trace("NO-HIT");
        verifyNoInteractions(lots, manufacturing);
    }

    @Test
    void lotPermissionDoesNotReadWarehouseDataAndKeepsLotContract() {
        granted.add("mes:lt:lot:trace");
        var lot = new LtLotVo();
        lot.setLotId(90L);
        lot.setLotCode("LOT-90");
        var chain = Map.<String, Object>of("lot", lot, "woIds", List.of(7L), "snIds", List.of(8L));
        when(lots.selectByCode("LOT-90")).thenReturn(lot);
        when(lots.trace(90L)).thenReturn(chain);

        var result = service.trace("LOT-90");

        assertEquals("LOT-90", result.getResolved().getLot().getLotCode());
        assertEquals(chain, result.getMes());
        verifyNoInteractions(snMapper, batchMapper, materialMapper, warehouse, manufacturing);
    }

    @Test
    void noSectionPermissionsNeverReadsBusinessData() {
        var result = service.trace("NO-HIT");
        assertNull(result.getMes());
        verifyNoInteractions(snMapper, batchMapper, materialMapper, warehouse, lots, manufacturing);
    }

    @Test
    void missingTenantFailsClosedBeforeAnyBusinessRead() {
        granted.add("wm:trace:query");
        tenant.when(TenantHelper::getTenantId).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.trace("SN-OTHER-TENANT"));
        verifyNoInteractions(snMapper, batchMapper, materialMapper, warehouse, lots, manufacturing);
    }

    @Test
    void warehouseSnKeepsSnOverBatchOverMaterialPrecedence() {
        granted.addAll(List.of("wm:trace:query", "mes:lt:lot:trace"));
        var sn = new WmSn();
        sn.setSnId(9007199254740993L);
        sn.setSnCode("SHARED-CODE");
        sn.setMaterialId(11L);
        sn.setBatchCode("B-SN");
        var batch = new WmBatch();
        batch.setBatchCode("SHARED-CODE");
        batch.setMaterialId(22L);
        var material = new BdMaterial();
        material.setMaterialId(33L);
        when(snMapper.selectOne(any())).thenReturn(sn);
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(materialMapper.selectOne(any())).thenReturn(material);
        var backward = new WmTraceResultVo();
        var forward = new WmTraceResultVo();
        when(warehouse.traceBackward(11L, "B-SN", "SHARED-CODE")).thenReturn(backward);
        when(warehouse.traceForward(11L, "B-SN", "SHARED-CODE")).thenReturn(forward);

        var result = service.trace("SHARED-CODE");

        assertEquals(9007199254740993L, result.getResolved().getSn().getSnId());
        assertNotNull(result.getResolved().getBatch());
        assertNotNull(result.getResolved().getMaterial());
        assertSame(backward, result.getBackward());
        assertSame(forward, result.getForward());
        verify(warehouse).traceBackward(11L, "B-SN", "SHARED-CODE");
        verify(warehouse).traceForward(11L, "B-SN", "SHARED-CODE");
    }

    @Test
    void missingKeywordHasNoInventedTraceSections() {
        granted.addAll(List.of("wm:trace:query", "mes:lt:lot:trace"));
        var result = service.trace("NO-HIT");
        assertNull(result.getResolved().getSn());
        assertNull(result.getResolved().getBatch());
        assertNull(result.getResolved().getMaterial());
        assertNull(result.getResolved().getLot());
        assertNull(result.getMes());
        assertNull(result.getBackward());
        assertNull(result.getForward());
        verifyNoInteractions(warehouse);
    }
    @Test
    void manufacturingOnlyPermissionResolvesProSnWithoutAnyWarehouseOrLotRead() {
        granted.add("mes:pro:trace:query");
        var trace = manufacturingTrace();
        when(manufacturing.trace("SN-MFG-001")).thenReturn(trace);

        var result = service.trace("SN-MFG-001");

        assertNotNull(result.getMes());
        assertSame(trace, result.getMes().get("manufacturing"));
        assertSame(trace.getSn(), result.getResolved().getManufacturingSn());
        assertNull(result.getResolved().getSn());
        verifyNoInteractions(snMapper, batchMapper, materialMapper, warehouse, lots);
    }

    @Test
    void warehouseManufacturingAndLotCollisionKeepsEveryAuthorizedSection() {
        granted.addAll(List.of("wm:trace:query", "mes:lt:lot:trace", "mes:pro:trace:query"));
        var trace = manufacturingTrace();
        when(manufacturing.trace("SN-MFG-001")).thenReturn(trace);
        var warehouseSn = new WmSn();
        warehouseSn.setSnCode("SN-MFG-001");
        warehouseSn.setMaterialId(11L);
        warehouseSn.setBatchCode("B-WM");
        when(snMapper.selectOne(any())).thenReturn(warehouseSn);
        var lot = new LtLotVo();
        lot.setLotId(91L);
        lot.setLotCode("SN-MFG-001");
        when(lots.selectByCode("SN-MFG-001")).thenReturn(lot);
        // Immutable legacy result catches attempts to mutate the lot service's response.
        var legacy = Map.<String, Object>of("lot", lot, "woIds", List.of(7L), "snIds", List.of(8L));
        when(lots.trace(91L)).thenReturn(legacy);
        var backward = new WmTraceResultVo();
        var forward = new WmTraceResultVo();
        when(warehouse.traceBackward(11L, "B-WM", "SN-MFG-001")).thenReturn(backward);
        when(warehouse.traceForward(11L, "B-WM", "SN-MFG-001")).thenReturn(forward);

        var result = service.trace("SN-MFG-001");

        assertNotNull(result.getMes().get("manufacturing"));
        assertSame(trace, result.getMes().get("manufacturing"));
        assertSame(lot, result.getMes().get("lot"));
        assertEquals(List.of(7L), result.getMes().get("woIds"));
        assertEquals(List.of(8L), result.getMes().get("snIds"));
        assertEquals(3, legacy.size());
        assertNotNull(result.getResolved().getSn());
        assertSame(trace.getSn(), result.getResolved().getManufacturingSn());
        assertSame(backward, result.getBackward());
        assertSame(forward, result.getForward());
    }

    @Test
    void authorizedMissingManufacturingSnDoesNotFabricateMesOrWarehouseTrace() {
        granted.add("mes:pro:trace:query");
        var result = service.trace("MISSING-MFG");
        verify(manufacturing).trace("MISSING-MFG");
        assertNull(result.getResolved().getManufacturingSn());
        assertNull(result.getMes());
        assertNull(result.getBackward());
        assertNull(result.getForward());
        verifyNoInteractions(snMapper, batchMapper, materialMapper, warehouse, lots);
    }

    private PdaManufacturingTraceVo manufacturingTrace() {
        var sn = new ProSnVo();
        sn.setSnId(9007199254740993L);
        sn.setSnCode("SN-MFG-001");
        sn.setMesWoId(7L);
        sn.setWoNo("MES-WO-001");
        var result = new PdaManufacturingTraceVo();
        result.setSn(sn);
        return result;
    }

}
