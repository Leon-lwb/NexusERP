package com.nexus.erp.bd;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.*;
import com.nexus.erp.bd.domain.bo.BdMaterialVendorTermsConfirmBo;
import com.nexus.erp.bd.domain.bo.BdUnitQuantityConfirmBo;
import com.nexus.erp.bd.mapper.BdCatalogConfirmationMapper;
import com.nexus.erp.bd.service.BdCatalogConfirmationService;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper.DatabaseClock;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅用模拟主数据与执行器回调验证确认合同，不连接数据库，不认证历史库存。 */
class BdCatalogConfirmationServiceTest {
    private static final String TENANT = "000000";
    private static final Long ROOT = 9001L, MATERIAL = 9101L, VENDOR = 9201L, RELATION = 9301L, ACTOR = 9401L;
    private static final Instant NOW = Instant.parse("2026-10-04T02:00:00Z");
    private static final LocalDateTime EFFECTIVE = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static MockedStatic<SpringUtil> spring;
    private MockedStatic<TenantHelper> tenant;
    private MockedStatic<LoginHelper> login;
    private BdCatalogConfirmationMapper mapper;
    private PuRequirementErpTransactionExecutor executor;
    private BdCatalogConfirmationService service;
    private BdUnitOfMeasure root;
    private BdMaterial material;
    private BdVendor vendor;
    private BdMaterialVendor relation;
    private boolean insideTransaction;

    @BeforeAll
    static void initConverter() {
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(new Converter() {
            @Override public <S, T> T convert(S source, Class<T> target) { return BeanUtil.copyProperties(source, target); }
        });
    }

    @AfterAll
    static void closeConverter() { spring.close(); }

