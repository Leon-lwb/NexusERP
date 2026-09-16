package com.nexus.workflow.service.impl;

import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批办结签章关卡决策逻辑单元测试。
 *
 * <p>这是 Task 15「不签章不生效」的唯一可单测部分：完整覆盖 needSign 真值判定、
 * 缺省值回退、非数字 dataId 降级，以及「未配置签章的历史流程必须零感知」这一核心回归点。
 * 真正的签章调用/事务回滚属于 {@code WfTaskServiceImpl.complete}，由 Task 17 的 E2E 覆盖。</p>
 *
 * @author nexus
 */
class ApprovalSignGateTest {

    private static final String DATA_ID = "1936000000000000123";

    /** 构造流程变量 */
    private static Map<String, Object> vars(String needSign, String sealId, String meaning,
                                            String authMethod, String credential) {
        Map<String, Object> map = new HashMap<>();
        if (needSign != null) {
            map.put(ApprovalSignGate.NEED_SIGN_KEY, needSign);
        }
        if (sealId != null) {
            map.put(ApprovalSignGate.SEAL_ID_KEY, sealId);
        }
        if (meaning != null) {
            map.put(ApprovalSignGate.MEANING_KEY, meaning);
        }
        if (authMethod != null) {
            map.put(ApprovalSignGate.AUTH_METHOD_KEY, authMethod);
        }
        if (credential != null) {
            map.put(ApprovalSignGate.CREDENTIAL_KEY, credential);
        }
        return map;
    }

    @Nested
    @DisplayName("needSign 真值判定")
    class NeedSign {

        @Test
        @DisplayName("缺少 needSign / 变量为 null：不要求签章（历史流程零感知）")
        void absentMeansNotRequired() {
            assertFalse(ApprovalSignGate.isSignRequired(null));
            assertFalse(ApprovalSignGate.isSignRequired(new HashMap<>()));
            Map<String, Object> explicitNull = new HashMap<>();
            explicitNull.put(ApprovalSignGate.NEED_SIGN_KEY, null);
            assertFalse(ApprovalSignGate.isSignRequired(explicitNull));
        }

        @Test
        @DisplayName("布尔值：仅 Boolean.TRUE 要求签章")
        void booleanValues() {
            Map<String, Object> required = new HashMap<>();
            required.put(ApprovalSignGate.NEED_SIGN_KEY, Boolean.TRUE);
            assertTrue(ApprovalSignGate.isSignRequired(required));

            Map<String, Object> notRequired = new HashMap<>();
            notRequired.put(ApprovalSignGate.NEED_SIGN_KEY, Boolean.FALSE);
            assertFalse(ApprovalSignGate.isSignRequired(notRequired));
        }

        @Test
        @DisplayName("字符串：\"true\" 忽略大小写与空白均要求签章")
        void stringTrueVariants() {
            assertTrue(ApprovalSignGate.isSignRequired(vars("true", null, null, null, null)));
            assertTrue(ApprovalSignGate.isSignRequired(vars("TRUE", null, null, null, null)));
            assertTrue(ApprovalSignGate.isSignRequired(vars("True", null, null, null, null)));
            assertTrue(ApprovalSignGate.isSignRequired(vars("  true  ", null, null, null, null)));
        }

        @Test
        @DisplayName("其它取值一律视为不要求签章")
        void otherValuesAreFalsy() {
            assertFalse(ApprovalSignGate.isSignRequired(vars("false", null, null, null, null)));
            assertFalse(ApprovalSignGate.isSignRequired(vars("FALSE", null, null, null, null)));
            assertFalse(ApprovalSignGate.isSignRequired(vars("", null, null, null, null)));
            assertFalse(ApprovalSignGate.isSignRequired(vars("  ", null, null, null, null)));
            // "1" 不是约定的真值，必须按不要求处理，避免前后端口径不一致
            assertFalse(ApprovalSignGate.isSignRequired(vars("1", null, null, null, null)));
            assertFalse(ApprovalSignGate.isSignRequired(vars("yes", null, null, null, null)));
        }
    }

    @Nested
    @DisplayName("buildCommand 是否触发签章")
    class Trigger {

