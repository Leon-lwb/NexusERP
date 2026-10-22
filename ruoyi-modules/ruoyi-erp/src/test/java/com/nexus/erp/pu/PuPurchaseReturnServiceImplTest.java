package com.nexus.erp.pu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.pu.domain.PuPurchaseReturn;
import com.nexus.erp.pu.domain.PuPurchaseReturnLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseReturnBo;
import com.nexus.erp.pu.invoice.service.IPuInvoiceService;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseReturnLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseReturnMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard;
import com.nexus.erp.pu.service.impl.PuPurchaseReturnServiceImpl;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购退货状态机单元测试（ERP-PU-03 未确认不可执行 / ERP-PU-04 不采信客户端 status）。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuPurchaseReturnServiceImplTest {

    @Mock
    private PuCertifiedPurchaseDocumentGuard certifiedDocumentGuard;
    @Mock
    private PuPurchaseReturnMapper baseMapper;
    @Mock
    private PuPurchaseReturnLineMapper lineMapper;
    @Mock
    private PuPurchaseOrderLineMapper orderLineMapper;
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
    @Mock
    private IPuInvoiceService puInvoiceService;

    @InjectMocks
    private PuPurchaseReturnServiceImpl returnService;

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
        TableInfoHelper.initTableInfo(assistant, PuPurchaseReturn.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseReturnLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    /** ERP-PU-03：草稿单不可直接执行（否则未确认即扣库存 + 红冲进项） */
    @Test
    void returnInputCannotAttachCertifiedOrderOrOrderLine() {
        PuPurchaseReturnBo bo = new PuPurchaseReturnBo(); bo.setOrderId(100L);
        PuPurchaseReturnLine line = new PuPurchaseReturnLine(); line.setOrderLineId(11L);
        bo.setPuPurchaseReturnLineList(List.of(line));
        ServiceException protectedError = new ServiceException("protected-document");
        lenient().doThrow(protectedError).when(certifiedDocumentGuard).requireLegacyReturnInput(100L,bo.getPuPurchaseReturnLineList());
        lenient().when(baseMapper.insert(any(PuPurchaseReturn.class))).thenReturn(1);
        Assertions.assertSame(
                protectedError,
                assertThrows(ServiceException.class, () -> returnService.insertByBo(bo)));
        Mockito.verifyNoInteractions(
                baseMapper,
                lineMapper,
                orderLineMapper,
                stockMapper,
                stockTxMapper,
                autoCodeUtil,
                puInvoiceService);
    }

    @Test
    void protectedReturnMutationsCannotWriteStatusStockInvoicesOrOrderQuantities() {
        ServiceException protectedError = new ServiceException("protected-document");
        lenient().doThrow(protectedError).when(certifiedDocumentGuard).requireLegacyReturnMutation(1L);
        List<Runnable> mutations = List.of(
            () -> returnService.confirm(1L), () -> returnService.execute(1L),
            () -> returnService.deleteByIds(new Long[]{1L}), () -> returnService.submit("1","flow"),
            () -> { WfMyBusiness flow = new WfMyBusiness(); flow.setDataId("1"); flow.setActStatus("pass"); returnService.afterFlowHandle(flow); }
        );
        for (Runnable mutation : mutations) {
            Assertions.assertSame(
                    protectedError, assertThrows(ServiceException.class, mutation::run));
        }
        Mockito.verifyNoInteractions(
                baseMapper,
                lineMapper,
                orderLineMapper,
                stockMapper,
                stockTxMapper,
                autoCodeUtil,
                puInvoiceService,
                processService);
    }

    @Test
    void execute_draftStatus_rejected() {
        PuPurchaseReturn draft = new PuPurchaseReturn();
        draft.setReturnId(1L);
        draft.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(draft);

        ServiceException ex = assertThrows(ServiceException.class, () -> returnService.execute(1L));
        assertTrue(ex.getMessage().contains("已确认"), ex.getMessage());
        verify(baseMapper, never()).update(any(), any());
        verify(stockMapper, never()).deductStock(any(), any(), any());
    }

    /** ERP-PU-03：CONFIRMED 单可执行（CAS 抢占成功） */
    @Test
    void execute_confirmedStatus_allowed() {
        PuPurchaseReturn confirmed = new PuPurchaseReturn();
        confirmed.setReturnId(1L);
        confirmed.setStatus("CONFIRMED");
        confirmed.setReturnNo("PR001");
        when(baseMapper.selectById(1L)).thenReturn(confirmed);

        PuPurchaseReturnLine line = new PuPurchaseReturnLine();
        line.setLineId(9L);
        line.setReturnId(1L);
        line.setMaterialId(500L);
        line.setReturnQuantity(new BigDecimal("3"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line));
        when(baseMapper.update(any(), any())).thenReturn(1);

        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(7L);
        stock.setMaterialId(500L);
        stock.setStockQuantity(new BigDecimal("100"));
        stock.setVersion(0);
        when(stockMapper.selectOne(any())).thenReturn(stock);
        when(stockMapper.deductStock(any(), any(), any())).thenReturn(1);

        returnService.execute(1L);

        verify(baseMapper).update(any(), any());
        verify(stockTxMapper).insert(any(WmMaterialStockTx.class));
    }

    /** ERP-PU-03：CAS 影响 0 行（并发已执行）→ 抛异常 */
    @Test
    void execute_casConflict_throws() {
        PuPurchaseReturn confirmed = new PuPurchaseReturn();
        confirmed.setReturnId(1L);
        confirmed.setStatus("CONFIRMED");
        when(baseMapper.selectById(1L)).thenReturn(confirmed);

        PuPurchaseReturnLine line = new PuPurchaseReturnLine();
        line.setLineId(9L);
        line.setReturnQuantity(new BigDecimal("3"));
        when(lineMapper.selectList(any())).thenReturn(List.of(line));
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> returnService.execute(1L));
        assertTrue(ex.getMessage().contains("并发"), ex.getMessage());
    }

    /** ERP-PU-04：请求体带 status=EXECUTED 也必须落 DRAFT */
    @Test
    void insertByBo_clientStatus_forcedToDraft() {
        PuPurchaseReturnBo bo = new PuPurchaseReturnBo();
        bo.setReturnNo("PR-NEW");
        bo.setStatus("EXECUTED");
        when(baseMapper.insert(any(PuPurchaseReturn.class))).thenReturn(1);

        assertEquals(1, returnService.insertByBo(bo));

        ArgumentCaptor<PuPurchaseReturn> captor = ArgumentCaptor.forClass(PuPurchaseReturn.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus());
    }

    /** ERP-PU-04：编辑时不得通过 BO 改状态（updateById 忽略 status） */
    @Test
    void updateByBo_clientStatus_notPersisted() {
        PuPurchaseReturn current = new PuPurchaseReturn();
        current.setReturnId(1L);
        current.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(current);
        when(baseMapper.updateById(any(PuPurchaseReturn.class))).thenReturn(1);

        PuPurchaseReturnBo bo = new PuPurchaseReturnBo();
        bo.setReturnId(1L);
        bo.setStatus("EXECUTED");

        returnService.updateByBo(bo);

        ArgumentCaptor<PuPurchaseReturn> captor = ArgumentCaptor.forClass(PuPurchaseReturn.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(null, captor.getValue().getStatus());
    }
}
