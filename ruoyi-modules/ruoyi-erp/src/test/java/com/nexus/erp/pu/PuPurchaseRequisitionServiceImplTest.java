package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionMapper;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard;
import com.nexus.erp.pu.service.impl.PuPurchaseRequisitionServiceImpl;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

// 同名类型说明：本文件同时使用 java.util.Date 和 java.sql.Date，后者保留全限定名以区分。

/** 采购申请幂等创建、乐观并发和条件补偿测试。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuPurchaseRequisitionServiceImplTest {

    private static final long CREATE_MILLIS = 1_795_622_400_000L;
    private static final long UPDATE_MILLIS = 1_795_622_460_000L;
    private static final long RUN_ID = 99L;
    private static final long ACTION_ID = 501L;
    private static final String IDEM_KEY = "decision-idem-501";

    @Mock
    private PuCertifiedPurchaseDocumentGuard certifiedDocumentGuard;
    @Mock
    private PuPurchaseRequisitionMapper baseMapper;
    @Mock
    private PuPurchaseRequisitionLineMapper lineMapper;
    @Mock
    private PuPurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    @Mock
    private BdMaterialMapper bdMaterialMapper;
    @Mock
    private WmMaterialStockMapper materialStockMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;

    @InjectMocks
    private PuPurchaseRequisitionServiceImpl requisitionService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BdMaterial.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseRequisition.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseRequisitionLine.class);
    }

    @Test
    void legacyApprovedRequisitionMustNotCreateAnotherUnattributedOrder() {
        PuPurchaseRequisition legacy = manualExistingRequisition(); legacy.setStatus("APPROVED");
        PuPurchaseRequisitionLine line = purchaseBo().getPuPurchaseRequisitionLineList().get(0);
        line.setUnit(""); line.setApplyQuantity(new BigDecimal("510"));
        Mockito.lenient().when(baseMapper.selectById(7001L)).thenReturn(legacy);
        Mockito.lenient().when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line));
        Mockito.lenient()
                .when(autoCodeUtil.genSerialCode("PU_ORDER_CODE", null))
                .thenReturn("PO-RAW");
        Mockito.lenient()
                .doAnswer(
                        invocation -> {
                            invocation.<PuPurchaseOrder>getArgument(0).setOrderId(9001L);
                            return 1;
                        })
                .when(purchaseOrderMapper)
                .insert(any(PuPurchaseOrder.class));
        Mockito.lenient()
                .when(purchaseOrderLineMapper.insert(any(PuPurchaseOrderLine.class)))
                .thenReturn(1);
        Mockito.lenient()
                .when(baseMapper.updateById(any(PuPurchaseRequisition.class)))
                .thenReturn(1);
        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.convertToPurchaseOrder(7001L));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"),error.getMessage());
        verifyNoInteractions(autoCodeUtil,purchaseOrderLineMapper);
        verify(purchaseOrderMapper,never()).insert(any(PuPurchaseOrder.class));
        assertEquals("",line.getUnit()); assertEquals(new BigDecimal("510"),line.getApplyQuantity());
    }

    @Test
    void historicalEmptyUnitAnd510QuantityRemainExactlyReadableWithoutProtectionUpgrading() {
        PuPurchaseRequisition legacy = manualExistingRequisition();
        PuPurchaseRequisitionLine line = purchaseBo().getPuPurchaseRequisitionLineList().get(0);
        line.setUnit(""); line.setApplyQuantity(new BigDecimal("510"));
        when(baseMapper.selectById(7001L)).thenReturn(legacy);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line));
        var result = requisitionService.selectById(7001L);
        assertEquals("",result.getPuPurchaseRequisitionLineList().get(0).getUnit());
        assertEquals(new BigDecimal("510"),result.getPuPurchaseRequisitionLineList().get(0).getApplyQuantity());
        verifyNoInteractions(certifiedDocumentGuard,autoCodeUtil,purchaseOrderMapper,purchaseOrderLineMapper);
    }

    @Test
    void protectedRequisitionMutationsMustNeverReachLegacyMappers() {
        Mockito.lenient()
                .when(baseMapper.selectById(7001L))
                .thenReturn(manualExistingRequisition());
        Mockito.lenient()
                .when(baseMapper.updateById(any(PuPurchaseRequisition.class)))
                .thenReturn(1);
        ServiceException protectedError = new ServiceException("protected-document");
        Mockito.lenient()
                .doThrow(protectedError)
                .when(certifiedDocumentGuard)
                .requireLegacyRequisitionMutation(7001L);
        List<Runnable> mutations = List.of(
            () -> requisitionService.close(7001L),
            () -> requisitionService.cancel(7001L),
            () -> requisitionService.deleteByIds(new Long[]{7001L}),
            () -> requisitionService.submit("7001", "flow"),
            () -> requisitionService.convertToPurchaseOrder(7001L),
            () -> requisitionService.deleteDraftIfUnchanged(7001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY),
            () -> { WfMyBusiness flow = new WfMyBusiness(); flow.setDataId("7001"); flow.setActStatus(ActStatus.pass); requisitionService.afterFlowHandle(flow); }
        );
        for (Runnable mutation : mutations) {
            assertSame(protectedError, assertThrows(ServiceException.class, mutation::run));
        }
        verifyNoInteractions(baseMapper, lineMapper, purchaseOrderMapper, purchaseOrderLineMapper, autoCodeUtil, processService);
    }

    @Test
    void protectedRequisitionEditChecksPersistedAndProposedLineIdentityBeforeLegacyReads() {
        PuPurchaseRequisitionBo bo = purchaseBo(); bo.setRequisitionId(7001L);
        ServiceException protectedError = new ServiceException("protected-document");
        Mockito.lenient()
                .doThrow(protectedError)
                .when(certifiedDocumentGuard)
                .requireLegacyRequisitionEdit(7001L, bo.getPuPurchaseRequisitionLineList());
        Mockito.lenient()
                .when(baseMapper.selectById(7001L))
                .thenReturn(manualExistingRequisition());
        assertSame(protectedError, assertThrows(ServiceException.class, () -> requisitionService.updateByBo(bo)));
        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }

    @Test
    void mixedLegacyAndProtectedRequisitionDeleteIsPreflightedBeforeFirstWrite() {
        ServiceException protectedError = new ServiceException("protected-document");
        Mockito.lenient()
                .doThrow(protectedError)
                .when(certifiedDocumentGuard)
                .requireLegacyRequisitionMutation(7002L);
        Mockito.lenient()
                .when(baseMapper.selectById(7001L))
                .thenReturn(manualExistingRequisition());
        Mockito.lenient().when(baseMapper.delete(any(Wrapper.class))).thenReturn(1);
        assertSame(protectedError, assertThrows(ServiceException.class, () -> requisitionService.deleteByIds(new Long[]{7001L,7002L})));
        verifyNoInteractions(baseMapper, lineMapper);
    }

    @Test
    void rawManualInsertWithoutCertifiedSourceMustRejectBeforeAnyPersistence() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionNo("PR-RAW");
        Mockito.lenient()
                .doAnswer(
                        invocation -> {
                            PuPurchaseRequisition row = invocation.getArgument(0);
                            row.setRequisitionId(7010L);
                            return 1;
                        })
                .when(baseMapper)
                .insert(any(PuPurchaseRequisition.class));
        Mockito.lenient()
                .when(lineMapper.insert(any(PuPurchaseRequisitionLine.class)))
                .thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.insertByBo(bo));

        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"), error.getMessage());
        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }

    @Test
    void rawCreateDraftCannotBecomeTrustedByMockingTheOldReadinessGuard() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        bo.setRequisitionNo("PR-RAW-AI");
        Mockito.lenient()
                .doAnswer(
                        invocation -> {
                            PuPurchaseRequisition row = invocation.getArgument(0);
                            row.setRequisitionId(7010L);
                            return 1;
                        })
                .when(baseMapper)
                .insert(any(PuPurchaseRequisition.class));
        Mockito.lenient()
                .when(lineMapper.insert(any(PuPurchaseRequisitionLine.class)))
                .thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"), error.getMessage());
        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }

    @Test
    void ordinaryFormCannotCreateAnAiDecisionProvenanceDocument() {
        assertThrows(ServiceException.class, () -> requisitionService.insertByBo(decisionPurchaseBo()));
        for (String source : List.of("ai_decision", " AI_DECISION ")) {
            var manual=purchaseBo();manual.setSourceType(source);
            assertThrows(ServiceException.class, () -> requisitionService.insertByBo(manual));
        }
        verifyNoInteractions(baseMapper,lineMapper,autoCodeUtil);
    }

    @Test
    void ordinaryFormCannotCreateUnattributedDemandOrLowStockLines() {
        for (String source : List.of("LOW_STOCK", "MES_SPARE_LOW_STOCK", "REQ_CONVERT", "MRP")) {
            PuPurchaseRequisitionBo bo = purchaseBo();
            bo.setSourceType(source);
            ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.insertByBo(bo));
            assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"), source);
        }
        for (String source : List.of("AI_DECISION", "LOW_STOCK", "MRP")) {
            PuPurchaseRequisitionBo bo = purchaseBo();
            bo.getPuPurchaseRequisitionLineList().get(0).setSourceType(source);
            ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.insertByBo(bo));
            assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"), source);
        }
        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }

    @Test
    void directDraftMustRejectLegacyAiIdentityBeforeIdempotentLookupOrWrites() {
        ServiceException error = assertThrows(ServiceException.class,
            () -> requisitionService.createDraft(decisionPurchaseBo()));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"));
        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }
    @Test
    void directDraftCannotBypassLowStockSourceGate() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setSourceType("MES_SPARE_LOW_STOCK");
        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"));
        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }
    @Test
    void rawDraftAuditFieldsCannotSubstituteForSourceCertification() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        bo.setCreateBy(1L); bo.setUpdateBy(1L); bo.setCreateDept(103L);
        bo.getPuPurchaseRequisitionLineList().get(0).setCreateBy(-1L);
        assertRawDraftRejected(bo);
        assertEquals(-1L,bo.getPuPurchaseRequisitionLineList().get(0).getCreateBy());
    }

    @Test
    void rawDraftDoesNotGenerateIdentityOrWriteBackVersion() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        assertRawDraftRejected(bo);
        assertEquals(null,bo.getRequisitionNo());
        assertEquals(0,bo.getVersion());
    }

    @Test
    void ordinaryUiNullKeyNoLongerAuthorizesUnattributedCreation() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.insertByBo(bo));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"));
        assertEquals(null,bo.getRequisitionId());
        verifyNoInteractions(baseMapper,lineMapper,autoCodeUtil);
    }

    @Test
    void rawDraftRejectsBeforeHeaderInsertCanReturnZero() {
        assertRawDraftRejected(purchaseBo());
    }

    @Test
    void rawDraftRejectsBeforeAnyHeaderIdentityCanBeGenerated() {
        assertRawDraftRejected(purchaseBo());
    }

    @Test
    void insertByBoRejectsExistingIdWithoutPersistence() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(9L);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> requisitionService.insertByBo(bo));

        assertTrue(exception.getMessage().contains("新建"), exception.getMessage());
        assertTrue(exception.getMessage().contains("ID"), exception.getMessage());
        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }

    @Test
    void sameDecisionKeyCannotAuthorizeRawReplayOrLookup() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        assertRawDraftRejected(bo);
        assertEquals(IDEM_KEY,bo.getDecisionIdempotencyKey());
    }

    @Test
    void rawReplayRejectsChangedProvenanceBeforeAnyLookup() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo(); bo.setDecisionRunId(RUN_ID+1);
        assertRawDraftRejected(bo);
    }

    @Test
    void rawReplayRejectsChangedPayloadScopeBeforeAnyLookup() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo(); bo.setApplicantId(8L);
        bo.setRequiredDate(java.sql.Date.valueOf("2026-10-01"));
        assertRawDraftRejected(bo);
    }

    @Test
    void rawReplayCannotTrustDelimiterBasedLineIdentity() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        bo.getPuPurchaseRequisitionLineList().get(0).setMaterialCode("M|unsafe");
        bo.getPuPurchaseRequisitionLineList().get(0).setMaterialName("code|name");
        assertRawDraftRejected(bo);
    }

    @Test
    void rawReplayCannotTrustDuplicateLinesOrderingOrDecimalScale() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        var lines = new ArrayList<>(bo.getPuPurchaseRequisitionLineList());
        lines.get(0).setApplyQuantity(new BigDecimal("5.00")); lines.add(matchingLines(null).get(0));
        bo.setPuPurchaseRequisitionLineList(lines);
        assertRawDraftRejected(bo);
        assertEquals(2,bo.getPuPurchaseRequisitionLineList().size());
    }

    @Test
    void rawReplayRejectsChangedDuplicateLineCountBeforeAnyLookup() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        bo.setPuPurchaseRequisitionLineList(List.of());
        assertRawDraftRejected(bo);
    }

    @Test
    void rawReplayNeverAttemptsTheOldDuplicateInsertRecovery() {
        assertRawDraftRejected(decisionPurchaseBo());
    }

    @Test
    void rawReplayCannotClaimAnOldSnapshotAsCurrentCertification() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        bo.setVersion(4); bo.setCreateTime(new Date(CREATE_MILLIS));
        assertRawDraftRejected(bo);
    }

    @Test
    void decisionPostCreateReadsBothHeaderAndLinesWithForUpdate() {
        when(baseMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase(Locale.ROOT).contains("FOR UPDATE")
                ? matchingExistingRequisition() : null;
        });
        when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase(Locale.ROOT).contains("FOR UPDATE")
                ? matchingLines(7001L) : List.of();
        });

        assertEquals(1, assertDoesNotThrow(() -> requisitionService.selectByIdForDecision(7001L))
            .getPuPurchaseRequisitionLineList().size());
        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> header = wrapperCaptor();
        ArgumentCaptor<Wrapper<PuPurchaseRequisitionLine>> lines = wrapperCaptor();
        verify(baseMapper).selectOne(header.capture());
        verify(lineMapper).selectList(lines.capture());
        assertSqlContains(header.getValue(), "requisition_id", "for update");
        assertSqlContains(lines.getValue(), "requisition_id", "for update");
    }

    @Test
    void decisionPostCreateCurrentReadIsExposedOnPurchaseService() {
        assertDoesNotThrow(
                () ->
                        IPuPurchaseRequisitionService.class.getMethod(
                                "selectByIdForDecision", Long.class));
    }

    @Test
    void compensatedPurchaseKeyCannotBeRecreatedThroughRawDraft() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        assertRawDraftRejected(bo);
        assertEquals(IDEM_KEY,bo.getDecisionIdempotencyKey());
        verifyNoInteractions(certifiedDocumentGuard);
    }

    @Test
    void rawDraftRejectsBeforeUnrelatedSqlUniquenessOrCompensationLookup() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo(); bo.setRequisitionNo("PR-CODE-COLLISION");
        assertRawDraftRejected(bo);
    }

    @Test
    void createDraftRejectsInvalidInputBeforeWriting() {
        PuPurchaseRequisitionBo withId = purchaseBo(); withId.setRequisitionId(9L);
        assertRawDraftRejected(null);
        assertRawDraftRejected(withId);
        assertRawDraftRejected(new PuPurchaseRequisitionBo());
    }

    @Test
    void rawDraftRejectsBeforeAnyLineInsertCanRun() {
        assertRawDraftRejected(purchaseBo());
        verifyNoInteractions(lineMapper);
    }

    @Test
    void deleteByIdsRejectsApprovingBeforeDeletingParentOrLines() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("APPROVING");
        when(baseMapper.selectById(7001L)).thenReturn(current);

        ServiceException error = assertThrows(ServiceException.class,
            () -> requisitionService.deleteByIds(new Long[]{7001L}));

        assertTrue(error.getMessage().contains("审批"), error.getMessage());
        assertEquals("APPROVING", current.getStatus());
        verifyNoInteractions(lineMapper);
        verify(baseMapper, never()).delete(any(Wrapper.class));
        verify(baseMapper, never()).deleteBatchIds(anyCollection());
    }

    @Test
    void deleteByIdsClaimsEligibleParentWithStatusAndVersionBeforeDeletingLines() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setVersion(0);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(1);

        assertEquals(1, requisitionService.deleteByIds(new Long[]{7001L}));

        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> parent = wrapperCaptor();
        InOrder order = inOrder(baseMapper, lineMapper);
        order.verify(baseMapper).selectById(7001L);
        order.verify(baseMapper).delete(parent.capture());
        order.verify(lineMapper).delete(any(Wrapper.class));
        assertSqlContains(parent.getValue(), "requisition_id", "status", "version");
        assertTrue(parameters(parent.getValue()).containsValue(7001L));
        assertTrue(parameters(parent.getValue()).containsValue("DRAFT"));
        assertTrue(parameters(parent.getValue()).containsValue(0));
        verify(baseMapper, never()).deleteBatchIds(anyCollection());
    }

    @Test
    void deleteByIdsCasMissAfterConcurrentSubmitThrowsBeforeTouchingLines() {
        PuPurchaseRequisition staleDraft = matchingExistingRequisition();
        staleDraft.setVersion(0);
        when(baseMapper.selectById(7001L)).thenReturn(staleDraft);
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
            () -> requisitionService.deleteByIds(new Long[]{7001L}));

        assertTrue(error.getMessage().contains("状态") || error.getMessage().contains("并发"), error.getMessage());
        verify(baseMapper).delete(any(Wrapper.class));
        verifyNoInteractions(lineMapper);
        verify(baseMapper, never()).deleteBatchIds(anyCollection());
    }

    @Test
    void deleteByIdsLaterCasConflictThrowsBeforeLinesAndDeclaresTransactionRollback() throws Exception {
        PuPurchaseRequisition first = matchingExistingRequisition();
        first.setVersion(0);
        PuPurchaseRequisition second = matchingExistingRequisition();
        second.setRequisitionId(7002L);
        second.setVersion(0);
        when(baseMapper.selectById(7001L)).thenReturn(first);
        when(baseMapper.selectById(7002L)).thenReturn(second);
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(1, 0);

        assertThrows(ServiceException.class, () -> requisitionService.deleteByIds(new Long[]{7001L, 7002L}));

        verify(baseMapper, times(2)).delete(any(Wrapper.class));
        verifyNoInteractions(lineMapper);
        verify(baseMapper, never()).deleteBatchIds(anyCollection());
        Transactional transaction = PuPurchaseRequisitionServiceImpl.class
            .getMethod("deleteByIds", Long[].class).getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(List.of(transaction.rollbackFor()).contains(Exception.class));
    }

    @Test
    void deleteByIdsRejectsInvalidAndDuplicateIdsBeforeAnyMapperCall() {
        Long[][] invalid = {null, {}, {null}, {0L}, {-1L}, {7001L, 7001L}};
        for (Long[] ids : invalid) {
            assertThrows(ServiceException.class, () -> requisitionService.deleteByIds(ids));
        }
        verifyNoInteractions(baseMapper, lineMapper);
    }

    @Test
    void deleteDraftIfUnchangedUsesVersionAndStructuredProvenanceBeforeLines() {
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(lineMapper.delete(any(Wrapper.class))).thenReturn(1);

        boolean deleted = requisitionService.deleteDraftIfUnchanged(
            7001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY);

        assertTrue(deleted);
        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> header = wrapperCaptor();
        ArgumentCaptor<Wrapper<PuPurchaseRequisitionLine>> lines = wrapperCaptor();
        InOrder order = inOrder(baseMapper, lineMapper);
        order.verify(baseMapper).delete(header.capture());
        order.verify(lineMapper).delete(lines.capture());
        assertSqlContains(header.getValue(), "requisition_id", "status", "version",
            "decision_run_id", "decision_action_id", "decision_idempotency_key");
        Map<String, Object> params = parameters(header.getValue());
        assertTrue(params.containsValue(7001L));
        assertTrue(params.containsValue("DRAFT"));
        assertTrue(params.containsValue(4));
        assertTrue(params.containsValue(RUN_ID));
        assertTrue(params.containsValue(ACTION_ID));
        assertTrue(params.containsValue(IDEM_KEY));
        assertFalse(header.getValue().getSqlSegment().toLowerCase(Locale.ROOT).contains("update_time"));
    }

    @Test
    void invalidVersionMarkerFailsClosedBeforeDelete() {
        assertFalse(requisitionService.deleteDraftIfUnchanged(
            7001L, "U:" + UPDATE_MILLIS, RUN_ID, ACTION_ID, IDEM_KEY));
        assertFalse(requisitionService.deleteDraftIfUnchanged(
            7001L, "V:-1", RUN_ID, ACTION_ID, IDEM_KEY));
        assertFalse(requisitionService.deleteDraftIfUnchanged(
            7001L, "V:x", RUN_ID, ACTION_ID, IDEM_KEY));

        verifyNoInteractions(baseMapper, lineMapper);
    }

    @Test
    void manualDraftEditCannotIntroduceAnAiHeaderOrDecisionIdentity() {
        for (boolean aiHeader : List.of(true, false)) {
            PuPurchaseRequisition current = manualExistingRequisition();
            when(baseMapper.selectById(7001L)).thenReturn(current);
            PuPurchaseRequisitionBo bo = aiHeader ? decisionPurchaseBo() : purchaseBo();
            bo.setRequisitionId(7001L);
            if (!aiHeader) bo.setDecisionRunId(RUN_ID);

            ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.updateByBo(bo));
            assertTrue(error.getMessage().contains("来源"));
        }
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
        verify(lineMapper, never()).delete(any(Wrapper.class));
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void manualHeaderCannotWashLegacyAiLineProvenanceDuringEdit() {
        PuPurchaseRequisition current = manualExistingRequisition();
        when(baseMapper.selectById(7001L)).thenReturn(current);
        Mockito.lenient().when(lineMapper.selectList(any(Wrapper.class))).thenReturn(matchingLines(7001L));
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(7001L);

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.updateByBo(bo));
        assertTrue(error.getMessage().contains("来源"));
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
        verify(lineMapper, never()).delete(any(Wrapper.class));
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }
    @Test
    void updateByBoCannotConcealPersistedAiDraftBehindManualHeaderAndLines() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        when(baseMapper.selectById(7001L)).thenReturn(current);
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(7001L);

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.updateByBo(bo));
        assertTrue(error.getMessage().contains("来源"));
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
        verifyNoInteractions(lineMapper);
    }

    @Test
    void deleteByIdsMustNotHideOrderedRequisitionProvenance() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("ORDERED");
        when(baseMapper.selectById(7001L)).thenReturn(current);

        ServiceException error = assertThrows(ServiceException.class,
            () -> requisitionService.deleteByIds(new Long[]{7001L}));
        assertTrue(error.getMessage().contains("转单"));
        verify(baseMapper, never()).delete(any(Wrapper.class));
        verifyNoInteractions(lineMapper);
    }

    @Test
    void cancelClosedRequisitionWithDownstreamOrderMustNotReleaseItsSource() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("CLOSED");
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(purchaseOrderMapper.selectCount(any())).thenReturn(1L);

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.cancel(7001L));
        assertTrue(error.getMessage().contains("下游订单"));
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
        verifyNoInteractions(lineMapper);
    }
    @Test
    void updateByBoRejectsUnverifiedMrpSourceBeforeMutatingHeaderOrLines() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("DRAFT");
        current.setSourceType("MANUAL");
        current.setDecisionIdempotencyKey(null);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(7001L);
        bo.setSourceType("REQ_CONVERT");

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.updateByBo(bo));
        assertTrue(error.getMessage().contains("来源占用"));
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
        verifyNoInteractions(lineMapper);
    }
    @Test
    void updateByBoUpdatesParentOptimisticallyBeforeReplacingLines() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setSourceType("MANUAL");
        current.setDecisionRunId(null);
        current.setDecisionActionId(null);
        current.setDecisionIdempotencyKey(null);
        current.setStatus("DRAFT");
        current.setVersion(3);
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(7001L);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(manualExistingLines(7001L));
        when(baseMapper.updateById(any(PuPurchaseRequisition.class))).thenReturn(1);
        when(lineMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(lineMapper.insert(any(PuPurchaseRequisitionLine.class))).thenReturn(1);

        int rows = requisitionService.updateByBo(bo);

        assertEquals(1, rows);
        ArgumentCaptor<PuPurchaseRequisition> parent = ArgumentCaptor.forClass(PuPurchaseRequisition.class);
        InOrder order = inOrder(baseMapper, lineMapper);
        order.verify(baseMapper).updateById(parent.capture());
        order.verify(lineMapper).delete(any(Wrapper.class));
        order.verify(lineMapper).insert(any(PuPurchaseRequisitionLine.class));
        assertEquals(3, parent.getValue().getVersion());
    }

    @Test
    void updateByBoCasMissLeavesLinesUntouched() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setSourceType("MANUAL");
        current.setDecisionRunId(null);
        current.setDecisionActionId(null);
        current.setDecisionIdempotencyKey(null);
        current.setStatus("DRAFT");
        current.setVersion(3);
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(7001L);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(manualExistingLines(7001L));
        when(baseMapper.updateById(any(PuPurchaseRequisition.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> requisitionService.updateByBo(bo));

        verify(lineMapper, never()).delete(any(Wrapper.class));
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void submitClaimsDraftVersionBeforeStartingFlow() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("DRAFT");
        current.setVersion(2);
        current.setSelectFlowId(55L);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(processService.startProcessByDataId(anyString(), anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                WfMyBusiness doing = new WfMyBusiness();
                doing.setDataId("7001");
                doing.setActStatus(ActStatus.doing);
                requisitionService.afterFlowHandle(doing);
                return R.ok();
            });

        assertTrue(assertDoesNotThrow(() -> requisitionService.submit("7001", null)));

        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> cas = wrapperCaptor();
        InOrder order = inOrder(baseMapper, processService);
        order.verify(baseMapper).update(isNull(), cas.capture());
        order.verify(processService).startProcessByDataId(anyString(), anyString(), anyString(), any());
        assertSqlContains(cas.getValue(), "requisition_id", "status", "version");
        assertTrue(parameters(cas.getValue()).containsValue("DRAFT"));
        assertTrue(parameters(cas.getValue()).containsValue(2));
        assertTrue(parameters(cas.getValue()).containsValue("APPROVING"));
        assertTrue(parameters(cas.getValue()).containsValue(3));
        assertSqlSetContains(cas.getValue(), "status", "version", "update_time");
        verify(baseMapper).selectById(7001L);
        verifyNoMoreInteractions(baseMapper, lineMapper);
    }

    @Test
    void submitCasMissNeverStartsFlow() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("DRAFT");
        current.setVersion(2);
        current.setSelectFlowId(55L);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> requisitionService.submit("7001", null));

        verifyNoInteractions(processService);
    }

    @Test
    void doingFlowCallbackReturnsBeforeParsingIdOrTouchingAnyMapper() {
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("not-a-number");
        business.setActStatus(ActStatus.doing);

        assertDoesNotThrow(() -> requisitionService.afterFlowHandle(business));

        verifyNoInteractions(baseMapper, lineMapper, purchaseOrderMapper, purchaseOrderLineMapper,
            bdMaterialMapper, materialStockMapper);
    }

    @Test
    void terminalFlowCallbacksCasFromApprovingWithStatusAndVersionInSameSql() {
        when(baseMapper.selectById(7001L)).thenAnswer(invocation -> {
            PuPurchaseRequisition current = matchingExistingRequisition();
            current.setStatus("APPROVING");
            current.setVersion(4);
            return current;
        });
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        String[] actions = {ActStatus.pass, ActStatus.reject, ActStatus.retrun,
            ActStatus.revoke, ActStatus.stop, ActStatus.recall};
        for (String action : actions) {
            WfMyBusiness business = new WfMyBusiness();
            business.setDataId("7001");
            business.setActStatus(action);
            assertDoesNotThrow(() -> requisitionService.afterFlowHandle(business));
        }

        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> updated = wrapperCaptor();
        verify(baseMapper, times(actions.length)).update(isNull(), updated.capture());
        List<String> destinations = List.of("APPROVED", "REJECTED", "REJECTED", "DRAFT", "DRAFT", "DRAFT");
        for (int i = 0; i < actions.length; i++) {
            Wrapper<PuPurchaseRequisition> cas = updated.getAllValues().get(i);
            assertSqlContains(cas, "requisition_id", "status", "version");
            assertSqlSetContains(cas, "status", "version", "update_time");
            assertTrue(parameters(cas).containsValue("APPROVING"));
            assertTrue(parameters(cas).containsValue(4));
            assertTrue(parameters(cas).containsValue(5));
            assertTrue(parameters(cas).containsValue(destinations.get(i)));
        }
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
    }

    @Test
    void terminalFlowCallbacksCannotReopenClosedCancelledOrOrdered() {
        String[] sources = {"CLOSED", "CANCELLED", "ORDERED"};
        String[] actions = {ActStatus.pass, ActStatus.reject, ActStatus.retrun,
            ActStatus.revoke, ActStatus.stop, ActStatus.recall};
        for (String source : sources) {
            for (String action : actions) {
                PuPurchaseRequisition current = matchingExistingRequisition();
                current.setStatus(source);
                when(baseMapper.selectById(7001L)).thenReturn(current);
                WfMyBusiness business = new WfMyBusiness();
                business.setDataId("7001");
                business.setActStatus(action);
                assertThrows(ServiceException.class, () -> requisitionService.afterFlowHandle(business));
            }
        }
        verify(baseMapper, never()).update(isNull(), any(Wrapper.class));
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
    }

    @Test
    void closeRejectsInFlightWorkflowWithoutChangingRequisition() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("APPROVING");
        when(baseMapper.selectById(7001L)).thenReturn(current);

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.close(7001L));

        assertTrue(error.getMessage().contains("流程"), error.getMessage());
        assertTrue(error.getMessage().contains("撤回") || error.getMessage().contains("终止"), error.getMessage());
        assertEquals("APPROVING", current.getStatus());
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
        verify(baseMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void cancelRejectsInFlightWorkflowWithoutChangingRequisition() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("APPROVING");
        when(baseMapper.selectById(7001L)).thenReturn(current);

        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.cancel(7001L));

        assertTrue(error.getMessage().contains("流程"), error.getMessage());
        assertTrue(error.getMessage().contains("撤回") || error.getMessage().contains("终止"), error.getMessage());
        assertEquals("APPROVING", current.getStatus());
        verify(baseMapper, never()).updateById(any(PuPurchaseRequisition.class));
        verify(baseMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void closeAndCancelStillAcceptCreatedDraftAndApproved() {
        AtomicReference<PuPurchaseRequisition> selected = new AtomicReference<>();
        when(baseMapper.selectById(7001L)).thenAnswer(invocation -> selected.get());
        when(baseMapper.updateById(any(PuPurchaseRequisition.class))).thenReturn(1);
        for (String source : List.of("CREATED", "DRAFT", "APPROVED")) {
            PuPurchaseRequisition closing = matchingExistingRequisition();
            closing.setStatus(source);
            selected.set(closing);
            requisitionService.close(7001L);
            assertEquals("CLOSED", closing.getStatus());

            PuPurchaseRequisition cancelling = matchingExistingRequisition();
            cancelling.setStatus(source);
            selected.set(cancelling);
            requisitionService.cancel(7001L);
            assertEquals("CANCELLED", cancelling.getStatus());
        }
        verify(baseMapper, times(6)).updateById(any(PuPurchaseRequisition.class));
    }

    @Test
    void flowReturnToDraftThenPermitsCancellationOrClosure() {
        AtomicReference<PuPurchaseRequisition> selected = new AtomicReference<>();
        when(baseMapper.selectById(7001L)).thenAnswer(invocation -> selected.get());
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            selected.get().setStatus("DRAFT");
            selected.get().setVersion(5);
            return 1;
        });
        when(baseMapper.updateById(any(PuPurchaseRequisition.class))).thenReturn(1);

        for (String actStatus : List.of(ActStatus.revoke, ActStatus.stop)) {
            PuPurchaseRequisition current = matchingExistingRequisition();
            current.setStatus("APPROVING");
            selected.set(current);
            WfMyBusiness business = new WfMyBusiness();
            business.setDataId("7001");
            business.setActStatus(actStatus);

            requisitionService.afterFlowHandle(business);
            assertEquals("DRAFT", current.getStatus());
            if (ActStatus.revoke.equals(actStatus)) {
                requisitionService.cancel(7001L);
                assertEquals("CANCELLED", current.getStatus());
            } else {
                requisitionService.close(7001L);
                assertEquals("CLOSED", current.getStatus());
            }
        }
        verify(baseMapper, times(2)).update(isNull(), any(Wrapper.class));
        verify(baseMapper, times(2)).updateById(any(PuPurchaseRequisition.class));
    }

    @Test
    void afterFlowCloseAndCancelThrowWhenOptimisticUpdateMisses() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("APPROVING");
        current.setVersion(4);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        when(baseMapper.updateById(any(PuPurchaseRequisition.class))).thenReturn(0);
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("7001");
        business.setActStatus(ActStatus.pass);

        assertThrows(ServiceException.class, () -> requisitionService.afterFlowHandle(business));
        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> cas = wrapperCaptor();
        verify(baseMapper).update(isNull(), cas.capture());
        assertSqlContains(cas.getValue(), "status", "version");
        assertTrue(parameters(cas.getValue()).containsValue("APPROVING"));
        assertThrows(ServiceException.class, () -> requisitionService.close(7001L));
        current.setStatus("DRAFT");
        assertThrows(ServiceException.class, () -> requisitionService.cancel(7001L));
    }

    @Test
    void legacyConversionRejectsBeforeOrderCreationOrRequisitionCas() throws Exception {
        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.convertToPurchaseOrder(7001L));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"));
        verifyNoInteractions(baseMapper,lineMapper,purchaseOrderMapper,purchaseOrderLineMapper,autoCodeUtil);
        assertNotNull(PuPurchaseRequisitionServiceImpl.class.getMethod("convertToPurchaseOrder",Long.class).getAnnotation(Transactional.class));
    }

    /** 没有同租户物料全局锁及逐需求占用时，低库存入口不能绕过 ERP 防重直接生成申请。 */
    @Test
    void legacyConversionMustNotDuplicateOrUpgradeAnExistingCommitment() {
        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.convertToPurchaseOrder(7001L));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"));
        verifyNoInteractions(baseMapper,lineMapper,purchaseOrderMapper,purchaseOrderLineMapper,autoCodeUtil);
    }
    @Test
    void generateFromLowStockFailsClosedBeforeReadingOrWritingBusinessData() {
        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.generateFromLowStock());
        assertTrue(error.getMessage().contains("低库存"));
        verifyNoInteractions(bdMaterialMapper, materialStockMapper, baseMapper, lineMapper, autoCodeUtil);
    }

    private void assertRawDraftRejected(PuPurchaseRequisitionBo bo) {
        Long originalId = bo == null ? null : bo.getRequisitionId();
        ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"),error.getMessage());
        if (bo != null) assertEquals(originalId,bo.getRequisitionId());
        verifyNoInteractions(baseMapper,lineMapper,purchaseOrderMapper,purchaseOrderLineMapper,autoCodeUtil);
    }

    private PuPurchaseRequisitionBo purchaseBo() {
        PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
        bo.setRequisitionName("安全草稿");
        bo.setRequisitionDate(new Date(CREATE_MILLIS));
        bo.setRequiredDate(java.sql.Date.valueOf("2026-09-30"));
        bo.setSourceType("MANUAL");
        bo.setApplicantId(7L);
        bo.setApplicantName("tester");
        bo.setPuPurchaseRequisitionLineList(matchingLines(null));
        bo.getPuPurchaseRequisitionLineList().get(0).setSourceType("MANUAL");
        bo.getPuPurchaseRequisitionLineList().get(0).setSourceId(null);
        return bo;
    }

    private PuPurchaseRequisitionBo decisionPurchaseBo() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setSourceType("AI_DECISION");
        bo.getPuPurchaseRequisitionLineList().get(0).setSourceType("AI_DECISION");
        bo.getPuPurchaseRequisitionLineList().get(0).setSourceId(RUN_ID);
        bo.setDecisionRunId(RUN_ID);
        bo.setDecisionActionId(ACTION_ID);
        bo.setDecisionIdempotencyKey(IDEM_KEY);
        bo.setVersion(0);
        return bo;
    }

    private PuPurchaseRequisition manualExistingRequisition() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setSourceType("MANUAL");
        current.setDecisionRunId(null);
        current.setDecisionActionId(null);
        current.setDecisionIdempotencyKey(null);
        return current;
    }

    private List<PuPurchaseRequisitionLine> manualExistingLines(Long requisitionId) {
        List<PuPurchaseRequisitionLine> lines = matchingLines(requisitionId);
        lines.get(0).setSourceType("MANUAL");
        lines.get(0).setSourceId(null);
        return lines;
    }
    private PuPurchaseRequisition matchingExistingRequisition() {
        PuPurchaseRequisition entity = new PuPurchaseRequisition();
        entity.setRequisitionId(7001L);
        entity.setRequisitionNo("PR-7001");
        entity.setRequisitionName("安全草稿");
        entity.setApplicantId(7L);
        entity.setApplicantName("tester");
        entity.setRequisitionDate(new Date(CREATE_MILLIS));
        entity.setRequiredDate(java.sql.Date.valueOf("2026-09-30"));
        entity.setSourceType("AI_DECISION");
        entity.setStatus("DRAFT");
        entity.setDecisionRunId(RUN_ID);
        entity.setDecisionActionId(ACTION_ID);
        entity.setDecisionIdempotencyKey(IDEM_KEY);
        entity.setVersion(4);
        entity.setCreateTime(new Date(CREATE_MILLIS));
        entity.setUpdateTime(new Date(UPDATE_MILLIS));
        return entity;
    }

    private List<PuPurchaseRequisitionLine> matchingLines(Long requisitionId) {
        PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
        line.setLineId(requisitionId == null ? null : 8101L);
        line.setRequisitionId(requisitionId);
        line.setMaterialId(101L);
        line.setMaterialCode("M-101");
        line.setMaterialName("物料101");
        line.setApplyQuantity(new BigDecimal("5.0000"));
        line.setShortageQuantity(new BigDecimal("5.0000"));
        line.setRequiredDate(java.sql.Date.valueOf("2026-09-28"));
        line.setSourceType("AI_DECISION");
        line.setSourceId(RUN_ID);
        return List.of(line);
    }

    private void stubHeaderInsert(Long id) {
        doAnswer(invocation -> {
            PuPurchaseRequisition entity = invocation.getArgument(0);
            entity.setRequisitionId(id);
            entity.setCreateTime(new Date(CREATE_MILLIS));
            entity.setUpdateTime(new Date(UPDATE_MILLIS));
            return 1;
        }).when(baseMapper).insert(any(PuPurchaseRequisition.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private Map<String, Object> parameters(Wrapper<?> wrapper) {
        wrapper.getSqlSegment();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }

    private void assertSqlSetContains(Wrapper<?> wrapper, String... fragments) {
        String sql = ((LambdaUpdateWrapper<?>) wrapper).getSqlSet()
            .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), () -> "SET缺少片段 " + fragment + ": " + sql);
        }
    }

    private void assertSqlContains(Wrapper<?> wrapper, String... fragments) {
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), () -> "SQL缺少片段 " + fragment + ": " + sql);
        }
    }
}
