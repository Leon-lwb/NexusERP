package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader.LineageSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.Stock;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Material;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.SupplierTerms;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.TrustedUnit;
import com.nexus.erp.bd.domain.vo.BdBomDetailVo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.IBdBomDetailService;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.service.impl.PrdMaterialRequirementServiceImpl;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementDetail;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementHead;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader.RawLockedFacts;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 实际新生成服务与插入边界的模拟接线测试；不访问数据库，不补认历史 MRP。 */
class ErpGeneratedMrpUnitIdentityTest {
    private static final String TENANT = "000000";
    private static final Long MATERIAL_ID = 10L, BASE_ID = 26L, BOM_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-10-04T02:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"件", "包装", "999999"})
    void newlyGeneratedAndInsertedUnitUsesBaseIdentityNotDisplayName(String name) {
        Fixture fixture = new Fixture(BASE_ID, name);
        PrdMaterialRequirementDetail inserted = fixture.generateAndInsert();
        assertEquals("26", inserted.getUnit());
        assertEquals(201L, inserted.getRequirementId());
        var certified = certify(inserted, true, true, true, 4);
        assertEquals("26", certified.demands().get(0).baseUnitId());
    }

    @Test
    void missingDisplayNameCannotEraseKnownBaseIdentity() {
        assertEquals("26", new Fixture(BASE_ID, null).generateAndInsert().getUnit());
    }

    @ParameterizedTest
    @ValueSource(longs = {9007199254740993L, Long.MAX_VALUE})
    void snowflakeBaseIdentityIsWrittenAsExactDecimalString(long baseId) {
        assertEquals(Long.toString(baseId), new Fixture(baseId, "件").generateAndInsert().getUnit());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void unknownBaseIdentityCannotBeGuessedFromNameOrMaterialId(Long baseId) {
        PrdMaterialRequirementDetail inserted = new Fixture(baseId, "26").generateAndInsert();
        assertEquals(MATERIAL_ID, inserted.getMaterialId());
        assertNull(inserted.getUnit());
        assertThrows(ServiceException.class, () -> certify(inserted, true, true, true, 4));
    }

    @Test
    void missingMaterialMasterRemainsUnknownEvenWhenBomNamesMaterial() {
        Fixture fixture = new Fixture(BASE_ID, "件");
        when(fixture.materials.selectVoList(any())).thenReturn(List.of());
        PrdMaterialRequirementDetail inserted = fixture.generateAndInsert();
        assertEquals(MATERIAL_ID, inserted.getMaterialId());
        assertNull(inserted.getUnit());
        assertThrows(ServiceException.class, () -> certify(inserted, true, true, true, 4));
    }

    @Test
    void generatedFractionalQuantityKeepsExisting54292Exactly() {
        Fixture fixture = new Fixture(BASE_ID, "件");
        fixture.bomDetail.setQuantityPerParent(new BigDecimal("0.54292"));
        PrdMaterialRequirementDetail inserted = fixture.generateAndInsert();
        assertEquals(0, new BigDecimal("5.4292").compareTo(inserted.getRequiredQuantity()));
        assertEquals(0, new BigDecimal("5.4292").compareTo(inserted.getShortageQuantity()));
        assertEquals(0, new BigDecimal("5.4292").compareTo(
            certify(inserted, true, true, true, 4).demands().get(0).netDemandBase()));
        assertThrows(ServiceException.class, () -> certify(inserted, true, true, true, 0),
            "原精度合同仍由认证端复验，不能因单位 ID 正确而取整放行");
    }

    @Test
    void lossRateStillUsesExistingBomCeilingRule() {
        Fixture fixture = new Fixture(BASE_ID, "件");
        fixture.bomDetail.setLossRate(new BigDecimal("5"));
        assertEquals(0, new BigDecimal("11").compareTo(fixture.generateAndInsert().getRequiredQuantity()));
    }

    @ParameterizedTest
    @CsvSource({"false,true,true", "true,false,true", "true,true,false"})
    void generatedUnitIdentityDoesNotGrantIndependentVerification(
            boolean unitVerified, boolean materialVerified, boolean stockVerified) {
        PrdMaterialRequirementDetail inserted = new Fixture(BASE_ID, "件").generateAndInsert();
        assertThrows(ServiceException.class,
            () -> certify(inserted, unitVerified, materialVerified, stockVerified, 4));
    }

    @Test
    void suppliedLegacyDetailIsNotNormalizedOrRecertifiedByInsertion() {
        Fixture fixture = new Fixture(BASE_ID, "件");
        PrdMaterialRequirementDetail legacy = new PrdMaterialRequirementDetail();
        legacy.setMaterialId(MATERIAL_ID);
        legacy.setUnit("件");
        legacy.setRequiredQuantity(new BigDecimal("5.4292"));
        legacy.setIssuedQuantity(BigDecimal.ZERO);
        legacy.setStatus(1L);
        PrdMaterialRequirementDetail inserted = fixture.insert(legacy);
        assertEquals("件", inserted.getUnit());
        assertEquals(new BigDecimal("5.4292"), inserted.getRequiredQuantity());
        assertThrows(ServiceException.class, () -> certify(inserted, true, true, true, 4),
            "旧名称单位不能仅凭物料身份被推断为可信基本单位");
    }

    private static ErpPurchaseDemandAllocator.AllocationInput certify(PrdMaterialRequirementDetail inserted,
            boolean unitVerified, boolean materialVerified, boolean stockVerified, int scale) {
        // 使用插入边界的原始单位和数量，认证资料另行构造，绝不由物料 ID 自动授予 verified。
        RawRequirementDetail row = new RawRequirementDetail();
        row.setDetailId(101L); row.setRequirementId(201L); row.setTenantId(TENANT);
        row.setMaterialId(inserted.getMaterialId()); row.setUnit(inserted.getUnit());
        row.setRequiredQuantity(inserted.getRequiredQuantity()); row.setIssuedQuantity(inserted.getIssuedQuantity());
        row.setStatus(inserted.getStatus()); row.setDelFlag("0"); row.setRequiredDate(Date.from(NOW));
        RawRequirementHead head = new RawRequirementHead();
        head.setRequirementId(201L); head.setTenantId(TENANT); head.setStatus("CONFIRMED"); head.setDelFlag("0");
        TrustedUnit unit = new TrustedUnit(TENANT, "26", "26", "数量", BigDecimal.ONE,
            "v1", scale, NOW.minusSeconds(60), unitVerified);
        CatalogSnapshot catalog = new CatalogSnapshot(TENANT, "20", "30", NOW, "+08:00",
            new Material(TENANT, "10", "26", materialVerified),
            new SupplierTerms(TENANT, "10", "30", "26", "26", BigDecimal.ONE, BigDecimal.ONE,
                true, BigDecimal.ONE, "t1", NOW.minusSeconds(60), NOW.plusSeconds(3600), NOW.minusSeconds(60), true),
            unit, unit, unit, Map.of("unit:26", "v1", "terms:30", "t1"));
        LineageSnapshot lineage = new LineageSnapshot(TENANT, MATERIAL_ID, BASE_ID, NOW, "+08:00", List.of(), Map.of());
        RawLockedFacts raw = new RawLockedFacts(List.of(row), List.of(head), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of());
        return ErpPurchasePoolCertification.certify(catalog, lineage, raw,
            new Stock(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "26", stockVerified),
            LocalDate.of(2026, 10, 18), Set.of(101L));
    }

    private static final class Fixture {
        final PrdMaterialRequirementMapper heads = mock(PrdMaterialRequirementMapper.class);
        final PrdMaterialRequirementDetailMapper details = mock(PrdMaterialRequirementDetailMapper.class);
        final IBdBomDetailService bomDetails = mock(IBdBomDetailService.class);
        final BdMaterialMapper materials = mock(BdMaterialMapper.class);
        final BdBomMapper boms = mock(BdBomMapper.class);
        final WmMaterialStockMapper stocks = mock(WmMaterialStockMapper.class);
        final BdBomDetailVo bomDetail = new BdBomDetailVo();
        final PrdMaterialRequirementServiceImpl service = new PrdMaterialRequirementServiceImpl(
            heads, details, bomDetails, materials, boms, stocks, mock(AutoCodeUtil.class));

        Fixture(Long baseId, String baseName) {
            BdMaterialVo material = new BdMaterialVo();
            material.setMaterialId(MATERIAL_ID); material.setBaseUomId(baseId); material.setBaseUomName(baseName);
            material.setSourceType("PROCUREMENT"); material.setIsPhantom(0);
            bomDetail.setChildMaterialId(MATERIAL_ID); bomDetail.setQuantityPerParent(BigDecimal.ONE);
            when(bomDetails.selectList(any())).thenReturn(List.of(bomDetail));
            when(materials.selectVoList(any())).thenReturn(List.of(material));
            when(stocks.selectList(any())).thenReturn(List.of());
            when(heads.insert(any(PrdMaterialRequirement.class))).thenReturn(1);
            when(details.insert(any(PrdMaterialRequirementDetail.class))).thenReturn(1);
        }

        PrdMaterialRequirementDetail generateAndInsert() {
            return insert(service.importBomDetails(BOM_ID, BigDecimal.TEN).get(0));
        }

        PrdMaterialRequirementDetail insert(PrdMaterialRequirementDetail row) {
            PrdMaterialRequirement head = new PrdMaterialRequirement();
            head.setRequirementId(201L); head.setPrdMaterialRequirementDetailList(List.of(row));
            assertEquals(1, service.insertPrdMaterialRequirement(head));
            ArgumentCaptor<PrdMaterialRequirementDetail> inserted = ArgumentCaptor.forClass(PrdMaterialRequirementDetail.class);
            verify(details).insert(inserted.capture());
            return inserted.getValue();
        }
    }
}
