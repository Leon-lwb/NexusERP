package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.WmWarehouseCodeCorrectionBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.WmWarehouseCodeCorrectionService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ERP 仓库编码全量纠正服务测试。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class WmWarehouseCodeCorrectionServiceTest {

    @Mock
    private WmWarehouseMapper warehouseMapper;
    @Mock
    private WmMaterialStockMapper materialStockMapper;
    @Mock
    private WmMaterialStockTxMapper materialStockTxMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @InjectMocks
    private WmWarehouseCodeCorrectionService service;

    @Test
    void correctUpdatesWarehouseAndAllInventorySnapshots() {
        when(warehouseMapper.selectById(21L)).thenReturn(warehouse(21L, "WH-SP-01"));
        when(autoCodeUtil.genSerialCode("WAREHOUSE_CODE", null)).thenReturn("WH011");
        when(warehouseMapper.update(any(), any())).thenReturn(1);

        String code = service.correct(21L,
            request("WH-SP-01", "纠正未按系统自动编码规则生成的业务编码"));

        assertEquals("WH011", code);
        ArgumentCaptor<WmWarehouse> master = ArgumentCaptor.forClass(WmWarehouse.class);
        verify(warehouseMapper).update(master.capture(), any());
        assertEquals(code, master.getValue().getWarehouseCode());
        ArgumentCaptor<WmMaterialStock> stock = ArgumentCaptor.forClass(WmMaterialStock.class);
        verify(materialStockMapper).update(stock.capture(), any());
        assertEquals(code, stock.getValue().getWarehouseCode());
        ArgumentCaptor<WmMaterialStockTx> tx = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(materialStockTxMapper).update(tx.capture(), any());
        assertEquals(code, tx.getValue().getWarehouseCode());
    }

    @Test
    void correctRejectsStaleExpectedCodeBeforeTakingNumber() {
        when(warehouseMapper.selectById(21L)).thenReturn(warehouse(21L, "WH-SP-01"));

        ServiceException failure = assertThrows(ServiceException.class,
            () -> service.correct(21L, request("WH-SP-02", "纠正原因")));

        assertEquals("仓库编码已变化，请刷新后重新核对", failure.getMessage());
        verifyNoInteractions(autoCodeUtil, materialStockMapper, materialStockTxMapper);
        verify(warehouseMapper, never()).update(any(), any());
    }

    @Test
    void correctRejectsCasConflictWithoutChangingInventorySnapshots() {
        when(warehouseMapper.selectById(21L)).thenReturn(warehouse(21L, "WH-SP-01"));
        when(autoCodeUtil.genSerialCode("WAREHOUSE_CODE", null)).thenReturn("WH011");
        when(warehouseMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class,
            () -> service.correct(21L, request("WH-SP-01", "纠正原因")));

        verifyNoInteractions(materialStockMapper, materialStockTxMapper);
    }

    private WmWarehouse warehouse(Long id, String code) {
        WmWarehouse warehouse = new WmWarehouse();
        warehouse.setWarehouseId(id);
        warehouse.setWarehouseCode(code);
        return warehouse;
    }

    private WmWarehouseCodeCorrectionBo request(String expectedCode, String reason) {
        WmWarehouseCodeCorrectionBo bo = new WmWarehouseCodeCorrectionBo();
        bo.setExpectedCurrentCode(expectedCode);
        bo.setReason(reason);
        return bo;
    }
}
