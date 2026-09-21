package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.EqRepairSpare;
import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.mapper.EqRepairSpareMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.service.IEqSparePartService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 维修领用备件明细单测（MES-EQ-08）。
 * <p>
 * 核心断言（修复前必失败）：维修单已完工/已作废后，领用明细不可删除、库存不可回滚
 * （此前 deleteByIds 无状态守卫，可事后抹除已发生的备件消耗）。
 */
@Tag("local")
@Tag("dev")
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

    @BeforeAll
    static void initTableInfo() {
        // deleteByIds 使用 Wrappers.<EqRepairSpare>lambdaQuery()，需要真实列缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqRepairSpare.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqRepair.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqRepairSpare.class);
        TableInfoHelper.remove(EqRepair.class);
    }

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

    // ==================== MES-EQ-08: 删除守卫 ====================

    @Test
    void deleteByIdsRejectsCompletedRepairBeforeRollingBackStock() {
        EqRepairSpare detail = detail(100L, 1L, 2L);
        when(repairSpareMapper.selectList(any())).thenReturn(List.of(detail));
        EqRepair repair = new EqRepair();
        repair.setRepairId(1L);
        repair.setStatus("COMPLETED");
        when(repairMapper.selectById(1L)).thenReturn(repair);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.deleteByIds(new Long[]{100L}));

        assertTrue(exception.getMessage().contains("无法删除领用明细"));
        // 先校验后动库存：库存回滚与物理删除都不得发生
        verifyNoInteractions(sparePartService);
        verify(repairSpareMapper, never()).deleteByIds(anyList());
    }

    @Test
    void deleteByIdsRejectsCancelledRepair() {
        EqRepairSpare detail = detail(100L, 1L, 2L);
        when(repairSpareMapper.selectList(any())).thenReturn(List.of(detail));
        EqRepair repair = new EqRepair();
        repair.setRepairId(1L);
        repair.setStatus("CANCELLED");
        when(repairMapper.selectById(1L)).thenReturn(repair);

        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{100L}));
        verifyNoInteractions(sparePartService);
    }

    @Test
    void deleteByIdsRollsBackStockWhenRepairStillRepairing() {
        EqRepairSpare detail = detail(100L, 1L, 2L);
        when(repairSpareMapper.selectList(any())).thenReturn(List.of(detail));
        EqRepair repair = new EqRepair();
        repair.setRepairId(1L);
        repair.setStatus("REPAIRING");
        when(repairMapper.selectById(1L)).thenReturn(repair);
        when(repairSpareMapper.deleteByIds(anyList())).thenReturn(1);

        assertTrue(service.deleteByIds(new Long[]{100L}));

        // 维修中仍允许撤销领用：必须回滚库存并物理删除
        verify(sparePartService).stockTransaction(any());
        verify(repairSpareMapper).deleteByIds(anyList());
    }

    private EqRepairSpare detail(Long id, Long repairId, Long spareId) {
        EqRepairSpare detail = new EqRepairSpare();
        detail.setId(id);
        detail.setRepairId(repairId);
        detail.setSpareId(spareId);
        detail.setQuantity(BigDecimal.ONE);
        return detail;
    }

    private EqRepairSpareBo request(int quantity) {
        EqRepairSpareBo bo = new EqRepairSpareBo();
        bo.setRepairId(1L);
        bo.setSpareId(2L);
        bo.setQuantity(BigDecimal.valueOf(quantity));
        return bo;
    }
}
