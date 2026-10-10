package com.nexus.mes.eq.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.bo.EqSpareCodeCorrectionBo;
import com.nexus.mes.eq.mapper.EqRepairSpareMapper;
import com.nexus.mes.eq.mapper.EqSpareErpSyncMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareReturnLineMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

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

        String code;
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            code = service.correct(9L,
                request("SP-BRG-6204", "纠正未按系统自动编码规则生成的业务编码"));
        }

        assertEquals("SP20261010001", code);
        verify(sparePartMapper).update(any(EqSparePart.class), any());
        verify(stockLogMapper).updateSpareCodeBySpareId("000000", 9L, code);
        verify(erpSyncMapper).updateSpareCodeBySpareId("000000", 9L, code);
        verify(repairSpareMapper).updateSpareCodeBySpareId("000000", 9L, code);
        verify(returnLineMapper).updateSpareCodeBySpareId("000000", 9L, code);
    }

    @Test
    void synchronizeSnapshotsUsesCurrentCodeWithoutTakingAnotherNumber() {
        when(sparePartMapper.selectById(9L)).thenReturn(spare(9L, "SP20261010001"));

        String code;
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            code = service.synchronizeSnapshots(9L,
                request("SP20261010001", "补齐逻辑删除历史行的编码快照"));
        }

        assertEquals("SP20261010001", code);
        verifyNoInteractions(autoCodeUtil);
        verify(stockLogMapper).updateSpareCodeBySpareId("000000", 9L, code);
        verify(erpSyncMapper).updateSpareCodeBySpareId("000000", 9L, code);
        verify(repairSpareMapper).updateSpareCodeBySpareId("000000", 9L, code);
        verify(returnLineMapper).updateSpareCodeBySpareId("000000", 9L, code);
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