    @BeforeEach
    void prepare() {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(ACTOR);
        mapper = mock(BdCatalogConfirmationMapper.class);
        executor = mock(PuRequirementErpTransactionExecutor.class);
        service = new BdCatalogConfirmationService(mapper, executor);
        when(executor.executeRepeatableRead(eq(TENANT), any())).thenAnswer(call -> {
            assertFalse(insideTransaction);
            insideTransaction = true;
            try { return ((Supplier<?>) call.getArgument(1)).get(); }
            finally { insideTransaction = false; }
        });
        root = unit(ROOT, ROOT, 1L, "数量", BigDecimal.ONE, 0, "old-unit");
        material = new BdMaterial();
        material.setMaterialId(MATERIAL); material.setTenantId(TENANT); material.setBaseUomId(ROOT);
        material.setStatus(1L); material.setDelFlag("0");
        vendor = new BdVendor();
        vendor.setVendorId(VENDOR); vendor.setTenantId(TENANT); vendor.setStatus("ACTIVE"); vendor.setDelFlag("0");
        relation = new BdMaterialVendor();
        relation.setRelationId(RELATION); relation.setMaterialId(MATERIAL); relation.setVendorId(VENDOR);
        relation.setTenantId(TENANT); relation.setStatus(1L); relation.setDelFlag("0");
        relation.setMinOrderQty(new BigDecimal("510"));
        when(mapper.lockUnit(TENANT, ROOT)).thenAnswer(call -> { assertTrue(insideTransaction); return root; });
        when(mapper.lockUnitFamily(TENANT, ROOT)).thenReturn(List.of(root));
        when(mapper.lockRelation(TENANT, RELATION)).thenAnswer(call -> { assertTrue(insideTransaction); return relation; });
        when(mapper.lockMaterial(TENANT, MATERIAL)).thenReturn(material);
        when(mapper.lockVendor(TENANT, VENDOR)).thenReturn(vendor);
        when(mapper.lockRelationIds(TENANT, MATERIAL, VENDOR)).thenReturn(List.of(RELATION));
        when(mapper.lockUnits(eq(TENANT), anyList())).thenReturn(List.of(root));
        when(mapper.databaseClock()).thenAnswer(call -> {
            assertTrue(insideTransaction);
            return new DatabaseClock(BigDecimal.valueOf(NOW.getEpochSecond()), "+08:00");
        });
        when(mapper.confirmUnit(any(), any(), any())).thenReturn(1);
        when(mapper.confirmTerms(any(), any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void cleanup() { login.close(); tenant.close(); }

    @Test
    void rootConfirmationGeneratesSelfMappingTimeVersionAndActor() {
        root.setPrimaryId(null); root.setConversionRate(null); root.setQuantityScale(null);
        root.setConversionVersion(null); root.setConversionVerifiedAt(null);
        var result = service.confirmQuantity(ROOT, quantity(null, ROOT, BigDecimal.ONE, 0));
        assertEquals(ROOT, result.getPrimaryId());
        assertEquals(0, result.getConversionRate().compareTo(BigDecimal.ONE));
        assertEquals(0, result.getQuantityScale());
        assertNotNull(result.getConversionVersion());
        assertEquals(NOW, result.getConversionVerifiedAt().toInstant());
        ArgumentCaptor<BdUnitOfMeasure> saved = ArgumentCaptor.forClass(BdUnitOfMeasure.class);
        verify(mapper).confirmUnit(saved.capture(), isNull(), eq("2026-10-04 10:00:00"));
        assertEquals(ACTOR, saved.getValue().getUpdateBy());
        verify(mapper, never()).rotateCertifiedUnitVersions(any(), any(), any(), any(), any(), any());
        verify(executor).executeRepeatableRead(eq(TENANT), any());
    }

    @Test
    void staleUnitVersionFailsWithoutWrites() {
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT, quantity("stale", ROOT, BigDecimal.ONE, 0)));
        verify(mapper, never()).confirmUnit(any(), any(), any());
    }

    @Test
    void unitCasZeroCannotBeReportedAsSuccess() {
        when(mapper.confirmUnit(any(), any(), any())).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT, quantity("old-unit", ROOT, BigDecimal.ONE, 0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 5})
    void unsupportedQuantityScaleFails(int scale) {
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT, quantity("old-unit", ROOT, BigDecimal.ONE, scale)));
        verify(mapper, never()).confirmUnit(any(), any(), any());
    }

    @Test
    void rootNonIdentityMappingFails() {
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT, quantity("old-unit", 9999L, BigDecimal.ONE, 0)));
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT, quantity("old-unit", ROOT, new BigDecimal("2"), 0)));
    }

    @Test
    void derivedConfirmationRotatesOnlyPreviouslyCertifiedCohort() {
        BdUnitOfMeasure child = unit(9002L, ROOT, 0L, "数量", new BigDecimal("10"), 0, "old-unit");
        BdUnitOfMeasure legacy = unit(9003L, ROOT, 0L, "数量", new BigDecimal("55"), null, null);
        when(mapper.lockUnit(TENANT, 9002L)).thenReturn(child);
        when(mapper.lockUnitFamily(TENANT, ROOT)).thenReturn(List.of(root, child, legacy));
        when(mapper.rotateCertifiedUnitVersions(any(), any(), any(), any(), any(), any())).thenReturn(1);
        var result = service.confirmQuantity(9002L, quantity("old-unit", ROOT, new BigDecimal("12"), 0));
        assertNotEquals("old-unit", result.getConversionVersion());
        verify(mapper).rotateCertifiedUnitVersions(eq(TENANT), eq(ROOT), eq(9002L), eq("old-unit"),
            eq(result.getConversionVersion()), eq(ACTOR));
        assertNull(legacy.getConversionVerifiedAt(), "历史单位不能由版本换代补认");
    }

    @Test
    void derivedUnitNeedsCertifiedSameDimensionRoot() {
        BdUnitOfMeasure child = unit(9002L, ROOT, 0L, "重量", BigDecimal.TEN, 0, null);
        when(mapper.lockUnit(TENANT, 9002L)).thenReturn(child);
        when(mapper.lockUnitFamily(TENANT, ROOT)).thenReturn(List.of(root, child));
        assertThrows(ServiceException.class, () -> service.confirmQuantity(9002L, quantity(null, ROOT, BigDecimal.TEN, 0)));
        root.setCategory("重量"); root.setConversionVerifiedAt(null);
        assertThrows(ServiceException.class, () -> service.confirmQuantity(9002L, quantity(null, ROOT, BigDecimal.TEN, 0)));
    }

    @Test
    void termsConfirmationUsesExplicitFactsNotLegacyMoqAndServerActor() {
        var result = service.confirmTerms(RELATION, terms(null, ROOT, ROOT, new BigDecimal("2"), BigDecimal.ONE,
            false, new BigDecimal("10"), EFFECTIVE, EXPIRES));
        assertEquals(0, new BigDecimal("2").compareTo(result.getVerifiedMoqQty()));
        assertEquals(0, new BigDecimal("510").compareTo(result.getMinOrderQty()));
        assertEquals(false, result.getAllowSplit());
        assertEquals(NOW, result.getTermsVerifiedAt().toInstant());
        assertNotNull(result.getTermsVersion());
        ArgumentCaptor<BdMaterialVendor> saved = ArgumentCaptor.forClass(BdMaterialVendor.class);
        verify(mapper).confirmTerms(saved.capture(), isNull(), eq("2026-10-01 00:00:00"),
            eq("2026-11-01 00:00:00"), eq("2026-10-04 10:00:00"));
        assertEquals(ACTOR, saved.getValue().getUpdateBy());
    }

    @Test
    void staleTermsVersionAndZeroCasFail() {
        relation.setTermsVersion("new-terms");
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms("stale")));
        verify(mapper, never()).confirmTerms(any(), any(), any(), any(), any());
        when(mapper.confirmTerms(any(), any(), any(), any(), any())).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms("new-terms")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "BLACKLIST", ""})
    void inactiveVendorFails(String status) {
        vendor.setStatus(status);
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
        verify(mapper, never()).confirmTerms(any(), any(), any(), any(), any());
    }

    @Test
    void crossTenantOrWrongMaterialIdentityFails() {
        relation.setTenantId("000001");
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
        relation.setTenantId(TENANT); relation.setMaterialId(9999L);
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
    }

    @Test
    void duplicateRelationshipFailsInsteadOfPickingOne() {
        when(mapper.lockRelationIds(TENANT, MATERIAL, VENDOR)).thenReturn(List.of(RELATION, 9302L));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
    }

    @Test
    void termsRequireExplicitMoqAndPrecisionCompatibleUnits() {
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, null, BigDecimal.ONE, true, BigDecimal.ONE, EFFECTIVE, EXPIRES)));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, new BigDecimal("0.5"), BigDecimal.ONE, true, BigDecimal.ONE, EFFECTIVE, EXPIRES)));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, BigDecimal.ONE, new BigDecimal("0.5"), true, BigDecimal.ONE, EFFECTIVE, EXPIRES)));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, BigDecimal.ONE, BigDecimal.ONE, true, new BigDecimal("0.5"), EFFECTIVE, EXPIRES)));
    }

    @Test
    void unknownExpiredFutureOrReversedDatesFail() {
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, BigDecimal.ONE, BigDecimal.ONE, true, BigDecimal.ONE, null, EXPIRES)));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, BigDecimal.ONE, BigDecimal.ONE, true, BigDecimal.ONE, EFFECTIVE, EFFECTIVE)));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, BigDecimal.ONE, BigDecimal.ONE, true, BigDecimal.ONE, EXPIRES, EFFECTIVE)));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            terms(null, ROOT, ROOT, BigDecimal.ONE, BigDecimal.ONE, true, BigDecimal.ONE, EXPIRES, EXPIRES.plusDays(1))));
    }

    @Test
    void unverifiedMissingOrDisabledUnitCannotCertifyTerms() {
        root.setConversionVersion(null);
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
        root.setConversionVersion("old-unit"); root.setStatus(0L);
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
        when(mapper.lockUnits(eq(TENANT), anyList())).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
    }

    @Test
    void unqualifiedDatabaseTimeZoneFailsWithoutWrites() {
        doReturn(new DatabaseClock(BigDecimal.valueOf(NOW.getEpochSecond()), "SYSTEM")).when(mapper).databaseClock();
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT, quantity("old-unit", ROOT, BigDecimal.ONE, 0)));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
        verify(mapper, never()).confirmUnit(any(), any(), any());
        verify(mapper, never()).confirmTerms(any(), any(), any(), any(), any());
    }

    @Test
    void missingTenantOrActorFailsBeforeTransactionOrReads() {
        tenant.when(TenantHelper::isEnable).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT, quantity("old-unit", ROOT, BigDecimal.ONE, 0)));
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        login.when(LoginHelper::getUserId).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION, validTerms(null)));
        verifyNoInteractions(executor, mapper);
    }

    @Test
    void evidenceReferenceCannotBeBlank() {
        assertThrows(ServiceException.class, () -> service.confirmQuantity(ROOT,
            new BdUnitQuantityConfirmBo("old-unit", ROOT, BigDecimal.ONE, 0, " ")));
        assertThrows(ServiceException.class, () -> service.confirmTerms(RELATION,
            new BdMaterialVendorTermsConfirmBo(null, MATERIAL, VENDOR, ROOT, ROOT, BigDecimal.ONE,
                BigDecimal.ONE, true, BigDecimal.ONE, EFFECTIVE, EXPIRES, "")));
    }

    private static BdUnitQuantityConfirmBo quantity(String version, Long primary, BigDecimal rate, int scale) {
        return new BdUnitQuantityConfirmBo(version, primary, rate, scale, "模拟资料：单位清单-E-001");
    }

    private static BdMaterialVendorTermsConfirmBo validTerms(String version) {
        return terms(version, ROOT, ROOT, BigDecimal.ONE, BigDecimal.ONE, true, BigDecimal.ONE, EFFECTIVE, EXPIRES);
    }

    private static BdMaterialVendorTermsConfirmBo terms(String version, Long order, Long moq, BigDecimal qty,
            BigDecimal multiple, Boolean split, BigDecimal pack, LocalDateTime effective, LocalDateTime expires) {
        return new BdMaterialVendorTermsConfirmBo(version, MATERIAL, VENDOR, order, moq, qty,
            multiple, split, pack, effective, expires, "模拟资料：供应商条款-E-001");
    }

    private static BdUnitOfMeasure unit(Long id, Long primary, Long isBase, String category,
            BigDecimal rate, Integer scale, String version) {
        BdUnitOfMeasure row = new BdUnitOfMeasure();
        row.setUomId(id); row.setTenantId(TENANT); row.setPrimaryId(primary); row.setIsBase(isBase);
        row.setUomName("模拟单位" + id); row.setCategory(category); row.setConversionRate(rate);
        row.setQuantityScale(scale); row.setConversionVersion(version);
        row.setConversionVerifiedAt(version == null ? null : Date.from(NOW.minusSeconds(86400)));
        row.setStatus(1L); row.setDelFlag("0"); return row;
    }
}
