package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.so.domain.SoQuotation;
import com.nexus.erp.so.domain.SoQuotationLine;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.bo.SoQuotationBo;
import com.nexus.erp.so.domain.vo.SoQuotationLineVo;
import com.nexus.erp.so.domain.vo.SoQuotationVo;
import com.nexus.erp.so.mapper.SoQuotationLineMapper;
import com.nexus.erp.so.mapper.SoQuotationMapper;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoQuotationService;
import com.nexus.erp.so.service.impl.SoQuotationServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 销售报价 Service 单元测试（批次 2 / T2.3-T2.4）。
 * <p>覆盖：状态由服务端强制（新增落 DRAFT、改单仅草稿、转单仅已确认）、
 * 明细行白名单重建（防客户端注入 tenantId 跨租户写入）、行金额与表头金额服务端重算、
 * 删除门禁。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoQuotationServiceImplTest {

    @Mock
    private SoQuotationMapper baseMapper;
    @Mock
    private SoQuotationLineMapper lineMapper;
    @Mock
    private SoSalesOrderHeaderMapper salesOrderMapper;
    @Mock
    private SoSalesOrderLineMapper salesOrderLineMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private SoQuotationServiceImpl quotationService;

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
        TableInfoHelper.initTableInfo(assistant, SoQuotation.class);
        TableInfoHelper.initTableInfo(assistant, SoQuotationLine.class);
        TableInfoHelper.initTableInfo(assistant, SoSalesOrderHeader.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private SoQuotation quotation(Long id, String status) {
        SoQuotation q = new SoQuotation();
        q.setQuotationId(id);
        q.setQuotationNo("BJ-TEST-001");
        q.setStatus(status);
        q.setClientId(1L);
        q.setClientName("测试客户");
        return q;
    }

    /** 客户端可篡改的明细行：注入了他单 ID / 其它租户 / 逻辑删除标记 / 伪造金额 */
    private SoQuotationLine tamperedLine() {
        SoQuotationLine line = new SoQuotationLine();
        line.setLineId(999L);
        line.setQuotationId(888L);
        line.setTenantId("888888");
        line.setDelFlag("1");
        line.setCreateBy(1L);
        line.setMaterialId(100L);
        line.setMaterialCode("M-001");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("100"));
        line.setTaxRate(new BigDecimal("13"));
        line.setAmount(new BigDecimal("999999"));
        line.setTaxAmount(new BigDecimal("999999"));
        return line;
    }

    // ==================== 新增 ====================

    /** 新增：客户端伪造 status=CONFIRMED 仍落 DRAFT；明细行白名单重建并重算金额 */
    @Test
    void insertByBo_forcesDraftAndSanitizesLines() {
        SoQuotationBo bo = new SoQuotationBo();
        bo.setQuotationNo("BJ-001");
        bo.setClientId(1L);
        bo.setStatus(ISoQuotationService.STATUS_CONFIRMED);
        bo.setSoQuotationLineList(List.of(tamperedLine()));

        when(baseMapper.insert(any(SoQuotation.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoQuotation.class).setQuotationId(7L);
            return 1;
        });
        when(lineMapper.insert(any(SoQuotationLine.class))).thenReturn(1);

        assertEquals(1, quotationService.insertByBo(bo));

        ArgumentCaptor<SoQuotation> headerCaptor = ArgumentCaptor.forClass(SoQuotation.class);
        verify(baseMapper).insert(headerCaptor.capture());
        SoQuotation saved = headerCaptor.getValue();
        assertEquals(ISoQuotationService.STATUS_DRAFT, saved.getStatus());
        // 表头金额 = Σ行金额 = 2 × 100 = 200.00；税额 = 200 × 13% = 26.00
        assertEquals(0, new BigDecimal("200.00").compareTo(saved.getTotalAmount()));
        assertEquals(0, new BigDecimal("26.00").compareTo(saved.getTaxAmount()));

        ArgumentCaptor<SoQuotationLine> lineCaptor = ArgumentCaptor.forClass(SoQuotationLine.class);
        verify(lineMapper).insert(lineCaptor.capture());
        SoQuotationLine line = lineCaptor.getValue();
        // 白名单重建：ID/租户/逻辑删除/审计字段全部丢弃，quotationId 由头表回填
        assertNull(line.getLineId());
        assertNull(line.getTenantId());
        assertNull(line.getDelFlag());
        assertNull(line.getCreateBy());
        assertEquals(7L, line.getQuotationId());
        // 金额服务端重算，客户端伪造的 999999 被覆盖
        assertEquals(0, new BigDecimal("200.00").compareTo(line.getAmount()));
        assertEquals(0, new BigDecimal("26.00").compareTo(line.getTaxAmount()));
    }

    /** 新增：负数单价直接拒绝 */
    @Test
    void insertByBo_rejectsNegativePrice() {
        SoQuotationBo bo = new SoQuotationBo();
        bo.setQuotationNo("BJ-002");
        SoQuotationLine line = new SoQuotationLine();
        line.setUnitPrice(new BigDecimal("-1"));
        bo.setSoQuotationLineList(List.of(line));

        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("单价不能为负数"), "实际: " + ex.getMessage());
    }

    /** 新增：税率超出 0~100 直接拒绝 */
    @Test
    void insertByBo_rejectsTaxRateOutOfRange() {
        SoQuotationBo bo = new SoQuotationBo();
        bo.setQuotationNo("BJ-003");
        SoQuotationLine line = new SoQuotationLine();
        line.setTaxRate(new BigDecimal("101"));
        bo.setSoQuotationLineList(List.of(line));

        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("税率必须在 0~100"), "实际: " + ex.getMessage());
    }

    // ==================== 修改 / 删除门禁 ====================

    /** 非草稿不可修改 */
    @Test
    void updateByBo_rejectsNonDraft() {
        when(baseMapper.selectById(1L)).thenReturn(quotation(1L, ISoQuotationService.STATUS_CONFIRMED));

        SoQuotationBo bo = new SoQuotationBo();
        bo.setQuotationId(1L);
        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.updateByBo(bo));
        assertTrue(ex.getMessage().contains("只有草稿状态"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).updateById(any(SoQuotation.class));
    }

    /** quotationId 为空直接拒绝 */
    @Test
    void updateByBo_rejectsNullId() {
        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.updateByBo(new SoQuotationBo()));
        assertTrue(ex.getMessage().contains("不能为空"), "实际: " + ex.getMessage());
    }

    /** 非草稿不可删除 */
    @Test
    void deleteByIds_rejectsNonDraft() {
        when(baseMapper.selectById(1L)).thenReturn(quotation(1L, ISoQuotationService.STATUS_CONVERTED));

        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.deleteByIds(new Long[]{1L}));
        assertTrue(ex.getMessage().contains("只有草稿状态"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).deleteBatchIds(anyList());
    }

    // ==================== 确认 / 转单 ====================

    /** 仅草稿可确认 */
    @Test
    void confirm_rejectsNonDraft() {
        when(baseMapper.selectById(1L)).thenReturn(quotation(1L, ISoQuotationService.STATUS_CONFIRMED));

        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.confirm(1L));
        assertTrue(ex.getMessage().contains("只有草稿状态"), "实际: " + ex.getMessage());
    }

    /** 已转换不可重复转换 */
    @Test
    void convertToSalesOrder_rejectsAlreadyConverted() {
        when(baseMapper.selectById(1L)).thenReturn(quotation(1L, ISoQuotationService.STATUS_CONVERTED));

        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.convertToSalesOrder(1L));
        assertTrue(ex.getMessage().contains("已转换"), "实际: " + ex.getMessage());
        verify(salesOrderMapper, never()).insert(any(SoSalesOrderHeader.class));
    }

    /** 草稿不能跳过确认直接转单（原实现只拦 CONVERTED） */
    @Test
    void convertToSalesOrder_rejectsDraft() {
        when(baseMapper.selectById(1L)).thenReturn(quotation(1L, ISoQuotationService.STATUS_DRAFT));

        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.convertToSalesOrder(1L));
        assertTrue(ex.getMessage().contains("只有已确认状态"), "实际: " + ex.getMessage());
        verify(salesOrderMapper, never()).insert(any(SoSalesOrderHeader.class));
    }

    /** 已确认且无明细行 -> 拒绝 */
    @Test
    void convertToSalesOrder_rejectsNoLines() {
        when(baseMapper.selectById(1L)).thenReturn(quotation(1L, ISoQuotationService.STATUS_CONFIRMED));
        when(lineMapper.selectList(any())).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class, () -> quotationService.convertToSalesOrder(1L));
        assertTrue(ex.getMessage().contains("无明细行"), "实际: " + ex.getMessage());
    }

    /** 已确认 -> 生成草稿销售订单并回写报价单状态为 CONVERTED */
    @Test
    void convertToSalesOrder_success_createsDraftOrderAndMarksConverted() {
        SoQuotation q = quotation(1L, ISoQuotationService.STATUS_CONFIRMED);
        when(baseMapper.selectById(1L)).thenReturn(q);

        SoQuotationLine line = new SoQuotationLine();
        line.setLineId(10L);
        line.setMaterialId(100L);
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("100"));
        line.setTaxRate(new BigDecimal("13"));
        line.setAmount(new BigDecimal("200.00"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        when(autoCodeUtil.genSerialCode(eq("SO_ORDER_CODE"), any())).thenReturn("SO-FROM-BJ-001");
        when(salesOrderMapper.insert(any(SoSalesOrderHeader.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoSalesOrderHeader.class).setSoId(66L);
            return 1;
        });
        when(salesOrderLineMapper.insertBatch(anyList())).thenReturn(true);
        // ERP-SO-04: 转单状态回写已改为条件更新 CAS（CONFIRMED -> CONVERTED），
        // 必须让该条件更新影响 1 行，否则服务端判定为「已被并发转换」而回滚
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertEquals(66L, quotationService.convertToSalesOrder(1L));

        ArgumentCaptor<SoSalesOrderHeader> orderCaptor = ArgumentCaptor.forClass(SoSalesOrderHeader.class);
        verify(salesOrderMapper).insert(orderCaptor.capture());
        assertEquals(ISoQuotationService.STATUS_DRAFT, orderCaptor.getValue().getOrderStatus());
        assertEquals("SO-FROM-BJ-001", orderCaptor.getValue().getSoNo());
        // 报价单状态回写为已转换：走 CAS 条件更新，不再使用 updateById
        assertEquals(ISoQuotationService.STATUS_CONVERTED, q.getStatus());
        verify(baseMapper).update(any(), any());
        verify(baseMapper, never()).updateById(any(SoQuotation.class));
    }

    // ==================== 明细「未提交」语义（null vs 空集合）====================

    /** 明细为 null = 不提交明细：明细与金额保持库中原值，不接受客户端改写 */
    @Test
    void updateByBo_nullLineList_keepsLinesAndAmounts() {
        SoQuotation existing = quotation(1L, ISoQuotationService.STATUS_DRAFT);
        existing.setTotalAmount(new BigDecimal("500.00"));
        existing.setTaxAmount(new BigDecimal("65.00"));
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(SoQuotation.class))).thenReturn(1);

        SoQuotationBo bo = new SoQuotationBo();
        bo.setQuotationId(1L);
        bo.setTotalAmount(BigDecimal.ZERO);   // 客户端伪造
        bo.setTaxAmount(BigDecimal.ZERO);
        bo.setSoQuotationLineList(null);

        quotationService.updateByBo(bo);

        // 不应触碰明细表
        verify(lineMapper, never()).delete(any());
        verify(lineMapper, never()).insert(any(SoQuotationLine.class));

        ArgumentCaptor<SoQuotation> captor = ArgumentCaptor.forClass(SoQuotation.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(0, new BigDecimal("500.00").compareTo(captor.getValue().getTotalAmount()));
        assertEquals(0, new BigDecimal("65.00").compareTo(captor.getValue().getTaxAmount()));
    }

    // ==================== selectById 明细行转 VO（批次 5）====================

    /**
     * 详情返回明细行 VO 而非实体：不暴露 tenantId/delFlag 等审计字段，
     * 同时保留报价表单实际绑定的字段（数量/单价/税率/金额/交货日期等）。
     */
    @Test
    void selectById_returnsLineVoKeepingFormFields() {
        SoQuotationVo header = new SoQuotationVo();
        header.setQuotationId(1L);
        when(baseMapper.selectVoById(1L)).thenReturn(header);

        SoQuotationLine line = new SoQuotationLine();
        line.setLineId(10L);
        line.setQuotationId(1L);
        line.setMaterialId(100L);
        line.setMaterialCode("M-001");
        line.setMaterialName("测试物料");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("100"));
        line.setAmount(new BigDecimal("200.00"));
        line.setTaxRate(new BigDecimal("13"));
        line.setTaxAmount(new BigDecimal("26.00"));
        line.setSortOrder(1);
        line.setTenantId("888888");
        line.setDelFlag("1");
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        SoQuotationVo result = quotationService.selectById(1L);

        assertEquals(1, result.getSoQuotationLineList().size());
        SoQuotationLineVo lineVo = result.getSoQuotationLineList().get(0);
        assertEquals("M-001", lineVo.getMaterialCode());
        assertEquals("测试物料", lineVo.getMaterialName());
        assertEquals(0, new BigDecimal("2").compareTo(lineVo.getQuantity()));
        assertEquals(0, new BigDecimal("100").compareTo(lineVo.getUnitPrice()));
        assertEquals(0, new BigDecimal("200.00").compareTo(lineVo.getAmount()));
        assertEquals(0, new BigDecimal("13").compareTo(lineVo.getTaxRate()));
        assertEquals(1, lineVo.getSortOrder());
    }
}
