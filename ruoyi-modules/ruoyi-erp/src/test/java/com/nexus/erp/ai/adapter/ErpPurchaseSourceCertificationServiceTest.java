package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader.LineageSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseInventorySourceVerifier.InventorySnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.*;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.*;
import com.nexus.erp.pu.service.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际来源服务调用与真实分配/数量策略的接线回归；来源Mapper为替身，不是实库验收。 */
class ErpPurchaseSourceCertificationServiceTest {
    private static final String TENANT = "000001";
    private static final Instant NOW = Instant.parse("2026-10-02T04:00:00Z");
    private static final LocalDate END = LocalDate.of(2026, 10, 16);
    private final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
    private final ErpPurchaseSourceSchemaVerifier schema = mock(ErpPurchaseSourceSchemaVerifier.class);
    private final PuRequirementCompetitionPoolReader pools = mock(PuRequirementCompetitionPoolReader.class);
    private final RawRequirementDetail detail = new RawRequirementDetail();
    private CatalogSnapshot catalogValue;
    private LineageSnapshot lineageValue;
    private InventorySnapshot inventoryValue;
    private ErpPurchaseCatalogSourceReader catalog;
    private ErpPurchaseAllocationLineageReader lineage;
    private ErpPurchaseStockLineageVerifier inventory;
    private ErpPurchaseSourceCertificationService service;
    private MockedStatic<TenantHelper> tenants;

    @BeforeEach void setUp() {
        tenants = mockStatic(TenantHelper.class); tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        catalogValue = catalog("t1");
        lineageValue = new LineageSnapshot(TENANT,10L,26L,NOW,"+08:00",List.of(),Map.of());
        inventoryValue = inventory(26L,Set.of(500L));
        catalog = mock(ErpPurchaseCatalogSourceReader.class, invocation ->
            invocation.getMethod().getName().equals("readLocked") ? catalogValue : RETURNS_DEFAULTS.answer(invocation));
        lineage = mock(ErpPurchaseAllocationLineageReader.class, invocation ->
            invocation.getMethod().getName().equals("readLocked") ? lineageValue : RETURNS_DEFAULTS.answer(invocation));
        inventory = mock(ErpPurchaseStockLineageVerifier.class, invocation ->
            invocation.getMethod().getName().equals("verifyLocked") ? stockSnapshot() : RETURNS_DEFAULTS.answer(invocation));
        service = new ErpPurchaseSourceCertificationService(qualification,pools,catalog,inventory,lineage,schema);
        detail.setDetailId(101L); detail.setRequirementId(201L); detail.setTenantId(TENANT); detail.setMaterialId(10L);
        detail.setUnit("26"); detail.setStatus(1L); detail.setDelFlag("0"); detail.setRequiredQuantity(new BigDecimal("6"));
        detail.setIssuedQuantity(BigDecimal.ZERO); detail.setRequiredDate(Date.from(Instant.parse("2026-10-06T00:00:00Z")));
        var head = new RawRequirementHead(); head.setRequirementId(201L); head.setTenantId(TENANT);
        head.setStatus("CONFIRMED"); head.setDelFlag("0"); head.setPlanId(301L);
        var stock = new RawStock(); stock.setStockId(500L); stock.setTenantId(TENANT); stock.setMaterialId(10L);
        stock.setBaseUomId("26"); stock.setStockQuantity(BigDecimal.ZERO); stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setFrozenFlag(0L); stock.setVersion(0); stock.setDelFlag("0");
        var raw = new PuRequirementCompetitionPoolReader.RawLockedFacts(List.of(detail),List.of(head),List.of(stock),
            List.of(),List.of(),List.of(),List.of(),List.of());
        rawFixture=raw;
        when(pools.materialPool(TENANT,10L,END)).thenReturn(raw);
    }

    @AfterEach void tearDown() { tenants.close(); }

