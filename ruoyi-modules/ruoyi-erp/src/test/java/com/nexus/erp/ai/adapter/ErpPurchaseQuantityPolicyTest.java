package com.nexus.erp.ai.adapter;

import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Demand;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Input;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Material;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.RejectionReason;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.SupplierTerms;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.TrustedUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** 不涉及 Spring、数据源或 I/O；这些仅是调用方提供的 ERP 事实。 */
class ErpPurchaseQuantityPolicyTest {
    private static final String TENANT = "000001";
    private static final String MATERIAL = "140";
    private static final String BASE = "26";
    private static final Instant SNAPSHOT = Instant.parse("2026-09-30T08:00:00Z");
    private static final Instant VERIFIED = Instant.parse("2026-09-29T08:00:00Z");
    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-31T23:59:59Z");
    private final ErpPurchaseQuantityPolicy policy = new ErpPurchaseQuantityPolicy();

    @Test
    void unverifiedPackagingCannotTurn54292PiecesIntoAnOrder() {
        Input in = input("5.4292");
        SupplierTerms unknown = terms(null, null, null, null, BASE, BASE, "v1", START, END, VERIFIED, false);
        rejected(withTerms(in, unknown), RejectionReason.UNVERIFIED_TERMS);
    }

    @Test
    void fourteenKgBelowVerifiedMoqOf510KgIsRejectedNotRaisedTo510() {
        Input in = input("14");
        rejected(withTerms(in, terms(bd("510"), bd("1"), true, bd("1"), BASE, BASE,
            "v1", START, END, VERIFIED, true)), RejectionReason.BELOW_MOQ);
    }

    @Test
    void mrpUnitDisagreeingWithVerifiedMaterialBaseIsRejected() {
        Input in = input("14");
        rejected(new Input(in.demand(), material("999"), in.terms(), in.baseUnit(), in.orderUnit(),
            in.moqUnit(), null, false), RejectionReason.UNIT_MISMATCH);
    }

    @Test
    void materialFromAnotherTenantCannotAuthorizeSource() {
        Input in = input("14");
        rejected(new Input(in.demand(), new Material("000002", MATERIAL, BASE, true), in.terms(),
            in.baseUnit(), in.orderUnit(), in.moqUnit(), null, false), RejectionReason.SCOPE_MISMATCH);
    }

    @Test
    void missingVerifiedMaterialFactCannotAuthorizeSource() {
        Input in = input("14");
        rejected(new Input(in.demand(), new Material(TENANT, MATERIAL, BASE, false), in.terms(),
            in.baseUnit(), in.orderUnit(), in.moqUnit(), null, false), RejectionReason.UNVERIFIED_SOURCE);
    }

    @Test
    void exactGramsToKilogramsAlsoConvertsMoqInGrams() {
        TrustedUnit grams = unit("27", "27", "MASS", "1", 0);
        TrustedUnit kilograms = unit("28", "27", "MASS", "1000", 4);
        Demand demand = new Demand(TENANT, MATERIAL, "27", bd("1500"), LocalDate.of(2026, 10, 2), SNAPSHOT, true);
        SupplierTerms terms = terms(bd("500"), bd("0.5"), true, bd("500"), "28", "27",
            "v2", START, END, VERIFIED, true);
        var result = policy.evaluate(new Input(demand, material("27"), terms, grams, kilograms, grams, null, false));
        assertTrue(result.allowed());
        assertNull(result.reason());
        qty("1.5", result.requestedOrderQty());
        qty("1500", result.orderedBaseQty());
        qty("0", result.excessBase());
    }

    @Test
    void exactMoqBoundaryIsAccepted() {
        Input in = input("14");
        var result = policy.evaluate(withTerms(in, terms(bd("14"), bd("2"), true, bd("1"), BASE, BASE,
            "v1", START, END, VERIFIED, true)));
        assertTrue(result.allowed());
        qty("14", result.requestedOrderQty());
        qty("14", result.orderedBaseQty());
        qty("0", result.excessBase());
    }

    @Test
    void orderingMultipleCannotBeRoundedUp() {
        Input in = input("3");
        rejected(withTerms(in, terms(bd("1"), bd("2"), true, bd("1"), BASE, BASE,
            "v1", START, END, VERIFIED, true)), RejectionReason.NOT_ORDER_MULTIPLE);
    }

    @Test
    void noSplitRequiresAnIntegralNumberOfVerifiedPacks() {
        Input in = input("5");
        rejected(withTerms(in, terms(bd("1"), bd("1"), false, bd("2"), BASE, BASE,
            "v1", START, END, VERIFIED, true)), RejectionReason.PACK_SPLIT_NOT_ALLOWED);
    }

