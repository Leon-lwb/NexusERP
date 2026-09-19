package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.so.domain.SoReturn;
import com.nexus.erp.so.domain.SoReturnLine;
import com.nexus.erp.so.domain.bo.SoReturnBo;
import com.nexus.erp.so.domain.vo.SoReturnLineVo;
import com.nexus.erp.so.domain.vo.SoReturnVo;
import com.nexus.erp.so.mapper.SoReturnLineMapper;
import com.nexus.erp.so.mapper.SoReturnMapper;
import com.nexus.erp.so.service.ISoReturnService;
import com.nexus.erp.so.service.impl.SoReturnServiceImpl;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售退货 Service 单元测试（批次 0 回归基线）。
 * <p>覆盖退货入库（execute）的既有正确行为：状态抢占防双执行、无明细拒绝、
 * 退货数量校验、库存新增/原子累加两条路径、库存并发冲突、库存流水 RETURN_IN 登记。</p>
 * <p><b>本类暂不覆盖</b>「已入库单可删除/可改量」这一 P0 缺陷（当前实现确实无门禁）——
 * 批次 1 / T1.3 补上门禁后，在其测试中追加拒绝断言。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoReturnServiceImplTest {

    @Mock
    private SoReturnMapper baseMapper;
    @Mock
    private SoReturnLineMapper lineMapper;
    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private WmMaterialStockTxMapper stockTxMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;

    @InjectMocks
    private SoReturnServiceImpl returnService;

    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initStatics() {
        springUtilMock = mockStatic(SpringUtil.class);
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SoReturn.class);
        TableInfoHelper.initTableInfo(assistant, SoReturnLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private SoReturn soReturn(Long id, String status) {
        SoReturn r = new SoReturn();
        r.setReturnId(id);
        r.setReturnNo("SR-TEST-001");
        r.setStatus(status);
        r.setSoId(1L);
        r.setSoNo("SO-TEST-001");
        r.setWarehouseId(5L);
        r.setWarehouseCode("WH-01");
        r.setWarehouseName("成品仓");
        return r;
    }

    private SoReturnLine returnLine(String quantity) {
        SoReturnLine l = new SoReturnLine();
        l.setLineId(10L);
        l.setReturnId(1L);
        l.setMaterialId(100L);
        l.setMaterialCode("M-001");
        l.setMaterialName("测试物料");
        l.setMaterialSpec("规格A");
        l.setReturnQuantity(quantity == null ? null : new BigDecimal(quantity));
        l.setSortOrder(1);
        return l;
    }

    // ==================== execute 门禁 ====================

    /** 退货单不存在 */
    @Test
    void execute_rejectsMissingReturn() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> returnService.execute(1L));
    }

    /** 仅「已确认」可执行入库：已收货/DRAFT/审批中/已关闭一律拒绝（T1.3） */
    @Test
    void execute_rejectsNonConfirmedStatus() {
        for (String status : List.of(ISoReturnService.STATUS_RECEIVED, ISoReturnService.STATUS_DRAFT,
            ISoReturnService.STATUS_APPROVING, ISoReturnService.STATUS_CLOSED)) {
            when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, status));
            ServiceException ex = assertThrows(ServiceException.class, () -> returnService.execute(1L));
            assertTrue(ex.getMessage().contains("只有已确认状态"), status + " 应被拒绝，实际: " + ex.getMessage());
        }
        verify(stockMapper, never()).insert(any(WmMaterialStock.class));
    }

    /** 无明细行不可执行 */
    @Test
    void execute_rejectsNoLines() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));
        when(lineMapper.selectList(any())).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> returnService.execute(1L));
        assertTrue(ex.getMessage().contains("无明细行"), "实际: " + ex.getMessage());
    }

    /** 状态被并发变更（抢占失败）时拒绝，防双执行重复入库 */
    @Test
    void execute_concurrentStatusConflictThrows() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));
        when(lineMapper.selectList(any())).thenReturn(List.of(returnLine("3")));
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> returnService.execute(1L));
        assertTrue(ex.getMessage().contains("并发"), "实际: " + ex.getMessage());
        verify(stockMapper, never()).insert(any(WmMaterialStock.class));
    }

    /** 退货数量必须大于 0 */
    @Test
    void execute_rejectsNonPositiveQuantity() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));
        when(lineMapper.selectList(any())).thenReturn(List.of(returnLine("0")));
        when(baseMapper.update(any(), any())).thenReturn(1);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> returnService.execute(1L));
        assertTrue(ex.getMessage().contains("必须大于0"), "实际: " + ex.getMessage());
    }

    // ==================== execute 入库 ====================

    /** 无库存行时新建库存并登记 RETURN_IN 流水 */
    @Test
    void execute_createsStockAndWritesTx() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));
        when(lineMapper.selectList(any())).thenReturn(List.of(returnLine("3")));
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(stockMapper.selectOne(any())).thenReturn(null);
        when(stockMapper.insert(any(WmMaterialStock.class))).thenReturn(1);
        when(stockTxMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);

        returnService.execute(1L);

        ArgumentCaptor<WmMaterialStock> stockCaptor = ArgumentCaptor.forClass(WmMaterialStock.class);
        verify(stockMapper).insert(stockCaptor.capture());
        WmMaterialStock newStock = stockCaptor.getValue();
        assertEquals(100L, newStock.getMaterialId());
        assertEquals(5L, newStock.getWarehouseId());
        assertEquals(0, new BigDecimal("3").compareTo(newStock.getStockQuantity()));
        // 新建库存行必须显式置 version=0，否则后续乐观锁更新不生效
        assertEquals(0, newStock.getVersion());

        ArgumentCaptor<WmMaterialStockTx> txCaptor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(txCaptor.capture());
        WmMaterialStockTx tx = txCaptor.getValue();
        assertEquals("RETURN_IN", tx.getTxType());
        assertEquals(0, new BigDecimal("3").compareTo(tx.getTxQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.getBeforeQuantity()));
        assertEquals(0, new BigDecimal("3").compareTo(tx.getAfterQuantity()));
        assertEquals("SO_RETURN", tx.getSourceDocType());
        assertEquals(1L, tx.getSourceDocId());
        assertEquals(10L, tx.getSourceDocLineId());
    }

    /** 已有库存行时走原子累加（乐观锁），不新建库存 */
    @Test
    void execute_existingStock_usesAtomicAdd() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));
        when(lineMapper.selectList(any())).thenReturn(List.of(returnLine("3")));
        when(baseMapper.update(any(), any())).thenReturn(1);

        WmMaterialStock existing = new WmMaterialStock();
        existing.setStockId(50L);
        existing.setMaterialId(100L);
        existing.setStockQuantity(new BigDecimal("10"));
        existing.setVersion(0);
        when(stockMapper.selectOne(any())).thenReturn(existing);
        when(stockMapper.addStock(eq(50L), any(BigDecimal.class), eq(0))).thenReturn(1);
        when(stockTxMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);

        returnService.execute(1L);

        verify(stockMapper).addStock(eq(50L), any(BigDecimal.class), eq(0));
        verify(stockMapper, never()).insert(any(WmMaterialStock.class));

        ArgumentCaptor<WmMaterialStockTx> txCaptor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(txCaptor.capture());
        assertEquals(0, new BigDecimal("10").compareTo(txCaptor.getValue().getBeforeQuantity()));
        assertEquals(0, new BigDecimal("13").compareTo(txCaptor.getValue().getAfterQuantity()));
    }

    /** 库存乐观锁冲突（addStock 影响 0 行）时抛异常 */
    @Test
    void execute_stockConflictThrows() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));
        when(lineMapper.selectList(any())).thenReturn(List.of(returnLine("3")));
        when(baseMapper.update(any(), any())).thenReturn(1);

        WmMaterialStock existing = new WmMaterialStock();
        existing.setStockId(50L);
        existing.setMaterialId(100L);
        existing.setStockQuantity(new BigDecimal("10"));
        existing.setVersion(0);
        when(stockMapper.selectOne(any())).thenReturn(existing);
        when(stockMapper.addStock(eq(50L), any(BigDecimal.class), eq(0))).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> returnService.execute(1L));
        assertTrue(ex.getMessage().contains("并发变更冲突"), "实际: " + ex.getMessage());
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    // ==================== 删除 / 修改门禁（T1.3）====================

    /** 空入参删除返回 0 */
    @Test
    void deleteByIds_emptyIds_returnsZero() {
        assertEquals(0, returnService.deleteByIds(new Long[0]));
        verify(baseMapper, never()).deleteBatchIds(anyList());
    }

    /** 已收货的退货单不可删除（原实现无门禁，库存已加回仍能删掉凭证） */
    @Test
    void deleteByIds_rejectsNonDraft() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_RECEIVED));

        ServiceException ex = assertThrows(ServiceException.class, () -> returnService.deleteByIds(new Long[]{1L}));
        assertTrue(ex.getMessage().contains("只有草稿状态"), "实际: " + ex.getMessage());
        verify(lineMapper, never()).delete(any());
        verify(baseMapper, never()).deleteBatchIds(anyList());
    }

    /** 草稿删除：删行 + 删头 */
    @Test
    void deleteByIds_draft_deletesLinesAndHeader() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_DRAFT));
        when(lineMapper.delete(any())).thenReturn(1);
        when(baseMapper.deleteBatchIds(anyList())).thenReturn(1);

        assertEquals(1, returnService.deleteByIds(new Long[]{1L}));

        verify(lineMapper).delete(any());
        verify(baseMapper).deleteBatchIds(anyList());
    }

    /** 非草稿不可修改 */
    @Test
    void updateByBo_rejectsNonDraft() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));

        SoReturnBo bo = new SoReturnBo();
        bo.setReturnId(1L);
        ServiceException ex = assertThrows(ServiceException.class, () -> returnService.updateByBo(bo));
        assertTrue(ex.getMessage().contains("只有草稿状态"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).updateById(any(SoReturn.class));
    }

    /** returnId 为空直接拒绝 */
    @Test
    void updateByBo_rejectsNullReturnId() {
        ServiceException ex = assertThrows(ServiceException.class, () -> returnService.updateByBo(new SoReturnBo()));
        assertTrue(ex.getMessage().contains("不能为空"), "实际: " + ex.getMessage());
    }

    // ==================== insertByBo ====================

    /** 新增：单号为空时按规则生成 */
    @Test
    void insertByBo_generatesReturnNoWhenBlank() {
        SoReturnBo bo = new SoReturnBo();
        bo.setSoId(1L);
        when(autoCodeUtil.genSerialCode(eq("SO_RETURN_CODE"), any())).thenReturn("SR-AUTO-0001");
        when(baseMapper.insert(any(SoReturn.class))).thenReturn(1);

        assertEquals(1, returnService.insertByBo(bo));

        ArgumentCaptor<SoReturn> captor = ArgumentCaptor.forClass(SoReturn.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("SR-AUTO-0001", captor.getValue().getReturnNo());
    }

    /** 新增：客户端伪造 status=RECEIVED 仍落 DRAFT（否则会出现「已收货」但库存从未加回的假数据） */
    @Test
    void insertByBo_forcesDraft() {
        SoReturnBo bo = new SoReturnBo();
        bo.setReturnNo("SR-MANUAL-1");
        bo.setStatus(ISoReturnService.STATUS_RECEIVED);
        when(baseMapper.insert(any(SoReturn.class))).thenReturn(1);

        returnService.insertByBo(bo);

        ArgumentCaptor<SoReturn> captor = ArgumentCaptor.forClass(SoReturn.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(ISoReturnService.STATUS_DRAFT, captor.getValue().getStatus());
    }

    // ==================== 流程回调守卫（加固轮）====================

    /** 流程回调改为条件更新（不再无条件 updateById，避免把已确认/已入库的退货单改回草稿） */
    @Test
    void afterFlowHandle_usesConditionalUpdate() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_APPROVING));
        when(baseMapper.update(any(), any())).thenReturn(1);

        returnService.afterFlowHandle(flowBusiness(ActStatus.pass));

        ArgumentCaptor<Wrapper<SoReturn>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        verify(baseMapper, never()).updateById(any(SoReturn.class));
        assertTrue(captor.getValue().getSqlSegment().contains("status"), "应为带状态条件的更新");
    }

    /**
     * 已入库（RECEIVED）的退货单不会被流程撤销回调改回 DRAFT。
     * <p>原实现无条件更新：库存已加回、单据却显示为草稿，账实不符。</p>
     */
    @Test
    void afterFlowHandle_doesNotRevertReceivedReturn() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_RECEIVED));
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> returnService.afterFlowHandle(flowBusiness(ActStatus.revoke)));

        verify(baseMapper, never()).updateById(any(SoReturn.class));
    }

    /** 已确认（CONFIRMED）的退货单同样不会被改回草稿（否则 T1.3 的入库门禁会把它卡死） */
    @Test
    void afterFlowHandle_doesNotRevertConfirmedReturn() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_CONFIRMED));
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> returnService.afterFlowHandle(flowBusiness(ActStatus.recall)));

        verify(baseMapper, never()).updateById(any(SoReturn.class));
    }

    /** 未知流程动作不产生任何更新 */
    @Test
    void afterFlowHandle_unknownAction_doesNothing() {
        when(baseMapper.selectById(1L)).thenReturn(soReturn(1L, ISoReturnService.STATUS_DRAFT));

        returnService.afterFlowHandle(flowBusiness("未知动作"));

        verify(baseMapper, never()).update(any(), any());
    }

    private WfMyBusiness flowBusiness(String actStatus) {
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("1");
        business.setActStatus(actStatus);
        return business;
    }

    // ==================== 明细「未提交」语义（null vs 空集合）====================

    /** 明细为 null = 不提交明细：明细与金额保持库中原值，不接受客户端改写 */
    @Test
    void updateByBo_nullLineList_keepsLinesAndAmounts() {
        SoReturn existing = soReturn(1L, ISoReturnService.STATUS_DRAFT);
        existing.setTotalAmount(new BigDecimal("300.00"));
        existing.setTaxAmount(new BigDecimal("39.00"));
        existing.setRefundAmount(new BigDecimal("300.00"));
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(SoReturn.class))).thenReturn(1);

        SoReturnBo bo = new SoReturnBo();
        bo.setReturnId(1L);
        bo.setTotalAmount(BigDecimal.ZERO);   // 客户端伪造
        bo.setRefundAmount(BigDecimal.ZERO);
        bo.setSoReturnLineList(null);

        returnService.updateByBo(bo);

        verify(lineMapper, never()).delete(any());
        verify(lineMapper, never()).insert(any(SoReturnLine.class));

        ArgumentCaptor<SoReturn> captor = ArgumentCaptor.forClass(SoReturn.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(0, new BigDecimal("300.00").compareTo(captor.getValue().getTotalAmount()));
        assertEquals(0, new BigDecimal("39.00").compareTo(captor.getValue().getTaxAmount()));
        assertEquals(0, new BigDecimal("300.00").compareTo(captor.getValue().getRefundAmount()));
    }

    // ==================== selectById 明细行转 VO（批次 5）====================

    /**
     * 详情返回明细行 VO 而非实体：不暴露 tenantId/delFlag 等审计字段，
     * 同时**必须保留**退货表单实际使用的 baseUomId / taxRate 等字段
     * —— 这些字段此前不在 SoReturnLineVo 里，若直接换类型会让表单加载丢字段、保存回写 null。
     */
    @Test
    void selectById_returnsLineVoKeepingFormFields() {
        SoReturnVo header = new SoReturnVo();
        header.setReturnId(1L);
        when(baseMapper.selectVoById(1L)).thenReturn(header);

        SoReturnLine line = new SoReturnLine();
        line.setLineId(10L);
        line.setReturnId(1L);
        line.setMaterialId(100L);
        line.setMaterialCode("M-001");
        line.setBaseUomId("77");
        line.setBaseUomName("件");
        line.setReturnQuantity(new BigDecimal("3"));
        line.setUnitPrice(new BigDecimal("100"));
        line.setTaxRate(new BigDecimal("13"));
        line.setTaxAmount(new BigDecimal("39"));
        line.setSortOrder(1);
        line.setTenantId("888888");
        line.setDelFlag("1");
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        SoReturnVo result = returnService.selectById(1L);

        assertEquals(1, result.getSoReturnLineList().size());
        SoReturnLineVo lineVo = result.getSoReturnLineList().get(0);
        // 退货表单实际绑定的字段一个都不能少
        assertEquals("M-001", lineVo.getMaterialCode());
        assertEquals("77", lineVo.getBaseUomId());
        assertEquals("件", lineVo.getBaseUomName());
        assertEquals(0, new BigDecimal("3").compareTo(lineVo.getReturnQuantity()));
        assertEquals(0, new BigDecimal("13").compareTo(lineVo.getTaxRate()));
        assertEquals(0, new BigDecimal("39").compareTo(lineVo.getTaxAmount()));
        assertEquals(1, lineVo.getSortOrder());
    }
}
