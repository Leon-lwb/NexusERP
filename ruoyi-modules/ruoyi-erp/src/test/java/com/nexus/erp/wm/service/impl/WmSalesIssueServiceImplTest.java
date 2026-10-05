package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.domain.WmSalesIssueLine;
import com.nexus.erp.wm.domain.bo.WmSalesIssueBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueLineMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueMapper;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.service.SalesIssueSourceGuard;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.common.mq.producer.ReliableMessageProducer;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售出库「单据与作业」守卫 单元测试（审计 ERP-WM-01 / ERP-WM-09 / ERP-WM-16 回归）
 *
 * <p><b>ERP-WM-01（修复前会失败）</b>：{@code completeSalesIssue} 是销售出库的唯一活路径，
 * 但从不调用 {@code IInventoryValuationService.issueStockCost}（带结转的
 * {@code postSalesIssue}/{@code SalesOutStrategy} 全仓无调用方）→ 库存数量减了金额没减，
 * 出库成本恒为 0、库存金额虚高。修复后流水必须带非 0 单位成本与金额。</p>
 * <p><b>ERP-WM-09 / ERP-WM-16</b>：出库单已执行后不得改/删；确认出库必须来自已审批状态。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmSalesIssueServiceImplTest {

    @Mock
    private WmSalesIssueMapper baseMapper;
    @Mock
    private WmSalesIssueLineMapper lineMapper;
    @Mock
    private WmMaterialStockMapper wmMaterialStockMapper;
    @Mock
    private WmMaterialStockTxMapper stockTxMapper;
    @Mock
    private WmServiceHelper wm;
    @Mock
    private ISoFulfillmentWritebackService fulfillmentWritebackService;
    @Mock
    private ReliableMessageProducer messageProducer;
    @Mock
    private BdMaterialMapper bdMaterialMapper;
    @Mock
    private SoSalesOrderLineMapper salesOrderLineMapper;
    @Mock
    private IInventoryValuationService valuationService;

    @Mock
    private SalesIssueSourceGuard sourceGuard;
    @Mock
    private WmBatchMapper batchMapper;

    @InjectMocks
    private WmSalesIssueServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmSalesIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmSalesIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStockTx.class);
        TableInfoHelper.initTableInfo(assistant, SoSalesOrderLine.class);
    }

    /**
     * ERP-WM-01 核心回归：整批出库（扣减后余量为 0）必须结转成本，流水 unit_cost / tx_amount 非 0。
     * <p>修复前：{@code valuationService.issueStockCost} 从未被调用，流水成本为 null/0。</p>
     */
    @Test
    void completeSalesIssue_carriesOutStockCostOnFullBatchIssue() {
        WmSalesIssue issue = new WmSalesIssue();
        issue.setIssueId(1L);
        issue.setIssueCode("SI-001");
        issue.setStatus("CONFIRMED");
        issue.setWarehouseId(5L);
        issue.setTenantId("000000");
        issue.setDelFlag("0");
        when(baseMapper.selectById(1L)).thenReturn(issue);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(sourceGuard.validateAndLock(any(), any())).thenReturn(Map.of());
        when(stockTxMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);

        WmSalesIssueLine line = new WmSalesIssueLine();
        line.setLineId(21L);
        line.setIssueId(1L);
        line.setTenantId("000000");
        line.setDelFlag("0");
        line.setMaterialId(100L);
        line.setMaterialName("M-001");
        line.setQuantityIssue(new BigDecimal("10"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        // 库存恰好等于出库量（整批出库，扣减后余量 0）
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(9L);
        stock.setTenantId("000000");
        stock.setMaterialId(100L);
        stock.setWarehouseId(5L);
        stock.setStockQuantity(new BigDecimal("10"));
        stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setVersion(0);
        when(wmMaterialStockMapper.selectList(any())).thenReturn(List.of(stock));
        when(wmMaterialStockMapper.deductStockAvailable(anyLong(), any(), any())).thenReturn(1);
        when(valuationService.issueStockCost(eq(9L), any(BigDecimal.class))).thenReturn(new BigDecimal("7.5"));
        when(baseMapper.updateById(any(WmSalesIssue.class))).thenReturn(1);

        service.completeSalesIssue(1L);

        // 关键断言 1：成本结转被调用（修复前为 0 次）
        verify(valuationService).issueStockCost(eq(9L), any(BigDecimal.class));

        // 关键断言 2：SALES_OUT 流水带非 0 成本，金额 = 单位成本 × 数量
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(captor.capture());
        WmMaterialStockTx tx = captor.getValue();
        assertEquals("SALES_OUT", tx.getTxType());
        assertNotNull(tx.getUnitCost(), "ERP-WM-01: 出库流水必须带单位成本（修复前恒为 null）");
        assertEquals(0, new BigDecimal("7.5").compareTo(tx.getUnitCost()));
        assertNotNull(tx.getTxAmount(), "ERP-WM-01: 出库流水必须带出库金额");
        assertEquals(0, new BigDecimal("75").compareTo(tx.getTxAmount()));
        assertEquals(0, new BigDecimal("10").compareTo(tx.getTxQuantity()));
    }

    /** ERP-WM-09：已完成的出库单不得删除（否则流水成为孤儿、库存差异无凭据） */
    @Test
    void deleteByIds_rejectsCompletedIssue() {
        WmSalesIssue existing = new WmSalesIssue();
        existing.setIssueId(1L);
        existing.setStatus("COMPLETED");
        when(baseMapper.selectById(1L)).thenReturn(existing);

        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{1L}));

        verify(lineMapper, never()).delete(any());
        verify(baseMapper, never()).deleteBatchIds(any());
    }

    /** ERP-WM-09：已完成的出库单不得修改明细（改数量/删行会让单据与库存流水对不上） */
    @Test
    void updateByBo_rejectsCompletedIssue() {
        WmSalesIssue existing = new WmSalesIssue();
        existing.setIssueId(1L);
        existing.setStatus("COMPLETED");
        lenient().when(baseMapper.selectById(any())).thenReturn(existing);

        WmSalesIssueBo bo = new WmSalesIssueBo();
        bo.setIssueId(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        assertEquals(true, ex.getMessage().contains("不允许修改"), "错误信息: " + ex.getMessage());
        verify(lineMapper, never()).delete(any());
    }

    /** ERP-WM-09：status 不得由 BO 直传，新增一律强制草稿 */
    @Test
    void insertByBo_forcesDraftStatus() {
        WmSalesIssueBo bo = new WmSalesIssueBo();
        bo.setIssueCode("SI-002");
        bo.setStatus("COMPLETED");

        service.insertByBo(bo);

        ArgumentCaptor<WmSalesIssue> captor = ArgumentCaptor.forClass(WmSalesIssue.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus(), "ERP-WM-09: 不得直造 COMPLETED");
    }

    /** ERP-WM-16：草稿单不得绕过审批直接确认（CONFIRMED 是 completeSalesIssue 的唯一前置状态） */
    @Test
    void confirmSalesIssue_rejectsDraftIssue() {
        WmSalesIssue issue = new WmSalesIssue();
        issue.setIssueId(1L);
        issue.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(issue);
        // CAS 白名单只含 APPROVED，DRAFT 时影响 0 行
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.confirmSalesIssue(1L));

        verify(baseMapper, never()).updateById(any(WmSalesIssue.class));
    }

    /** ERP-WM-16 对照：已审批单确认走 CAS 成功 */
    @Test
    void confirmSalesIssue_acceptsApprovedIssue() {
        WmSalesIssue issue = new WmSalesIssue();
        issue.setIssueId(2L);
        issue.setStatus("APPROVED");
        when(baseMapper.selectById(2L)).thenReturn(issue);
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertEquals(1, service.confirmSalesIssue(2L));
        verify(baseMapper, never()).updateById(any(WmSalesIssue.class));
    }
}
