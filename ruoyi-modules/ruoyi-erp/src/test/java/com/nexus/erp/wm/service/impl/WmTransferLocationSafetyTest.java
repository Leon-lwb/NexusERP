package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmTransferBo;
import com.nexus.erp.wm.domain.vo.WmTransferVo;
import com.nexus.erp.wm.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WmTransferLocationSafetyTest {
    @Mock WmServiceHelper wm;
    @Mock WmTransferMapper baseMapper;
    @Mock WmTransferLineMapper lineMapper;
    @Mock WmStockOperationServiceImpl stockOperationService;
    @Mock WmStorageLocationMapper locationMapper;
    @Mock WmWarehouseMapper warehouseMapper;
    @Mock WmStorageAreaMapper areaMapper;
    @InjectMocks WmTransferServiceImpl service;

    @BeforeAll static void tables() {
        var a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : List.of(WmTransfer.class, WmTransferLine.class, WmStorageLocation.class, WmWarehouse.class, WmStorageArea.class)) TableInfoHelper.initTableInfo(a, c);
    }

    @Test void codeOnlyPcFormResolvesTrustedLocationIdsBeforeSaving() {
        configureMasterData();
        WmTransferBo bo = draft();
        WmTransferLine line = bo.getWmTransferLineList().get(0);
        when(baseMapper.insert(any(WmTransfer.class))).thenAnswer(i -> { ((WmTransfer)i.getArgument(0)).setTransferId(11L); return 1; });
        service.insertByBo(bo);
        ArgumentCaptor<WmTransferLine> inserted = ArgumentCaptor.forClass(WmTransferLine.class);
        verify(lineMapper).insert(inserted.capture());
        assertEquals(1L, inserted.getValue().getFromLocationId(), "PC输入的来源库位编码必须解析为实际ID");
        assertEquals(20L, inserted.getValue().getToLocationId(), "PC输入的目标库位编码必须解析为实际ID");
        assertEquals("法兰库位", line.getToLocationName());
    }

    @Test void wrongWarehouseLocationCannotReachStockPosting() {
        WmTransfer head = new WmTransfer(); head.setTransferId(11L); head.setStatus("APPROVED"); head.setFromWarehouseId(1L); head.setToWarehouseId(2L);
        WmTransferLine line = draft().getWmTransferLineList().get(0); line.setFromLocationId(1L); line.setToLocationId(2L);
        when(baseMapper.selectById(11L)).thenReturn(head); when(baseMapper.update(any(), any())).thenReturn(1); when(lineMapper.selectList(any())).thenReturn(List.of(line));
        lenient().when(warehouseMapper.selectById(any())).thenAnswer(i -> warehouse(((Number)i.getArgument(0)).longValue()));
        lenient().when(locationMapper.selectById(any())).thenAnswer(i -> location(((Number)i.getArgument(0)).longValue(), 1L));
        assertThrows(ServiceException.class, () -> service.execute(11L), "原料仓库位不能挂到半成品仓");
        verifyNoInteractions(stockOperationService);
    }

    @Test void missingOrNegativeQuantityCannotBeSaved() {
        WmTransferBo bo = draft(); bo.getWmTransferLineList().get(0).setQuantity(BigDecimal.ZERO);
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        verify(baseMapper, never()).insert(any(WmTransfer.class));
        verifyNoInteractions(lineMapper);
    }

    @Test void detailProjectionKeepsWarehouseAndAreaIdentityForEditing() {
        WmTransfer entity = new WmTransfer(); entity.setFromWarehouseId(1L); entity.setFromWarehouseCode("WH001"); entity.setToWarehouseId(2L); entity.setToWarehouseCode("WH002"); entity.setFromAreaId(1L); entity.setToAreaId(5L);
        var json = new ObjectMapper().valueToTree(MapstructUtils.convert(entity, WmTransferVo.class));
        assertEquals(1L, json.path("fromWarehouseId").asLong());
        assertEquals(2L, json.path("toWarehouseId").asLong());
        assertEquals("WH002", json.path("toWarehouseCode").asText());
        assertEquals(5L, json.path("toAreaId").asLong());
    }

    private void configureMasterData() {
        lenient().when(warehouseMapper.selectById(any())).thenAnswer(i -> warehouse(((Number)i.getArgument(0)).longValue()));
        lenient().when(locationMapper.selectOne(any())).thenAnswer(i -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)i.getArgument(0);
            query.getSqlSegment();
            String values = query.getParamNameValuePairs().values().toString();
            return values.contains("L020") ? location(20L, 2L) : location(1L, 1L);
        });
        lenient().when(areaMapper.selectById(any())).thenAnswer(i -> { WmStorageArea a = new WmStorageArea(); a.setAreaId(((Number)i.getArgument(0)).longValue()); a.setAreaCode("A0005"); a.setAreaName("机加工半成品区"); return a; });
    }
    private WmWarehouse warehouse(Long id) { WmWarehouse w = new WmWarehouse(); w.setWarehouseId(id); w.setWarehouseCode("WH00" + id); w.setWarehouseName(id == 1L ? "原料仓库" : "半成品仓库"); w.setStatus(1L); return w; }
    private WmStorageLocation location(Long id, Long warehouse) { WmStorageLocation l = new WmStorageLocation(); l.setLocationId(id); l.setLocationCode(id == 20L ? "L020" : "L001"); l.setLocationName(id == 20L ? "法兰库位" : "钢板库位"); l.setWarehouseId(warehouse); l.setAreaId(warehouse == 2L ? 5L : 1L); l.setEnableFlag(1L); l.setFrozenFlag(0L); return l; }
    private WmTransferBo draft() { WmTransferBo b = new WmTransferBo(); b.setTransferNo("TR202610210001"); b.setFromWarehouseId(1L); b.setToWarehouseId(2L); WmTransferLine l = new WmTransferLine(); l.setMaterialId(23L); l.setFromLocationCode("L001"); l.setToLocationCode("L020"); l.setQuantity(new BigDecimal("4")); b.setWmTransferLineList(List.of(l)); return b; }
}