    @Test void sameTransactionSourcesActuallyReachAllocatorAndExactQuantityPolicy() {
        var result = prepare();
        verify(qualification, atLeastOnce()).requireSourceChainTransaction();
        verify(pools).materialPool(TENANT,10L,END);
        verify(inventory).verifyLocked(TENANT,10L);
        assertEquals(1, mockingDetails(catalog).getInvocations().stream().filter(i -> i.getMethod().getName().equals("readLocked")).count());
        assertEquals(1, mockingDetails(lineage).getInvocations().stream().filter(i -> i.getMethod().getName().equals("readLocked")).count());
        verify(catalog,never()).read(anyString(),anyLong(),anyLong());
        verify(lineage,never()).read(anyString(),anyLong());
        assertEquals(1,result.lines().size()); assertEquals(101L,result.lines().get(0).detailId());
        assertEquals(0,new BigDecimal("6").compareTo(result.lines().get(0).orderQuantity()));
        assertEquals(0,new BigDecimal("6").compareTo(result.allocation().allocations().get(0).requestableBase()));
        assertTrue(result.sourceFingerprint().matches("[0-9a-f]{64}"));
        assertThrows(UnsupportedOperationException.class,() -> result.lines().clear());
        assertThrows(UnsupportedOperationException.class,() -> result.sourceVersions().clear());
    }

    @Test void physicalTransactionFailureStopsBeforeAnySourceQuery() {
        doThrow(new ServiceException("not a physical ERP transaction")).when(qualification).requireSourceChainTransaction();
        assertThrows(ServiceException.class,this::prepare); verifyNoInteractions(schema,pools,catalog,inventory,lineage);
    }

    @Test void wrongTenantOrDisabledTenantIsRejectedBeforeTransactionOrMapper() {
        tenants.when(TenantHelper::getTenantId).thenReturn("000002");
        assertThrows(ServiceException.class,this::prepare); verifyNoInteractions(qualification,schema,pools,catalog,inventory,lineage);
    }

