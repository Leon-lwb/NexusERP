package com.nexus.common.mq.iqc;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.iqc.ReceiptIqcBirthPort.Birth;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 出生事实的规范完整性摘要；持久来源归属必须另由实际数据库核验，摘要不是写授权。 */
public final class ReceiptIqcBirthSupport {
    public static final String KIND = "ERP_RECEIPT_IQC_BIRTH_V2";
    private ReceiptIqcBirthSupport() { }

    public static String birthHash(Birth birth) {
        shape(birth);
        return hash(KIND, birth.tenantId(), birth.birthId(), birth.receiveId(), birth.materialId(),
            birth.baseUomId(), birth.quantityScale(), quantity(birth.quantity()), birth.receiptScopeSha256(),
            birth.capturedAt(), birth.receiveCode(), birth.materialCode(), birth.materialName(),
            birth.vendorId(), birth.vendorName());
    }

    public static void validate(Birth birth) {
        shape(birth);
        need(digest(birth.birthSha256()) && birth.birthSha256().equals(birthHash(birth)));
    }

    public static String bindingHash(Long inspectionId, Birth birth, long boundAt) {
        validate(birth); need(positive(inspectionId) && boundAt >= birth.capturedAt());
        return hash("MES_IQC_NATIVE_BINDING_V2", inspectionId, birth.tenantId(), birth.birthId(),
            birth.birthSha256(), birth.baseUomId(), birth.quantityScale(), quantity(birth.quantity()),
            birth.receiptScopeSha256(), boundAt);
    }

    private static void shape(Birth b) {
        need(b != null && b.tenantId() != null && b.tenantId().matches("[0-9]{6,20}")
            && positive(b.birthId()) && positive(b.receiveId()) && positive(b.materialId())
            && positive(b.baseUomId()) && positive(b.vendorId()) && b.quantityScale() != null
            && b.quantityScale() >= 0 && b.quantityScale() <= 4 && b.quantity() != null
            && b.quantity().signum() > 0 && b.quantity().stripTrailingZeros().scale() <= b.quantityScale()
            && b.quantity().compareTo(BigDecimal.TEN.pow(10)) < 0 && digest(b.receiptScopeSha256())
            && b.capturedAt() > 0 && text(b.receiveCode(), 64) && text(b.materialCode(), 64)
            && text(b.materialName(), 255) && text(b.vendorName(), 255));
    }
    private static String quantity(BigDecimal q) { return q.stripTrailingZeros().toPlainString(); }
    private static boolean digest(String s) { return s != null && s.matches("[0-9a-f]{64}"); }
    private static boolean text(String s, int max) { return s != null && !s.isBlank() && s.length() <= max; }
    private static boolean positive(Long id) { return id != null && id > 0; }
    private static void need(boolean good) { if (!good) throw new ServiceException("IQC_BIRTH_INTEGRITY_INVALID"); }

    private static String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                byte[] raw = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(raw.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':'); digest.update(raw); digest.update((byte) ';');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("出生来源摘要算法不可用", impossible);
        }
    }
}