    @Test
    void wholePacksAreAllowedWhenSplitIsForbidden() {
        Input in = input("6");
        assertTrue(policy.evaluate(withTerms(in, terms(bd("1"), bd("1"), false, bd("2"), BASE, BASE,
            "v1", START, END, VERIFIED, true))).allowed());
    }

    @Test
    void largerVerifiedCandidateIsExcessRatherThanAdditionalMrpDemand() {
        var result = policy.evaluate(withCandidate(input("14"), "15", true));
        assertFalse(result.allowed());
        assertEquals(RejectionReason.EXCESS_BASE, result.reason());
        qty("15", result.requestedOrderQty());
        qty("15", result.orderedBaseQty());
        qty("1", result.excessBase());
    }

    @Test
    void smallerCandidateCannotPretendToCoverDemand() {
        rejected(withCandidate(input("14"), "13", true), RejectionReason.INSUFFICIENT_BASE);
    }

    @Test
    void unverifiedCandidateCannotComeFromAModel() {
        rejected(withCandidate(input("14"), "14", false), RejectionReason.UNVERIFIED_SOURCE);
    }

    @Test
    void incompatiblePhysicalDimensionsAreRejectedEvenWithNumericRates() {
        Input in = input("14");
        TrustedUnit count = unit(BASE, BASE, "COUNT", "1", 4);
        rejected(new Input(in.demand(), in.material(), in.terms(), in.baseUnit(), count, count, null, false),
            RejectionReason.UNIT_MISMATCH);
    }

    @Test
    void termsFromAnotherTenantAreRejected() {
        Input in = input("14");
        SupplierTerms t = in.terms();
        rejected(withTerms(in, new SupplierTerms("000002", t.materialId(), t.termsId(), t.orderUomId(),
            t.moqUomId(), t.verifiedMoqQty(), t.orderMultiple(), t.allowSplit(), t.unitsPerPack(),
            t.termsVersion(), t.effectiveAt(), t.expiresAt(), t.verifiedAt(), true)),
            RejectionReason.SCOPE_MISMATCH);
    }

    @Test
    void unitsFromAnotherTenantAreRejected() {
        Input in = input("14");
        TrustedUnit u = in.baseUnit();
        TrustedUnit other = new TrustedUnit("000002", u.unitId(), u.baseUnitId(), u.dimensionId(),
            u.basePerUnit(), u.conversionVersion(), u.quantityScale(), u.verifiedAt(), true);
        rejected(new Input(in.demand(), in.material(), in.terms(), other, in.orderUnit(), in.moqUnit(), null, false),
            RejectionReason.SCOPE_MISMATCH);
    }

    @Test
    void supplierTermsForAnotherMaterialAreRejected() {
        Input in = input("14");
        SupplierTerms t = in.terms();
        rejected(withTerms(in, new SupplierTerms(t.tenantId(), "139", t.termsId(), t.orderUomId(),
            t.moqUomId(), t.verifiedMoqQty(), t.orderMultiple(), t.allowSplit(), t.unitsPerPack(),
            t.termsVersion(), t.effectiveAt(), t.expiresAt(), t.verifiedAt(), true)),
            RejectionReason.SCOPE_MISMATCH);
    }

    @Test
    void unitMustBelongToTheMaterialBaseUnit() {
        Input in = input("14");
        TrustedUnit wrong = unit(BASE, "999", "MASS", "1", 4);
        rejected(new Input(in.demand(), in.material(), in.terms(), wrong, wrong, wrong, null, false),
            RejectionReason.UNIT_MISMATCH);
    }

    @Test
    void mismatchedOrderUnitIdDoesNotBorrowATrustedConversion() {
        Input in = input("14");
        SupplierTerms t = in.terms();
        rejected(withTerms(in, terms(t.verifiedMoqQty(), t.orderMultiple(), t.allowSplit(), t.unitsPerPack(),
            "999", BASE, "v1", START, END, VERIFIED, true)), RejectionReason.UNIT_MISMATCH);
    }

    @Test
    void futureTermsCannotBeUsedAtSnapshot() {
        Input in = input("14");
        rejected(withTerms(in, terms(bd("1"), bd("1"), true, bd("1"), BASE, BASE,
            "v1", SNAPSHOT.plusSeconds(1), END, VERIFIED, true)), RejectionReason.TERMS_NOT_EFFECTIVE);
    }

