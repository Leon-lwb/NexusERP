package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.bd.domain.BdVendor;
import com.nexus.erp.bd.mapper.BdVendorMapper;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseOrderBo;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard;
import com.nexus.erp.pu.service.PuPurchaseReceivingService;
import com.nexus.erp.pu.service.impl.PuPurchaseOrderServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 来源订单只允许通过申请原子转单创建；所有 Mapper 均为 Mock，不连接数据库。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuPurchaseOrderServiceImplTest {

    private static final long REQUISITION_ID = 42L;
    private static final long VENDOR_ID = 81L;
    private static final long ORDER_ID = 710L;

    @Mock
    private PuCertifiedPurchaseDocumentGuard certifiedDocumentGuard;
    @Mock
    private PuPurchaseOrderMapper baseMapper;
    @Mock
    private PuPurchaseOrderLineMapper lineMapper;
    @Mock
    private PuPurchaseRequisitionMapper requisitionMapper;
    @Mock
    private PuPurchaseRequisitionLineMapper requisitionLineMapper;
    @Mock
    private PuPurchaseReceivingService purchaseReceivingService;
    @Mock
    private BdVendorMapper vendorMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;
    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<PuPurchaseOrder>> orderQueryCaptor;
    @Captor
    private ArgumentCaptor<PuPurchaseOrder> insertedOrderCaptor;

    @InjectMocks
    private PuPurchaseOrderServiceImpl orderService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PuPurchaseOrder.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseOrderLine.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseRequisitionLine.class);
    }

    @Test
    void protectedOrderMutationsMustNeverReachLegacyMappers() {
        ServiceException protectedError = new ServiceException("protected-document");
        lenient().doThrow(protectedError).when(certifiedDocumentGuard).requireLegacyOrderMutation(42L);
        List<Runnable> mutations = List.of(
            () -> orderService.close(42L), () -> orderService.cancel(42L),
            () -> orderService.deleteByIds(new Long[]{42L}), () -> orderService.submit("42", "flow"),
            () -> orderService.generateReceivingLink(42L),
            () -> { WfMyBusiness flow = new WfMyBusiness(); flow.setDataId("42"); flow.setActStatus("pass"); orderService.afterFlowHandle(flow); }
        );
        for (Runnable mutation : mutations) {
            Assertions.assertSame(
                    protectedError, assertThrows(ServiceException.class, mutation::run));
        }
        verifyNoInteractions(baseMapper, lineMapper, vendorMapper, autoCodeUtil, processService, purchaseReceivingService);
    }

    @Test
    void protectedOrderEditChecksCurrentAndProposedReferencesBeforeAnyLegacyRead() {
        PuPurchaseOrderBo bo = manualOrderBo(); bo.setOrderId(42L);
        ServiceException protectedError = new ServiceException("protected-document");
        lenient().doThrow(protectedError).when(certifiedDocumentGuard).requireLegacyOrderEdit(42L, null, bo.getLineList());
        Assertions.assertSame(
                protectedError,
                assertThrows(ServiceException.class, () -> orderService.updateByBo(bo)));
        verifyNoInteractions(baseMapper, lineMapper, vendorMapper, autoCodeUtil);
    }

    @Test
    void mixedLegacyAndProtectedOrderDeleteIsPreflightedBeforeFirstWrite() {
        ServiceException protectedError = new ServiceException("protected-document");
        lenient().doThrow(protectedError).when(certifiedDocumentGuard).requireLegacyOrderMutation(43L);
        Assertions.assertSame(
                protectedError,
                assertThrows(
                        ServiceException.class,
                        () -> orderService.deleteByIds(new Long[] {42L, 43L})));
        verifyNoInteractions(baseMapper, lineMapper);
    }

    @Test
    void rawManualOrderCannotCreateAnUnattributedPurchaseCommitment() {
        PuPurchaseOrderBo bo = manualOrderBo();
        bo.setOrderNo("PO-RAW");
        lenient().when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());
        lenient().when(baseMapper.insert(any(PuPurchaseOrder.class))).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class, () -> orderService.insertByBo(bo));

        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"), error.getMessage());
        verifyNoInteractions(baseMapper, lineMapper, vendorMapper, autoCodeUtil);
    }

    @ParameterizedTest
    @ValueSource(longs = {REQUISITION_ID, 0L, -1L, Long.MAX_VALUE})
    void linkedInsertIsRejectedBeforeAnyDependencyInteraction(long requisitionId) {
        PuPurchaseOrderBo bo = manualOrderBo();
        bo.setRequisitionId(requisitionId);
        /** RED 时允许旧实现正常下单；GREEN 的早期门禁不应消费这个供应商 stub。 */
        lenient().when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());

        assertConversionRequired(() -> orderService.insertByBo(bo));

        assertEquals(requisitionId, bo.getRequisitionId());
        verifyNoInteractions(baseMapper, lineMapper, requisitionMapper, requisitionLineMapper,
            vendorMapper, autoCodeUtil, purchaseReceivingService, processService,
            wfMyBusinessServiceImpl, procInsIdHelper);
    }

    @ParameterizedTest
    @ValueSource(longs = {421L, 0L, -1L, Long.MAX_VALUE})
    void requisitionLineSourceCannotBeSavedAsManualOrder(long requisitionLineId) {
        PuPurchaseOrderBo bo = manualOrderBo();
        PuPurchaseOrderLine sourcedLine = new PuPurchaseOrderLine();
        sourcedLine.setLineId(901L);
        sourcedLine.setRequisitionLineId(requisitionLineId);
        sourcedLine.setMaterialId(140L);
        sourcedLine.setOrderQuantity(BigDecimal.ONE);
        bo.setLineList(List.of(bo.getLineList().get(0), sourcedLine));
        lenient().when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());

        assertConversionRequired(() -> orderService.insertByBo(bo));

        assertNull(bo.getRequisitionId());
        assertEquals(requisitionLineId, sourcedLine.getRequisitionLineId());
        assertEquals(901L, sourcedLine.getLineId());
        assertNull(sourcedLine.getOrderId());
        verifyNoInteractions(baseMapper, lineMapper, requisitionMapper, requisitionLineMapper,
            vendorMapper, autoCodeUtil, purchaseReceivingService, processService,
            wfMyBusinessServiceImpl, procInsIdHelper);
    }

    @Test
    void repeatedLinkedSaveIsRejectedEvenWhenCallerSuppliesAnOrderId() {
        PuPurchaseOrderBo bo = manualOrderBo();
        bo.setRequisitionId(REQUISITION_ID);
        lenient().when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());

        assertConversionRequired(() -> orderService.insertByBo(bo));
        bo.setOrderId(ORDER_ID);
        assertConversionRequired(() -> orderService.insertByBo(bo));

        verifyNoInteractions(baseMapper, lineMapper, requisitionMapper, requisitionLineMapper,
            vendorMapper, autoCodeUtil);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"DRAFT", "SUBMITTED", "APPROVING", "REJECTED", "CANCELLED", "CLOSED", "ORDERED"})
    void nonApprovedRequisitionCannotBePrefilled(String status) {
        when(requisitionMapper.selectById(REQUISITION_ID)).thenReturn(requisition(status));
        /** RED 时旧实现可返回预填对象；GREEN 必须在读取明细之前拒绝。 */
        lenient().when(requisitionLineMapper.selectList(any())).thenReturn(List.of(requisitionLine()));

        ServiceException error = assertThrows(ServiceException.class,
            () -> orderService.generateFromRequisition(REQUISITION_ID));

        assertTrue(error.getMessage().contains("已批准"));
        verify(requisitionMapper).selectById(REQUISITION_ID);
        verifyNoMoreInteractions(requisitionMapper);
        verifyNoInteractions(baseMapper, lineMapper, requisitionLineMapper, vendorMapper, autoCodeUtil);
    }

    @Test
    void missingRequisitionCannotBePrefilled() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> orderService.generateFromRequisition(REQUISITION_ID));

        assertTrue(error.getMessage().contains("不存在"));
        verify(requisitionMapper).selectById(REQUISITION_ID);
        verifyNoMoreInteractions(requisitionMapper);
        verifyNoInteractions(baseMapper, lineMapper, requisitionLineMapper, vendorMapper, autoCodeUtil);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    void alreadyAcceptedRequisitionCannotBePrefilledEvenIfStillApproved(long existingOrders) {
        when(requisitionMapper.selectById(REQUISITION_ID)).thenReturn(requisition("APPROVED"));
        lenient().when(baseMapper.selectCount(any())).thenReturn(existingOrders);
        lenient().when(requisitionLineMapper.selectList(any())).thenReturn(List.of(requisitionLine()));

        ServiceException error = assertThrows(ServiceException.class,
            () -> orderService.generateFromRequisition(REQUISITION_ID));

        assertTrue(error.getMessage().contains("转单"));
        verify(baseMapper).selectCount(orderQueryCaptor.capture());
        LambdaQueryWrapper<PuPurchaseOrder> query = orderQueryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("requisition_id"));
        assertFalse(query.getSqlSegment().contains("status"), "任何状态的既有承接订单都不得被忽略");
        assertEquals(List.of(REQUISITION_ID), List.copyOf(query.getParamNameValuePairs().values()));
        verify(requisitionMapper).selectById(REQUISITION_ID);
        verifyNoMoreInteractions(baseMapper, requisitionMapper);
        verifyNoInteractions(lineMapper, requisitionLineMapper, vendorMapper, autoCodeUtil);
    }

    @Test
    void approvedUnconvertedRequisitionPrefillPreservesProvenanceWithoutWriting() {
        stubApprovedPrefill(List.of(requisitionLine()));

        PuPurchaseOrderBo bo = orderService.generateFromRequisition(REQUISITION_ID);

        assertNull(bo.getOrderId());
        assertEquals(REQUISITION_ID, bo.getRequisitionId());
        assertEquals("REQ-42", bo.getRequisitionNo());
        assertEquals("DRAFT", bo.getStatus());
        assertEquals("CNY", bo.getCurrency());
        assertEquals(BigDecimal.ONE, bo.getExchangeRate());
        assertNotNull(bo.getOrderDate());
        assertEquals(1, bo.getLineList().size());
        PuPurchaseOrderLine line = bo.getLineList().get(0);
        assertEquals(421L, line.getRequisitionLineId());
        assertEquals(140L, line.getMaterialId());
        assertEquals("个", line.getUnit());
        assertEquals(new BigDecimal("5.4292"), line.getOrderQuantity());
        assertEquals(BigDecimal.ZERO, line.getReceivedQuantity());
        verifyOnlyPrefillReads();
    }

    @Test
    void approvedReadOnlyPrefillStillCannotBeSavedThroughInsert() {
        stubApprovedPrefill(List.of(requisitionLine()));
        PuPurchaseOrderBo bo = orderService.generateFromRequisition(REQUISITION_ID);
        bo.setVendorId(VENDOR_ID);
        lenient().when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());

        assertConversionRequired(() -> orderService.insertByBo(bo));

        assertEquals(REQUISITION_ID, bo.getRequisitionId());
        verifyOnlyPrefillReads();
    }

    @Test
    void approvedPrefillWithHeaderSourceRemovedStillCannotBeSavedAsManualOrder() {
        stubApprovedPrefill(List.of(requisitionLine()));
        PuPurchaseOrderBo bo = orderService.generateFromRequisition(REQUISITION_ID);
        bo.setRequisitionId(null);
        bo.setVendorId(VENDOR_ID);
        lenient().when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());

        assertConversionRequired(() -> orderService.insertByBo(bo));

        assertEquals(421L, bo.getLineList().get(0).getRequisitionLineId());
        verifyOnlyPrefillReads();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void approvedRequisitionWithoutLinesCannotBePrefilled(List<PuPurchaseRequisitionLine> lines) {
        stubApprovedPrefill(lines);

        ServiceException error = assertThrows(ServiceException.class,
            () -> orderService.generateFromRequisition(REQUISITION_ID));

        assertTrue(error.getMessage().contains("没有明细"));
        verifyOnlyPrefillReads();
    }

    @Test
    void rawManualOrderCannotAuthorizeCreationWithAnApprovedStatus() {
        PuPurchaseOrderBo bo = manualOrderBo(); bo.setStatus("APPROVED");
        assertRawOrderRejected(bo);
        assertEquals("APPROVED",bo.getStatus());
    }

    @Test
    void rawManualOrderNumberCannotAuthorizeUnattributedCreation() {
        PuPurchaseOrderBo bo = manualOrderBo(); bo.setOrderNo("PO-MANUAL-0001");
        assertRawOrderRejected(bo);
        assertEquals("PO-MANUAL-0001",bo.getOrderNo());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rawManualOrderWithoutLinesCannotCreateAnUnattributedHeader(List<PuPurchaseOrderLine> lines) {
        PuPurchaseOrderBo bo = manualOrderBo(); bo.setLineList(lines);
        assertRawOrderRejected(bo);
    }

    @Test
    void rawManualOrderRejectsBeforeVendorValidationOrNumberReservation() {
        PuPurchaseOrderBo bo = manualOrderBo(); bo.setVendorId(null);
        assertRawOrderRejected(bo);
    }

    @ParameterizedTest
    @ValueSource(longs = {REQUISITION_ID, 0L, -1L})
    void manualOrderCannotAcquireRequisitionHeaderThroughEdit(long requisitionId) {
        stubExistingEdit(false);
        PuPurchaseOrderBo bo = editOrderBo(false);
        bo.setRequisitionId(requisitionId);

        assertEditSourceRejected(bo);

        assertEquals(requisitionId, bo.getRequisitionId());
        verifyNoInteractions(lineMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {43L, 0L, -1L})
    void convertedOrderCannotChangeOrRemoveRequisitionHeader(Long requisitionId) {
        stubExistingEdit(true);
        PuPurchaseOrderBo bo = editOrderBo(true);
        bo.setRequisitionId(requisitionId);

        assertEditSourceRejected(bo);

        assertEquals(requisitionId, bo.getRequisitionId());
        verifyNoInteractions(lineMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"REQ-OTHER"})
    void convertedOrderCannotChangeOrDiscardRequisitionNumber(String requisitionNo) {
        stubExistingEdit(true);
        PuPurchaseOrderBo bo = editOrderBo(true);
        bo.setRequisitionNo(requisitionNo);

        assertEditSourceRejected(bo);

        assertEquals(requisitionNo, bo.getRequisitionNo());
        verifyNoInteractions(lineMapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {421L, 0L, -1L})
    void manualOrderLineCannotAcquireRequisitionSourceThroughEdit(long requisitionLineId) {
        stubExistingEdit(false);
        PuPurchaseOrderBo bo = editOrderBo(false);
        bo.getLineList().get(1).setRequisitionLineId(requisitionLineId);

        assertEditSourceRejected(bo);

        assertEquals(requisitionLineId, bo.getLineList().get(1).getRequisitionLineId());
        verify(lineMapper).selectList(any());
        verifyNoMoreInteractions(lineMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REMOVE_SOURCE", "CHANGE_SOURCE", "CHANGE_MATERIAL", "REMOVE_MATERIAL",
        "REMOVE_SOURCE_ROW", "EMPTY_LINE_LIST", "DUPLICATE_SOURCE_ROW", "ADD_SOURCED_ROW",
        "MOVE_SOURCE_TO_MANUAL_ROW", "CLEAR_ORDER_LINE_ID", "FOREIGN_ORDER_LINE_ID"})
    void convertedOrderEditMustPreserveOriginalSourceRowAndMaterial(String change) {
        stubExistingEdit(true);
        PuPurchaseOrderBo bo = editOrderBo(true);
        PuPurchaseOrderLine source = bo.getLineList().get(0);
        PuPurchaseOrderLine manual = bo.getLineList().get(1);
        switch (change) {
            case "REMOVE_SOURCE" -> source.setRequisitionLineId(null);
            case "CHANGE_SOURCE" -> source.setRequisitionLineId(422L);
            case "CHANGE_MATERIAL" -> source.setMaterialId(199L);
            case "REMOVE_MATERIAL" -> source.setMaterialId(null);
            case "REMOVE_SOURCE_ROW" -> bo.setLineList(List.of(manual));
            case "EMPTY_LINE_LIST" -> bo.setLineList(List.of());
            case "DUPLICATE_SOURCE_ROW" -> bo.setLineList(List.of(source, manual,
                existingOrderLine(7101L, 421L, 140L)));
            case "ADD_SOURCED_ROW" -> bo.setLineList(List.of(source, manual,
                existingOrderLine(null, 422L, 140L)));
            case "MOVE_SOURCE_TO_MANUAL_ROW" -> {
                source.setRequisitionLineId(null);
                manual.setRequisitionLineId(421L);
                manual.setMaterialId(140L);
            }
            case "CLEAR_ORDER_LINE_ID" -> source.setLineId(null);
            case "FOREIGN_ORDER_LINE_ID" -> source.setLineId(9009L);
            default -> throw new IllegalArgumentException(change);
        }

        assertEditSourceRejected(bo);

        verify(lineMapper).selectList(any());
        verifyNoMoreInteractions(lineMapper);
    }

    @Test
    void convertedOrderCanFillVendorAndEditOrdinaryFieldsWhenSourceIdentityIsPreserved() {
        PuPurchaseOrder current = stubExistingEdit(true);
        assertNull(current.getVendorId(), "转单后尚未补供应商的订单应能正常编辑");
        PuPurchaseOrderBo bo = editOrderBo(true);
        bo.setStatus("APPROVED");
        bo.setRemark("补全供应商及单价");
        bo.getLineList().get(0).setUnitPrice(new BigDecimal("12.50"));
        bo.getLineList().get(1).setMaterialId(199L);
        when(baseMapper.updateById(any(PuPurchaseOrder.class))).thenReturn(1);

        assertEquals(1, orderService.updateByBo(bo));

        verify(baseMapper).updateById(insertedOrderCaptor.capture());
        PuPurchaseOrder updated = insertedOrderCaptor.getValue();
        assertEquals(REQUISITION_ID, updated.getRequisitionId());
        assertEquals("REQ-42", updated.getRequisitionNo());
        assertEquals(VENDOR_ID, updated.getVendorId());
        assertNull(updated.getStatus(), "编辑不可直接改变订单状态");
        assertEquals(421L, bo.getLineList().get(0).getRequisitionLineId());
        assertEquals(140L, bo.getLineList().get(0).getMaterialId());
        assertEquals(new BigDecimal("12.50"), bo.getLineList().get(0).getUnitPrice());
        assertNull(bo.getLineList().get(1).getRequisitionLineId());
        verify(lineMapper).delete(any());
        verify(lineMapper).insert(bo.getLineList().get(0));
        verify(lineMapper).insert(bo.getLineList().get(1));
        verifyNoInteractions(requisitionMapper, requisitionLineMapper, autoCodeUtil);
    }

    @Test
    void convertedHeaderOnlyEditDoesNotDeleteOrReplaceOriginalSourceLines() {
        stubExistingEdit(true);
        PuPurchaseOrderBo bo = editOrderBo(true);
        bo.setLineList(null);
        when(baseMapper.updateById(any(PuPurchaseOrder.class))).thenReturn(1);

        assertEquals(1, orderService.updateByBo(bo));

        verify(baseMapper).updateById(insertedOrderCaptor.capture());
        assertEquals(REQUISITION_ID, insertedOrderCaptor.getValue().getRequisitionId());
        assertEquals(VENDOR_ID, insertedOrderCaptor.getValue().getVendorId());
        verifyNoInteractions(lineMapper, requisitionMapper, requisitionLineMapper, autoCodeUtil);
    }

    @Test
    void legacyLineSourceCannotBeDiscardedButCanBeEditedWithoutInventingHeaderSource() {
        PuPurchaseOrder current = existingOrder(false);
        when(baseMapper.selectById(ORDER_ID)).thenReturn(current);
        lenient().when(lineMapper.selectList(any())).thenReturn(List.of(
            existingOrderLine(7101L, 421L, 140L), existingOrderLine(7102L, null, 145L)));
        when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());
        when(baseMapper.updateById(any(PuPurchaseOrder.class))).thenReturn(1);
        PuPurchaseOrderBo bo = editOrderBo(false);
        bo.getLineList().get(0).setRequisitionLineId(421L);

        assertEquals(1, orderService.updateByBo(bo));

        verify(baseMapper).updateById(insertedOrderCaptor.capture());
        assertNull(insertedOrderCaptor.getValue().getRequisitionId());
        assertEquals(421L, bo.getLineList().get(0).getRequisitionLineId());
        assertEquals(140L, bo.getLineList().get(0).getMaterialId());
        verify(lineMapper).insert(bo.getLineList().get(0));
        verifyNoInteractions(requisitionMapper, requisitionLineMapper, autoCodeUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "SUBMITTED"})
    void ordinaryManualOrderCanStillEditMaterialsAndOtherFields(String status) {
        PuPurchaseOrder current = stubExistingEdit(false);
        current.setStatus(status);
        PuPurchaseOrderBo bo = editOrderBo(false);
        bo.setStatus("APPROVED");
        bo.getLineList().get(0).setMaterialId(199L);
        when(baseMapper.updateById(any(PuPurchaseOrder.class))).thenReturn(1);

        assertEquals(1, orderService.updateByBo(bo));

        verify(baseMapper).updateById(insertedOrderCaptor.capture());
        assertNull(insertedOrderCaptor.getValue().getRequisitionId());
        assertNull(insertedOrderCaptor.getValue().getStatus());
        assertEquals(199L, bo.getLineList().get(0).getMaterialId());
        assertNull(bo.getLineList().get(0).getRequisitionLineId());
        verify(lineMapper).delete(any());
        verify(lineMapper).insert(bo.getLineList().get(0));
        verify(lineMapper).insert(bo.getLineList().get(1));
        verifyNoInteractions(requisitionMapper, requisitionLineMapper, autoCodeUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL_READ", "EMPTY_READ", "UNSOURCED_ROWS"})
    void convertedDetailEditFailsClosedWhenPersistedSourceCannotBeVerified(String readResult) {
        stubExistingEdit(true);
        List<PuPurchaseOrderLine> persistedLines = switch (readResult) {
            case "NULL_READ" -> null;
            case "EMPTY_READ" -> List.of();
            case "UNSOURCED_ROWS" -> List.of(existingOrderLine(7101L, null, 140L),
                existingOrderLine(7102L, null, 145L));
            default -> throw new IllegalArgumentException(readResult);
        };
        when(lineMapper.selectList(any())).thenReturn(persistedLines);
        PuPurchaseOrderBo bo = editOrderBo(true);
        /** 客户端仍保留申请头，但删除申请行来源；不能把无法核验的持久化明细当作无来源手工订单。 */
        bo.getLineList().get(0).setRequisitionLineId(null);

        assertEditSourceRejected(bo);

        assertEquals(REQUISITION_ID, bo.getRequisitionId());
        assertEquals(7101L, bo.getLineList().get(0).getLineId());
        verify(lineMapper).selectList(any());
        verifyNoMoreInteractions(lineMapper);
    }

    @Test
    void clientSourceIdsCannotReplaceMissingPersistedSourceProof() {
        stubExistingEdit(true);
        when(lineMapper.selectList(any())).thenReturn(List.of());
        PuPurchaseOrderBo bo = editOrderBo(true);

        assertEditSourceRejected(bo);

        assertEquals(421L, bo.getLineList().get(0).getRequisitionLineId());
        assertEquals(7101L, bo.getLineList().get(0).getLineId());
        verify(lineMapper).selectList(any());
        verifyNoMoreInteractions(lineMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL_ROW", "MISSING_LINE_ID", "MISSING_ORDER_ID", "FOREIGN_ORDER_ID",
        "MISSING_MATERIAL_ID", "DUPLICATE_LINE_ID"})
    void detailEditFailsClosedWhenPersistedSourceIdentityIsIncompleteOrAmbiguous(String corruption) {
        stubExistingEdit(true);
        PuPurchaseOrderBo bo = editOrderBo(true);
        PuPurchaseOrderLine source = existingOrderLine(7101L, 421L, 140L);
        List<PuPurchaseOrderLine> persistedLines = new ArrayList<>(List.of(source,
            existingOrderLine(7102L, null, 145L)));
        switch (corruption) {
            case "NULL_ROW" -> persistedLines.set(0, null);
            case "MISSING_LINE_ID" -> {
                source.setLineId(null);
                bo.getLineList().get(0).setLineId(null);
            }
            case "MISSING_ORDER_ID" -> {
                source.setOrderId(null);
                bo.getLineList().get(0).setOrderId(null);
            }
            case "FOREIGN_ORDER_ID" -> {
                source.setOrderId(999L);
                bo.getLineList().get(0).setOrderId(999L);
            }
            case "MISSING_MATERIAL_ID" -> {
                source.setMaterialId(null);
                bo.getLineList().get(0).setMaterialId(null);
            }
            case "DUPLICATE_LINE_ID" -> persistedLines.add(existingOrderLine(7101L, 422L, 140L));
            default -> throw new IllegalArgumentException(corruption);
        }
        when(lineMapper.selectList(any())).thenReturn(persistedLines);

        assertEditSourceRejected(bo);

        verify(lineMapper).selectList(any());
        verifyNoMoreInteractions(lineMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullRequestedDetailIsRejectedBeforeAnyWriteOrPartialSourceReplacement(boolean sourced) {
        stubExistingEdit(sourced);
        PuPurchaseOrderBo bo = editOrderBo(sourced);
        List<PuPurchaseOrderLine> requestedLines = new ArrayList<>(bo.getLineList());
        requestedLines.set(1, null);
        bo.setLineList(requestedLines);

        assertEditSourceRejected(bo);

        assertEquals(7101L, bo.getLineList().get(0).getLineId());
        verify(lineMapper).selectList(any());
        verifyNoMoreInteractions(lineMapper);
    }

    @Test
    void manualDetailEditAlsoFailsClosedWhenPersistedLinesReadIsUnavailable() {
        stubExistingEdit(false);
        when(lineMapper.selectList(any())).thenReturn((List<PuPurchaseOrderLine>) null);

        assertEditSourceRejected(editOrderBo(false));

        verify(lineMapper).selectList(any());
        verifyNoMoreInteractions(lineMapper);
    }

    @Test
    void manualOrderCanStillAddDetailsWhenPersistedDetailListIsEmpty() {
        stubExistingEdit(false);
        when(lineMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.updateById(any(PuPurchaseOrder.class))).thenReturn(1);
        PuPurchaseOrderBo bo = editOrderBo(false);
        bo.getLineList().forEach(line -> {
            line.setLineId(null);
            line.setOrderId(null);
        });

        assertEquals(1, orderService.updateByBo(bo));

        verify(lineMapper).selectList(any());
        verify(lineMapper).delete(any());
        bo.getLineList().forEach(line -> {
            assertEquals(ORDER_ID, line.getOrderId());
            assertNull(line.getRequisitionLineId());
            verify(lineMapper).insert(line);
        });
        verifyNoInteractions(requisitionMapper, requisitionLineMapper, autoCodeUtil);
    }

    private void assertEditSourceRejected(PuPurchaseOrderBo bo) {
        ServiceException error = assertThrows(ServiceException.class, () -> orderService.updateByBo(bo));
        assertTrue(error.getMessage().contains("来源"));
        verify(baseMapper).selectById(ORDER_ID);
        verifyNoMoreInteractions(baseMapper);
        verify(lineMapper, never()).delete(any());
        verify(lineMapper, never()).insert(any(PuPurchaseOrderLine.class));
        verifyNoInteractions(vendorMapper, autoCodeUtil, requisitionMapper, requisitionLineMapper,
            purchaseReceivingService, processService, wfMyBusinessServiceImpl, procInsIdHelper);
    }

    private PuPurchaseOrder stubExistingEdit(boolean sourced) {
        PuPurchaseOrder current = existingOrder(sourced);
        when(baseMapper.selectById(ORDER_ID)).thenReturn(current);
        /** RED 旧实现不读原明细；GREEN 拒绝来源变更时也不应读取供应商。 */
        lenient().when(lineMapper.selectList(any())).thenReturn(List.of(
            existingOrderLine(7101L, sourced ? 421L : null, 140L),
            existingOrderLine(7102L, null, 145L)));
        lenient().when(vendorMapper.selectById(VENDOR_ID)).thenReturn(activeVendor());
        return current;
    }

    private PuPurchaseOrder existingOrder(boolean sourced) {
        PuPurchaseOrder order = new PuPurchaseOrder();
        order.setOrderId(ORDER_ID);
        order.setStatus("DRAFT");
        if (sourced) {
            order.setRequisitionId(REQUISITION_ID);
            order.setRequisitionNo("REQ-42");
        }
        return order;
    }

    private PuPurchaseOrderBo editOrderBo(boolean sourced) {
        PuPurchaseOrderBo bo = manualOrderBo();
        bo.setOrderId(ORDER_ID);
        if (sourced) {
            bo.setRequisitionId(REQUISITION_ID);
            bo.setRequisitionNo("REQ-42");
        }
        bo.setLineList(List.of(existingOrderLine(7101L, sourced ? 421L : null, 140L),
            existingOrderLine(7102L, null, 145L)));
        return bo;
    }

    private PuPurchaseOrderLine existingOrderLine(Long lineId, Long requisitionLineId, Long materialId) {
        PuPurchaseOrderLine line = new PuPurchaseOrderLine();
        line.setLineId(lineId);
        line.setOrderId(ORDER_ID);
        line.setRequisitionLineId(requisitionLineId);
        line.setMaterialId(materialId);
        line.setOrderQuantity(BigDecimal.ONE);
        return line;
    }

    private void assertConversionRequired(Runnable save) {
        ServiceException error = assertThrows(ServiceException.class, save::run);
        assertTrue(error.getMessage().contains("转单"));
        assertTrue(error.getMessage().contains("convertToPurchaseOrder"));
    }

    private void stubApprovedPrefill(List<PuPurchaseRequisitionLine> lines) {
        when(requisitionMapper.selectById(REQUISITION_ID)).thenReturn(requisition("APPROVED"));
        /** 旧实现尚无来源承接查询，RED 不应因未使用 stub 而产生伪失败。 */
        lenient().when(baseMapper.selectCount(any())).thenReturn(0L);
        when(requisitionLineMapper.selectList(any())).thenReturn(lines);
    }

    private void verifyOnlyPrefillReads() {
        verify(requisitionMapper).selectById(REQUISITION_ID);
        verify(baseMapper).selectCount(any());
        verify(requisitionLineMapper).selectList(any());
        verifyNoMoreInteractions(baseMapper, requisitionMapper, requisitionLineMapper);
        verifyNoInteractions(lineMapper, vendorMapper, autoCodeUtil, purchaseReceivingService,
            processService, wfMyBusinessServiceImpl, procInsIdHelper);
    }

    private void assertRawOrderRejected(PuPurchaseOrderBo bo) {
        ServiceException error = assertThrows(ServiceException.class,() -> orderService.insertByBo(bo));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"),error.getMessage());
        verifyNoInteractions(baseMapper,lineMapper,requisitionMapper,requisitionLineMapper,vendorMapper,autoCodeUtil);
    }

    @Test
    void vendorReadinessStillRejectsMissingVendorForExistingBusinessPaths() {
        ServiceException error = assertThrows(ServiceException.class,() -> orderService.validateVendor(null,null));
        assertTrue(error.getMessage().contains("供应商ID不能为空"),error.getMessage());
        verifyNoInteractions(vendorMapper,autoCodeUtil,baseMapper,lineMapper);
    }

    private PuPurchaseOrderBo manualOrderBo() {
        PuPurchaseOrderBo bo = new PuPurchaseOrderBo();
        bo.setVendorId(VENDOR_ID);
        PuPurchaseOrderLine line = new PuPurchaseOrderLine();
        line.setMaterialId(140L);
        line.setOrderQuantity(new BigDecimal("3"));
        bo.setLineList(List.of(line));
        return bo;
    }

    private BdVendor activeVendor() {
        BdVendor vendor = new BdVendor();
        vendor.setVendorId(VENDOR_ID);
        vendor.setStatus("ACTIVE");
        return vendor;
    }

    private PuPurchaseRequisition requisition(String status) {
        PuPurchaseRequisition requisition = new PuPurchaseRequisition();
        requisition.setRequisitionId(REQUISITION_ID);
        requisition.setRequisitionNo("REQ-42");
        requisition.setStatus(status);
        return requisition;
    }

    private PuPurchaseRequisitionLine requisitionLine() {
        PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
        line.setLineId(421L);
        line.setRequisitionId(REQUISITION_ID);
        line.setMaterialId(140L);
        line.setMaterialCode("MAT-140");
        line.setUnit("个");
        line.setApplyQuantity(new BigDecimal("5.4292"));
        return line;
    }
}
