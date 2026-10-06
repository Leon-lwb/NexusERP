package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅编辑领料头表与明确编辑行全集的边界回归；仅使用内存持久化快照，不连接业务服务。 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmProductionIssueHeaderOnlyEditTest {
    private static final Long ISSUE_ID = 41L;
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
    }

    @ParameterizedTest(name = "未领行头表编辑：显式null={0}")
    @ValueSource(booleans = {false, true})
    void omittedOrNullListKeepsThreeUnissuedRowsAndRecalculatesHeader(boolean explicitNull) {
        prepare("CREATED", line(42L, "2", "0", "2"), line(43L, "3", "0", "3"),
            line(44L, "4", "0", "4"));
        List<WmProductionIssueLine> before = activeLines();
        WmProductionIssueBo bo = headerEdit();
        if (explicitNull) {
            bo.setWmProductionIssueLineList(null);
        }

        int changed = assertDoesNotThrow(() -> service.updateByBo(bo), "未提供明细列表的头表编辑应成功");

        assertAll(
            () -> assertEquals(1, changed, "头表编辑应返回真实更新行数"),
            () -> assertEquals(before, storedLines, "原三行ID、数量、单位、来源、状态及删除标志不得改变"),
            () -> assertEquals(before, activeLines(), "仅选择头表仓库不可静默删除三条未领行"),
            () -> assertEquals(55L, storedIssue.getWarehouseId(), "应正常保存头表仓库"),
            () -> assertEquals(91L, storedIssue.getWoId(), "头表来源工单必须保留"),
            () -> assertEquals("CREATED", storedIssue.getStatus(), "仅编辑头表不得改变领料状态"),
            () -> assertEquals(new BigDecimal("9"), storedIssue.getTotalQty(), "仍按原三行重算数量，忽略客户端总量"),
            () -> assertEquals(new BigDecimal("18"), storedIssue.getTotalAmount(), "仍按原三行重算金额"));
        assertNoLineMutationOrPosting();
    }

    @ParameterizedTest(name = "部分已领头表编辑：显式null={0}")
    @ValueSource(booleans = {false, true})
    void omittedOrNullListDoesNotRejectPreviouslyIssuedRows(boolean explicitNull) {
        prepare("PARTIAL", line(42L, "2", "1", "0"), line(43L, "3", "0", "3"));
        List<WmProductionIssueLine> before = activeLines();
        WmProductionIssueBo bo = headerEdit();
        if (explicitNull) {
            bo.setWmProductionIssueLineList(null);
        }

        assertDoesNotThrow(() -> service.updateByBo(bo), "未提供列表并不表示删除已领行，不应拒绝头表编辑");

        assertAll(
            () -> assertEquals(before, storedLines, "已领行和未领行均应完整保留"),
            () -> assertEquals("PARTIAL", storedIssue.getStatus(), "仅编辑头表必须保留部分已领状态"),
            () -> assertEquals(new BigDecimal("4"), storedIssue.getTotalQty(), "零本次量应按累计已领参与汇总"));
        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("显式空列表仍逻辑删除全部未领行并清空汇总")
    void explicitEmptyListStillDeletesUnissuedRows() {
        prepare("CREATED", line(42L, "2", "0", "2"), line(43L, "3", "0", "3"),
            line(44L, "4", "0", "4"));
        WmProductionIssueBo bo = headerEdit();
        bo.setWmProductionIssueLineList(List.of());

        service.updateByBo(bo);

        assertAll(
            () -> assertTrue(activeLines().isEmpty(), "明确提交空全集仍表示删除全部未领行"),
            () -> assertTrue(storedLines.stream().allMatch(row -> "1".equals(row.getDelFlag())), "原行应按逻辑删除保留身份"),
            () -> assertEquals(BigDecimal.ZERO, storedIssue.getTotalQty(), "删除后汇总数量应为零"),
            () -> assertEquals(BigDecimal.ZERO, storedIssue.getTotalAmount(), "删除后汇总金额应为零"));
        verify(lineMapper).deleteById(42L);
        verify(lineMapper).deleteById(43L);
        verify(lineMapper).deleteById(44L);
        verifyNoInteractions(stockOperationService, stockMapper, detailMapper);
    }

    @Test
    @DisplayName("显式空列表仍拒绝删除已领行且不得删掉其他未领行")
    void explicitEmptyListStillRejectsIssuedRowsBeforeDeletingAnyLine() {
        prepare("PARTIAL", line(42L, "2", "1", "0"), line(43L, "3", "0", "3"));
        List<WmProductionIssueLine> before = activeLines();
        WmProductionIssueBo bo = headerEdit();
        bo.setWmProductionIssueLineList(List.of());

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateByBo(bo),
            "明确删除已领行必须保留原拒绝规则");

        assertEquals("已领料行不可通过编辑删除", error.getMessage(), "应拒绝明确删除已领行而非拒绝合法头编辑");
        assertEquals(before, storedLines, "拒绝后不得删除或重建任何原行");
        assertNoLineMutationOrPosting();
    }

    @Test
    @DisplayName("非空完整列表仍更新可编辑字段并保留执行结果和原来源")
    void explicitFullListStillEditsRowsAndPreservesExecutionFields() {
        prepare("PARTIAL", line(42L, "2", "1", "0"), line(43L, "3", "0", "3"));
        List<WmProductionIssueLine> requested = activeLines();
        WmProductionIssueLine edited = requested.get(0);
        edited.setCurrentQty(BigDecimal.ONE);
        edited.setIssuedQty(new BigDecimal("999"));
        edited.setShortQty(BigDecimal.ZERO);
        edited.setRequiredQty(new BigDecimal("999"));
        edited.setLineStatus("ISSUED");
        WmProductionIssueBo bo = headerEdit();
        bo.setWmProductionIssueLineList(requested);

        service.updateByBo(bo);

        WmProductionIssueLine row = storedLines.get(0);
        assertAll(
            () -> assertEquals(2, activeLines().size(), "完整全集编辑不得误删原行"),
            () -> assertEquals(42L, row.getLineId(), "行ID必须保留"),
            () -> assertEquals(BigDecimal.ONE, row.getCurrentQty(), "明确行编辑仍应保存本次数量"),
            () -> assertEquals(BigDecimal.ONE, row.getIssuedQty(), "已领量由后端保留，不接受伪造"),
            () -> assertEquals(BigDecimal.ONE, row.getShortQty(), "欠领量由后端保留"),
            () -> assertEquals(new BigDecimal("2"), row.getRequiredQty(), "应领量由后端保留"),
            () -> assertEquals("PARTIAL", row.getLineStatus(), "执行状态由后端保留"),
            () -> assertEquals(142L, row.getRequirementDetailId(), "MRP来源不可改绑"));
        verify(lineMapper).updateById(argThat((WmProductionIssueLine value) -> Long.valueOf(42L).equals(value.getLineId())));
        verify(lineMapper, never()).deleteById(anyLong());
        verifyNoInteractions(stockOperationService, stockMapper, detailMapper);
    }

    @Test
    @DisplayName("非空列表明确遗漏未领行仍按全集编辑删除该行")
    void explicitListStillDeletesOmittedUnissuedRows() {
        prepare("CREATED", line(42L, "2", "0", "2"), line(43L, "3", "0", "3"));
        WmProductionIssueBo bo = headerEdit();
        bo.setWmProductionIssueLineList(List.of(activeLines().get(0)));

        service.updateByBo(bo);

        assertEquals(List.of(42L), activeLines().stream().map(WmProductionIssueLine::getLineId).toList(),
            "明确编辑全集仍删除未提供的未领行，不能将非空列表也视为头表编辑");
        assertEquals("1", storedLines.get(1).getDelFlag(), "原未领行应逻辑删除而非新建替换");
        verify(lineMapper).deleteById(43L);
        verify(lineMapper, never()).insert(any(WmProductionIssueLine.class));
    }

    private WmProductionIssueBo headerEdit() {
        WmProductionIssueBo bo = new WmProductionIssueBo();
        bo.setIssueId(ISSUE_ID);
        bo.setWarehouseId(55L);
        bo.setWarehouseCode("OFFLINE-RAW");
        bo.setWarehouseName("离线原料仓");
        bo.setTotalQty(new BigDecimal("9999"));
        bo.setTotalAmount(new BigDecimal("9999"));
        return bo;
    }

    private void prepare(String status, WmProductionIssueLine... rows) {
        storedIssue = new WmProductionIssue();
        storedIssue.setIssueId(ISSUE_ID);
        storedIssue.setIssueCode("OFFLINE-HEADER-ONLY");
        storedIssue.setWoId(91L);
        storedIssue.setWoNo("离线工单");
        storedIssue.setStatus(status);
        storedLines = new ArrayList<>(List.of(rows));
        when(issueMapper.selectById(ISSUE_ID)).thenAnswer(invocation -> {
            WmProductionIssue copy = new WmProductionIssue();
            BeanUtils.copyProperties(storedIssue, copy);
            return copy;
        });
        when(issueMapper.update(any(WmProductionIssue.class), any(Wrapper.class))).thenAnswer(invocation -> {
            copyNonNullProperties(invocation.getArgument(0), storedIssue);
            return 1;
        });
        when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> activeLines());
        // 更新和删除只修改持久快照；查询始终返回副本，不能把内存变更误当成已保存。
        lenient().doAnswer(invocation -> {
            copyNonNullProperties(invocation.getArgument(0), storedIssue);
            return 1;
        }).when(issueMapper).updateById(any(WmProductionIssue.class));
        lenient().doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            storedLines.stream().filter(row -> row.getLineId().equals(id)).findFirst().orElseThrow().setDelFlag("1");
            return 1;
        }).when(lineMapper).deleteById(anyLong());
        lenient().doAnswer(invocation -> {
            WmProductionIssueLine value = invocation.getArgument(0);
            WmProductionIssueLine stored = storedLines.stream()
                .filter(row -> row.getLineId().equals(value.getLineId())).findFirst().orElseThrow();
            copyNonNullProperties(value, stored);
            return 1;
        }).when(lineMapper).updateById(any(WmProductionIssueLine.class));
    }

    private List<WmProductionIssueLine> activeLines() {
        return storedLines.stream().filter(row -> "0".equals(row.getDelFlag())).map(row -> {
            WmProductionIssueLine copy = new WmProductionIssueLine();
            BeanUtils.copyProperties(row, copy);
            return copy;
        }).toList();
    }

    private static WmProductionIssueLine line(Long id, String required, String issued, String current) {
        WmProductionIssueLine row = new WmProductionIssueLine();
        row.setLineId(id);
        row.setIssueId(ISSUE_ID);
        row.setMaterialId(id + 10L);
        row.setBaseUomId("离线基本单位");
        row.setQuantity(new BigDecimal(required));
        row.setRequiredQty(new BigDecimal(required));
        row.setIssuedQty(new BigDecimal(issued));
        row.setCurrentQty(new BigDecimal(current));
        row.setShortQty(row.getRequiredQty().subtract(row.getIssuedQty()));
        row.setRequirementDetailId(id + 100L);
        row.setLineStatus(row.getIssuedQty().signum() == 0 ? "PENDING" : "PARTIAL");
        row.setUnitPrice(new BigDecimal("2"));
        row.setAmount(row.getQuantity().multiply(row.getUnitPrice()));
        row.setDelFlag("0");
        return row;
    }

    /** 模拟MyBatis默认非空更新：显式零值保存，空字段不覆盖原值。 */
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

    private void assertNoLineMutationOrPosting() {
        verify(lineMapper, never()).deleteById(anyLong());
        verify(lineMapper, never()).updateById(any(WmProductionIssueLine.class));
        verify(lineMapper, never()).insert(any(WmProductionIssueLine.class));
        verifyNoInteractions(stockOperationService, stockMapper, warehouseMapper, autoCodeUtil,
            workOrderMapper, requirementMapper, detailMapper);
    }
}