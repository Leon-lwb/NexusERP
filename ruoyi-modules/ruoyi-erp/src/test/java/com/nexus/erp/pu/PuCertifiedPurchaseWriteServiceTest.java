package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedLine;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.service.*;

import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

class PuCertifiedPurchaseWriteServiceTest {
    private static final String TENANT="000000", KEY="formal-procurement-case-0001", HASH="a".repeat(64);
    private static final LocalDate DAY=LocalDate.of(2026,10,3);
    private PuCertifiedPurchaseAuthorizer auth;
    private PuRequirementErpTransactionExecutor tx;
    private PuRequirementErpTransactionQualification qualification;
    private PuRequirementMaterialLockService locks;
    private ErpPurchaseSourceCertificationService certification;
    private PuCertifiedPurchaseWriteMapper mapper;
    private PuRequirementAllocationLedgerMapper ledger;
    private PuRequirementAllocationEventMapper events;
    private PuCertifiedPurchaseNumberReservation numbers;
    private PuCertifiedPurchaseLifecycleMapper definitions;
    private PuCertifiedPurchaseLifecycleService lifecycle;
    private List<PuRequirementAllocationLedger> recoveryRows;
    private PuCertifiedPurchaseWriteService service;
    private ErpDecisionPurchaseScopeGuard scopeGuard;
    private MockedStatic<TenantHelper> tenants;

