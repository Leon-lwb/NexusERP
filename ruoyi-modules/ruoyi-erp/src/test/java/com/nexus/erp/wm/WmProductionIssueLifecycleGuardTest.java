package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.impl.WmProductionIssueServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmProductionIssueLifecycleGuardTest {
    @Mock WmProductionIssueMapper issueMapper;
    @Mock WmProductionIssueLineMapper lineMapper;
    @Mock WmMaterialStockMapper stockMapper;
    @Mock WmWarehouseMapper warehouseMapper;
    @Mock IWmStockOperationService stockOperationService;
    @Mock AutoCodeUtil autoCodeUtil;
    @Mock PrdWorkOrderMapper workOrderMapper;
    @Mock PrdMaterialRequirementMapper requirementMapper;
    @Mock PrdMaterialRequirementDetailMapper detailMapper;
    @InjectMocks WmProductionIssueServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    private WmProductionIssue issue(String status) {
        WmProductionIssue issue = new WmProductionIssue();
        issue.setIssueId(41L);
        issue.setIssueCode("LL202609290001");
        issue.setWoId(11L);
        issue.setStatus(status);
        issue.setTotalQty(new BigDecimal("9"));
        return issue;
    }

    @Test
    void completedAndPartiallyPostedIssuesCannotBeDeletedOrReopened() {
        for (String status : List.of("COMPLETED", "PARTIAL")) {
            reset(issueMapper, lineMapper);
            when(issueMapper.selectById(41L)).thenReturn(issue(status));
            WmProductionIssueBo bo = new WmProductionIssueBo();
            bo.setIssueId(41L);
            bo.setStatus("CREATED");
            assertThrows(ServiceException.class, () -> service.updateByBo(bo), status + " must not reopen");
            assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{41L}), status + " must not delete");
            verifyNoInteractions(lineMapper);
            verify(issueMapper, never()).deleteBatchIds(anyCollection());
        }
    }

    @Test
    void forgedStatusAndSourceCannotBeInjectedIntoCreatedIssue() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(41L);
        bo.setIssueCode("LL202609290001");
        bo.setStatus("COMPLETED");
        bo.setWoId(15L);
        when(issueMapper.selectById(41L)).thenReturn(issue("CREATED"));
        assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        verifyNoInteractions(lineMapper);
        verify(issueMapper, never()).updateById(any(WmProductionIssue.class));
    }

    @Test
    void clientSuppliedHeaderTotalsCannotOverrideRecalculationAfterEdit() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(41L);
        bo.setIssueCode("LL202609290001");
        bo.setWoId(11L);
        bo.setStatus("CREATED");
        bo.setTotalQty(new BigDecimal("9999"));
        bo.setTotalAmount(new BigDecimal("9999"));
        bo.setWmProductionIssueLineList(List.of());
        when(issueMapper.selectById(41L)).thenReturn(issue("CREATED"));
        when(issueMapper.update(any(WmProductionIssue.class), any(Wrapper.class))).thenReturn(1);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        /** 正控完整汇总与显式未计价写空均返回一行，失败路径另由事务反例覆盖。 */
        when(issueMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        when(issueMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.updateByBo(bo);
        ArgumentCaptor<WmProductionIssue> captured = ArgumentCaptor.forClass(WmProductionIssue.class);
        verify(issueMapper, atLeastOnce()).updateById(captured.capture());
        assertEquals(BigDecimal.ZERO, captured.getAllValues().get(captured.getAllValues().size()-1).getTotalQty(),
            "last persisted header update must be the server-side recalculation");
    }

    @Test
    void postedLineCannotChangeMaterialOrRequirementIdentity() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(41L);
        bo.setStatus("PARTIAL");
        bo.setWoId(11L);
        when(issueMapper.selectById(41L)).thenReturn(issue("PARTIAL"));
        when(issueMapper.update(any(WmProductionIssue.class), any(Wrapper.class))).thenReturn(1);
        WmProductionIssueLine stored = existingLine();
        WmProductionIssueLine requested = existingLine();
        requested.setMaterialId(77L);
        requested.setCurrentQty(BigDecimal.ONE);
        bo.setWmProductionIssueLineList(List.of(requested));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
        assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        verify(lineMapper, never()).updateById(any(WmProductionIssueLine.class));
        verify(lineMapper, never()).insert(any(WmProductionIssueLine.class));
    }

    @Test
    void unknownLineIdMustNotBeReclassifiedAsNewLineAndDeleteOriginal() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(41L);
        bo.setStatus("CREATED");
        when(issueMapper.selectById(41L)).thenReturn(issue("CREATED"));
        when(issueMapper.update(any(WmProductionIssue.class), any(Wrapper.class))).thenReturn(1);
        WmProductionIssueLine stored = existingLine();
        stored.setIssuedQty(BigDecimal.ZERO);
        WmProductionIssueLine requested = existingLine();
        requested.setLineId(999L);
        bo.setWmProductionIssueLineList(List.of(requested));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
        assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        verify(lineMapper, never()).insert(any(WmProductionIssueLine.class));
        verify(lineMapper, never()).deleteById(anyLong());
    }

    @Test
    void partiallyIssuedRowMaySelectNextBatchWithoutChangingOriginalLedgerSource() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(41L);
        bo.setWoId(11L);
        bo.setStatus("PARTIAL");
        when(issueMapper.selectById(41L)).thenReturn(issue("PARTIAL"));
        when(issueMapper.update(any(WmProductionIssue.class), any(Wrapper.class))).thenReturn(1);
        WmProductionIssueLine stored = existingLine();
        WmProductionIssueLine requested = existingLine();
        requested.setBatchId(88L);
        requested.setCurrentQty(BigDecimal.ONE);
        requested.setIssuedQty(new BigDecimal("99"));
        bo.setWmProductionIssueLineList(List.of(requested));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
        /** 正控完整汇总与显式未计价写空均返回一行，失败路径另由事务反例覆盖。 */
        when(issueMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        when(issueMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.updateByBo(bo);
        ArgumentCaptor<WmProductionIssueLine> row = ArgumentCaptor.forClass(WmProductionIssueLine.class);
        verify(lineMapper).updateById(row.capture());
        assertEquals(42L, row.getValue().getLineId());
        assertEquals(BigDecimal.ONE, row.getValue().getIssuedQty());
        assertEquals(88L, row.getValue().getBatchId());
        assertEquals(55L, row.getValue().getRequirementDetailId());
        verifyNoInteractions(stockOperationService);
    }

    @Test
    void newIssueCannotInjectCompletedStatusOrIssuedQuantity() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueCode("LL202609290002");
        bo.setStatus("COMPLETED");
        bo.setWmProductionIssueLineList(List.of(existingLine()));
        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        verifyNoInteractions(issueMapper, lineMapper);
    }

    private WmProductionIssueLine existingLine() {
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setLineId(42L);
        line.setIssueId(41L);
        line.setMaterialId(66L);
        line.setBaseUomId("2");
        line.setRequirementDetailId(55L);
        line.setRequiredQty(new BigDecimal("3"));
        line.setIssuedQty(BigDecimal.ONE);
        line.setShortQty(new BigDecimal("2"));
        line.setLineStatus("PARTIAL");
        return line;
    }

    @Test
    void postingMustNotOverwriteRecalculatedTotalsWithPreviouslyLoadedHeader() {
        WmProductionIssue record = issue("CREATED");
        record.setWarehouseId(7L);
        WmWarehouse warehouse = new WmWarehouse();
        warehouse.setWarehouseId(7L);
        warehouse.setStatus(1L);
        warehouse.setWarehouseName("原料仓");
        when(warehouseMapper.selectById(7L)).thenReturn(warehouse);
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setIssueId(41L);
        line.setLineId(42L);
        line.setMaterialId(66L);
        line.setMaterialName("钢板");
        line.setRequiredQty(new BigDecimal("2"));
        line.setIssuedQty(BigDecimal.ZERO);
        line.setCurrentQty(BigDecimal.ONE);
        line.setLineStatus("PENDING");
        when(issueMapper.selectById(41L)).thenReturn(record);
        when(issueMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line));
        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(66L);
        stock.setWarehouseId(7L);
        stock.setStockQuantity(BigDecimal.TEN);
        stock.setReservedQuantity(BigDecimal.ZERO);
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stock));
        /** 正控明细与单头必须持久成功，不能把默认零行当作过账成功。 */
        when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenReturn(1);
        when(issueMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        service.execute(41L);
        ArgumentCaptor<WmProductionIssue> updates = ArgumentCaptor.forClass(WmProductionIssue.class);
        verify(issueMapper, atLeastOnce()).updateById(updates.capture());
        List<WmProductionIssue> values = updates.getAllValues();
        assertEquals(BigDecimal.ONE, values.get(values.size()-1).getTotalQty(),
            "last persisted header update must be the newly issued quantity, not the stale pre-post value 9");
        assertEquals("PARTIAL", record.getStatus());
        verify(stockOperationService).postProductionIssue(same(line), any());
    }
}
