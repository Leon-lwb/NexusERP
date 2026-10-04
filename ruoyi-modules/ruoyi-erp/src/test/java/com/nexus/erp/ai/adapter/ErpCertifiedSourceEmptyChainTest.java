package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.*;
import com.nexus.erp.bd.domain.*;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.*;
import com.nexus.erp.pu.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 库存、原流水和完整采购三层必须独立复读真实持久出生；Mapper 替身不代表实库/API验收。 */
class ErpCertifiedSourceEmptyChainTest {
    final ErpCertifiedSourceBirthReaderTest born=new ErpCertifiedSourceBirthReaderTest();
    final ErpPurchaseInventorySourceMapper stock=mock(ErpPurchaseInventorySourceMapper.class);
    final ErpPurchaseStockLineageMapper tx=mock(ErpPurchaseStockLineageMapper.class);
    final PuRequirementCompetitionPoolReader pools=mock(PuRequirementCompetitionPoolReader.class);
    final ErpPurchaseCatalogSourceReader catalogs=mock(ErpPurchaseCatalogSourceReader.class);
    final ErpPurchaseAllocationLineageReader allocations=mock(ErpPurchaseAllocationLineageReader.class);
    final ErpPurchaseSourceSchemaVerifier schema=mock(ErpPurchaseSourceSchemaVerifier.class);
    ErpPurchaseInventorySourceVerifier inventory;ErpPurchaseStockLineageVerifier lineage;ErpPurchaseSourceCertificationService source;
    static final String T="000000";static final LocalDate END=LocalDate.of(2026,10,16);
    static final Instant NOW=Instant.ofEpochMilli(ErpCertifiedSourceBirthReaderTest.AT+86400000);
    @BeforeEach void setup(){
        born.setup();
        var m=new BdMaterial();m.setTenantId(T);m.setMaterialId(1001L);m.setBaseUomId(301L);m.setStatus(1L);m.setDelFlag("0");m.setSafetyStock(BigDecimal.ZERO);
        var u=new BdUnitOfMeasure();u.setTenantId(T);u.setUomId(301L);u.setPrimaryId(301L);u.setIsBase(1L);u.setCategory("数量");u.setConversionRate(BigDecimal.ONE);u.setQuantityScale(4);u.setConversionVersion("u-new");u.setConversionVerifiedAt(new Date(ErpCertifiedSourceBirthReaderTest.AT-1000));u.setStatus(1L);u.setDelFlag("0");
        when(stock.lockMaterial(T,1001L)).thenReturn(m);when(stock.lockUnit(T,301L)).thenReturn(u);when(stock.lockStocks(T,1001L)).thenReturn(List.of());
        when(stock.selectDatabaseClock()).thenReturn(new ErpPurchaseInventorySourceMapper.DatabaseClock(BigDecimal.valueOf(NOW.getEpochSecond()),"+08:00"));
        when(stock.selectQuantityColumns()).thenReturn(List.of(new ErpPurchaseInventorySourceMapper.QuantityColumn("bd_material","safety_stock","decimal",18,4),new ErpPurchaseInventorySourceMapper.QuantityColumn("wm_material_stock","stock_quantity","decimal",18,4),new ErpPurchaseInventorySourceMapper.QuantityColumn("wm_material_stock","reserved_quantity","decimal",18,4)));
        when(tx.pinTableDefinitions()).thenReturn(List.of());
        when(tx.selectTableEngines()).thenReturn(List.of("bd_material","bd_unit_of_measure","wm_material_stock","wm_warehouse","wm_storage_area","wm_storage_location","wm_material_stock_tx","wm_material_receive","wm_material_receive_line","wm_material_receive_detail","wm_production_issue","wm_production_issue_line","prd_material_requirement","prd_material_requirement_detail","prd_work_order").stream().map(t->new ErpPurchaseStockLineageMapper.TableEngine(t,"InnoDB")).toList());
        when(tx.lockUnit(T,301L)).thenReturn(new ErpPurchaseStockLineageMapper.UnitRow(301L,T,4,"u-new",BigDecimal.valueOf(ErpCertifiedSourceBirthReaderTest.AT-1000),"数量",1L,301L,BigDecimal.ONE,1L,"0"));
        when(tx.lockStocks(T,1001L)).thenReturn(List.of());
        when(tx.selectDatabaseClock()).thenReturn(new ErpPurchaseStockLineageMapper.DatabaseClock(BigDecimal.valueOf(NOW.getEpochSecond()),"+08:00"));
        var cols=new ArrayList<ErpPurchaseStockLineageMapper.QuantityColumn>();
        Map.of("wm_material_stock",List.of("stock_quantity","reserved_quantity"),"wm_material_stock_tx",List.of("tx_quantity","before_quantity","after_quantity"),"wm_material_receive_line",List.of("quantity_received"),"wm_material_receive_detail",List.of("quantity"),"wm_production_issue",List.of("total_qty"),"wm_production_issue_line",List.of("quantity","required_qty","issued_qty","short_qty"),"prd_material_requirement_detail",List.of("required_quantity","issued_quantity")).forEach((t,fields)->fields.forEach(f->cols.add(new ErpPurchaseStockLineageMapper.QuantityColumn(t,f,"decimal",18,4))));
        cols.add(new ErpPurchaseStockLineageMapper.QuantityColumn("bd_unit_of_measure","conversion_rate","decimal",18,6));when(tx.selectQuantityColumns()).thenReturn(cols);
        var detail=new RawRequirementDetail();detail.setTenantId(T);detail.setDetailId(101L);detail.setRequirementId(201L);detail.setMaterialId(1001L);detail.setUnit("301");detail.setRequiredQuantity(new BigDecimal("6"));detail.setIssuedQuantity(BigDecimal.ZERO);detail.setRequiredDate(Date.from(Instant.parse("2026-10-06T00:00:00Z")));detail.setStatus(1L);detail.setDelFlag("0");
        var head=new RawRequirementHead();head.setTenantId(T);head.setRequirementId(201L);head.setStatus("CONFIRMED");head.setDelFlag("0");
        when(pools.materialPool(T,1001L,END)).thenReturn(new PuRequirementCompetitionPoolReader.RawLockedFacts(List.of(detail),List.of(head),List.of(),List.of(),List.of(),List.of(),List.of(),List.of()));
        var unit=new TrustedUnit(T,"301","301","数量",BigDecimal.ONE,"u-new",4,u.getConversionVerifiedAt().toInstant(),true);
        when(catalogs.readLocked(T,1001L,401L)).thenReturn(new CatalogSnapshot(T,"401","701",NOW,"+08:00",new Material(T,"1001","301",true),new SupplierTerms(T,"1001","701","301","301",BigDecimal.ONE,BigDecimal.ONE,false,BigDecimal.ONE,"terms-new",NOW.minusSeconds(1),NOW.plusSeconds(86400),NOW.minusSeconds(1),true),unit,unit,unit,Map.of("terms:701","terms-new","unit:301","u-new")));
        when(allocations.readLocked(T,1001L)).thenReturn(new ErpPurchaseAllocationLineageReader.LineageSnapshot(T,1001L,301L,NOW,"+08:00",List.of(),Map.of()));
        inventory=new ErpPurchaseInventorySourceVerifier(stock,born.qualification,born.reader);
        lineage=new ErpPurchaseStockLineageVerifier(tx,inventory,born.qualification,born.reader);
        source=new ErpPurchaseSourceCertificationService(born.qualification,pools,catalogs,lineage,allocations,schema,null,born.reader);
    }
    @AfterEach void clean(){born.clean();}
    @Test void bornEmptyActuallyTraversesInventoryLineageAndFullSourceCertification(){
        var result=source.prepareLocked(T,1001L,401L,END,Set.of(101L));
        assertEquals(new BigDecimal("6.0000"),result.lines().get(0).coveredDemandBase().setScale(4));
        assertTrue(result.sourceFingerprint().matches("[0-9a-f]{64}"));
        verify(born.mapper,atLeast(3)).lockBirth(T,1001L);
        assertTrue(result.sourceVersions().keySet().stream().anyMatch(k->k.contains("erp_certified_source_birth:901")));
    }
    @Test void previewAndWriteProduceIdenticalCompleteFingerprintsWithoutProvisioningGuard(){
        var readOnly=mock(ErpPurchaseCompetitionSourceReader.class);
        var raw=pools.materialPool(T,1001L,END); when(readOnly.readLocked(T,1001L,END)).thenReturn(raw);
        source=new ErpPurchaseSourceCertificationService(born.qualification,pools,catalogs,lineage,allocations,schema,readOnly,born.reader);
        clearInvocations(pools);
        var preview=source.previewLocked(T,1001L,401L,END,Set.of(101L));
        verify(pools,never()).materialPool(anyString(),anyLong(),any());
        var write=source.prepareLocked(T,1001L,401L,END,Set.of(101L));
        assertEquals(write.sourceFingerprint(),preview.sourceFingerprint());
        assertEquals(write.lines(),preview.lines());
    }
    @Test void waitingAtFullBirthRecheckCannotReuseExpiredTermsAtEarlierInventoryClock(){
        var original=catalogs.readLocked(T,1001L,401L);var terms=original.terms();
        when(catalogs.readLocked(T,1001L,401L)).thenReturn(new CatalogSnapshot(original.tenantId(),original.vendorId(),original.relationId(),NOW,"+08:00",original.material(),
            new SupplierTerms(terms.tenantId(),terms.materialId(),terms.termsId(),terms.orderUomId(),terms.moqUomId(),terms.verifiedMoqQty(),terms.orderMultiple(),terms.allowSplit(),terms.unitsPerPack(),terms.termsVersion(),terms.effectiveAt(),NOW.plusSeconds(1),terms.verifiedAt(),true),
            original.baseUnit(),original.orderUnit(),original.moqUnit(),original.sourceVersions()));
        var calls=new java.util.concurrent.atomic.AtomicInteger();var delayed=new java.util.concurrent.atomic.AtomicBoolean();
        when(born.mapper.lockBirth(T,1001L)).thenAnswer(c->{if(calls.incrementAndGet()>=4)delayed.set(true);return born.birth;});
        when(stock.selectDatabaseClock()).thenAnswer(c->new ErpPurchaseInventorySourceMapper.DatabaseClock(BigDecimal.valueOf(NOW.plusSeconds(delayed.get()?10:0).getEpochSecond()),"+08:00"));
        assertThrows(ServiceException.class,()->source.prepareLocked(T,1001L,401L,END,Set.of(101L)));
    }
    @Test void noBirthStillRefusesEmptyAtFirstInventoryLayer(){when(born.mapper.lockBirth(T,1001L)).thenReturn(null);assertThrows(ServiceException.class,()->inventory.verifyLocked(T,1001L));}
    @Test void orphanOriginalTransactionRefusesAllThreeLayers(){when(born.mapper.lockAllTransactions(1001L)).thenReturn(List.of(55L));assertThrows(ServiceException.class,()->source.prepareLocked(T,1001L,401L,END,Set.of(101L)));}
}