    @Test void incompleteStockIdCoverageCannotQualifyAllocator() {
        inventoryValue = inventory(26L,Set.of(501L));
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void stockBaseUnitDisagreementCannotQualifyAllocator() {
        inventoryValue = inventory(27L,Set.of(500L));
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void databaseDayDisagreementCannotUseJvmTodayOrProceed() {
        lineageValue = new LineageSnapshot(TENANT,10L,26L,NOW.minusSeconds(86400),"+08:00",List.of(),Map.of());
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void packagingFraction54292IsRejectedNeverRoundedToSix() {
        detail.setRequiredQuantity(new BigDecimal("5.4292"));
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void oldMrpUnitDisplayNameBlocksBeforeCandidateQuantity() {
        detail.setUnit("件"); assertThrows(ServiceException.class,this::prepare);
    }

    @Test void sourceVersionChangesFingerprintEvenWhenQuantityDoesNotChange() {
        String first = prepare().sourceFingerprint(); catalogValue = catalog("t2");
        assertNotEquals(first,prepare().sourceFingerprint());
    }

    @Test void decimalPresentationCannotInvalidateSameSourceFingerprint() {
        String first = prepare().sourceFingerprint(); detail.setRequiredQuantity(new BigDecimal("6.0000"));
        assertEquals(first,prepare().sourceFingerprint());
    }

    @Test void equalNetDemandCannotHideChangedOriginalRequiredAndIssuedFacts() {
        String previous = prepare().sourceFingerprint();
        detail.setRequiredQuantity(new BigDecimal("7")); detail.setIssuedQuantity(BigDecimal.ONE);
        var changed = prepare();
        assertEquals(0,new BigDecimal("6").compareTo(changed.lines().get(0).coveredDemandBase()));
        assertNotEquals(previous,changed.sourceFingerprint());
    }

    @Test void mrpHeaderStatusMustRemainPartOfOriginalSourceFingerprint() {
        String previous = prepare().sourceFingerprint();
        pools.materialPool(TENANT,10L,END).requirementHeads().get(0).setStatus("DRAFT");
        assertNotEquals(previous,prepare().sourceFingerprint());
    }

    @Test void laterSourceClockCannotReuseTermsExpiredSinceCatalogRead() {
        var previous = catalogValue; var oldTerms = previous.terms();
        var terms = new SupplierTerms(oldTerms.tenantId(),oldTerms.materialId(),oldTerms.termsId(),oldTerms.orderUomId(),
            oldTerms.moqUomId(),oldTerms.verifiedMoqQty(),oldTerms.orderMultiple(),oldTerms.allowSplit(),oldTerms.unitsPerPack(),
            oldTerms.termsVersion(),oldTerms.effectiveAt(),NOW.plusSeconds(1),oldTerms.verifiedAt(),true);
        catalogValue = new CatalogSnapshot(previous.tenantId(),previous.vendorId(),previous.relationId(),NOW,previous.databaseTimeZone(),
            previous.material(),terms,previous.baseUnit(),previous.orderUnit(),previous.moqUnit(),previous.sourceVersions());
        inventoryValue = new InventorySnapshot(TENANT,10L,26L,4,NOW.plusSeconds(10),"+08:00",BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,Set.of(500L),Map.of("stock:500","0"));
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void schemaRefusalOccursBeforeGuardOrBusinessSources() {
        doThrow(new ServiceException("unknown engine")).when(schema).requireTransactionalSources();
        assertThrows(ServiceException.class,this::prepare); verifyNoInteractions(pools,catalog,inventory,lineage);
    }

    @Test void inventoryMustBeLastIndependentPotentiallyWaitingRead() {
        prepare();
        // 通过真实Mock调用记录核验顺序，不模拟“已确认来源”顺序旗号。
        var lineageCall = mockingDetails(lineage).getInvocations().stream().filter(i -> i.getMethod().getName().equals("readLocked")).findFirst().orElseThrow();
        var inventoryCall = mockingDetails(inventory).getInvocations().stream().filter(i -> i.getMethod().getName().equals("verifyLocked")).findFirst().orElseThrow();
        assertTrue(lineageCall.getSequenceNumber() < inventoryCall.getSequenceNumber());
    }

    @Test void unownedReservationCannotCreateVerifiedPurchaseGap() {
        inventoryValue = new InventorySnapshot(TENANT,10L,26L,4,NOW,"+08:00",new BigDecimal("10"),
            new BigDecimal("8"),BigDecimal.ZERO,BigDecimal.ZERO,Set.of(500L),Map.of("stock:500","0"));
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void zeroGapStillRequiresCurrentTermsNotExpiredAfterCatalog() {
        detail.setIssuedQuantity(new BigDecimal("6"));
        var previous = catalogValue; var oldTerms = previous.terms();
        var terms = new SupplierTerms(oldTerms.tenantId(),oldTerms.materialId(),oldTerms.termsId(),oldTerms.orderUomId(),
            oldTerms.moqUomId(),oldTerms.verifiedMoqQty(),oldTerms.orderMultiple(),oldTerms.allowSplit(),oldTerms.unitsPerPack(),
            oldTerms.termsVersion(),oldTerms.effectiveAt(),NOW.plusSeconds(1),oldTerms.verifiedAt(),true);
        catalogValue = new CatalogSnapshot(previous.tenantId(),previous.vendorId(),previous.relationId(),NOW,previous.databaseTimeZone(),
            previous.material(),terms,previous.baseUnit(),previous.orderUnit(),previous.moqUnit(),previous.sourceVersions());
        inventoryValue = new InventorySnapshot(TENANT,10L,26L,4,NOW.plusSeconds(10),"+08:00",BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,Set.of(500L),Map.of("stock:500","0"));
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void finalInventoryClockCannotPrecedeLaterLineageProof() {
        lineageValue = new LineageSnapshot(TENANT,10L,26L,NOW.plusSeconds(10),"+08:00",List.of(),Map.of());
        assertThrows(ServiceException.class,this::prepare);
    }

    private ErpPurchaseSourceCertificationService.CertifiedPurchaseScope prepare() {
        return service.prepareLocked(TENANT,10L,20L,END,Set.of(101L));
    }
    private static InventorySnapshot inventory(Long unit,Set<Long> ids) {
        return new InventorySnapshot(TENANT,10L,unit,4,NOW,"+08:00",BigDecimal.ZERO,BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,ids,Map.of("stock:500","0","warehouse:1","1"));
    }
    private static CatalogSnapshot catalog(String version) {
        var unit = new TrustedUnit(TENANT,"26","26","数量",BigDecimal.ONE,"u1",4,NOW.minusSeconds(60),true);
        var terms = new SupplierTerms(TENANT,"10","30","26","26",BigDecimal.ONE,BigDecimal.ONE,false,
            BigDecimal.ONE,version,NOW.minusSeconds(60),NOW.plusSeconds(3600),NOW.minusSeconds(60),true);
        return new CatalogSnapshot(TENANT,"20","30",NOW,"+08:00",new Material(TENANT,"10","26",true),
            terms,unit,unit,unit,Map.of("terms:30",version,"unit:26","u1"));
    }
    private PuRequirementCompetitionPoolReader.RawLockedFacts rawFixture;
    private Map<Long,BigDecimal> owned=Map.of();
    private BigDecimal provenStockQuantityOverride;
    private ErpPurchaseStockLineageVerifier.StockLineageSnapshot stockSnapshot() {
        var stocks=rawFixture.stocks().stream().map(row->new ErpPurchaseStockLineageVerifier.CertifiedStockLineage(
            new com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper.StockRow(row.getStockId(),row.getTenantId(),row.getMaterialId(),row.getBaseUomId(),
                1L,2L,null,null,null,provenStockQuantityOverride==null?row.getStockQuantity():provenStockQuantityOverride,
                row.getReservedQuantity(),row.getFrozenFlag(),row.getVersion(),row.getDelFlag(),null,null),false,List.of(),Map.of())).toList();
        return new ErpPurchaseStockLineageVerifier.StockLineageSnapshot(inventoryValue,owned,Map.of(),stocks,Set.of(900L),Map.of("tx:900","proof-v1"));
    }
    @Test void sourceChainMustRejectRawPoolAndOriginalStockLineageMismatch() {
        provenStockQuantityOverride=new BigDecimal("510");
        assertThrows(ServiceException.class,this::prepare);
    }
    @Test void originalLineageReadFailureCannotCertifyEvenAnExplicitZeroBalance() {
        doThrow(new ServiceException("无原始库存流水")).when(inventory).verifyLocked(TENANT,10L);
        assertThrows(ServiceException.class,this::prepare);
    }

    @Test void authenticatedOwnReservationReducesItsGapInsteadOfTriggeringDuplicatePurchase() {
        var stock=rawFixture.stocks().get(0);stock.setStockQuantity(new BigDecimal("2"));stock.setReservedQuantity(new BigDecimal("2"));
        inventoryValue=new InventorySnapshot(TENANT,10L,26L,4,NOW,"+08:00",new BigDecimal("2"),new BigDecimal("2"),
            BigDecimal.ZERO,BigDecimal.ZERO,Set.of(500L),Map.of("stock:500","0"));
        owned=Map.of(101L,new BigDecimal("2"));
        assertEquals(0,new BigDecimal("4").compareTo(prepare().lines().get(0).orderQuantity()));
    }
    @Test void authenticatedFutureOwnerRemainsReservedAndCannotBeBorrowedByThisPool() {
        var stock=rawFixture.stocks().get(0);stock.setStockQuantity(new BigDecimal("2"));stock.setReservedQuantity(new BigDecimal("2"));
        inventoryValue=new InventorySnapshot(TENANT,10L,26L,4,NOW,"+08:00",new BigDecimal("2"),new BigDecimal("2"),
            BigDecimal.ZERO,BigDecimal.ZERO,Set.of(500L),Map.of("stock:500","0"));
        owned=Map.of(999L,new BigDecimal("2"));
        assertEquals(0,new BigDecimal("6").compareTo(prepare().lines().get(0).orderQuantity()));
    }
    @Test void totalAuthenticatedOwnershipMustExactlyReconcileOriginalReservedBalance() {
        owned=Map.of(101L,BigDecimal.ONE);
        assertThrows(ServiceException.class,this::prepare);
    }

}
