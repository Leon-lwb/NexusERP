package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

/** 摘要只校验原生绑定完整性，不能仅凭DTO或摘要授予写权限。 */
class ReceiptIqcBirthSupportTest {
    static Birth birth(Long unit, BigDecimal qty, String digest) {
        return new Birth("000007", 71L, 101L, 201L, unit, 4, qty, "a".repeat(64), digest,
            1700000000000L, "RCV-20261003-001", "MAT-001", "来源测试物料", 301L, "来源测试供应商");
    }
    static String hash(Birth birth) throws Exception {
        return (String) Class.forName("com.nexus.common.mq.iqc.ReceiptIqcBirthSupport")
            .getMethod("birthHash", Birth.class).invoke(null, birth);
    }
    @Test void quantityCanonicalizationKeepsSameNativeMeaning() throws Exception {
        assertEquals(hash(birth(401L, new BigDecimal("12.0000"), null)), hash(birth(401L, new BigDecimal("12"), null)));
    }
    @Test void originalUnitAndQuantityArePartOfTheFrozenHash() throws Exception {
        String original = hash(birth(401L, new BigDecimal("12"), null));
        assertNotEquals(original, hash(birth(402L, new BigDecimal("12"), null)));
        assertNotEquals(original, hash(birth(401L, new BigDecimal("13"), null)));
    }
    @Test void validShapeAndHashCannotBeReplacedByCallerVerifiedFlag() throws Exception {
        var kind = Class.forName("com.nexus.common.mq.iqc.ReceiptIqcBirthSupport");
        var validate = kind.getMethod("validate", Birth.class);
        Birth original = birth(401L, new BigDecimal("12"), null);
        Birth valid = birth(401L, new BigDecimal("12"), hash(original));
        assertDoesNotThrow(() -> validate.invoke(null, valid));
        assertThrows(
                InvocationTargetException.class,
                () ->
                        validate.invoke(
                                null, birth(401L, new BigDecimal("13"), valid.birthSha256())));
    }
    @Test void bindingIncludesNativeInspectionIdentity() throws Exception {
        var kind = Class.forName("com.nexus.common.mq.iqc.ReceiptIqcBirthSupport");
        var method = kind.getMethod("bindingHash", Long.class, Birth.class, long.class);
        Birth raw = birth(401L, new BigDecimal("12"), null); Birth valid = birth(401L, new BigDecimal("12"), hash(raw));
        assertNotEquals(method.invoke(null, 501L, valid, 1700000001000L), method.invoke(null, 502L, valid, 1700000001000L));
    }
}
