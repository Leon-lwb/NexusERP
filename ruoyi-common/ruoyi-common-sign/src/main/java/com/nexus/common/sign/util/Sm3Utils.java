package com.nexus.common.sign.util;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

/**
 * SM3 摘要工具（国密，GB/T 32905-2016）。
 * <p>本模块唯一的密码学出口；哈希链、文档摘要统一走此处。</p>
 *
 * @author nexus
 */
public final class Sm3Utils {

    /** 字段分隔符：避免 ["ab","c"] 与 ["a","bc"] 产生相同摘要 */
    private static final byte SEPARATOR = 0x1F;

    private Sm3Utils() {
    }

    /**
     * 对多个字符串字段求 SM3，字段间插入分隔符防拼接歧义。
     *
     * @param parts 待摘要字段（null 视为空串）
     * @return 64 位小写十六进制摘要
     */
    public static String hashHex(String... parts) {
        SM3Digest digest = new SM3Digest();
        if (parts != null) {
            for (String part : parts) {
                byte[] b = (part == null ? "" : part).getBytes(StandardCharsets.UTF_8);
                digest.update(b, 0, b.length);
                digest.update(SEPARATOR);
            }
        }
        return doFinalHex(digest);
    }

    /**
     * 对字节数组求 SM3（不追加分隔符）。
     *
     * @param data 字节数组（null 视为空数组）
     * @return 64 位小写十六进制摘要
     */
    public static String hashHexBytes(byte[] data) {
        SM3Digest digest = new SM3Digest();
        if (data != null && data.length > 0) {
            digest.update(data, 0, data.length);
        }
        return doFinalHex(digest);
    }

    private static String doFinalHex(SM3Digest digest) {
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return Hex.toHexString(out);
    }
}
