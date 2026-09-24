package com.nexus.erp.pu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.tenant.helper.TenantHelper;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    void createDraftReturnsGeneratedIdAndWritesBackStructuredVersion() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-7001");
        stubHeaderInsert(7001L);
        when(lineMapper.insert(any(PuPurchaseRequisitionLine.class))).thenReturn(1);

        Long id = requisitionService.createDraft(bo);

        assertEquals(7001L, id);
        assertEquals(7001L, bo.getRequisitionId());
        assertEquals("PR-7001", bo.getRequisitionNo());
        assertEquals("DRAFT", bo.getStatus());
        assertEquals(RUN_ID, bo.getDecisionRunId());
        assertEquals(ACTION_ID, bo.getDecisionActionId());
        assertEquals(IDEM_KEY, bo.getDecisionIdempotencyKey());
        assertEquals(0, bo.getVersion());
        verify(lineMapper).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void insertByBoKeepsOrdinaryUiNullKeyCreationBehavior() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-UI-1");
        stubHeaderInsert(7010L);
        when(lineMapper.insert(any(PuPurchaseRequisitionLine.class))).thenReturn(1);

        assertEquals(1, requisitionService.insertByBo(bo));

        assertEquals(7010L, bo.getRequisitionId());
        assertEquals("PR-UI-1", bo.getRequisitionNo());
        assertEquals(0, bo.getVersion());
        assertEquals(null, bo.getDecisionIdempotencyKey());
        verify(baseMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void headerInsertZeroDoesNotWriteLines() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-FAIL");
        when(baseMapper.insert(any(PuPurchaseRequisition.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void missingGeneratedHeaderIdDoesNotWriteLines() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-NO-ID");
        when(baseMapper.insert(any(PuPurchaseRequisition.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
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
    void sameDecisionKeyReturnsExistingIdWithoutSecondInsertOrLines() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        PuPurchaseRequisition existing = matchingExistingRequisition();
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(matchingLines(7001L));

        Long id = requisitionService.createDraft(bo);

        assertEquals(7001L, id);
        assertEquals(7001L, bo.getRequisitionId());
        assertEquals("PR-7001", bo.getRequisitionNo());
        assertEquals(4, bo.getVersion());
        verify(baseMapper, never()).insert(any(PuPurchaseRequisition.class));
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
        verifyNoInteractions(autoCodeUtil);
    }

    @Test
    void sameDecisionKeyWithDifferentProvenanceConflictsWithoutWriting() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        PuPurchaseRequisition existing = matchingExistingRequisition();
        existing.setDecisionRunId(RUN_ID + 1);
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verify(baseMapper, never()).insert(any(PuPurchaseRequisition.class));
        verifyNoInteractions(lineMapper, autoCodeUtil);
    }

    @Test
    void sameDecisionKeyWithDifferentPayloadScopeConflictsWithoutWriting() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        PuPurchaseRequisition existing = matchingExistingRequisition();
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        PuPurchaseRequisitionLine changed = matchingLines(7001L).get(0);
        changed.setApplyQuantity(new BigDecimal("6"));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(changed));

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verify(baseMapper, never()).insert(any(PuPurchaseRequisition.class));
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void sameDecisionKeyRejectsDelimiterCollisionBetweenMaterialCodeAndName() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        PuPurchaseRequisitionLine requested = bo.getPuPurchaseRequisitionLineList().get(0);
        requested.setMaterialCode("A|B");
        requested.setMaterialName("C");
        PuPurchaseRequisitionLine persisted = matchingLines(7001L).get(0);
        persisted.setMaterialCode("A");
        persisted.setMaterialName("B|C");
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(matchingExistingRequisition());
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(persisted));

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verify(baseMapper, never()).insert(any(PuPurchaseRequisition.class));
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void sameDecisionKeyKeepsDuplicateLineCountsWhileIgnoringOrderAndDecimalScale() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        PuPurchaseRequisitionLine requested = bo.getPuPurchaseRequisitionLineList().get(0);
        PuPurchaseRequisitionLine requestedOther = matchingLines(null).get(0);
        requestedOther.setMaterialId(202L);
        bo.setPuPurchaseRequisitionLineList(List.of(requested, requested, requestedOther));
        PuPurchaseRequisitionLine existing = matchingLines(7001L).get(0);
        existing.setApplyQuantity(new BigDecimal("5.0"));
        PuPurchaseRequisitionLine existingOther = matchingLines(7001L).get(0);
        existingOther.setMaterialId(202L);
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(matchingExistingRequisition());
        when(lineMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(existingOther, existing, existing));

        assertEquals(7001L, requisitionService.createDraft(bo));

        verify(baseMapper, never()).insert(any(PuPurchaseRequisition.class));
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void sameDecisionKeyRejectsChangedDuplicateLineCount() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        PuPurchaseRequisitionLine line = bo.getPuPurchaseRequisitionLineList().get(0);
        bo.setPuPurchaseRequisitionLineList(List.of(line, line));
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(matchingExistingRequisition());
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(matchingLines(7001L));

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verify(baseMapper, never()).insert(any(PuPurchaseRequisition.class));
    }

    @Test
    void duplicateDecisionInsertRereadsAndReturnsMatchingExistingRow() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null, matchingExistingRequisition());
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-RACE");
        when(baseMapper.insert(any(PuPurchaseRequisition.class)))
            .thenThrow(new DuplicateKeyException("duplicate decision key"));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(matchingLines(7001L));

        Long id = requisitionService.createDraft(bo);

        assertEquals(7001L, id);
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void duplicateKeyReplayReadsLinesWithCurrentReadAfterOldSnapshot() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null, matchingExistingRequisition());
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-RACE");
        when(baseMapper.insert(any(PuPurchaseRequisition.class)))
            .thenThrow(new DuplicateKeyException("duplicate decision key"));
        when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase(Locale.ROOT).contains("FOR UPDATE")
                ? matchingLines(7001L) : List.of();
        });

        assertEquals(7001L, assertDoesNotThrow(() -> requisitionService.createDraft(bo)));
        ArgumentCaptor<Wrapper<PuPurchaseRequisitionLine>> lines = wrapperCaptor();
        verify(lineMapper).selectList(lines.capture());
        assertSqlContains(lines.getValue(), "for update");
        verify(baseMapper, never()).delete(any(Wrapper.class));
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
        assertDoesNotThrow(() -> com.nexus.erp.pu.service.IPuPurchaseRequisitionService.class
            .getMethod("selectByIdForDecision", Long.class));
    }

    @Test
    void compensatedPurchaseKeyCannotBeRecreatedAfterLogicalDelete() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-NEW");
        when(baseMapper.insert(any(PuPurchaseRequisition.class)))
            .thenThrow(new DuplicateKeyException("tenant and decision key still occupied"));
        when(baseMapper.findCompensatedDecisionId("000000", IDEM_KEY)).thenReturn(7001L);

        assertTrue(requisitionService.deleteDraftIfUnchanged(7001L, "V:4", RUN_ID, ACTION_ID, IDEM_KEY));
        try (MockedStatic<TenantHelper> tenant = Mockito.mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            ServiceException error = assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));
            assertTrue(error.getMessage().contains("已补偿"), error.getMessage());
        }
        verify(baseMapper).findCompensatedDecisionId("000000", IDEM_KEY);
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void unrelatedPurchaseUniqueKeyCollisionIsNotMisclassifiedAsCompensated() {
        PuPurchaseRequisitionBo bo = decisionPurchaseBo();
        when(baseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-CODE-COLLISION");
        DuplicateKeyException duplicate = new DuplicateKeyException("duplicate business code");
        when(baseMapper.insert(any(PuPurchaseRequisition.class))).thenThrow(duplicate);
        when(baseMapper.findCompensatedDecisionId("000000", IDEM_KEY)).thenReturn(null);

        try (MockedStatic<TenantHelper> tenant = Mockito.mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            assertSame(duplicate, assertThrows(DuplicateKeyException.class, () -> requisitionService.createDraft(bo)));
        }
        verify(baseMapper).findCompensatedDecisionId("000000", IDEM_KEY);
        verify(lineMapper, never()).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void createDraftRejectsInvalidInputBeforeWriting() {
        PuPurchaseRequisitionBo withId = purchaseBo();
        withId.setRequisitionId(9L);
        PuPurchaseRequisitionBo withoutLines = new PuPurchaseRequisitionBo();

        assertThrows(IllegalArgumentException.class, () -> requisitionService.createDraft(null));
        assertThrows(ServiceException.class, () -> requisitionService.createDraft(withId));
        assertThrows(ServiceException.class, () -> requisitionService.createDraft(withoutLines));

        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }

    @Test
    void lineInsertMustAffectExactlyOneRow() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-LINE-FAIL");
        stubHeaderInsert(7002L);
        when(lineMapper.insert(any(PuPurchaseRequisitionLine.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));
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
    void updateByBoUpdatesParentOptimisticallyBeforeReplacingLines() {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("DRAFT");
        current.setVersion(3);
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(7001L);
        when(baseMapper.selectById(7001L)).thenReturn(current);
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
        current.setStatus("DRAFT");
        current.setVersion(3);
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setRequisitionId(7001L);
        when(baseMapper.selectById(7001L)).thenReturn(current);
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
    void convertThrowsAfterCreatedOrderWhenRequisitionCasMissesForTransactionRollback() throws Exception {
        PuPurchaseRequisition current = matchingExistingRequisition();
        current.setStatus("APPROVED");
        current.setVersion(4);
        when(baseMapper.selectById(7001L)).thenReturn(current);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(matchingLines(7001L));
        when(autoCodeUtil.genSerialCode("PU_ORDER_CODE", null)).thenReturn("PO-1");
        doAnswer(invocation -> {
            PuPurchaseOrder order = invocation.getArgument(0);
            order.setOrderId(9001L);
            return 1;
        }).when(purchaseOrderMapper).insert(any(PuPurchaseOrder.class));
        when(purchaseOrderLineMapper.insert(any(PuPurchaseOrderLine.class))).thenReturn(1);
        when(baseMapper.updateById(any(PuPurchaseRequisition.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> requisitionService.convertToPurchaseOrder(7001L));

        verify(purchaseOrderMapper).insert(any(PuPurchaseOrder.class));
        verify(purchaseOrderLineMapper).insert(any(PuPurchaseOrderLine.class));
        Transactional transactional = PuPurchaseRequisitionServiceImpl.class
            .getMethod("convertToPurchaseOrder", Long.class).getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(List.of(transactional.rollbackFor()).contains(Exception.class));
    }

    /** 物料状态条件必须是数值 1（启用），且不得再出现字符串 ACTIVE。 */
    @Test
    @SuppressWarnings("unchecked")
    void generateFromLowStockMaterialStatusComparedNumerically() {
        when(bdMaterialMapper.selectList(any())).thenReturn(List.of());

        requisitionService.generateFromLowStock();

        ArgumentCaptor<LambdaQueryWrapper<BdMaterial>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bdMaterialMapper).selectList(captor.capture());
        captor.getValue().getSqlSegment();
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L));
        assertFalse(captor.getValue().getParamNameValuePairs().containsValue("ACTIVE"));
    }

    private PuPurchaseRequisitionBo purchaseBo() {
        PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
        bo.setRequisitionName("安全草稿");
        bo.setRequisitionDate(new Date(CREATE_MILLIS));
        bo.setRequiredDate(java.sql.Date.valueOf("2026-09-30"));
        bo.setSourceType("AI_DECISION");
        bo.setApplicantId(7L);
        bo.setApplicantName("tester");
        bo.setPuPurchaseRequisitionLineList(matchingLines(null));
        return bo;
    }

    private PuPurchaseRequisitionBo decisionPurchaseBo() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setDecisionRunId(RUN_ID);
        bo.setDecisionActionId(ACTION_ID);
        bo.setDecisionIdempotencyKey(IDEM_KEY);
        bo.setVersion(0);
        return bo;
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