    @Test
    void expiredTermsCannotBeUsedAtSnapshot() {
        Input in = input("14");
        rejected(withTerms(in, terms(bd("1"), bd("1"), true, bd("1"), BASE, BASE,
            "v1", START, SNAPSHOT.minusSeconds(1), VERIFIED, true)), RejectionReason.TERMS_NOT_EFFECTIVE);
    }

    @Test
    void futureTermVerificationCannotBeUsedAtSnapshot() {
        Input in = input("14");
        rejected(withTerms(in, terms(bd("1"), bd("1"), true, bd("1"), BASE, BASE,
            "v1", START, END, SNAPSHOT.plusSeconds(1), true)), RejectionReason.TERMS_NOT_EFFECTIVE);
    }

    @Test
    void futureUnitVerificationCannotBeUsedAtSnapshot() {
        Input in = input("14");
        TrustedUnit u = in.baseUnit();
        TrustedUnit future = new TrustedUnit(u.tenantId(), u.unitId(), u.baseUnitId(), u.dimensionId(),
            u.basePerUnit(), u.conversionVersion(), u.quantityScale(), SNAPSHOT.plusSeconds(1), true);
        rejected(new Input(in.demand(), in.material(), in.terms(), future, future, future, null, false),
            RejectionReason.UNVERIFIED_UNIT);
    }

    @Test
    void missingMoqOrTermsVersionIsNotAZeroMoq() {
        Input in = input("14");
        rejected(withTerms(in, terms(null, bd("1"), true, bd("1"), BASE, BASE,
            "v1", START, END, VERIFIED, true)), RejectionReason.UNVERIFIED_TERMS);
        rejected(withTerms(in, terms(bd("1"), bd("1"), true, bd("1"), BASE, BASE,
            null, START, END, VERIFIED, true)), RejectionReason.UNVERIFIED_TERMS);
    }

    @Test
    void missingSplitOrPackRuleIsNotImplicitPermission() {
        Input in = input("14");
        rejected(withTerms(in, terms(bd("1"), bd("1"), null, bd("1"), BASE, BASE,
            "v1", START, END, VERIFIED, true)), RejectionReason.UNVERIFIED_TERMS);
        rejected(withTerms(in, terms(bd("1"), bd("1"), true, null, BASE, BASE,
            "v1", START, END, VERIFIED, true)), RejectionReason.UNVERIFIED_TERMS);
    }

    @Test
    void missingConversionVersionOrQuantityScaleIsNotProof() {
        Input in = input("14");
        TrustedUnit u = in.baseUnit();
        TrustedUnit missing = new TrustedUnit(u.tenantId(), u.unitId(), u.baseUnitId(), u.dimensionId(),
            u.basePerUnit(), null, null, u.verifiedAt(), true);
        rejected(new Input(in.demand(), in.material(), in.terms(), missing, missing, missing, null, false),
            RejectionReason.UNVERIFIED_UNIT);
    }

    @Test
    void fractionalPiecesCannotIgnoreDeclaredZeroScale() {
        Input in = input("5.4292");
        TrustedUnit each = unit(BASE, BASE, "COUNT", "1", 0);
        rejected(new Input(in.demand(), in.material(), in.terms(), each, each, each, null, false),
            RejectionReason.INVALID_PRECISION);
    }

    @Test
    void nonTerminatingConversionCannotBeRounded() {
        Input in = input("1");
        TrustedUnit order = unit("28", BASE, "MASS", "3", 4);
        SupplierTerms t = terms(bd("0.1"), bd("0.1"), true, bd("1"), "28", BASE,
            "v1", START, END, VERIFIED, true);
        rejected(new Input(in.demand(), in.material(), t, in.baseUnit(), order, in.moqUnit(), null, false),
            RejectionReason.NON_EXACT_CONVERSION);
    }

    @Test
    void excessiveMagnitudeIsRejectedBeforeArithmeticOrPersistence() {
        rejected(input("1E+100"), RejectionReason.QUANTITY_OUT_OF_RANGE);
    }

    @Test
    void exponentAtBigDecimalLimitCannotOverflowTheDigitCountGuard() {
        rejected(withTerms(input("1E+2147483647"), null), RejectionReason.QUANTITY_OUT_OF_RANGE);
    }
    @Test
    void zeroOrNegativeCoveredDemandIsNotAnOrder() {
        rejected(input("0"), RejectionReason.INVALID_QUANTITY);
        rejected(input("-1"), RejectionReason.INVALID_QUANTITY);
    }

