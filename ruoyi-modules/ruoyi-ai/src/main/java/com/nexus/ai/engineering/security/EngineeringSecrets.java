package com.nexus.ai.engineering.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** 随机凭证及单向摘要；不得记录凭证原文。 */
public final class EngineeringSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();

    private EngineeringSecrets() {}

    /** 生成 256 位不可猜随机凭证。 */
    public static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 计算安全命名空间内的 SHA-256 摘要。 */
    public static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行时缺少SHA-256", e);
        }
    }

    /** 使用定长摘要进行常量时间比较。 */
    public static boolean matches(String expected, String raw) {
        return expected != null
                && raw != null
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        hash(raw).getBytes(StandardCharsets.US_ASCII));
    }
}
