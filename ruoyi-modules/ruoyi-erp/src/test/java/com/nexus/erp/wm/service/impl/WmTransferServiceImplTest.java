package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;
import com.nexus.erp.wm.mapper.WmTransferLineMapper;
import com.nexus.erp.wm.mapper.WmTransferMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调拨单「单据与作业」状态机 单元测试（审计 ERP-WM-07 / ERP-WM-08 / ERP-WM-14 回归）
 *
 * <p><b>ERP-WM-07（修复前会失败）</b>：{@code execute} 的抢占条件是 {@code ne(EXECUTED)}，
 * 即草稿/驳回单可直接过账 —— 绕过审批流程，也绕过 PDA 的「下架 → 扫目标库位」作业步骤。</p>
 * <p><b>ERP-WM-14</b>：目标仓库/目标库位为空时必须拒绝，否则目标库存行维度落 NULL。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmTransferServiceImplTest {

    @Mock
    private WmServiceHelper wm;
    @Mock
    private WmTransferMapper baseMapper;
    @Mock
    private WmTransferLineMapper lineMapper;
    @Mock
    private WmStockOperationServiceImpl stockOperationService;

    @InjectMocks
    private WmTransferServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmTransfer.class);
        TableInfoHelper.initTableInfo(assistant, WmTransferLine.class);
    }

    /** ERP-WM-07 核心回归：草稿调拨单不可执行（CAS 白名单只有 APPROVED / TAKEN_OFF） */
    @Test
    void execute_rejectsDraftTransfer() {
        WmTransfer draft = new WmTransfer();
        draft.setTransferId(1L);
        draft.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(draft);
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertThrows(RuntimeException.class, () -> service.execute(1L));

        verify(stockOperationService, never()).postTransfer(any());
        verify(baseMapper, never()).updateById(any(WmTransfer.class));
    }

    /** ERP-WM-07：已驳回（回到 DRAFT）同样不可执行 */
    @Test
    void execute_rejectsRejectedTransfer() {
        WmTransfer rejected = new WmTransfer();
        rejected.setTransferId(3L);
        rejected.setStatus("REJECTED");
        when(baseMapper.selectById(3L)).thenReturn(rejected);
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertThrows(RuntimeException.class, () -> service.execute(3L));

        verify(stockOperationService, never()).postTransfer(any());
    }

    /** ERP-WM-07 对照：已审批单可进入过账流程（此处因缺目标仓库在后续校验失败，证明已越过状态闸门） */
    @Test
    void execute_approvedTransferPassesStatusGate() {
        WmTransfer approved = new WmTransfer();
        approved.setTransferId(2L);
        approved.setStatus("APPROVED");
        when(baseMapper.selectById(2L)).thenReturn(approved);
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertThrows(ServiceException.class, () -> service.execute(2L));

        verify(stockOperationService, never()).postTransfer(any());
    }

    /** ERP-WM-14：目标仓库为空必须拒绝（否则目标库存行 warehouse_id = NULL） */
    @Test
    void execute_rejectsMissingTargetWarehouse() {
        WmTransfer approved = new WmTransfer();
        approved.setTransferId(4L);
        approved.setStatus("APPROVED");
        approved.setFromWarehouseId(5L);
        approved.setToWarehouseId(null);
        when(baseMapper.selectById(4L)).thenReturn(approved);
        when(baseMapper.update(any(), any())).thenReturn(1);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.execute(4L));

        assertEqualsContains(ex, "目标仓库");
        verify(stockOperationService, never()).postTransfer(any());
    }

    /** ERP-WM-14：目标库位为空必须拒绝（否则目标库存行 location_id = NULL，3D 库位视图看不到） */
    @Test
    void execute_rejectsMissingTargetLocation() {
        WmTransfer approved = new WmTransfer();
        approved.setTransferId(5L);
        approved.setStatus("APPROVED");
        approved.setFromWarehouseId(5L);
        approved.setToWarehouseId(6L);
        when(baseMapper.selectById(5L)).thenReturn(approved);
        when(baseMapper.update(any(), any())).thenReturn(1);

        WmTransferLine line = new WmTransferLine();
        line.setLineId(51L);
        line.setTransferId(5L);
        line.setMaterialName("M-001");
        line.setFromLocationId(11L);
        line.setToLocationId(null);
        when(lineMapper.selectList(any())).thenReturn(java.util.List.of(line));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.execute(5L));

        assertEqualsContains(ex, "目标库位");
        verify(stockOperationService, never()).postTransfer(any());
    }

    /** ERP-WM-08：已执行调拨单不可删除 */
    @Test
    void deleteByIds_rejectsExecutedTransfer() {
        WmTransfer executed = new WmTransfer();
        executed.setTransferId(6L);
        executed.setStatus("EXECUTED");
        when(baseMapper.selectById(6L)).thenReturn(executed);

        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{6L}));

        verify(lineMapper, never()).delete(any());
        verify(baseMapper, never()).deleteBatchIds(any());
    }

    private void assertEqualsContains(ServiceException ex, String fragment) {
        if (ex.getMessage() == null || !ex.getMessage().contains(fragment)) {
            throw new AssertionError("期望错误信息包含[" + fragment + "]，实际: " + ex.getMessage());
        }
    }
}
