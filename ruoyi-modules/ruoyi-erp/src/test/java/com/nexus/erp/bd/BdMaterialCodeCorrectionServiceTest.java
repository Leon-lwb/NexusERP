package com.nexus.erp.bd;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdMaterialCodeCorrectionBo;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.BdMaterialCodeCorrectionService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
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
 * ERP 物料编码全量纠正服务测试。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class BdMaterialCodeCorrectionServiceTest {

    @Mock
    private BdMaterialMapper materialMapper;
    @Mock
    private WmMaterialStockMapper materialStockMapper;
    @Mock
    private WmMaterialStockTxMapper materialStockTxMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @InjectMocks
    private BdMaterialCodeCorrectionService service;

    @Test
    void correctUpdatesMaterialAndAllInventorySnapshots() {
        when(materialMapper.selectById(11L)).thenReturn(material(11L, "SP-BRG-6204"));
        when(autoCodeUtil.genSerialCode("MATERIAL_CODE", null)).thenReturn("IF20261010001");
        when(materialMapper.update(any(), any())).thenReturn(1);

        String code = service.correct(11L,
            request("SP-BRG-6204", "纠正未按系统自动编码规则生成的业务编码"));

        assertEquals("IF20261010001", code);
        ArgumentCaptor<BdMaterial> master = ArgumentCaptor.forClass(BdMaterial.class);
        verify(materialMapper).update(master.capture(), any());
        assertEquals(code, master.getValue().getMaterialCode());
        ArgumentCaptor<WmMaterialStock> stock = ArgumentCaptor.forClass(WmMaterialStock.class);
        verify(materialStockMapper).update(stock.capture(), any());
        assertEquals(code, stock.getValue().getMaterialCode());
        ArgumentCaptor<WmMaterialStockTx> tx = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(materialStockTxMapper).update(tx.capture(), any());
        assertEquals(code, tx.getValue().getMaterialCode());
    }

    @Test
    void correctRejectsStaleExpectedCodeBeforeTakingNumber() {
        when(materialMapper.selectById(11L)).thenReturn(material(11L, "SP-BRG-6204"));

        ServiceException failure = assertThrows(ServiceException.class,
            () -> service.correct(11L, request("SP-BRG-6205", "纠正原因")));

        assertEquals("物料编码已变化，请刷新后重新核对", failure.getMessage());
        verifyNoInteractions(autoCodeUtil, materialStockMapper, materialStockTxMapper);
        verify(materialMapper, never()).update(any(), any());
    }

    @Test
    void correctRejectsCasConflictWithoutChangingInventorySnapshots() {
        when(materialMapper.selectById(11L)).thenReturn(material(11L, "SP-BRG-6204"));
        when(autoCodeUtil.genSerialCode("MATERIAL_CODE", null)).thenReturn("IF20261010001");
        when(materialMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class,
            () -> service.correct(11L, request("SP-BRG-6204", "纠正原因")));

        verifyNoInteractions(materialStockMapper, materialStockTxMapper);
    }

    private BdMaterial material(Long id, String code) {
        BdMaterial material = new BdMaterial();
        material.setMaterialId(id);
        material.setMaterialCode(code);
        return material;
    }

    private BdMaterialCodeCorrectionBo request(String expectedCode, String reason) {
        BdMaterialCodeCorrectionBo bo = new BdMaterialCodeCorrectionBo();
        bo.setExpectedCurrentCode(expectedCode);
        bo.setReason(reason);
        return bo;
    }
}