    @BeforeEach void setup() {
        tenants=mockStatic(TenantHelper.class);
        tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        auth=mock(PuCertifiedPurchaseAuthorizer.class);tx=mock(PuRequirementErpTransactionExecutor.class);
        qualification=mock(PuRequirementErpTransactionQualification.class);locks=mock(PuRequirementMaterialLockService.class);
        certification=mock(ErpPurchaseSourceCertificationService.class);mapper=mock(PuCertifiedPurchaseWriteMapper.class);
        ledger=mock(PuRequirementAllocationLedgerMapper.class);events=mock(PuRequirementAllocationEventMapper.class);
        numbers=mock(PuCertifiedPurchaseNumberReservation.class);definitions=mock(PuCertifiedPurchaseLifecycleMapper.class);
        when(definitions.pinAllocationDefinitions()).thenReturn(List.of());
        when(definitions.lifecycleTableDefinitions()).thenReturn(List.of(
            new PuCertifiedPurchaseLifecycleMapper.TableDefinition("pu_certified_purchase_command","InnoDB"),
            new PuCertifiedPurchaseLifecycleMapper.TableDefinition("pu_requirement_allocation_ledger","InnoDB"),
            new PuCertifiedPurchaseLifecycleMapper.TableDefinition("pu_requirement_allocation_event","InnoDB")));
        List<PuCertifiedPurchaseLifecycleMapper.IndexDefinition> permanent=new ArrayList<>();
        Map.of("pu_certified_purchase_command","request_key","pu_requirement_allocation_ledger","allocation_key","pu_requirement_allocation_event","event_key")
            .forEach((table,key)->{permanent.add(new PuCertifiedPurchaseLifecycleMapper.IndexDefinition(table,"KEY",0,1,"tenant_id",null));
                permanent.add(new PuCertifiedPurchaseLifecycleMapper.IndexDefinition(table,"KEY",0,2,key,null));});
        permanent.add(new PuCertifiedPurchaseLifecycleMapper.IndexDefinition("pu_requirement_allocation_event","SEQUENCE",0,1,"tenant_id",null));
        permanent.add(new PuCertifiedPurchaseLifecycleMapper.IndexDefinition("pu_requirement_allocation_event","SEQUENCE",0,2,"allocation_id",null));
        permanent.add(new PuCertifiedPurchaseLifecycleMapper.IndexDefinition("pu_requirement_allocation_event","SEQUENCE",0,3,"event_sequence",null));
        when(definitions.lifecycleUniqueDefinitions()).thenReturn(permanent);
        when(auth.requireUser()).thenReturn(new DecisionActor(TENANT,7L,"正式测试员",Set.of("pu:requisition:add")));
        when(tx.executeRepeatableRead(eq(TENANT),any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
        when(mapper.commandTableEngine()).thenReturn("InnoDB");when(mapper.pinCommandDefinition()).thenReturn(List.of());
        when(mapper.commandUniqueKeyColumns()).thenReturn(List.of(
            new PuCertifiedPurchaseWriteMapper.CommandUniqueKeyColumn("PRIMARY",0,1,"tenant_id",null),
            new PuCertifiedPurchaseWriteMapper.CommandUniqueKeyColumn("PRIMARY",0,2,"request_key",null),
            new PuCertifiedPurchaseWriteMapper.CommandUniqueKeyColumn("uk_certified_purchase_requisition",0,1,"tenant_id",null),
            new PuCertifiedPurchaseWriteMapper.CommandUniqueKeyColumn("uk_certified_purchase_requisition",0,2,"requisition_id",null)));
        when(numbers.reserveRequisitionNumber()).thenReturn("CGSQ202610030001");
        scopeGuard=mock(ErpDecisionPurchaseScopeGuard.class);
        lifecycle=new PuCertifiedPurchaseLifecycleService(tx,qualification,locks,mock(PuRequirementMaterialGuardMapper.class),ledger,events,definitions);
        when(locks.lockExisting(TENANT,List.of(139L))).thenReturn(Map.of(139L,0));
        service=new PuCertifiedPurchaseWriteService(auth,tx,qualification,locks,certification,mapper,ledger,events,numbers,definitions,scopeGuard,lifecycle);
    }
    @AfterEach void close() { tenants.close(); }
    private PuCertifiedPurchaseCommand command() { return new PuCertifiedPurchaseCommand(KEY,139L,9L,DAY,List.of(21L,22L),HASH); }
    private void ready() {
        CertifiedPurchaseScope scope=mock(CertifiedPurchaseScope.class);
        when(scope.tenantId()).thenReturn(TENANT);when(scope.materialId()).thenReturn(139L);when(scope.vendorId()).thenReturn(9L);
        when(scope.windowEnd()).thenReturn(DAY);when(scope.sourceFingerprint()).thenReturn(HASH);
        var at = Instant.parse("2026-10-02T00:00:00Z");
        var rootUnit =
                new ErpPurchaseQuantityPolicy.TrustedUnit(
                        TENANT, "4", "4", "MASS", BigDecimal.ONE, "unit-v1", 4, at, true);
        var terms =
                new ErpPurchaseQuantityPolicy.SupplierTerms(
                        TENANT,
                        "139",
                        "700",
                        "4",
                        "4",
                        new BigDecimal("0.0001"),
                        new BigDecimal("0.0001"),
                        true,
                        BigDecimal.ONE,
                        "terms-v1",
                        at.minusSeconds(1),
                        at.plusSeconds(31536000),
                        at,
                        true);
        when(scope.catalog())
                .thenReturn(
                        new ErpPurchaseCatalogSourceReader.CatalogSnapshot(
                                TENANT,
                                "9",
                                "700",
                                at,
                                "+08:00",
                                new ErpPurchaseQuantityPolicy.Material(TENANT, "139", "4", true),
                                terms,
                                rootUnit,
                                rootUnit,
                                rootUnit,
                                Map.of(
                                        "material:139",
                                        "a".repeat(64),
                                        "vendor:9",
                                        "b".repeat(64))));
        when(scope.lines()).thenReturn(List.of(new CertifiedLine(21L,DAY,new BigDecimal("5.4292"),new BigDecimal("5.4292"),4L,4L),
            new CertifiedLine(22L,DAY,new BigDecimal("1.0000"),BigDecimal.ONE,4L,4L)));
        when(certification.prepareLocked(TENANT,139L,9L,DAY,Set.of(21L,22L))).thenReturn(scope);
        when(mapper.insertDocument(anyLong(),eq(TENANT),anyString(),anyString(),eq(7L),anyString(),anyString(),anyString(),
            isNull(),isNull(),eq(KEY))).thenReturn(1);
        when(mapper.insertLine(anyLong(),eq(TENANT),anyLong(),eq(139L),eq("4"),any(),anyString(),anyLong(),eq(7L))).thenReturn(1);
        when(ledger.insert(any())).thenReturn(1);when(events.insert(any())).thenReturn(1);
        when(mapper.insertCommand(eq(TENANT),any(),eq(DAY.toString()))).thenReturn(1);
    }
    @Test void writesHeadEachMrpLineLedgerAndAppendOnlyEventUsingCertifiedExactQuantities() {
        ready();var result=service.createManual(command());
        assertFalse(result.replayed());assertTrue(result.requisitionId()>0);assertEquals(2,result.lines().size());
        assertEquals(new BigDecimal("5.4292"),result.lines().get(0).applyQuantity());
        ArgumentCaptor<PuRequirementAllocationLedger> rows=ArgumentCaptor.forClass(PuRequirementAllocationLedger.class);
        verify(ledger,times(2)).insert(rows.capture());
        assertEquals(List.of(21L,22L),rows.getAllValues().stream().map(PuRequirementAllocationLedger::getDetailId).toList());
        assertTrue(rows.getAllValues().stream().allMatch(x->"MRP_DETAIL".equals(x.getSourceType())&&x.getOrderLineId()==null));
        ArgumentCaptor<PuRequirementAllocationEvent> history=ArgumentCaptor.forClass(PuRequirementAllocationEvent.class);
        verify(events,times(2)).insert(history.capture());
        assertTrue(history.getAllValues().stream().allMatch(x->x.getOccupiedBaseBefore().signum()==0&&x.getActorId().equals(7L)));
        verify(certification).prepareLocked(TENANT,139L,9L,DAY,Set.of(21L,22L));
        verify(mapper).insertCommand(eq(TENANT),any(),eq(DAY.toString()));
    }
    @Test void clientCannotSupplyQuantityUnitTenantIdNumberOrVerifiedFlag() {
        Set<String> fields=new HashSet<>();for(var c:PuCertifiedPurchaseCommand.class.getRecordComponents())fields.add(c.getName());
        assertEquals(Set.of("requestKey","materialId","vendorId","windowEnd","requirementDetailIds","expectedSourceFingerprint"),fields);
    }
    @Test void duplicateMrpIdentityIsNotSilentlyDeduplicated() {
        assertThrows(ServiceException.class,()->service.createManual(new PuCertifiedPurchaseCommand(KEY,139L,9L,DAY,List.of(21L,21L),null)));
        verifyNoInteractions(tx,numbers,mapper);
    }
    @Test void deniedIdentityIsCheckedBeforeAnyMapperOrNumber() {
        when(auth.requireUser()).thenThrow(new ServiceException("无权限"));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(tx,numbers,mapper);
    }
    @Test void crossTenantIsRejectedEvenIfInternalActorClaimsPermission() {
        when(auth.requireUser()).thenReturn(new DecisionActor("111111",7L,"x",Set.of("pu:requisition:add")));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(tx,numbers,mapper);
    }
    @Test void sourceFailureCannotWriteDocumentLinesOrOccupation() {
        when(certification.prepareLocked(any(),any(),any(),any(),any())).thenThrow(new ServiceException("未知来源"));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(ledger,events);
        verify(mapper,never()).insertDocument(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void frozenSourceChangeIsNotAutoApprovedOrReallocated() {
        ready();var c=new PuCertifiedPurchaseCommand(KEY,139L,9L,DAY,List.of(21L,22L),"b".repeat(64));
        assertThrows(ServiceException.class,()->service.createManual(c));verifyNoInteractions(ledger,events);
    }
    @Test void eventFailureStopsBeforeCommandCommitMarker() {
        ready();when(events.insert(any())).thenReturn(0);
        assertThrows(ServiceException.class,()->service.createManual(command()));
        verify(mapper,never()).insertCommand(any(),any(),any());
    }
    @Test void nonTransactionalCommandTableIsRejectedBeforeCodeReservation() {
        when(mapper.commandTableEngine()).thenReturn("MyISAM");
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification,ledger,events);
    }
    private PuCertifiedPurchaseWriteMapper.CommandReceipt commitAndPrepareReplay() {
        ready();var first=service.createManual(command());
        ArgumentCaptor<PuCertifiedPurchaseWriteMapper.CommandReceipt> saved=ArgumentCaptor.forClass(PuCertifiedPurchaseWriteMapper.CommandReceipt.class);
        verify(mapper).insertCommand(eq(TENANT),saved.capture(),eq(DAY.toString()));
        var receipt=saved.getValue();
        when(mapper.lockCommand(TENANT,KEY)).thenReturn(receipt);
        when(mapper.lockDocument(TENANT,first.requisitionId())).thenReturn(new PuCertifiedPurchaseWriteMapper.DocumentReceipt(
            first.requisitionId(),first.requisitionNo(),"APPROVED",3,KEY,"0","MANUAL",7L,null,null));
        when(mapper.lockLines(TENANT,first.requisitionId())).thenReturn(first.lines().stream().map(l->new PuCertifiedPurchaseWriteMapper.LineReceipt(
            l.lineId(),l.materialId(),l.requirementDetailId(),l.baseUomId().toString(),l.applyQuantity(),DAY+" 00:00:00","MRP","0")).toList());
        ArgumentCaptor<PuRequirementAllocationLedger> allocations=ArgumentCaptor.forClass(PuRequirementAllocationLedger.class);
        verify(ledger,times(2)).insert(allocations.capture());
        ArgumentCaptor<PuRequirementAllocationEvent> creates=ArgumentCaptor.forClass(PuRequirementAllocationEvent.class);
        verify(events,times(2)).insert(creates.capture());
        recoveryRows=allocations.getAllValues();
        for(var row:recoveryRows) {
            row.setCreateTime(new Date(1));when(ledger.lockByKey(TENANT,row.getAllocationKey())).thenReturn(row);
            var initial=creates.getAllValues().stream().filter(e->e.getAllocationId().equals(row.getAllocationId())).findFirst().orElseThrow();
            initial.setOccurredAt(new Date(1));when(mapper.lockCreationEvents(TENANT,row.getAllocationId())).thenReturn(List.of(initial));
        }
        clearInvocations(numbers,certification,ledger,events,mapper,tx);
        return receipt;
    }
    @Test void recoversActualOriginalDocumentAfterErpCommitWithoutMasterWritebackOrNewNumber() {
        var saved=commitAndPrepareReplay();
        var reordered=new PuCertifiedPurchaseCommand(KEY,139L,9L,DAY,List.of(22L,21L),HASH);
        var result=service.createManual(reordered);
        assertTrue(result.replayed());assertEquals(saved.requisitionId(),result.requisitionId());assertEquals("APPROVED",result.status());
        verifyNoInteractions(numbers,certification);verify(ledger,never()).insert(any());verify(events,never()).insert(any());
        verify(mapper,never()).insertCommand(any(),any(),any());
    }
    @Test void keyReuseWithDifferentBodyCannotReserveCodeOrReallocate() {
        commitAndPrepareReplay();
        assertThrows(ServiceException.class,()->service.createManual(new PuCertifiedPurchaseCommand(KEY,139L,10L,DAY,List.of(21L,22L),HASH)));
        verifyNoInteractions(numbers,certification);verify(mapper,never()).insertCommand(any(),any(),any());
    }
    @Test void compensatedKeyIsPermanentlyOccupiedInsteadOfRecreatingADeletedDraft() {
        var s=commitAndPrepareReplay();
        when(mapper.lockCommand(TENANT,KEY)).thenReturn(new PuCertifiedPurchaseWriteMapper.CommandReceipt(s.requestKey(),s.requestHash(),s.actorId(),
            s.entryType(),s.materialId(),s.vendorId(),s.requisitionId(),s.requisitionNo(),s.sourceFingerprint(),s.resultFingerprint(),"COMPENSATED",s.catalogFingerprint()));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }
    @Test void ordinaryLineEditCannotTurnPersistedCommandIntoNewAuthority() {
        var s=commitAndPrepareReplay();
        when(mapper.lockLines(TENANT,s.requisitionId())).thenReturn(List.of(new PuCertifiedPurchaseWriteMapper.LineReceipt(
            99L,139L,21L,"4",new BigDecimal("510"),DAY+" 00:00:00","MRP","0")));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }
    @Test void foreignExistingCommandOwnerIsDeniedEvenOnIdempotentReplay() {
        commitAndPrepareReplay();when(auth.requireUser()).thenReturn(new DecisionActor(TENANT,8L,"另一用户",Set.of("pu:requisition:add")));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }

    @Test void commandPinMustBeEmptyAndCannotBeSatisfiedByFabricatedRows() {
        when(mapper.pinCommandDefinition()).thenReturn(List.of(KEY));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }

    @Test void permanentRequestStoreWithoutActualUniqueKeysCannotOpenWrites() {
        ready();when(mapper.commandUniqueKeyColumns()).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }
    @Test void partialRequestKeyIndexDoesNotProvidePermanentIdempotency() {
        ready();when(mapper.commandUniqueKeyColumns()).thenReturn(List.of(
            new PuCertifiedPurchaseWriteMapper.CommandUniqueKeyColumn("PRIMARY",0,1,"tenant_id",null),
            new PuCertifiedPurchaseWriteMapper.CommandUniqueKeyColumn("PRIMARY",0,2,"request_key",16)));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }

    @Test void persistedCommandCannotRecoverIfOriginalOccupationWasRemoved() {
        commitAndPrepareReplay();when(ledger.lockByKey(any(),any())).thenReturn(null);
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }
    @Test void persistedCommandCannotRecoverWithoutOriginalAppendOnlyCreationEvent() {
        commitAndPrepareReplay();when(mapper.lockCreationEvents(any(),any())).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }

    @Test void originalDocumentSourceMarkerCannotBeChangedAfterPermanentCommandCommit() {
        var saved=commitAndPrepareReplay();
        when(mapper.lockDocument(TENANT,saved.requisitionId())).thenReturn(new PuCertifiedPurchaseWriteMapper.DocumentReceipt(
            saved.requisitionId(),saved.requisitionNo(),"APPROVED",3,KEY,"0","LEGACY",7L,null,null));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }
    @Test void originalDocumentRequesterCannotBeChangedAfterPermanentCommandCommit() {
        var saved=commitAndPrepareReplay();
        when(mapper.lockDocument(TENANT,saved.requisitionId())).thenReturn(new PuCertifiedPurchaseWriteMapper.DocumentReceipt(
            saved.requisitionId(),saved.requisitionNo(),"APPROVED",3,KEY,"0","MANUAL",8L,null,null));
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification);
    }

    private List<PuCertifiedPurchaseWriteService.FrozenLine> aiLines() {
        return List.of(new PuCertifiedPurchaseWriteService.FrozenLine(21L,4L,new BigDecimal("5.4292"),4L,new BigDecimal("5.4292"),DAY),
            new PuCertifiedPurchaseWriteService.FrozenLine(22L,4L,BigDecimal.ONE,4L,BigDecimal.ONE,DAY));
    }
    private DecisionActor aiActor(){return new DecisionActor(TENANT,7L,"正式测试员",Set.of("pu:requisition:add","ai:decision:execute"));}
    private PuCertifiedPurchaseWriteService.FrozenContract contract(String fingerprint,String termsVersion) {
        Map<String,String> metadata=new HashMap<>();metadata.put("materialVersion","a".repeat(64));metadata.put("vendorVersion","b".repeat(64));
        metadata.put("supplierTermsId","700");metadata.put("supplierTermsVersion",termsVersion);
        metadata.put("baseUomId","4");metadata.put("baseUomVersion","unit-v1");metadata.put("orderUomId","4");
        metadata.put("orderUomVersion","unit-v1");metadata.put("conversionVersion","unit-v1");
        return new PuCertifiedPurchaseWriteService.FrozenContract(fingerprint,metadata,11L);
    }
    private void aiReady() {
        ready();when(mapper.insertDocument(anyLong(),eq(TENANT),anyString(),anyString(),eq(7L),anyString(),anyString(),eq("AI_DECISION"),
            eq(100L),eq(200L),eq(KEY))).thenReturn(1);

    }
    @Test void recoverNeverReservesNumberOrEnsuresGuardOnConfirmedMiss() {
        var result=service.recoverForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1"));
        assertNull(result,"确定无永久提交时只能返回空结果，不能补建采购");
        verify(locks).lockExisting(TENANT,List.of(139L));
        verify(locks,never()).lock(any(),any());
        verifyNoInteractions(numbers,certification,ledger,events);
        verify(mapper,never()).insertCommand(any(),any(),any());
        verify(mapper,never()).insertDocument(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void internalFrozenContractCarriesExplicitRootButKeepsTwoArgumentCompatibility() {
        var names=Arrays.stream(PuCertifiedPurchaseWriteService.FrozenContract.class.getRecordComponents()).map(c->c.getName()).toList();
        assertEquals(List.of("payloadFingerprint","metadata","rootWorkOrderId"),names,"完整内部合同须有可重读的工单根，不能反解摘要");
        assertDoesNotThrow(()->PuCertifiedPurchaseWriteService.FrozenContract.class.getConstructor(String.class,Map.class),
            "保留旧两参构造，但缺根不能新建新版采购");
    }
    @Test void checksOwnershipInsideCreationRrBeforeCertificationAndAllInserts() {
        aiReady();final boolean[] inside={false};final int[] count={0};
        when(tx.executeRepeatableRead(eq(TENANT),any())).thenAnswer(invocation->{
            count[0]++;inside[0]=true;
            try{return ((Supplier<?>)invocation.getArgument(1)).get();}finally{inside[0]=false;}
        });
        doAnswer(invocation->{
            assertTrue(inside[0],"根归属只能在writer拥有的ERP RR内检查");
            assertEquals(2,count[0],"永久结果MISS后必须在最后落账事务复核来源");return null;
        }).when(scopeGuard).requireWorkOrderSourcesLocked(TENANT,11L,139L,9L,Set.of(21L,22L),DAY);
        service.createForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1"));
        var order=inOrder(scopeGuard,certification,mapper,ledger,events);
        order.verify(scopeGuard).requireWorkOrderSourcesLocked(TENANT,11L,139L,9L,Set.of(21L,22L),DAY);
        order.verify(certification).prepareLocked(TENANT,139L,9L,DAY,Set.of(21L,22L));
        order.verify(mapper).insertDocument(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        verify(locks,never()).lock(any(),any());
    }
    @Test void missingRootCompatibilityContractCannotCreateNewAiPurchase() {
        aiReady();var complete=contract("c".repeat(64),"terms-v1");
        var noRoot=new PuCertifiedPurchaseWriteService.FrozenContract(complete.payloadFingerprint(),complete.metadata());
        assertThrows(ServiceException.class,()->service.createForDecision(command(),aiActor(),100L,200L,aiLines(),noRoot),
            "保留两参构造不代表缺根的新合同可创建采购");
        verifyNoInteractions(numbers,certification,ledger,events);
    }
    @Test void ownershipRaceStopsBeforeAllBusinessRows() {
        aiReady();doThrow(new ServiceException("根归属已变化")).when(scopeGuard)
            .requireWorkOrderSourcesLocked(TENANT,11L,139L,9L,Set.of(21L,22L),DAY);
        assertThrows(ServiceException.class,()->service.createForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1")),
            "冻结后的工单归属竞态不得落头行、占用、事件或command");
        verifyNoInteractions(certification,ledger,events);
        verify(mapper,never()).insertDocument(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        verify(mapper,never()).insertCommand(any(),any(),any());
    }
    @Test void existingManualMrpAndLowStockEntriesDoNotAcquireAiRootGuard() {
        ready();assertNotNull(service.createManual(command()),"原人工入口继续复用认证writer");
        assertNotNull(service.createMrp(command()),"原MRP入口继续复用认证writer");
        assertNotNull(service.createLowStock(command()),"原低库存入口继续保持逐MRP认证");
        verifyNoInteractions(scopeGuard);
        verify(certification,times(3)).prepareLocked(TENANT,139L,9L,DAY,Set.of(21L,22L));
    }
    @Test void allAiHeadRowsLedgerEventsAndPermanentCommandRemainInSameFinalRr() {
        aiReady();final boolean[] inside={false};final int[] count={0};
        when(tx.executeRepeatableRead(eq(TENANT),any())).thenAnswer(invocation->{
            count[0]++;inside[0]=true;
            try{return ((Supplier<?>)invocation.getArgument(1)).get();}finally{inside[0]=false;}
        });
        when(numbers.reserveRequisitionNumber()).thenAnswer(invocation->{assertFalse(inside[0],"master编号必须在ERP事务外保留");return "PR-OFFLINE-001";});
        Answer<Integer> written =
                invocation -> {
                    assertTrue(inside[0], "头、行、占用、事件、command都必须在最后ERP事务内写");
                    assertEquals(2, count[0], "所有业务写入须加入相同最后ERP RR");
                    return 1;
                };
        when(mapper.insertDocument(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenAnswer(written);
        when(mapper.insertLine(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenAnswer(written);
        when(ledger.insert(any())).thenAnswer(written);when(events.insert(any())).thenAnswer(written);
        when(mapper.insertCommand(any(),any(),any())).thenAnswer(written);
        var result=service.createForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1"));
        assertEquals("DRAFT",result.status(),"原子创建只返回实际初始草稿");assertEquals(0,result.version(),"原子创建版本为零");
        assertEquals(2,count[0],"恢复查只读与创建事务必须真实分开");
    }
    private PuCertifiedPurchaseResult commitAiAndPrepareReplay() {
        aiReady();var first=service.createForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1"));
        ArgumentCaptor<PuCertifiedPurchaseWriteMapper.CommandReceipt> saved=ArgumentCaptor.forClass(PuCertifiedPurchaseWriteMapper.CommandReceipt.class);
        verify(mapper).insertCommand(eq(TENANT),saved.capture(),any());when(mapper.lockCommand(TENANT,KEY)).thenReturn(saved.getValue());
        when(mapper.lockDocument(TENANT,first.requisitionId())).thenReturn(new PuCertifiedPurchaseWriteMapper.DocumentReceipt(
            first.requisitionId(),first.requisitionNo(),"DRAFT",0,KEY,"0","AI_DECISION",7L,100L,200L));
        when(mapper.lockLines(TENANT,first.requisitionId())).thenReturn(first.lines().stream().map(l->new PuCertifiedPurchaseWriteMapper.LineReceipt(
            l.lineId(),l.materialId(),l.requirementDetailId(),l.baseUomId().toString(),l.applyQuantity(),DAY+" 00:00:00","MRP","0")).toList());
        ArgumentCaptor<PuRequirementAllocationLedger> allocations=ArgumentCaptor.forClass(PuRequirementAllocationLedger.class);
        verify(ledger,times(2)).insert(allocations.capture());
        ArgumentCaptor<PuRequirementAllocationEvent> creates=ArgumentCaptor.forClass(PuRequirementAllocationEvent.class);
        verify(events,times(2)).insert(creates.capture());
        recoveryRows=allocations.getAllValues();
        for(var row:recoveryRows) {
            row.setCreateTime(new Date(1));row.setVersion(0);when(ledger.lockByKey(TENANT,row.getAllocationKey())).thenReturn(row);
            var initial=creates.getAllValues().stream().filter(e->e.getAllocationId().equals(row.getAllocationId())).findFirst().orElseThrow();
            initial.setOccurredAt(new Date(1));when(mapper.lockCreationEvents(TENANT,row.getAllocationId())).thenReturn(List.of(initial));
            when(events.lockByAllocation(TENANT,row.getAllocationId())).thenReturn(List.of(initial));
        }
        prepareRealLifecycleReplay(first,saved.getValue());
        clearInvocations(mapper,certification,numbers,ledger,events,scopeGuard,locks);return first;
    }
    /** 完整 CREATE/v0 实际持久边界替身，生命周期服务本体不 mock。 */
    private void prepareRealLifecycleReplay(PuCertifiedPurchaseResult first,PuCertifiedPurchaseWriteMapper.CommandReceipt saved) {
        when(definitions.commandTableEngine()).thenReturn("InnoDB");when(definitions.pinCommandDefinition()).thenReturn(List.of());
        when(definitions.databaseNow()).thenReturn(new Date(1));
        when(definitions.lockMaterial(TENANT,139L)).thenReturn(new PuCertifiedPurchaseLifecycleMapper.Material(TENANT,139L,4L,1L,"0"));
        when(definitions.lockRootUnit(TENANT,4L)).thenReturn(new PuCertifiedPurchaseLifecycleMapper.RootUnit(TENANT,4L,"根单位",1L,4L,BigDecimal.ONE,4,"unit-v1",new Date(1),1L,"0"));
        var head=new PuCertifiedPurchaseLifecycleMapper.RequisitionHead(TENANT,first.requisitionId(),first.requisitionNo(),"AI_DECISION","DRAFT",KEY,0,"0");
        when(definitions.lockRequisitionHead(TENANT,first.requisitionId())).thenReturn(head);
        when(definitions.lockRequisitionStateAudit(TENANT,first.requisitionId())).thenReturn(new PuCertifiedPurchaseLifecycleMapper.RequisitionStateAudit(TENANT,first.requisitionId(),KEY,"DRAFT",0,null,new Date(1),null,"0"));
        when(definitions.lockCommand(TENANT,KEY)).thenReturn(new PuCertifiedPurchaseLifecycleMapper.CommandSource(TENANT,KEY,saved.requestHash(),7L,"AI_DECISION",139L,9L,first.requisitionId(),first.requisitionNo(),saved.sourceFingerprint(),saved.resultFingerprint(),"COMMITTED",saved.catalogFingerprint(),DAY.toString()));
        List<PuCertifiedPurchaseLifecycleMapper.RequisitionLine> lines=new ArrayList<>();List<PuCertifiedPurchaseLifecycleMapper.RequisitionApprovalAudit> audits=new ArrayList<>();
        for(var line:first.lines()) {
            var source=new PuCertifiedPurchaseLifecycleMapper.RequisitionLine(TENANT,line.lineId(),first.requisitionId(),139L,"4",line.applyQuantity(),BigDecimal.ZERO,"MRP",line.requirementDetailId(),DAY.toString(),"0");lines.add(source);
            audits.add(new PuCertifiedPurchaseLifecycleMapper.RequisitionApprovalAudit(TENANT,line.lineId(),first.requisitionId(),BigDecimal.ZERO,null,new Date(1),"0"));
            when(definitions.lockRequisitionLine(TENANT,line.lineId())).thenReturn(source);
            when(definitions.lockDemandDetail(TENANT,line.requirementDetailId())).thenReturn(new PuCertifiedPurchaseLifecycleMapper.DemandDetail(TENANT,line.requirementDetailId(),31L,139L,1L,"0"));
            when(definitions.lockOrdersForRequisitionLine(TENANT,line.lineId())).thenReturn(List.of());
            var allocation=recoveryRows.stream().filter(row->line.lineId().equals(row.getRequisitionLineId())).findFirst().orElseThrow();
            when(definitions.lockRelatedAllocations(TENANT,line.lineId(),null)).thenReturn(List.of(allocation));
        }
        when(definitions.lockDemandHead(TENANT,31L)).thenReturn(new PuCertifiedPurchaseLifecycleMapper.DemandHead(TENANT,31L,"CONFIRMED","0"));
        when(definitions.lockRequisitionLines(TENANT,first.requisitionId())).thenReturn(lines);
        when(definitions.lockRequisitionApprovalAudits(TENANT,first.requisitionId())).thenReturn(audits);
        when(definitions.lockCommandAllocations(TENANT,KEY)).thenReturn(recoveryRows);when(definitions.lockRequisitionAllocations(TENANT,first.requisitionId())).thenReturn(recoveryRows);
    }
    @Test void committedRequestRecoversAfterOwnOccupationChangedWithoutSourceRecertification() {
        var first=commitAiAndPrepareReplay();
        when(certification.prepareLocked(any(),any(),any(),any(),any())).thenThrow(new ServiceException("自己的占用已改变全局来源指纹"));
        var result=service.recoverForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1"));
        assertEquals(first.requisitionId(),result.requisitionId(),"恢复必须认领原始永久提交");
        assertTrue(result.replayed(),"恢复必须标识为真实重放");
        assertFalse(mockingDetails(lifecycle).isMock(),"完整历史认证必须走真实生命周期，不以委托调用计数代替");
        verify(events,times(2)).lockByAllocation(eq(TENANT),any());
        assertEquals("DRAFT",result.status(),"恢复回执必须仍为原创建草稿");
        assertEquals(0,result.version(),"恢复回执必须仍为原创建零版本");
        verify(locks,times(first.lines().size()+1)).lockExisting(TENANT,List.of(139L));verify(locks,never()).lock(any(),any());
        verifyNoInteractions(numbers,certification,scopeGuard);verify(ledger,never()).insert(any());verify(events,never()).insert(any());
    }
    @Test void completedRootDoesNotEraseExistingReceiptOrAllocateAgain() {
        var first=commitAiAndPrepareReplay();
        doThrow(new ServiceException("根工单后来已完工")).when(scopeGuard).requireWorkOrderSourcesLocked(any(),any(),any(),any(),any(),any());
        var result=service.createForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1"));
        assertEquals(first.requisitionId(),result.requisitionId(),"已有永久提交不能被后续根状态抹去");
        verifyNoInteractions(numbers,certification,scopeGuard);verify(locks,never()).lock(any(),any());
    }
    @Test void r5RealWriterMustRejectVersionFiveWithOnlyCreationInsteadOfEmptyMockProof() {
        commitAiAndPrepareReplay();recoveryRows.forEach(row->row.setVersion(5));
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1")),
            "R5 版本五仅 CREATE 的历史必须经真实 writer 和生命周期门禁拒绝，不能以空 mock List 通过");
        verifyNoInteractions(numbers,certification,scopeGuard);
    }

    @Test void incompleteLifecycleProofCannotBeProjectedOrBecomeANewCreation() {
        commitAiAndPrepareReplay();
        when(events.lockByAllocation(any(),any())).thenThrow(new IllegalStateException("离线完整生命周期读取不可确认"));
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1")),
            "未知完整历史必须传播，不得投影原草稿或转换为未命中");
        assertThrows(ServiceException.class,()->service.createForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1")),
            "创建入口的已有请求也必须使用完整历史核验，不能绕过");
        verifyNoInteractions(numbers,certification,scopeGuard);verify(ledger,never()).insert(any());verify(events,never()).insert(any());
    }

    @Test void recoveryConflictingActorRootRunActionOrContractIsNeverMiss() {
        commitAiAndPrepareReplay();var full=contract("c".repeat(64),"terms-v1");
        var other=new DecisionActor(TENANT,8L,"另一执行人",aiActor().permissions());
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),other,100L,200L,aiLines(),full),"其他执行人不能认领原提交");
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),aiActor(),101L,200L,aiLines(),full),"运行冲突不得返回空结果");
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),aiActor(),100L,201L,aiLines(),full),"动作冲突不得返回空结果");
        var otherRoot=new PuCertifiedPurchaseWriteService.FrozenContract(full.payloadFingerprint(),full.metadata(),12L);
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),aiActor(),100L,200L,aiLines(),otherRoot),"根身份必须进入永久请求摘要");
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v2")),
            "合同变化不得catch成MISS");
        verifyNoInteractions(numbers,certification,scopeGuard);
    }
    @Test void recoveryRequiresOriginalCreationEventAndUnmodifiedLineFingerprint() {
        commitAiAndPrepareReplay();when(mapper.lockCreationEvents(any(),any())).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.recoverForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1")),
            "没有原始CREATE事件不能仅凭结果缓存认领单据");
        verifyNoInteractions(numbers,certification,scopeGuard);
    }
    private void savedAiContract() {
        aiReady();service.createForDecision(command(),aiActor(),100L,200L,aiLines(),contract("c".repeat(64),"terms-v1"));
        ArgumentCaptor<PuCertifiedPurchaseWriteMapper.CommandReceipt> captured=ArgumentCaptor.forClass(PuCertifiedPurchaseWriteMapper.CommandReceipt.class);
        verify(mapper).insertCommand(eq(TENANT),captured.capture(),any());when(mapper.lockCommand(TENANT,KEY)).thenReturn(captured.getValue());
        clearInvocations(mapper,certification,numbers,ledger,events);
    }
    @Test void originalCompleteAiBodyCannotBeReinterpretedWithDifferentTermsVersion() {
        savedAiContract();assertThrows(ServiceException.class,()->service.createForDecision(command(),aiActor(),100L,200L,aiLines(),
            contract("c".repeat(64),"terms-v2")));verifyNoInteractions(certification,numbers);
        verify(mapper,never()).lockDocument(any(),any());
    }
    @Test void originalCompleteAiBodyCannotBeReinterpretedWithDifferentTargetFingerprint() {
        savedAiContract();assertThrows(ServiceException.class,()->service.createForDecision(command(),aiActor(),100L,200L,aiLines(),
            contract("d".repeat(64),"terms-v1")));verifyNoInteractions(certification,numbers);verify(mapper,never()).lockDocument(any(),any());
    }
    @Test void firstAiWriteMustMatchExplicitCatalogTermsBeforeAnyDocumentInsert() {
        aiReady();assertThrows(ServiceException.class,()->service.createForDecision(command(),aiActor(),100L,200L,aiLines(),
            contract("c".repeat(64),"wrong-v2")));
        verify(mapper,never()).insertDocument(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test void createMustRefuseIfPermanentAllocationAndEventUniqueDefinitionsAreMissing() {
        ready();when(definitions.lifecycleUniqueDefinitions()).thenReturn(List.of());
        assertThrows(ServiceException.class,()->service.createManual(command()));verifyNoInteractions(numbers,certification,ledger,events);
    }

}
