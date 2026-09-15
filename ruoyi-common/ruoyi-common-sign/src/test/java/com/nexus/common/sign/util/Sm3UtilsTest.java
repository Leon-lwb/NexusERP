package com.nexus.common.sign.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SM3 摘要工具测试。
 *
 * @author nexus
 */
class Sm3UtilsTest {

    /** GB/T 32905-2016 标准测试向量：SM3("abc") */
    private static final String GM_STD_VECTOR =
        "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0";

    @Test
    @DisplayName("hashHexBytes: 'abc' 匹配 GB/T 32905-2016 标准测试向量")
    void hashHexBytes_abc_matchesGmStandardVector() {
        assertEquals(GM_STD_VECTOR, Sm3Utils.hashHexBytes("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("hashHex: 空输入产生合法 64 位十六进制")
    void hashHex_emptyInput_returns64Hex() {
        String r = Sm3Utils.hashHex();
        assertEquals(64, r.length());
        assertTrue(r.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("hashHex: 单参数因追加分隔符，结果与纯 SM3 不同（有意为之）")
    void hashHex_singlePart_differsFromPureSm3() {
        assertNotEquals(GM_STD_VECTOR, Sm3Utils.hashHex("abc"),
            "hashHex 会追加 0x1F 分隔符，不应等于纯 SM3");
    }

    @Test
    @DisplayName("hashHex: 分隔符避免拼接歧义 —— [ab,c] != [a,bc]")
    void hashHex_separatorPreventsAmbiguity() {
        assertNotEquals(Sm3Utils.hashHex("ab", "c"), Sm3Utils.hashHex("a", "bc"));
    }

    @Test
    @DisplayName("hashHex: 相同输入稳定可复现")
    void hashHex_deterministic() {
        assertEquals(Sm3Utils.hashHex("x", "y"), Sm3Utils.hashHex("x", "y"));
    }

    @Test
    @DisplayName("hashHex: null 与空串等价且不抛异常")
    void hashHex_nullTreatedAsEmpty() {
        assertEquals(Sm3Utils.hashHex((String) null), Sm3Utils.hashHex(""));
    }

    @Test
    @DisplayName("hashHexBytes: null 与空数组等价，且不抛异常")
    void hashHexBytes_nullTreatedAsEmpty() {
        assertEquals(Sm3Utils.hashHexBytes(null), Sm3Utils.hashHexBytes(new byte[0]));
    }

    @Test
    @DisplayName("hashHexBytes: 任意字节输入返回 64 位小写十六进制")
    void hashHexBytes_returns64Hex() {
        String r = Sm3Utils.hashHexBytes(new byte[]{0x00, (byte) 0xFF, 0x7F});
        assertEquals(64, r.length());
        assertTrue(r.matches("[0-9a-f]{64}"));
    }
}
