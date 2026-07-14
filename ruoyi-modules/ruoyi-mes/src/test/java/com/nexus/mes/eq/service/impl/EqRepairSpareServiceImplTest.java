package com.nexus.mes.eq.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.mapper.EqRepairSpareMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.service.IEqSparePartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EqRepairSpareServiceImplTest {

    @Mock
    private EqRepairSpareMapper repairSpareMapper;
    @Mock
    private EqSparePartMapper sparePartMapper;
    @Mock
    private IEqSparePartService sparePartService;
    @Mock
    private EqRepairMapper repairMapper;
    @InjectMocks
    private EqRepairSpareServiceImpl service;

    @Test
    void batchAddRejectsNonPositiveQuantityBeforeChangingStock() {
        EqRepairSpareBo bo = request(-1);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.batchAdd(List.of(bo)));

        assertTrue(exception.getMessage().contains("数量必须大于0"));
        verifyNoInteractions(repairMapper, sparePartMapper, sparePartService, repairSpareMapper);
    }

    @Test
    void batchAddRejectsMissingRepairBeforeChangingStock() {
        EqRepairSpareBo bo = request(1);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.batchAdd(List.of(bo)));

        assertTrue(exception.getMessage().contains("维修单不存在"));
        verifyNoInteractions(sparePartMapper, sparePartService, repairSpareMapper);
    }

    @Test
    void batchAddRejectsRepairOutsideRepairingState() {
        EqRepairSpareBo bo = request(1);
        EqRepair repair = new EqRepair();
        repair.setStatus("COMPLETED");
        org.mockito.Mockito.when(repairMapper.selectById(bo.getRepairId())).thenReturn(repair);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.batchAdd(List.of(bo)));

        assertTrue(exception.getMessage().contains("维修中"));
        verifyNoInteractions(sparePartMapper, sparePartService, repairSpareMapper);
    }

    private EqRepairSpareBo request(int quantity) {
        EqRepairSpareBo bo = new EqRepairSpareBo();
        bo.setRepairId(1L);
        bo.setSpareId(2L);
        bo.setQuantity(BigDecimal.valueOf(quantity));
        return bo;
    }
}
