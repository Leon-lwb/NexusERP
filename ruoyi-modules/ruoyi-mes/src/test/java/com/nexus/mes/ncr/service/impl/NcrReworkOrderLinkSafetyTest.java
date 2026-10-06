package com.nexus.mes.ncr.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.domain.NcrReworkOrder;
import com.nexus.mes.ncr.domain.vo.NcrReworkOrderVo;
import com.nexus.mes.ncr.mapper.NcrDefectMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.mapper.NcrReworkOrderMapper;
import com.nexus.mes.ncr.service.INcrDefectService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 返工派生落账的失败关闭回归。
 * 使用真实派生及处置方法、内存替身，不代表物理数据库事务回滚或实际登录。
 */
@ExtendWith(MockitoExtension.class)
class NcrReworkOrderLinkSafetyTest {

    private static final Long NCR_ID = 1L;
    private static final Long ORIGINAL_WO_ID = 10L;
    private static final Long REWORK_WO_ID = 777L;
    private static final Long REWORK_ID = 501L;
    private static final BigDecimal REWORK_QTY = new BigDecimal("5");
    private static final String REASON = "落账安全返工";

    @Mock private NcrReworkOrderMapper baseMapper;
    @Mock private IProWorkOrderService proWorkOrderService;
    @Mock private ProWorkOrderMapper proWorkOrderMapper;
    @Mock private AutoCodeUtil autoCodeUtil;
    @Mock private NcrOrderMapper ncrMapper;
    @InjectMocks private NcrReworkOrderServiceImpl service;

