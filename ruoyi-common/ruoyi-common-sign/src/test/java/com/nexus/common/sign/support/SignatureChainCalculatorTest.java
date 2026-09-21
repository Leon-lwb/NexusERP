package com.nexus.common.sign.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 哈希链计算器测试。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class SignatureChainCalculatorTest {

    private static final long T = 1_755_000_000_000L;

    @Test
    @DisplayName("compute: 稳定可复现，返回 64 位小写十六进制")
    void compute_deterministic() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, 9L, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, 9L, "审批通过", null, T);
        assertEquals(a, b);
        assertTrue(a.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("compute: 签署含义变化改变结果")
    void compute_meaningChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批驳回", null, T);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("compute: 签署人变化改变结果")
    void compute_signerChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 8L, null, "审批通过", null, T);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("compute: 签署时间变化改变结果")
    void compute_timeChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T + 1);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("compute: prevHash 参与计算，链式可验证")
    void compute_prevHashParticipates() {
        String first = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T);
        String secondA = SignatureChainCalculator.compute(first, 2L, "APPROVAL", 100L, 7L, null, "审批", null, T + 1);
        String secondB = SignatureChainCalculator.compute(null, 2L, "APPROVAL", 100L, 7L, null, "审批", null, T + 1);
        assertNotEquals(secondA, secondB);
        assertNotEquals(first, secondA, "链首与后继不应相同");
    }

    @Test
    @DisplayName("compute: 空值与空串等价（null 与 \"\" 同义）")
    void compute_nullAndEmptyEquivalent() {
        assertEquals(
            SignatureChainCalculator.compute(null, 1L, "APPROVAL", null, 7L, null, "审批", null, T),
            SignatureChainCalculator.compute("", 1L, "APPROVAL", null, 7L, null, "审批", "", T));
    }

    @Test
    @DisplayName("compute: 业务单据ID 变化改变结果")
    void compute_bizIdChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 101L, 7L, null, "审批", null, T);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("compute: 印章变化改变结果（用章 vs 未用章）")
    void compute_sealChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, 9L, "审批", null, T);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("computeV2: 认证方式变化改变结果（auth_method 编入哈希，DB 直改可被验签检出）")
    void computeV2_authMethodChange_altersHash() {
        String a = SignatureChainCalculator.computeV2(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T, "PASSWORD", null);
        String b = SignatureChainCalculator.computeV2(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T, "LOGIN_ONLY", null);
        assertNotEquals(a, b, "PASSWORD↔LOGIN_ONLY 改写直接影响证据效力，必须可检出");
    }

    @Test
    @DisplayName("computeV2: 单据编号变化改变结果")
    void computeV2_bizNoChange_altersHash() {
        String a = SignatureChainCalculator.computeV2(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T, "PASSWORD", "proc-1");
        String b = SignatureChainCalculator.computeV2(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T, "PASSWORD", "proc-2");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("computeV2 与 v1 同输入结果不同（公式升级，新旧哈希不冲突）")
    void computeV2_differsFromV1() {
        String v1 = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T);
        String v2 = SignatureChainCalculator.computeV2(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T, "PASSWORD", "proc-1");
        assertNotEquals(v1, v2);
    }

    @Test
    @DisplayName("computeV2: 确定性（同输入同输出）")
    void computeV2_deterministic() {
        String a = SignatureChainCalculator.computeV2("a".repeat(64), 2L, "SO_CONTRACT", 200L, 8L, 9L, "合同签署", "c".repeat(64), T + 1, "PASSWORD", "HT-2026");
        String b = SignatureChainCalculator.computeV2("a".repeat(64), 2L, "SO_CONTRACT", 200L, 8L, 9L, "合同签署", "c".repeat(64), T + 1, "PASSWORD", "HT-2026");
        assertEquals(a, b);
    }
}
