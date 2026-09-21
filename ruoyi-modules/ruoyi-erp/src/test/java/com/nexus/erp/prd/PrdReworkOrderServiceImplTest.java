package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdReworkOrder;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdReworkOrderMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.impl.PrdReworkOrderServiceImpl;
import com.nexus.erp.wm.service.IWmStockOperationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 返工单状态机单元测试（ERP-PRD-04 出入库必须真正推进状态 / ERP-PRD-05 状态流转改 CAS）。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdReworkOrderServiceImplTest {

    @Mock
    private PrdReworkOrderMapper baseMapper;
    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private IWmStockOperationService stockOperationService;

    @InjectMocks
    private PrdReworkOrderServiceImpl reworkOrderService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PrdReworkOrder.class);
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
    }

    /** ERP-PRD-04：出库消息必须把 DRAFT 推进到 IN_PROGRESS（此前只 log.info） */
    @Test
    void advanceByStockOperation_issue_advancesToInProgress() {
        when(baseMapper.selectById(1L)).thenReturn(rework(1L, "DRAFT", new BigDecimal("10")));
        when(baseMapper.update(any(), any())).thenReturn(1);

        reworkOrderService.advanceByStockOperation(1L, "ISSUE", null);

        verify(baseMapper).update(any(), any());
    }

    /** ERP-PRD-04：出库消息重复投递（已是 IN_PROGRESS）→ 幂等，不重复更新 */
    @Test
    void advanceByStockOperation_issueRepeated_idempotent() {
        when(baseMapper.selectById(1L)).thenReturn(rework(1L, "IN_PROGRESS", new BigDecimal("10")));

        reworkOrderService.advanceByStockOperation(1L, "ISSUE", null);

        verify(baseMapper, never()).update(any(), any());
    }

    /** ERP-PRD-04/05：入库数量未达返工数量 → 保持进行中，不置 COMPLETED */
    @Test
    void advanceByStockOperation_receivePartial_keepsInProgress() {
        when(baseMapper.selectById(1L)).thenReturn(rework(1L, "IN_PROGRESS", new BigDecimal("10")));

        reworkOrderService.advanceByStockOperation(1L, "RECEIVE", new BigDecimal("4"));

        verify(baseMapper, never()).update(any(), any());
    }

    /** ERP-PRD-04/05：入库数量达量 → 条件更新置 COMPLETED */
    @Test
    void advanceByStockOperation_receiveFull_completes() {
        when(baseMapper.selectById(1L)).thenReturn(rework(1L, "IN_PROGRESS", new BigDecimal("10")));
        when(baseMapper.update(any(), any())).thenReturn(1);

        reworkOrderService.advanceByStockOperation(1L, "RECEIVE", new BigDecimal("10"));

        verify(baseMapper).update(any(), any());
    }

    /** ERP-PRD-05：start 用 CAS，抢不到时报可读异常 */
    @Test
    void start_casConflict_throws() {
        when(baseMapper.selectById(1L)).thenReturn(rework(1L, "DRAFT", new BigDecimal("10")));
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> reworkOrderService.start(1L));
        assertTrue(ex.getMessage().contains("并发"), ex.getMessage());
    }

    /** ERP-PRD-05：complete 抢到状态后原子累加来源工单报废量 */
    @Test
    void complete_casWon_accumulatesSourceWoScrap() {
        PrdReworkOrder order = rework(1L, "IN_PROGRESS", new BigDecimal("10"));
        order.setSourceWoId(77L);
        order.setScrapQty(new BigDecimal("2"));
        when(baseMapper.selectById(1L)).thenReturn(order);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(workOrderMapper.update(any(), any())).thenReturn(1);

        reworkOrderService.complete(1L);

        verify(workOrderMapper).update(any(), any());
    }

    /** ERP-PRD-05：complete CAS 失败 → 不回写来源工单 */
    @Test
    void complete_casConflict_noWorkOrderWrite() {
        PrdReworkOrder order = rework(1L, "IN_PROGRESS", new BigDecimal("10"));
        order.setSourceWoId(77L);
        order.setScrapQty(new BigDecimal("2"));
        when(baseMapper.selectById(1L)).thenReturn(order);
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class, () -> reworkOrderService.complete(1L));
        verify(workOrderMapper, never()).update(any(), any());
    }

    private PrdReworkOrder rework(Long reworkId, String status, BigDecimal reworkQty) {
        PrdReworkOrder order = new PrdReworkOrder();
        order.setReworkId(reworkId);
        order.setReworkNo("RW001");
        order.setStatus(status);
        order.setReworkQty(reworkQty);
        return order;
    }
}
