package com.nexus.erp.bd;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.erp.bd.domain.bo.BdMaterialVendorTermsConfirmBo;
import com.nexus.erp.bd.domain.bo.BdUnitQuantityConfirmBo;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 即使全局忽略未知属性，确认请求也必须拒绝伪造核验身份、时间和版本字段。 */
class BdCatalogConfirmationRequestTest {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @ParameterizedTest
    @ValueSource(strings = {"tenantId", "actorId", "updateBy", "verifiedAt", "conversionVerifiedAt", "conversionVersion"})
    void unitRequestRejectsServerOwnedFields(String field) {
        String body = "{\"primaryId\":9001,\"conversionRate\":1,\"quantityScale\":0,\"reference\":\"模拟资料\",\"" + field + "\":\"fake\"}";
        assertThrows(Exception.class, () -> json.readValue(body, BdUnitQuantityConfirmBo.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenantId", "actor", "updateBy", "verifiedAt", "termsVerifiedAt", "termsVersion"})
    void termsRequestRejectsServerOwnedFields(String field) {
        assertThrows(Exception.class, () -> json.readValue("{\"" + field + "\":\"fake\"}", BdMaterialVendorTermsConfirmBo.class));
    }

    @Test
    void quantityScaleAndEvidenceAreValidatedAtApiBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new BdUnitQuantityConfirmBo(null, 9001L, BigDecimal.ONE, 5, "模拟资料")).isEmpty());
            assertFalse(validator.validate(new BdUnitQuantityConfirmBo(null, 9001L, BigDecimal.ONE, 0, " ")).isEmpty());
            assertTrue(validator.validate(new BdUnitQuantityConfirmBo(null, 9001L, BigDecimal.ONE, 0, "模拟资料")).isEmpty());
        }
    }

    @Test
    void termsDateInputHasExplicitShanghaiWallClockFormat() throws Exception {
        var request = json.readValue("""
            {"materialId":"9101","vendorId":"9201","orderUomId":"9001","moqUomId":"9001",
             "verifiedMoqQty":2,"orderMultiple":1,"allowSplit":false,"unitsPerPack":10,
             "termsEffectiveAt":"2026-10-01 00:00:00","termsExpiresAt":"2026-11-01 00:00:00","reference":"模拟资料"}
            """, BdMaterialVendorTermsConfirmBo.class);
        assertEquals(0, request.termsEffectiveAt().getHour());
        assertNull(request.expectedVersion());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }
}