    @BeforeAll
    static void initializeNcrColumns() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), NcrOrder.class);
    }

    @AfterAll
    static void clearNcrColumns() {
        TableInfoHelper.remove(NcrOrder.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void deriveReworkOrder_rejectsInvalidNcrIdBeforeDependencies(Long ncrId) {
        ServiceException failure = assertThrows(ServiceException.class,
            () -> service.deriveReworkOrder(ncrId, ORIGINAL_WO_ID, REWORK_QTY, REASON),
            "不合格单主键无效时必须在访问依赖前拒绝");
        assertTrue(failure.getMessage().contains("NCR"), "异常必须说明不合格单主键无效");
        verifyNoInteractions(baseMapper, proWorkOrderService, proWorkOrderMapper, autoCodeUtil);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void deriveReworkOrder_rejectsInvalidOriginalWoIdBeforeDependencies(Long originalWoId) {
        ServiceException failure = assertThrows(ServiceException.class,
            () -> service.deriveReworkOrder(NCR_ID, originalWoId, REWORK_QTY, REASON),
            "原工单主键无效时必须在访问依赖前拒绝");
        assertTrue(failure.getMessage().contains("原工单"), "异常必须说明原工单主键无效");
        verifyNoInteractions(baseMapper, proWorkOrderService, proWorkOrderMapper, autoCodeUtil);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void deriveReworkOrder_rejectsInvalidQuantityBeforeDependencies(String quantity) {
        BigDecimal qty = quantity == null ? null : new BigDecimal(quantity);
        ServiceException failure = assertThrows(ServiceException.class,
            () -> service.deriveReworkOrder(NCR_ID, ORIGINAL_WO_ID, qty, REASON),
            "返工数量必须为正，不能创建无效工单");
        assertTrue(failure.getMessage().contains("数量"), "异常必须说明返工数量无效");
        verifyNoInteractions(baseMapper, proWorkOrderService, proWorkOrderMapper, autoCodeUtil);
    }

    @Test
    void deriveReworkOrder_rejectsMissingOriginalWorkOrder() {
        assertThrows(ServiceException.class, this::derive, "原工单不存在时必须拒绝派生");
        verify(proWorkOrderMapper).selectById(ORIGINAL_WO_ID);
        verifyNoInteractions(baseMapper, proWorkOrderService, autoCodeUtil);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2})
    void deriveReworkOrder_rejectsWorkOrderInsertNotExactlyOne(int rows) {
        prepareOriginal();
        stubWorkOrderInsert(rows, REWORK_WO_ID);
        assertThrows(ServiceException.class, this::derive, "返工工单必须恰好新增一行");
        verifyNoInteractions(baseMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void deriveReworkOrder_rejectsInvalidCreatedWorkOrderId(Long id) {
        prepareOriginal();
        stubWorkOrderInsert(1, id);
        assertThrows(ServiceException.class, this::derive, "返工工单必须回写真实正主键");
        verifyNoInteractions(baseMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2})
    void deriveReworkOrder_rejectsAssociationInsertNotExactlyOne(int rows) {
        prepareCreatedWorkOrder();
        stubAssociationInsert(rows, REWORK_ID);
        assertThrows(ServiceException.class, this::derive, "返工关联必须恰好新增一行，不能把零行当成功");
        verify(baseMapper, never()).selectVoById(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void deriveReworkOrder_rejectsInvalidCreatedAssociationId(Long id) {
        prepareCreatedWorkOrder();
        stubAssociationInsert(1, id);
        assertThrows(ServiceException.class, this::derive, "关联插入成功后必须回写真实正主键");
        verify(baseMapper, never()).selectVoById(any());
    }

    @Test
    void deriveReworkOrder_rejectsMissingAssociationReadback() {
        prepareInsertedAssociation();
        assertThrows(ServiceException.class, this::derive, "关联回读为空必须抛异常，不得返回空成功");
        verify(baseMapper).selectVoById(REWORK_ID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 502})
    void deriveReworkOrder_rejectsMismatchedAssociationReadbackId(Long id) {
        NcrReworkOrderVo view = prepareReadback();
        view.setReworkId(id);
        assertThrows(ServiceException.class, this::derive, "回读关联主键必须等于本次插入主键");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 2})
    void deriveReworkOrder_rejectsMismatchedNcrReadbackId(Long id) {
        NcrReworkOrderVo view = prepareReadback();
        view.setNcrId(id);
        assertThrows(ServiceException.class, this::derive, "回读关联必须属于本次不合格单");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 11})
    void deriveReworkOrder_rejectsMismatchedOriginalWorkOrderReadbackId(Long id) {
        NcrReworkOrderVo view = prepareReadback();
        view.setOriginalWoId(id);
        assertThrows(ServiceException.class, this::derive, "回读关联必须指向本次原工单");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 778})
    void deriveReworkOrder_rejectsMismatchedReworkWorkOrderReadbackId(Long id) {
        NcrReworkOrderVo view = prepareReadback();
        view.setReworkWoId(id);
        assertThrows(ServiceException.class, this::derive, "回读关联必须指向本次新增返工工单");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "6"})
    void deriveReworkOrder_rejectsMismatchedQuantityReadback(String quantity) {
        NcrReworkOrderVo view = prepareReadback();
        view.setReworkQty(quantity == null ? null : new BigDecimal(quantity));
        assertThrows(ServiceException.class, this::derive, "回读返工数量必须与本次派生数量数值一致");
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "5.0000"})
    void deriveReworkOrder_returnsConfirmedReadbackWithEquivalentQuantityScale(String quantity) {
        NcrReworkOrderVo view = prepareReadback();
        view.setReworkQty(new BigDecimal(quantity));

        assertSame(view, derive(), "只能返回本次插入且身份和数量一致的真实回读视图");

        ArgumentCaptor<ProWorkOrderBo> workOrder = ArgumentCaptor.forClass(ProWorkOrderBo.class);
        ArgumentCaptor<NcrReworkOrder> association = ArgumentCaptor.forClass(NcrReworkOrder.class);
        var writes = inOrder(proWorkOrderService, baseMapper);
        writes.verify(proWorkOrderService).insertByBo(workOrder.capture());
        writes.verify(baseMapper).insert(association.capture());
        writes.verify(baseMapper).selectVoById(REWORK_ID);
        assertEquals(REWORK_QTY, workOrder.getValue().getPlannedQty(), "返工工单必须使用本次数量");
        assertEquals("REWORK", workOrder.getValue().getSourceType(), "返工工单来源必须保持返工");
        assertAll(
            () -> assertEquals(REWORK_ID, association.getValue().getReworkId(), "关联主键必须来自插入回写"),
            () -> assertEquals(NCR_ID, association.getValue().getNcrId(), "关联必须属于本次不合格单"),
            () -> assertEquals(ORIGINAL_WO_ID, association.getValue().getOriginalWoId(), "原工单身份必须保持"),
            () -> assertEquals(REWORK_WO_ID, association.getValue().getReworkWoId(), "返工工单身份必须来自新增回写"),
            () -> assertEquals(REWORK_QTY, association.getValue().getReworkQty(), "关联必须使用本次返工数量"));
        verify(proWorkOrderService, never()).selectById(anyLong());
        verify(proWorkOrderService, never()).selectList(any());
    }

    @Test
    void deriveReworkOrder_propagatesWorkOrderInsertFailure() {
        prepareOriginal();
        RuntimeException failure = new RuntimeException("工单写入异常");
        when(proWorkOrderService.insertByBo(any())).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, this::derive, "底层工单异常不得降级为成功"),
            "必须原样传播底层工单写入异常");
        verifyNoInteractions(baseMapper);
    }

    @Test
    void deriveReworkOrder_propagatesAssociationInsertFailure() {
        prepareCreatedWorkOrder();
        RuntimeException failure = new RuntimeException("关联写入异常");
        when(baseMapper.insert(any(NcrReworkOrder.class))).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, this::derive, "关联写入异常不得降级为成功"),
            "必须原样传播底层关联写入异常");
        verify(baseMapper, never()).selectVoById(any());
    }

    @Test
    void deriveReworkOrder_propagatesAssociationReadbackFailure() {
        prepareInsertedAssociation();
        RuntimeException failure = new RuntimeException("关联回读异常");
        when(baseMapper.selectVoById(REWORK_ID)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, this::derive, "关联回读异常不得降级为成功"),
            "必须原样传播底层关联回读异常");
    }

    @ParameterizedTest
    @ValueSource(strings = {"关联零行", "回读为空", "身份不符"})
    void dispose_reworkAssociationFailureNeverAdvancesToInRework(String failureMode) {
        prepareCreatedWorkOrder();
        if ("关联零行".equals(failureMode)) {
            stubAssociationInsert(0, REWORK_ID);
        } else {
            stubAssociationInsert(1, REWORK_ID);
            if ("身份不符".equals(failureMode)) {
                NcrReworkOrderVo view = validView();
                view.setNcrId(2L);
                when(baseMapper.selectVoById(REWORK_ID)).thenReturn(view);
            }
        }
        NcrOrderServiceImpl caller = prepareCaller();
        try (MockedStatic<LoginHelper> ignored = mockStatic(LoginHelper.class)) {
            RuntimeException failure = assertThrows(RuntimeException.class,
                () -> caller.dispose(NCR_ID, "REWORK", REASON), "派生失败必须阻断返工状态更新");
            assertInstanceOf(ServiceException.class, failure.getCause(), "处置失败必须保留落账业务异常原因");
        }
        verify(ncrMapper, times(1)).update(isNull(), any());
        // 此处只证明没有执行返工中的第二次条件更新，不声称第一次更新已在物理数据库回滚。
    }

    @Test
    void dispose_confirmedReworkAssociationPreservesExistingCasFlow() {
        prepareReadback();
        NcrOrderServiceImpl caller = prepareCaller();
        try (MockedStatic<LoginHelper> ignored = mockStatic(LoginHelper.class)) {
            assertTrue(caller.dispose(NCR_ID, "REWORK", REASON), "确认落账后必须保持原返工处置成功契约");
        }
        verify(ncrMapper, times(2)).update(isNull(), any());
    }

    private NcrReworkOrderVo derive() {
        return service.deriveReworkOrder(NCR_ID, ORIGINAL_WO_ID, REWORK_QTY, REASON);
    }

    private void prepareOriginal() {
        ProWorkOrder original = new ProWorkOrder();
        original.setMesWoId(ORIGINAL_WO_ID);
        original.setWoNo("WO-001");
        original.setMaterialId(1001L);
        original.setMaterialCode("M1001");
        original.setMaterialName("测试物料");
        when(proWorkOrderMapper.selectById(ORIGINAL_WO_ID)).thenReturn(original);
        when(autoCodeUtil.genSerialCode("MES_REWORK_CODE", null)).thenReturn("RW-001", "RW-LINK-001");
    }

    private void stubWorkOrderInsert(int rows, Long id) {
        when(proWorkOrderService.insertByBo(any())).thenAnswer(invocation -> {
            ((ProWorkOrderBo) invocation.getArgument(0)).setMesWoId(id);
            return rows;
        });
    }

    private void stubAssociationInsert(int rows, Long id) {
        when(baseMapper.insert(any(NcrReworkOrder.class))).thenAnswer(invocation -> {
            ((NcrReworkOrder) invocation.getArgument(0)).setReworkId(id);
            return rows;
        });
    }

    private void prepareCreatedWorkOrder() {
        prepareOriginal();
        stubWorkOrderInsert(1, REWORK_WO_ID);
    }

    private void prepareInsertedAssociation() {
        prepareCreatedWorkOrder();
        stubAssociationInsert(1, REWORK_ID);
    }

    private NcrReworkOrderVo prepareReadback() {
        prepareInsertedAssociation();
        NcrReworkOrderVo view = validView();
        when(baseMapper.selectVoById(REWORK_ID)).thenReturn(view);
        return view;
    }

    private NcrReworkOrderVo validView() {
        NcrReworkOrderVo view = new NcrReworkOrderVo();
        view.setReworkId(REWORK_ID);
        view.setNcrId(NCR_ID);
        view.setOriginalWoId(ORIGINAL_WO_ID);
        view.setReworkWoId(REWORK_WO_ID);
        view.setReworkQty(REWORK_QTY);
        return view;
    }

    private NcrOrderServiceImpl prepareCaller() {
        NcrOrder order = new NcrOrder();
        order.setNcrId(NCR_ID);
        order.setStatus("OPEN");
        order.setWoId(ORIGINAL_WO_ID);
        order.setDefectQty(REWORK_QTY);
        when(ncrMapper.selectById(NCR_ID)).thenReturn(order);
        when(ncrMapper.update(isNull(), any())).thenReturn(1);
        return new NcrOrderServiceImpl(ncrMapper, mock(NcrDefectMapper.class), autoCodeUtil,
            mock(ILtLotService.class), mock(LtLotMapper.class), mock(QcIpqcMapper.class),
            mock(IProAndonService.class), service, mock(INcrDefectService.class), mock(ESignatureService.class));
    }
}
