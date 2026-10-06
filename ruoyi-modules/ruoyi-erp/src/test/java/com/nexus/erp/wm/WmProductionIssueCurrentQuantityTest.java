package com.nexus.erp.wm;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueShortageBo;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 本次领料量持久清零及单头累计数量回归；不连接数据库或业务服务。 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmProductionIssueCurrentQuantityTest {
    private static final Long ISSUE_ID = 41L;
    private static final Long WAREHOUSE_ID = 7L;

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

    private WmProductionIssue storedIssue;
    private List<WmProductionIssueLine> storedLines;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    @Test
    void currentQuantityUsesNonNullUpdateStrategy() {
        var currentQtyField = TableInfoHelper.getTableInfo(WmProductionIssueLine.class).getFieldList().stream()
            .filter(field -> "currentQty".equals(field.getProperty())).findFirst().orElseThrow();
        assertEquals(FieldStrategy.NOT_NULL, currentQtyField.getUpdateStrategy(),
            "回归中的非空更新模拟必须符合实体的实际更新策略");
    }

    @Test
    void executePersistsZeroForEveryPostedLineAndKeepsCompletedTotalsAndAmounts() {
        prepareExecution("CREATED",
            line(42L, "6", "0", "6", "0.42", "2.52"),
            line(43L, "6", "0", "6", "0.67", "4.02"),
            line(44L, "12", "0", "12", "1.40", "16.80"));
        List<BigDecimal> postedQuantities = new ArrayList<>();
        doAnswer(invocation -> {
            WmProductionIssueLine row = invocation.getArgument(0);
            postedQuantities.add(row.getQuantity());
            return null;
        }).when(stockOperationService).postProductionIssue(any(WmProductionIssueLine.class), any());

        service.execute(ISSUE_ID);

        for (WmProductionIssueLine row : storedLines) {
            assertAll("行 " + row.getLineId() + " 的独立持久快照",
                () -> assertEquals(BigDecimal.ZERO, row.getCurrentQty(), "本次量必须作为非空零值持久更新"),
                () -> assertEquals(row.getRequiredQty(), row.getIssuedQty()),
                () -> assertEquals(BigDecimal.ZERO, row.getShortQty()),
                () -> assertEquals(row.getRequiredQty(), row.getQuantity(), "不得改变原行数量语义"),
                () -> assertEquals("ISSUED", row.getLineStatus()));
        }
        assertEquals(List.of(new BigDecimal("6"), new BigDecimal("6"), new BigDecimal("12")), postedQuantities);
        assertEquals("COMPLETED", storedIssue.getStatus());
        assertDecimal("24", storedIssue.getTotalQty());
        assertDecimal("23.34", storedIssue.getTotalAmount());
        verify(lineMapper, times(3)).updateById(any(WmProductionIssueLine.class));
        verifyNoInteractions(detailMapper);
    }

    @Test
    void twoPartialPostingsAggregateCumulativeIssuedQuantityWithoutRepricingStoredAmount() {
        prepareExecution("CREATED", line(42L, "10", "0", "2", "3", "7.77"));

        service.execute(ISSUE_ID);
        assertDecimal("2", storedIssue.getTotalQty());
        assertDecimal("7.77", storedIssue.getTotalAmount());
        assertEquals("PARTIAL", storedIssue.getStatus());

        storedLines.get(0).setCurrentQty(new BigDecimal("3"));
        service.execute(ISSUE_ID);

        WmProductionIssueLine row = storedLines.get(0);
        assertAll(
            () -> assertDecimal("5", storedIssue.getTotalQty()),
            () -> assertDecimal("5", row.getIssuedQty()),
            () -> assertEquals(BigDecimal.ZERO, row.getCurrentQty(), "第二次执行后仍须持久清零"),
            () -> assertDecimal("5", row.getShortQty()),
            () -> assertDecimal("10", row.getQuantity()),
            () -> assertEquals("PARTIAL", row.getLineStatus()),
            () -> assertEquals("PARTIAL", storedIssue.getStatus()),
            () -> assertDecimal("7.77", row.getAmount()),
            () -> assertDecimal("7.77", storedIssue.getTotalAmount()),
            () -> assertDecimal("3", row.getUnitPrice()));
        verify(stockOperationService, times(2)).postProductionIssue(any(WmProductionIssueLine.class), any());
        verify(issueMapper, times(2)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void executeIncludesPreviouslyIssuedRowsWithZeroCurrentQuantityWithoutPostingThemAgain() {
        WmProductionIssueLine completed = line(42L, "6", "6", "0", "0.42", "2.52");
        completed.setLineStatus("ISSUED");
        prepareExecution("PARTIAL", completed, line(43L, "10", "1", "3", "2", "8"));

        service.execute(ISSUE_ID);

        assertDecimal("10", storedIssue.getTotalQty());
        assertDecimal("10.52", storedIssue.getTotalAmount());
        assertDecimal("6", storedLines.get(0).getIssuedQty());
        assertDecimal("4", storedLines.get(1).getIssuedQty());
        assertEquals(BigDecimal.ZERO, storedLines.get(1).getCurrentQty());
        verify(stockOperationService).postProductionIssue(argThat(row -> Long.valueOf(43L).equals(row.getLineId())), any());
        verify(lineMapper).updateById(argThat((WmProductionIssueLine row) -> Long.valueOf(43L).equals(row.getLineId())));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "2.5, 9, 2.5",
        "0, 9, 9",
        "NULL, 9, 9",
        "-1, 9, 9",
        "0, NULL, 0",
        "NULL, NULL, 0"
    }, nullValues = "NULL")
    void totalsPreferOnlyPositiveCurrentQuantityOtherwiseCumulativeIssuedQuantity(
        BigDecimal currentQty, BigDecimal issuedQty, BigDecimal expectedQty) {
        WmProductionIssueLine row = line(42L, "20", "0", "0", "3", null);
        row.setCurrentQty(currentQty);
        row.setIssuedQty(issuedQty);
        prepareTotals(row);

        recalcTotals();

        assertAll(
            () -> assertDecimal(expectedQty.toPlainString(), storedIssue.getTotalQty()),
            () -> assertDecimal(expectedQty.multiply(new BigDecimal("3")).toPlainString(), storedIssue.getTotalAmount()));
    }

    @Test
    void totalsKeepExistingAmountsIncludingZeroAndUsePriceOnlyWhenAmountIsNull() {
        prepareTotals(
            line(42L, "10", "3", "0", "10", "7.77"),
            line(43L, "10", "9", "2.5", "100", "0"),
            line(44L, "10", "2", null, "3", null));

        recalcTotals();

        assertAll(
            () -> assertDecimal("7.5", storedIssue.getTotalQty()),
            () -> assertDecimal("13.77", storedIssue.getTotalAmount()));
    }

    @Test
    void completeWithShortagePersistsZeroAndKeepsIssuedQuantitiesGapsAndExistingAmounts() {
        WmProductionIssueLine completed = line(44L, "2", "2", "0", "3", null);
        completed.setLineStatus("ISSUED");
        preparePersistence("PARTIAL",
            line(42L, "10", "3", "5", "10", "7.77"),
            line(43L, "8", "0", "8", "2", null), completed);
        when(issueMapper.selectById(ISSUE_ID)).thenAnswer(invocation -> copyIssue(storedIssue));
        PdaIssueShortageBo partialShortage = shortage(42L, "待补料");
        PdaIssueShortageBo pendingShortage = shortage(43L, "");

        service.completeWithShortage(ISSUE_ID, List.of(partialShortage, pendingShortage));

        WmProductionIssueLine partial = storedLines.get(0);
        WmProductionIssueLine pending = storedLines.get(1);
        assertAll(
            () -> assertEquals(BigDecimal.ZERO, partial.getCurrentQty(), "部分已领行缺货关闭必须持久清零"),
            () -> assertEquals(BigDecimal.ZERO, pending.getCurrentQty(), "未领行缺货关闭必须持久清零"),
            () -> assertDecimal("3", partial.getIssuedQty()),
            () -> assertDecimal("0", pending.getIssuedQty()),
            () -> assertDecimal("7", partial.getShortQty()),
            () -> assertDecimal("8", pending.getShortQty()),
            () -> assertEquals("SHORT_CLOSED", partial.getLineStatus()),
            () -> assertEquals("SHORT_CLOSED", pending.getLineStatus()),
            () -> assertEquals("待补料", partial.getShortageReason()),
            () -> assertEquals("缺货", pending.getShortageReason()),
            () -> assertEquals("ISSUED", storedLines.get(2).getLineStatus()),
            () -> assertEquals("COMPLETED", storedIssue.getStatus()),
            () -> assertDecimal("5", storedIssue.getTotalQty()),
            () -> assertDecimal("13.77", storedIssue.getTotalAmount()),
            () -> assertDecimal("7.77", partial.getAmount()),
            () -> assertNull(pending.getAmount()),
            () -> assertDecimal("10", partial.getUnitPrice()));
        verify(lineMapper, times(2)).updateById(any(WmProductionIssueLine.class));
        verifyNoInteractions(stockOperationService, stockMapper, warehouseMapper, detailMapper);
    }

    private void prepareExecution(String status, WmProductionIssueLine... rows) {
        preparePersistence(status, rows);
        when(issueMapper.selectById(ISSUE_ID)).thenAnswer(invocation -> copyIssue(storedIssue));
        when(issueMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        WmWarehouse warehouse = new WmWarehouse();
        warehouse.setWarehouseId(WAREHOUSE_ID);
        warehouse.setStatus(1L);
        warehouse.setWarehouseCode("OFFLINE");
        warehouse.setWarehouseName("离线原料仓");
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockQuantity(new BigDecimal("100"));
        stock.setReservedQuantity(BigDecimal.ZERO);
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stock));
    }

    private void preparePersistence(String status, WmProductionIssueLine... rows) {
        prepareTotals(rows);
        storedIssue.setStatus(status);
        storedIssue.setWarehouseId(WAREHOUSE_ID);
        doAnswer(invocation -> {
            WmProductionIssueLine update = invocation.getArgument(0);
            WmProductionIssueLine stored = storedLines.stream()
                .filter(row -> row.getLineId().equals(update.getLineId())).findFirst().orElseThrow();
            copyNonNullProperties(update, stored);
            return 1;
        }).when(lineMapper).updateById(any(WmProductionIssueLine.class));
    }

    private void prepareTotals(WmProductionIssueLine... rows) {
        storedIssue = new WmProductionIssue();
        storedIssue.setIssueId(ISSUE_ID);
        storedLines = new ArrayList<>(List.of(rows));
        // 每次查询返回独立副本，防止内存置空被误当成已经持久更新。
        when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> storedLines.stream()
            .map(WmProductionIssueCurrentQuantityTest::copyLine).toList());
        doAnswer(invocation -> {
            copyNonNullProperties(invocation.getArgument(0), storedIssue);
            return 1;
        }).when(issueMapper).updateById(any(WmProductionIssue.class));
    }

    private void recalcTotals() {
        ReflectionTestUtils.invokeMethod(service, "recalcHeaderTotals", ISSUE_ID);
    }

    private static WmProductionIssueLine line(Long lineId, String required, String issued, String current,
                                             String price, String amount) {
        WmProductionIssueLine row = new WmProductionIssueLine();
        row.setLineId(lineId);
        row.setIssueId(ISSUE_ID);
        row.setMaterialId(lineId + 100L);
        row.setMaterialName("离线物料" + lineId);
        row.setRequiredQty(decimal(required));
        row.setQuantity(decimal(required));
        row.setIssuedQty(decimal(issued));
        row.setCurrentQty(decimal(current));
        row.setShortQty(decimal(required).subtract(decimal(issued)));
        row.setLineStatus("PENDING");
        row.setUnitPrice(decimal(price));
        row.setAmount(decimal(amount));
        return row;
    }

    private static PdaIssueShortageBo shortage(Long lineId, String reason) {
        PdaIssueShortageBo shortage = new PdaIssueShortageBo();
        shortage.setLineId(lineId);
        shortage.setReason(reason);
        return shortage;
    }

    private static WmProductionIssueLine copyLine(WmProductionIssueLine source) {
        WmProductionIssueLine copy = new WmProductionIssueLine();
        BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private static WmProductionIssue copyIssue(WmProductionIssue source) {
        WmProductionIssue copy = new WmProductionIssue();
        BeanUtils.copyProperties(source, copy);
        return copy;
    }

    /** 模拟默认非空更新：空属性不覆盖已有值，显式零值必须落到持久快照。 */
    private static void copyNonNullProperties(Object source, Object target) {
        BeanWrapper input = new BeanWrapperImpl(source);
        BeanWrapper output = new BeanWrapperImpl(target);
        for (PropertyDescriptor property : input.getPropertyDescriptors()) {
            String name = property.getName();
            if (input.isReadableProperty(name) && output.isWritableProperty(name)) {
                Object value = input.getPropertyValue(name);
                if (value != null) {
                    output.setPropertyValue(name, value);
                }
            }
        }
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual, "汇总或持久数量金额不能为空");
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "期望 " + expected + "，实际 " + actual);
    }
}
