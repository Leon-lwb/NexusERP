package com.nexus.erp.pu.service.support;
import com.nexus.common.core.exception.ServiceException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
/** Length-prefixed canonical digest, shared with the writer's frozen result contract. */
public final class PuCertifiedPurchaseLifecycleSupport {
    private PuCertifiedPurchaseLifecycleSupport() { }
    public static String hash(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new ServiceException("可信采购生命周期摘要不可用"); }
    }
    public static boolean decimal(BigDecimal value) {
        if (value == null || value.signum() < 0) return false;
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() <= 4 && (long) normalized.precision() - normalized.scale() <= 14;
    }
    public static boolean positive(Long value) { return value != null && value > 0; }
    public static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.equals(value.strip()) && value.length() <= max;
    }
}
