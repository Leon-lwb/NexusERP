package com.nexus.common.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 长度分帧的请求指纹，防止分隔符碰撞；不会输出原始业务载荷。
 */
public final class TraceHashes {
    private TraceHashes() {}

    public static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String fingerprint(Object... values) {
        StringBuilder framed = new StringBuilder();
        for (Object value : values) {
            if (value == null) {
                framed.append("-1:");
                continue;
            }
            String text = value.toString();
            framed.append(text.length()).append(':').append(text);
        }
        return sha256(framed.toString());
    }
}