        @Test
        @DisplayName("未要求签章时返回 null（调用方据此完全跳过签章逻辑）")
        void noSignReturnsNull() {
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("false", null, null, null, null)));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, new HashMap<>()));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, null));
        }

        @Test
        @DisplayName("没有 dataId 时即要求签章也不触发（纯审批动作不签章）")
        void blankDataIdReturnsNull() {
            Map<String, Object> v = vars("true", null, null, null, "pw");
            assertNull(ApprovalSignGate.buildCommand(null, v));
            assertNull(ApprovalSignGate.buildCommand("", v));
            assertNull(ApprovalSignGate.buildCommand("   ", v));
        }

        @Test
        @DisplayName("要求签章且有 dataId：构造 APPROVAL 签名入参")
        void buildsApprovalCommand() {
            SignCommand cmd = ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", "1936000000000000999", "审批通过", SignAuthMethod.LOGIN_ONLY, null));
            assertNotNull(cmd);
            assertEquals(SignBizType.APPROVAL, cmd.bizType());
            assertEquals(Long.valueOf(DATA_ID), cmd.bizId());
            assertNull(cmd.bizNo(), "审批场景无单据编号，bizNo 必须为 null");
            assertEquals("审批通过", cmd.signatureMeaning());
            assertEquals(Long.valueOf(1936000000000000999L), cmd.sealId());
            assertEquals(SignAuthMethod.LOGIN_ONLY, cmd.authMethod());
            assertNull(cmd.credential());
        }
    }

    @Nested
    @DisplayName("缺省值与字段回退")
    class Defaults {

        @Test
        @DisplayName("signMeaning 缺失或空白时回退「审批签批」")
        void defaultMeaning() {
            assertEquals("审批签批", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, null, null)).signatureMeaning());
            assertEquals("审批签批", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, "   ", null, null)).signatureMeaning());
        }

        @Test
        @DisplayName("signMeaning 自定义值去首尾空白后使用")
        void customMeaningTrimmed() {
            assertEquals("同意", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, "  同意  ", null, null)).signatureMeaning());
        }

        @Test
        @DisplayName("signAuthMethod 缺失或空白时回退 PASSWORD")
        void defaultAuthMethod() {
            assertEquals(SignAuthMethod.PASSWORD, ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, null, "pw")).authMethod());
            assertEquals(SignAuthMethod.PASSWORD, ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, "  ", "pw")).authMethod());
        }

        @Test
        @DisplayName("signSealId 缺失/空白/非法时为 null（仅逻辑签名不盖章），不阻断签章")
        void sealIdOptional() {
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", null, null, null, null)).sealId());
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", "", null, null, null)).sealId());
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", "   ", null, null, null)).sealId());
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", "abc", null, null, null)).sealId());
        }

        @Test
        @DisplayName("signSealId 兼容字符串与整型两种提交形态")
        void sealIdParsing() {
            assertEquals(Long.valueOf(123L), ApprovalSignGate.buildCommand(DATA_ID, vars("true", "123", null, null, null)).sealId());
            assertEquals(Long.valueOf(123L), ApprovalSignGate.buildCommand(DATA_ID, vars("true", " 123 ", null, null, null)).sealId());

            Map<String, Object> longSeal = vars("true", null, null, null, null);
            longSeal.put(ApprovalSignGate.SEAL_ID_KEY, 456L);
            assertEquals(Long.valueOf(456L), ApprovalSignGate.buildCommand(DATA_ID, longSeal).sealId());

            Map<String, Object> intSeal = vars("true", null, null, null, null);
            intSeal.put(ApprovalSignGate.SEAL_ID_KEY, 7);
            assertEquals(Long.valueOf(7L), ApprovalSignGate.buildCommand(DATA_ID, intSeal).sealId());

            // 浮点形态（雪花ID被误序列化为 Double）不能静默转成错误ID
            Map<String, Object> doubleSeal = vars("true", null, null, null, null);
            doubleSeal.put(ApprovalSignGate.SEAL_ID_KEY, 1.23E18);
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, doubleSeal).sealId());
        }
    }

    @Nested
    @DisplayName("凭证解析")
    class Credential {

        @Test
        @DisplayName("凭证缺失时为 null（LOGIN_ONLY 场景）")
        void absentCredential() {
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", null, null, null, null)).credential());
        }

        @Test
        @DisplayName("凭证原样透传，不做 trim（首尾空白是密码的一部分）")
        void credentialPreservedExactly() {
            assertEquals("  pw 123  ", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, null, "  pw 123  ")).credential());
            assertEquals("", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, null, "")).credential());
        }
    }

    @Nested
    @DisplayName("非数字 dataId 降级")
    class NonNumericDataId {

        @Test
        @DisplayName("UUID 形式 dataId：bizId 落 null 但仍可签章（es_signature.biz_id 可空）")
        void uuidDataIdStillSigns() {
            SignCommand cmd = ApprovalSignGate.buildCommand("9f1c1d5e-2b3a-4c5d-8e7f-0123456789ab",
                vars("true", null, null, null, "pw"));
            assertNotNull(cmd, "非数字 dataId 不得导致签章被跳过");
            assertNull(cmd.bizId());
            assertEquals(SignBizType.APPROVAL, cmd.bizType());
            assertEquals("审批签批", cmd.signatureMeaning());
        }

        @Test
        @DisplayName("parseLongQuietly：null/空白/非数字/溢出均返回 null")
        void parseLongQuietlyDegrades() {
            assertNull(ApprovalSignGate.parseLongQuietly(null));
            assertNull(ApprovalSignGate.parseLongQuietly(""));
            assertNull(ApprovalSignGate.parseLongQuietly("   "));
            assertNull(ApprovalSignGate.parseLongQuietly("12a3"));
            assertNull(ApprovalSignGate.parseLongQuietly("abc"));
            assertNull(ApprovalSignGate.parseLongQuietly("9f1c1d5e-2b3a-4c5d-8e7f-0123456789ab"));
            assertNull(ApprovalSignGate.parseLongQuietly("999999999999999999999999"));
            assertEquals(123L, ApprovalSignGate.parseLongQuietly("123"));
            assertEquals(123L, ApprovalSignGate.parseLongQuietly(" 123 "));
            assertEquals(-5L, ApprovalSignGate.parseLongQuietly("-5"));
        }
    }
}
