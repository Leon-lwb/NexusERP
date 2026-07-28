package com.nexus.mes.ncr.service.impl;

import com.nexus.mes.ncr.domain.NcrReworkOrder;
import com.nexus.mes.ncr.mapper.NcrReworkOrderMapper;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NCR 返工工单 Service 单元测试
 * <p>覆盖 Bug2 回归: deriveReworkOrder 用 ProWorkOrderMapper(原始实体) 而非 proWorkOrderService.selectById(富集查 bd_ 跨库)。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class NcrReworkOrderServiceImplTest {

    @Mock private NcrReworkOrderMapper baseMapper;
    @Mock private IProWorkOrderService proWorkOrderService;
    @Mock private ProWorkOrderMapper proWorkOrderMapper;
    @Mock private AutoCodeUtil autoCodeUtil;
    @InjectMocks private NcrReworkOrderServiceImpl service;

    @Captor private ArgumentCaptor<ProWorkOrderBo> reworkBoCaptor;
    @Captor private ArgumentCaptor<NcrReworkOrder> reworkOrderCaptor;

    @Test
    void deriveReworkOrder_usesMapperNotServiceAndSetsSourceTypeRework() {
        // 原工单(Mapper 查原始实体, 不走 selectById 富集)
        ProWorkOrder originalWo = new ProWorkOrder();
        originalWo.setMesWoId(10L);
        originalWo.setWoNo("WO-001");
        originalWo.setMaterialId(1001L);
        originalWo.setMaterialCode("M1001");
        originalWo.setMaterialName("测试物料");
        originalWo.setRoutingId(1L);
        originalWo.setWorkshopId(5L);
        originalWo.setBomId(2L);
        originalWo.setUnitId(3L);
        when(proWorkOrderMapper.selectById(10L)).thenReturn(originalWo);

        when(autoCodeUtil.genSerialCode("MES_REWORK_CODE", null)).thenReturn("RW20260728001");
        // insertByBo 回写 reworkWoId 到 BO
        when(proWorkOrderService.insertByBo(any())).thenAnswer(inv -> {
            ((ProWorkOrderBo) inv.getArgument(0)).setMesWoId(777L);
            return 1;
        });

        service.deriveReworkOrder(1L, 10L, new BigDecimal("5"), "E2E返工");

        // Bug2 回归: 必须用 proWorkOrderMapper.selectById(原始实体), 不能用 proWorkOrderService.selectById(富集查bd_)
        verify(proWorkOrderMapper).selectById(10L);
        verify(proWorkOrderService, never()).selectById(anyLong());

        // 返工工单 BO: sourceType=REWORK, plannedQty=reworkQty
        verify(proWorkOrderService).insertByBo(reworkBoCaptor.capture());
        ProWorkOrderBo reworkBo = reworkBoCaptor.getValue();
        assertEquals("REWORK", reworkBo.getSourceType(), "返工工单 sourceType 必须为 REWORK (漏设则 completeWo 守卫失效)");
        assertEquals(new BigDecimal("5"), reworkBo.getPlannedQty());
        assertEquals("RW20260728001", reworkBo.getWoNo());
        assertEquals(1001L, reworkBo.getMaterialId());
        assertEquals(1L, reworkBo.getRoutingId());

        // 返工关联记录: reworkWoId 取自 insertByBo 回写(非 selectList 查回)
        verify(baseMapper).insert(reworkOrderCaptor.capture());
        NcrReworkOrder ro = reworkOrderCaptor.getValue();
        assertEquals(777L, ro.getReworkWoId(), "reworkWoId 应取自 insertByBo 回写 (Bug2: 避免 selectList 富集跨库)");
        assertEquals(1L, ro.getNcrId());
        assertEquals(10L, ro.getOriginalWoId());
        assertEquals("OPEN", ro.getStatus());
        verify(proWorkOrderService, never()).selectList(any());
    }

    @Test
    void deriveReworkOrder_throwsWhenOriginalWoNotFound() {
        when(proWorkOrderMapper.selectById(99L)).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class,
            () -> service.deriveReworkOrder(1L, 99L, BigDecimal.ONE, "x"));
        assertTrue(e.getMessage().contains("原工单不存在"));
    }
}
