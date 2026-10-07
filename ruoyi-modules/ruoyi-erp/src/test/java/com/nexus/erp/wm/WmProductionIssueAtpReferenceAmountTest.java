package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.impl.WmProductionIssueServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 生产领料可用库存与参考金额口径回归；仅模拟持久层，不连接外部业务系统。 */
@ExtendWith(MockitoExtension.class)
class WmProductionIssueAtpReferenceAmountTest {
    @Mock WmProductionIssueMapper baseMapper;
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
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
    }

    @Test
    @DisplayName("候选库存限定未冻结未删、数据库日期有效和正 ATP，并按空效期最后的 FEFO 排序")
    void candidatesApplyEligibleStockAndNullLastFefo() {
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of());
        service.selectAvailableStock(23L, 7L);
        ArgumentCaptor<LambdaQueryWrapper<WmMaterialStock>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(stockMapper).selectVoList(query.capture());
        assertEligibleStock(query.getValue());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("stock_quantity - reserved_quantity"), "候选必须按扣除预留后的 ATP 过滤");
        assertTrue(sql.contains("> 0"), "候选只应保留已知正 ATP");
        assertTrue(sql.contains("warehouse_id"), "指定仓库时须保留仓库边界");
        assertTrue(sql.contains("ORDER BY expire_date IS NULL ASC, expire_date ASC, recpt_date ASC, stock_id ASC"),
            "FEFO 应先按非空效期、效期、入库时间和库存主键排序");
    }

    @Test
    @DisplayName("候选保留真实账面量并单独返回截零后的 ATP")
    void candidatesKeepLedgerQuantityAndExposeAtp() {
        WmMaterialStockVo stock = new WmMaterialStockVo();
        stock.setStockQuantity(decimal("10"));
        stock.setReservedQuantity(decimal("3"));
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(stock));
        WmMaterialStockVo result = service.selectAvailableStock(23L, null).get(0);
        assertEquals(decimal("10"), result.getStockQuantity(), "账面数量不得改名或改值冒充 ATP");
        assertEquals(decimal("3"), result.getReservedQuantity(), "预留数量应保持真实值");
        assertEquals(decimal("7"), result.getAvailableQuantity(), "可用量必须扣除预留量");
    }

    @Test
    @DisplayName("批量可用库存扣除预留、截零并明确无库存的真实零")
    void batchAtpSubtractsReservationsAndClampsAtZero() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            stock(23L, "10", "3"), stock(23L, "2", "5"), stock(24L, "4", "1")));
        Map<Long, BigDecimal> result = service.sumAvailableStockByMaterials(List.of(23L, 24L, 25L));
        assertEquals(decimal("7"), result.get(23L), "超额预留不能令其他库存 ATP 被扣成负数");
        assertEquals(decimal("3"), result.get(24L), "不同物料可用量必须独立汇总");
        assertEquals(BigDecimal.ZERO, result.get(25L), "查询成功且没有有效库存的物料应明确返回真实零");
        ArgumentCaptor<LambdaQueryWrapper<WmMaterialStock>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(stockMapper).selectList(query.capture());
        assertEligibleStock(query.getValue());
    }

    @ParameterizedTest(name = "账面量 {0}、预留量 {1} 的 ATP 应为 {2}")
    @CsvSource({"10, 10, 0", "10, 4, 6", "10, 11, 0", "10, 0, 10"})
    @DisplayName("批准计划的全预留、部分预留及超额预留数量矩阵")
    void approvedAtpExamplesRemainExact(String ledger, String reserved, String expected) {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stock(23L, ledger, reserved)));
        assertEquals(decimal(expected), service.sumAvailableStockByMaterials(List.of(23L)).get(23L),
            "账面量扣除预留后须逐库存截零，不能使用账面量冒充可用量");
    }

    @Test
    @DisplayName("账面量缺失时物料汇总保持未知而非零或部分可用量")
    void batchUnknownLedgerQuantityPoisonsTheWholeMaterialTotal() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            stock(23L, "10", "3"), stock(23L, null, "0"), stock(23L, "4", "1")));
        Map<Long, BigDecimal> result = service.sumAvailableStockByMaterials(List.of(23L));
        assertTrue(result.containsKey(23L), "未知汇总应显式保留物料键");
        assertNull(result.get(23L), "不得忽略未知账面量后返回部分可用量");
    }

    @Test
    @DisplayName("预留量缺失时物料汇总保持未知而非按未预留处理")
    void batchUnknownReservationDoesNotBecomeReliableZero() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stock(23L, "10", null)));
        assertNull(service.sumAvailableStockByMaterials(List.of(23L)).get(23L), "未知预留量不等于可靠零预留");
    }

    @Test
    @DisplayName("执行前库存校验与候选汇总使用相同有效库存和 ATP 口径")
    void scopedAtpUsesTheSameEligibilityAndQuantity() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stock(23L, "10", "3"), stock(23L, "2", "5")));
        BigDecimal atp = ReflectionTestUtils.invokeMethod(service, "sumAvailable", 23L, 7L, 55L, 64L);
        assertEquals(decimal("7"), atp, "执行校验也应逐库存扣预留并截零");
        ArgumentCaptor<LambdaQueryWrapper<WmMaterialStock>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(stockMapper).selectList(query.capture());
        assertEligibleStock(query.getValue());
        assertTrue(query.getValue().getSqlSegment().contains("batch_id"), "执行校验须保留批次范围");
        assertTrue(query.getValue().getSqlSegment().contains("location_id"), "执行校验须保留库位范围");
    }

    @Test
    @DisplayName("执行前遇到未知库存数量时不能默认为可靠 ATP")
    void scopedAtpRejectsUnknownQuantity() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stock(23L, "10", null)));
        ServiceException error = assertThrows(ServiceException.class,
            () -> ReflectionTestUtils.invokeMethod(service, "sumAvailable", 23L, 7L, null, null),
            "未知预留量必须阻止执行库存校验");
        assertTrue(error.getMessage().contains("未知"), "拒绝原因应明确为数量未知");
        verifyNoInteractions(stockOperationService);
    }

    @Test
    @DisplayName("原三行参考价格为空的历史零总额在读端应为未计价且不写历史数据")
    void historicalZeroHeaderWithThreeUnpricedLinesReadsAsUnknown() {
        WmProductionIssueVo header = detailHeader(
            line(41L, "0", "6", null, null), line(41L, "0", "6", null, null), line(41L, "0", "12", null, null));
        WmProductionIssueVo result = service.selectById(41L);
        assertNull(result.getTotalAmount(), "历史单头零不能掩盖三行参考金额未知");
        assertEquals(decimal("24"), header.getTotalQty(), "读取参考金额不能改账面数量");
        assertTrue(result.getWmProductionIssueLineList().stream().allMatch(row -> row.getUnitPrice() == null && row.getAmount() == null),
            "读端不得用真实成本回填原参考价或明细金额");
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
        verify(baseMapper, never()).update(any(), any(Wrapper.class));
        verifyNoInteractions(stockMapper, stockOperationService);
    }

    @Test
    @DisplayName("任何未计价行均令参考总金额未知，不能漏行后返回部分金额")
    void mixedPricedAndUnpricedLinesKeepTotalUnknown() {
        detailHeader(line(41L, "6", "0", "0.42", "2.52"), line(41L, "6", "0", null, null));
        assertNull(service.selectById(41L).getTotalAmount(), "部分金额已知仍不足以声明完整参考总额");
    }

    @Test
    @DisplayName("已知零参考金额必须保留真实零")
    void explicitZeroReferenceAmountRemainsZero() {
        detailHeader(line(41L, "6", "0", null, "0"));
        assertEquals(BigDecimal.ZERO, service.selectById(41L).getTotalAmount(), "明细显式零金额不能变成未知");
    }

    @Test
    @DisplayName("已知参考价格可计算缺失金额，完成行按累计已领量计算")
    void knownReferencePriceComputesCompletedAmount() {
        detailHeader(line(41L, "0", "6", "0.42", null));
        assertEquals(decimal("2.52"), service.selectById(41L).getTotalAmount(), "累计已领量与参考价可生成可靠参考金额");
    }

    @Test
    @DisplayName("已知零参考价应形成可靠零参考总额")
    void knownZeroReferencePriceComputesZero() {
        detailHeader(line(41L, "6", "0", "0", null));
        assertEquals(BigDecimal.ZERO, service.selectById(41L).getTotalAmount(), "已知零参考价不等于未计价");
    }

    @Test
    @DisplayName("数量未知时即使参考价已知也不能生成可靠零参考金额")
    void unknownQuantityDoesNotCreateReliableZeroReferenceAmount() {
        detailHeader(line(41L, null, null, "1", null));
        assertNull(service.selectById(41L).getTotalAmount(), "参考数量未知不能通过零兜底形成完整参考金额");
    }

    @Test
    @DisplayName("参考列表按明细批量重算，不逐单查询且不修改历史表")
    void listReferenceTotalsUseOneBatchLineRead() {
        WmProductionIssueVo unknown = header(41L);
        WmProductionIssueVo known = header(42L);
        when(baseMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(unknown, known));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            line(41L, "6", "0", null, null), line(42L, "6", "0", "0.42", "2.52")));
        List<WmProductionIssueVo> result = service.selectList(new WmProductionIssueBo());
        assertNull(result.get(0).getTotalAmount(), "列表也应把未知参考总额返回为空");
        assertEquals(decimal("2.52"), result.get(1).getTotalAmount(), "列表应按完整已知明细返回参考总额");
        verify(lineMapper, times(1)).selectList(any(Wrapper.class));
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
        verifyNoInteractions(stockMapper, stockOperationService);
    }

    @Test
    @DisplayName("分页列表与详情采用相同未计价口径并保持分页总数")
    void pagedReferenceTotalsUseTheSameUnknownRule() {
        Page<WmProductionIssueVo> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(header(41L), header(42L)));
        when(baseMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> page);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            line(41L, "6", "0", null, null), line(42L, "6", "0", "0", null)));
        var result = service.selectPageList(new WmProductionIssueBo(), new PageQuery(10, 1));
        assertEquals(2L, result.getTotal(), "金额读端修正不得改变分页总数");
        assertNull(result.getRows().get(0).getTotalAmount(), "分页未计价行应返回未知参考总额");
        assertEquals(BigDecimal.ZERO, result.getRows().get(1).getTotalAmount(), "分页已知零参考价应真实返回零");
        verify(lineMapper, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("新汇总参考金额未知时须显式写空，不能被非空字段策略保留旧零")
    void recalculatedUnknownTotalIsExplicitlyPersistedAsNull() {
        when(baseMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line(41L, "0", "6", null, null)));
        ReflectionTestUtils.invokeMethod(service, "recalcHeaderTotals", 41L);
        ArgumentCaptor<WmProductionIssue> header = ArgumentCaptor.forClass(WmProductionIssue.class);
        verify(baseMapper).updateById(header.capture());
        assertEquals(decimal("6"), header.getValue().getTotalQty(), "新汇总应保留累计已领数量");
        assertNull(header.getValue().getTotalAmount(), "新汇总不得把未知参考价当零");
        ArgumentCaptor<LambdaUpdateWrapper<WmProductionIssue>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), update.capture());
        assertTrue(update.getValue().getSqlSet().contains("total_amount="), "空总额必须通过显式字段更新避免被忽略");
        assertTrue(update.getValue().getParamNameValuePairs().containsValue(null), "持久更新值应包含空参考金额");
        assertTrue(update.getValue().getSqlSegment().contains("issue_id"), "空金额更新必须限定本单头");
    }

    @Test
    @DisplayName("无有效明细的新汇总为未知，不能证明参考金额已知零")
    void emptyRecalculatedTotalsRemainUnknown() {
        when(baseMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        ReflectionTestUtils.invokeMethod(service, "recalcHeaderTotals", 41L);
        ArgumentCaptor<WmProductionIssue> header = ArgumentCaptor.forClass(WmProductionIssue.class);
        verify(baseMapper).updateById(header.capture());
        assertNull(header.getValue().getTotalAmount(), "没有有效明细不能证明参考零金额");
        verify(baseMapper).update(isNull(), any(Wrapper.class));
    }

    private static void assertEligibleStock(LambdaQueryWrapper<WmMaterialStock> query) {
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("frozen_flag"), "只允许未冻结库存");
        assertTrue(sql.contains("del_flag"), "库存表未启用逻辑删除，必须显式排除已删记录");
        assertTrue(sql.contains("expire_date IS NULL OR expire_date >= CURDATE()"), "效期应使用数据库日期而非应用时钟");
        assertTrue(query.getParamNameValuePairs().containsValue(0L), "冻结标志应为未冻结");
        assertTrue(query.getParamNameValuePairs().containsValue("0"), "删除标志应为有效未删");
    }

    private WmProductionIssueVo detailHeader(WmProductionIssueLine... lines) {
        WmProductionIssueVo header = header(41L);
        when(baseMapper.selectVoById(41L)).thenReturn(header);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(lines));
        return header;
    }

    private static WmProductionIssueVo header(long id) {
        WmProductionIssueVo header = new WmProductionIssueVo();
        header.setIssueId(id);
        header.setIssueCode("离线领料回归");
        header.setTotalQty(decimal("24"));
        header.setTotalAmount(BigDecimal.ZERO);
        return header;
    }

    private static WmMaterialStock stock(long materialId, String quantity, String reserved) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(materialId);
        stock.setStockQuantity(decimal(quantity));
        stock.setReservedQuantity(decimal(reserved));
        return stock;
    }

    private static WmProductionIssueLine line(long issueId, String current, String issued, String price, String amount) {
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setIssueId(issueId);
        line.setCurrentQty(decimal(current));
        line.setIssuedQty(decimal(issued));
        line.setUnitPrice(decimal(price));
        line.setAmount(decimal(amount));
        return line;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
