package com.nexus.mes.eq.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqRepairSpare;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.EqSpareReturnLine;
import com.nexus.mes.eq.domain.EqSpareStockLog;
import com.nexus.mes.eq.domain.bo.EqSpareCodeCorrectionBo;
import com.nexus.mes.eq.mapper.EqRepairSpareMapper;
import com.nexus.mes.eq.mapper.EqSpareErpSyncMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareReturnLineMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
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
 * MES 备件编码全量纠正服务测试。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class EqSpareCodeCorrectionServiceTest {

    @Mock
    private EqSparePartMapper sparePartMapper;
    @Mock
    private EqSpareStockLogMapper stockLogMapper;
    @Mock
    private EqSpareErpSyncMapper erpSyncMapper;
    @Mock
    private EqRepairSpareMapper repairSpareMapper;
    @Mock
    private EqSpareReturnLineMapper returnLineMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @InjectMocks
    private EqSpareCodeCorrectionService service;

    @Test
    void correctUpdatesMasterAndEverySpareCodeSnapshot() {
        when(sparePartMapper.selectById(9L)).thenReturn(spare(9L, "SP-BRG-6204"));
        when(autoCodeUtil.genSerialCode("MES_SPARE_CODE", null)).thenReturn("SP20261010001");
        when(sparePartMapper.update(any(), any())).thenReturn(1);

        String code = service.correct(9L,
            request("SP-BRG-6204", "纠正未按系统自动编码规则生成的业务编码"));

        assertEquals("SP20261010001", code);
        ArgumentCaptor<EqSparePart> master = ArgumentCaptor.forClass(EqSparePart.class);
        verify(sparePartMapper).update(master.capture(), any());
        assertEquals(code, master.getValue().getSpareCode());
        verifySnapshotCode(stockLogMapper, code);
        verifySnapshotCode(erpSyncMapper, code);
        verifySnapshotCode(repairSpareMapper, code);
        verifySnapshotCode(returnLineMapper, code);
    }

    @Test
    void correctRejectsStaleExpectedCodeBeforeTakingNumber() {
        when(sparePartMapper.selectById(9L)).thenReturn(spare(9L, "SP-BRG-6204"));

        ServiceException failure = assertThrows(ServiceException.class,
            () -> service.correct(9L, request("SP-BRG-6205", "纠正原因")));

        assertEquals("备件编码已变化，请刷新后重新核对", failure.getMessage());
        verifyNoInteractions(autoCodeUtil, stockLogMapper, erpSyncMapper, repairSpareMapper, returnLineMapper);
        verify(sparePartMapper, never()).update(any(), any());
    }

    @Test
    void correctRejectsCasConflictWithoutChangingSnapshots() {
        when(sparePartMapper.selectById(9L)).thenReturn(spare(9L, "SP-BRG-6204"));
        when(autoCodeUtil.genSerialCode("MES_SPARE_CODE", null)).thenReturn("SP20261010001");
        when(sparePartMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class,
            () -> service.correct(9L, request("SP-BRG-6204", "纠正原因")));

        verifyNoInteractions(stockLogMapper, erpSyncMapper, repairSpareMapper, returnLineMapper);
    }

    private void verifySnapshotCode(EqSpareStockLogMapper mapper, String code) {
        ArgumentCaptor<EqSpareStockLog> captor = ArgumentCaptor.forClass(EqSpareStockLog.class);
        verify(mapper).update(captor.capture(), any());
        assertEquals(code, captor.getValue().getSpareCode());
    }

    private void verifySnapshotCode(EqSpareErpSyncMapper mapper, String code) {
        ArgumentCaptor<EqSpareErpSync> captor = ArgumentCaptor.forClass(EqSpareErpSync.class);
        verify(mapper).update(captor.capture(), any());
        assertEquals(code, captor.getValue().getSpareCode());
    }

    private void verifySnapshotCode(EqRepairSpareMapper mapper, String code) {
        ArgumentCaptor<EqRepairSpare> captor = ArgumentCaptor.forClass(EqRepairSpare.class);
        verify(mapper).update(captor.capture(), any());
        assertEquals(code, captor.getValue().getSpareCode());
    }

    private void verifySnapshotCode(EqSpareReturnLineMapper mapper, String code) {
        ArgumentCaptor<EqSpareReturnLine> captor = ArgumentCaptor.forClass(EqSpareReturnLine.class);
        verify(mapper).update(captor.capture(), any());
        assertEquals(code, captor.getValue().getSpareCode());
    }

    private EqSparePart spare(Long id, String code) {
        EqSparePart spare = new EqSparePart();
        spare.setSpareId(id);
        spare.setSpareCode(code);
        return spare;
    }

    private EqSpareCodeCorrectionBo request(String expectedCode, String reason) {
        EqSpareCodeCorrectionBo bo = new EqSpareCodeCorrectionBo();
        bo.setExpectedCurrentCode(expectedCode);
        bo.setReason(reason);
        return bo;
    }
}