    @Test
    void validZeroExcessDoesNotDependOnBigDecimalScale() {
        var result = policy.evaluate(withCandidate(input("14.0000"), "14.0", true));
        assertTrue(result.allowed());
        qty("14", result.requestedOrderQty());
        qty("14", result.orderedBaseQty());
        qty("0", result.excessBase());
    }

    @Test
    void fifthDecimalInCoveredDemandIsAPrecisionErrorNotAnOverflow() {
        rejected(input("5.42921"), RejectionReason.INVALID_PRECISION);
    }

    @Test
    void fifthDecimalInCandidateCannotBeRoundedToFitTheSqlLine() {
        rejected(withCandidate(input("1"), "1.00001", true), RejectionReason.INVALID_PRECISION);
    }

    @Test
    void fifthDecimalInSupplierMoqIsNotAConfirmedFourDecimalTerm() {
        Input in = input("14");
        rejected(withTerms(in, terms(bd("1.00001"), bd("1"), true, bd("1"), BASE, BASE,
            "v1", START, END, VERIFIED, true)), RejectionReason.INVALID_PRECISION);
    }

    @Test
    void unverifiedMrpSourceMustBeRejected() {
        Input in = input("14");
        Demand d = in.demand();
        rejected(new Input(new Demand(d.tenantId(), d.materialId(), d.baseUomId(), d.coveredDemandBase(),
            d.requiredDate(), d.snapshotAt(), false), in.material(), in.terms(), in.baseUnit(),
            in.orderUnit(), in.moqUnit(), null, false), RejectionReason.UNVERIFIED_SOURCE);
    }

    @Test
    void missingOrZeroConversionRateCannotAuthorizeAnOrder() {
        Input in = input("14");
        TrustedUnit u = in.baseUnit();
        TrustedUnit missing = new TrustedUnit(u.tenantId(), u.unitId(), u.baseUnitId(), u.dimensionId(),
            null, u.conversionVersion(), u.quantityScale(), u.verifiedAt(), true);
        rejected(new Input(in.demand(), in.material(), in.terms(), missing, missing, missing, null, false),
            RejectionReason.UNVERIFIED_UNIT);
        TrustedUnit zero = unit(BASE, BASE, "MASS", "0", 4);
        rejected(new Input(in.demand(), in.material(), in.terms(), zero, zero, zero, null, false),
            RejectionReason.UNIT_MISMATCH);
    }

    @Test
    void termsAreStillEffectiveAtExactExpiryInstant() {
        Input in = input("14");
        assertTrue(policy.evaluate(withTerms(in, terms(bd("1"), bd("1"), true, bd("1"), BASE, BASE,
            "v1", START, SNAPSHOT, VERIFIED, true))).allowed());
    }

    private void rejected(Input in, RejectionReason reason) {
        var result = policy.evaluate(in);
        assertFalse(result.allowed(), "must fail closed");
        assertEquals(reason, result.reason());
    }

    private static void qty(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, bd(expected).compareTo(actual));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }

    private static Material material(String base) {
        return new Material(TENANT, MATERIAL, base, true);
    }

    private static TrustedUnit unit(String id, String base, String dimension, String rate, int scale) {
        return new TrustedUnit(TENANT, id, base, dimension, bd(rate), "u1", scale, VERIFIED, true);
    }

    private static SupplierTerms terms(BigDecimal moq, BigDecimal multiple, Boolean split, BigDecimal pack,
                                       String orderUom, String moqUom, String version,
                                       Instant effective, Instant expires, Instant verified, boolean trusted) {
        return new SupplierTerms(TENANT, MATERIAL, "700", orderUom, moqUom, moq, multiple, split, pack,
            version, effective, expires, verified, trusted);
    }

    private static Input input(String baseDemand) {
        TrustedUnit base = unit(BASE, BASE, "MASS", "1", 4);
        return new Input(new Demand(TENANT, MATERIAL, BASE, bd(baseDemand),
            LocalDate.of(2026, 10, 2), SNAPSHOT, true),
            material(BASE), terms(bd("1"), bd("1"), true, bd("1"), BASE, BASE, "v1", START, END, VERIFIED, true),
            base, base, base, null, false);
    }

    private static Input withTerms(Input in, SupplierTerms t) {
        return new Input(in.demand(), in.material(), t, in.baseUnit(), in.orderUnit(), in.moqUnit(),
            in.candidateOrderQty(), in.candidateVerified());
    }

    private static Input withCandidate(Input in, String qty, boolean verified) {
        return new Input(in.demand(), in.material(), in.terms(), in.baseUnit(), in.orderUnit(),
            in.moqUnit(), bd(qty), verified);
    }
}
