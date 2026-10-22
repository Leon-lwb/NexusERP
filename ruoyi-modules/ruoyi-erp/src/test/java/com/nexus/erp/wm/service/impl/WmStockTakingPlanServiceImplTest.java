package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import com.nexus.erp.wm.domain.WmStockTakingPlan;
import com.nexus.erp.wm.domain.bo.WmStockTakingPlanBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmStockTakingLineMapper;
import com.nexus.erp.wm.mapper.WmStockTakingPlanMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 盘点「单据与作业」守卫 单元测试（审计 ERP-WM-02 / ERP-WM-10 回归）
 *
 * <p><b>ERP-WM-02（修复前会失败）</b>：{@code generateLines} 把 {@code actual_quantity} 初始化为 0，
 * {@code postAdjustment} 又不校验是否录入实盘数 —— 未盘点直接过账时 {@code diff = 0 - 账面量}，
 * 逐行把库存调整为 0，一次点击清零整仓（审批通过自动过账同理）。</p>
 * <p><b>ERP-WM-10</b>：对已过账计划重建明细会抹掉盘点结果，进而重复调整库存。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmStockTakingPlanServiceImplTest {

    @Mock
    private WmStockTakingPlanMapper baseMapper;
    @Mock
    private WmStockTakingLineMapper lineMapper;
    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private IWmStockOperationService stockOperationService;
    @Mock
    private WmServiceHelper wm;

    @InjectMocks
    private WmStockTakingPlanServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmStockTakingPlan.class);
        TableInfoHelper.initTableInfo(assistant, WmStockTakingLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    /** ERP-WM-02 核心回归：存在未录入实盘数的行时，过账必须被拒绝且库存不得变动 */
    @Test
    void postAdjustment_rejectsWhenActualQuantityNotEntered() {
        WmStockTakingPlan plan = new WmStockTakingPlan();
        plan.setPlanId(1L);
        plan.setStatus("GENERATED");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        when(baseMapper.update(any(), any())).thenReturn(1);

        WmStockTakingLine line = new WmStockTakingLine();
        line.setLineId(11L);
        line.setPlanId(1L);
        line.setMaterialCode("M-001");
        line.setBookQuantity(new BigDecimal("100"));
        // 未盘点：实盘数为 NULL（修复前 generateLines 会写 0）
        line.setActualQuantity(null);
        line.setStatus("PENDING");
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.postAdjustment(1L));

        assertEquals(true, ex.getMessage().contains("未录入实盘数"), "错误信息需说明原因: " + ex.getMessage());
        assertEquals(true, ex.getMessage().contains("M-001"), "错误信息需列出缺失行");
        // 关键断言：不得生成任何库存调整，也不得把行标记为已过账
        verify(stockOperationService, never()).postCountAdjust(any(), any(), any(), any(), any());
        verify(lineMapper, never()).updateById(any(WmStockTakingLine.class));
    }

    /** ERP-WM-02：generateLines 不得把实盘数默认写 0，必须留 NULL 表示"未录入" */
    @Test
    void generateLines_leavesActualQuantityNull() {
        WmStockTakingPlan plan = new WmStockTakingPlan();
        plan.setPlanId(2L);
        plan.setStatus("DRAFT");
        plan.setWarehouseId(5L);
        when(baseMapper.selectById(2L)).thenReturn(plan);

        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(9L);
        stock.setMaterialId(100L);
        stock.setMaterialCode("M-002");
        stock.setStockQuantity(new BigDecimal("30"));
        when(stockMapper.selectList(any())).thenReturn(List.of(stock));

        service.generateLines(2L);

        ArgumentCaptor<WmStockTakingLine> captor = ArgumentCaptor.forClass(WmStockTakingLine.class);
        verify(lineMapper).insert(captor.capture());
        assertNull(captor.getValue().getActualQuantity(),
            "ERP-WM-02: 生成盘点行时实盘数必须为 NULL（未录入），不得默认写 0");
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(0, new BigDecimal("30").compareTo(captor.getValue().getBookQuantity()));
    }

    /** ERP-WM-10：已过账计划不得再重建盘点明细（否则差异记录被抹掉后可重复调整库存） */
    @Test
    void generateLines_rejectsAlreadyPostedPlan() {
        WmStockTakingPlan plan = new WmStockTakingPlan();
        plan.setPlanId(3L);
        plan.setStatus("POSTED");
        when(baseMapper.selectById(3L)).thenReturn(plan);

        assertThrows(ServiceException.class, () -> service.generateLines(3L));

        verify(lineMapper, never()).delete(any());
        verify(lineMapper, never()).insert(any(WmStockTakingLine.class));
    }

    /** ERP-WM-10：已过账计划不得删除 */
    @Test
    void deleteByIds_rejectsPostedPlan() {
        WmStockTakingPlan existing = new WmStockTakingPlan();
        existing.setPlanId(4L);
        existing.setStatus("POSTED");
        when(baseMapper.selectById(4L)).thenReturn(existing);

        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{4L}));

        verify(lineMapper, never()).delete(any());
        verify(baseMapper, never()).deleteBatchIds(any());
    }

    /** ERP-WM-11：审批中(APPROVING)不得走手工过账通道（须由审批通过回调处理） */
    @Test
    void postAdjustment_rejectsApprovingPlan() {
        WmStockTakingPlan plan = new WmStockTakingPlan();
        plan.setPlanId(6L);
        plan.setStatus("APPROVING");
        when(baseMapper.selectById(6L)).thenReturn(plan);
        // 服务端在尝试状态抢占之前即拒绝审批中的计划。

        assertThrows(RuntimeException.class, () -> service.postAdjustment(6L));

        verify(lineMapper, never()).selectList(any());
        verify(stockOperationService, never()).postCountAdjust(any(), any(), any(), any(), any());
    }

    /** 对照用例：实盘数已录入时正常过账（差异写回行） */
    @Test
    void postAdjustment_appliesDifferenceWhenAllLinesCounted() {
        WmStockTakingPlan plan = new WmStockTakingPlan();
        plan.setPlanId(7L);
        plan.setStatus("GENERATED");
        when(baseMapper.selectById(7L)).thenReturn(plan);
        when(baseMapper.update(any(), any())).thenReturn(1);

        WmStockTakingLine line = new WmStockTakingLine();
        line.setLineId(71L);
        line.setPlanId(7L);
        line.setMaterialCode("M-007");
        line.setBookQuantity(new BigDecimal("100"));
        line.setActualQuantity(new BigDecimal("92"));
        line.setStatus("PENDING");
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        service.postAdjustment(7L);

        verify(stockOperationService).postCountAdjust(any(), any(), any(), any(), any());
        assertEquals("POSTED", line.getStatus());
        assertEquals(0, new BigDecimal("-8").compareTo(line.getDifferenceQuantity()));
    }

    /** 显式忽略：本测试类不校验 insertByBo 的单号生成（WmServiceHelper 为 mock） */
    @Test
    void insertByBo_forcesDraftStatus() {
        lenient().when(baseMapper.insert(any(WmStockTakingPlan.class))).thenReturn(1);
        WmStockTakingPlanBo bo = new WmStockTakingPlanBo();
        bo.setStatus("POSTED");
        bo.setPlanCode("PT-001");

        service.insertByBo(bo);

        ArgumentCaptor<WmStockTakingPlan> captor = ArgumentCaptor.forClass(WmStockTakingPlan.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus(), "ERP-WM-10: status 不得由 BO 直传");
    }
}
