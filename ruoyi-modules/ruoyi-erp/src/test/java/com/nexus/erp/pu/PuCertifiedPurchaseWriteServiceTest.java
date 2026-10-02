package com.nexus.erp.pu;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedLine;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.service.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

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
    private PuCertifiedPurchaseWriteService service;
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
        service=new PuCertifiedPurchaseWriteService(auth,tx,qualification,locks,certification,mapper,ledger,events,numbers,definitions);
    }
    @AfterEach void close() { tenants.close(); }
    private PuCertifiedPurchaseCommand command() { return new PuCertifiedPurchaseCommand(KEY,139L,9L,DAY,List.of(21L,22L),HASH); }
    private void ready() {
        CertifiedPurchaseScope scope=mock(CertifiedPurchaseScope.class);
        when(scope.tenantId()).thenReturn(TENANT);when(scope.materialId()).thenReturn(139L);when(scope.vendorId()).thenReturn(9L);
        when(scope.windowEnd()).thenReturn(DAY);when(scope.sourceFingerprint()).thenReturn(HASH);
        var at=java.time.Instant.parse("2026-10-02T00:00:00Z");
        var rootUnit=new com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.TrustedUnit(TENANT,"4","4","MASS",BigDecimal.ONE,"unit-v1",4,at,true);
        var terms=new com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.SupplierTerms(TENANT,"139","700","4","4",new BigDecimal("0.0001"),
            new BigDecimal("0.0001"),true,BigDecimal.ONE,"terms-v1",at.minusSeconds(1),at.plusSeconds(31536000),at,true);
        when(scope.catalog()).thenReturn(new com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot(TENANT,"9","700",at,"+08:00",
            new com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Material(TENANT,"139","4",true),terms,rootUnit,rootUnit,rootUnit,
            Map.of("material:139","a".repeat(64),"vendor:9","b".repeat(64))));
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
        for(var row:allocations.getAllValues()) {
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
        return new PuCertifiedPurchaseWriteService.FrozenContract(fingerprint,metadata);
    }
    private void aiReady() {
        ready();when(mapper.insertDocument(anyLong(),eq(TENANT),anyString(),anyString(),eq(7L),anyString(),anyString(),eq("AI_DECISION"),
            eq(100L),eq(200L),eq(KEY))).thenReturn(1);

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
