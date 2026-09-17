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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批办结签章关卡决策逻辑单元测试。
 *
 * <p>这是「不签章不生效」的唯一可单测部分：完整覆盖 needSign 真值判定、缺省值回退、
 * 非数字 dataId 降级，以及「未配置签章的历史流程必须零感知」这一核心回归点。
 * 真正的签章调用/事务回滚属于 {@code WfTaskServiceImpl.complete}，由 Task 17 的 E2E 覆盖。</p>
 *
 * <p>{@code Priority} 覆盖本次修复的核心：签章要求的三级优先级
 * （节点属性 {@code flowable:needSign} &gt; 引擎流程变量 {@code needSign} &gt; 请求体），
 * 其中「属性为真时请求体 needSign=false 无效」正是修复前的客户端绕过路径。</p>
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
    @DisplayName("needSign 真值判定（第三优先级：请求体，服务端未配置时）")
    class NeedSign {

        @Test
        @DisplayName("缺少 needSign / 变量为 null：不要求签章（历史流程零感知）")
        void absentMeansNotRequired() {
            assertFalse(ApprovalSignGate.isSignRequired(null, null));
            assertFalse(ApprovalSignGate.isSignRequired(new HashMap<>(), null));
            Map<String, Object> explicitNull = new HashMap<>();
            explicitNull.put(ApprovalSignGate.NEED_SIGN_KEY, null);
            assertFalse(ApprovalSignGate.isSignRequired(explicitNull, null));
            // 显式传入「服务端未配置」（Boolean null 而非缺参）同样落到请求体判定
            assertFalse(ApprovalSignGate.isSignRequired(explicitNull, null));
        }

        @Test
        @DisplayName("布尔值：仅 Boolean.TRUE 要求签章")
        void booleanValues() {
            Map<String, Object> required = new HashMap<>();
            required.put(ApprovalSignGate.NEED_SIGN_KEY, Boolean.TRUE);
            assertTrue(ApprovalSignGate.isSignRequired(required, null));

            Map<String, Object> notRequired = new HashMap<>();
            notRequired.put(ApprovalSignGate.NEED_SIGN_KEY, Boolean.FALSE);
            assertFalse(ApprovalSignGate.isSignRequired(notRequired, null));
        }

        @Test
        @DisplayName("字符串：\"true\" 忽略大小写与空白均要求签章")
        void stringTrueVariants() {
            assertTrue(ApprovalSignGate.isSignRequired(vars("true", null, null, null, null), null));
            assertTrue(ApprovalSignGate.isSignRequired(vars("TRUE", null, null, null, null), null));
            assertTrue(ApprovalSignGate.isSignRequired(vars("True", null, null, null, null), null));
            assertTrue(ApprovalSignGate.isSignRequired(vars("  true  ", null, null, null, null), null));
        }

        @Test
        @DisplayName("其它取值一律视为不要求签章")
        void otherValuesAreFalsy() {
            assertFalse(ApprovalSignGate.isSignRequired(vars("false", null, null, null, null), null));
            assertFalse(ApprovalSignGate.isSignRequired(vars("FALSE", null, null, null, null), null));
            assertFalse(ApprovalSignGate.isSignRequired(vars("", null, null, null, null), null));
            assertFalse(ApprovalSignGate.isSignRequired(vars("  ", null, null, null, null), null));
            // "1" 不是约定的真值，必须按不要求处理，避免前后端口径不一致
            assertFalse(ApprovalSignGate.isSignRequired(vars("1", null, null, null, null), null));
            assertFalse(ApprovalSignGate.isSignRequired(vars("yes", null, null, null, null), null));
        }
    }

    @Nested
    @DisplayName("签章要求三级优先级：节点属性 > 服务端流程变量 > 请求体")
    class Priority {

        @Test
        @DisplayName("属性 flowable:needSign=true 时，请求体 needSign=false 无法绕过签章")
        void attributeTrueBeatsBodyFalse() {
            Boolean serverNeedSign = ApprovalSignGate.resolveServerNeedSign("true", null);
            assertEquals(Boolean.TRUE, serverNeedSign);

            Map<String, Object> body = vars("false", "1936000000000000999", null, null, "pw");
            assertTrue(ApprovalSignGate.isSignRequired(body, serverNeedSign),
                "属性要求签章时请求体的 needSign=false 必须被忽略（修复前的绕过路径）");
            SignCommand cmd = ApprovalSignGate.buildCommand(DATA_ID, body, serverNeedSign);
            assertNotNull(cmd, "属性要求签章时仍必须构造签名入参");
            assertEquals("pw", cmd.credential(), "凭证仍来自请求体，客户端只能提交凭证而不能取消签章");
        }

        @Test
        @DisplayName("属性 flowable:needSign=false 时，请求体 needSign=true 也不签章")
        void attributeFalseBeatsBodyTrue() {
            Boolean serverNeedSign = ApprovalSignGate.resolveServerNeedSign("false", null);
            assertEquals(Boolean.FALSE, serverNeedSign);

            Map<String, Object> body = vars("true", null, null, null, "pw");
            assertFalse(ApprovalSignGate.isSignRequired(body, serverNeedSign));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, body, serverNeedSign));
        }

        @Test
        @DisplayName("属性未设置时，服务端流程变量优先于请求体")
        void processVariableBeatsBody() {
            Boolean serverNeedSign = ApprovalSignGate.resolveServerNeedSign(null, "true");
            assertEquals(Boolean.TRUE, serverNeedSign);
            assertTrue(ApprovalSignGate.isSignRequired(vars("false", null, null, null, "pw"), serverNeedSign));
            assertNotNull(ApprovalSignGate.buildCommand(DATA_ID, vars("false", null, null, null, "pw"), serverNeedSign));

            // 反向：实例变量显式 false，请求体无法翻盘
            Boolean variableFalse = ApprovalSignGate.resolveServerNeedSign(null, Boolean.FALSE);
            assertEquals(Boolean.FALSE, variableFalse);
            assertFalse(ApprovalSignGate.isSignRequired(vars("true", null, null, null, "pw"), variableFalse));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", null, null, null, "pw"), variableFalse));
        }

        @Test
        @DisplayName("属性未设置且流程变量未设置：请求体决定（历史行为原样保留）")
        void bodyDecidesWhenServerSilent() {
            Boolean serverNeedSign = ApprovalSignGate.resolveServerNeedSign(null, null);
            assertNull(serverNeedSign, "服务端未配置时必须返回 null，交由请求体判定");
            assertTrue(ApprovalSignGate.isSignRequired(vars("true", null, null, null, "pw"), serverNeedSign));
            assertFalse(ApprovalSignGate.isSignRequired(vars("false", null, null, null, "pw"), serverNeedSign));
        }

        @Test
        @DisplayName("三级都未配置：不需要签章，buildCommand 返回 null（存量流程零感知）")
        void allAbsentNotRequired() {
            Boolean serverNeedSign = ApprovalSignGate.resolveServerNeedSign(null, null);
            assertFalse(ApprovalSignGate.isSignRequired(null, serverNeedSign));
            assertFalse(ApprovalSignGate.isSignRequired(new HashMap<>(), serverNeedSign));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, null, serverNeedSign));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, new HashMap<>(), serverNeedSign));
        }

        @Test
        @DisplayName("属性优先于流程变量：两者矛盾时以属性为准（含属性为 false 的反向场景）")
        void attributeWinsOverProcessVariable() {
            assertEquals(Boolean.TRUE, ApprovalSignGate.resolveServerNeedSign("true", "false"));
            assertEquals(Boolean.TRUE, ApprovalSignGate.resolveServerNeedSign("true", Boolean.FALSE));
            assertEquals(Boolean.FALSE, ApprovalSignGate.resolveServerNeedSign("false", "true"));
            assertEquals(Boolean.FALSE, ApprovalSignGate.resolveServerNeedSign("false", Boolean.TRUE));
        }

        @Test
        @DisplayName("属性存在但取值非 true（\"\"/\"  \"/\"1\"/\"yes\"）：同样权威地判定为不要求")
        void nonTrueAttributeIsAuthoritative() {
            assertEquals(Boolean.FALSE, ApprovalSignGate.resolveServerNeedSign("", "true"));
            assertEquals(Boolean.FALSE, ApprovalSignGate.resolveServerNeedSign("   ", "true"));
            assertEquals(Boolean.FALSE, ApprovalSignGate.resolveServerNeedSign("1", "true"));
            assertEquals(Boolean.FALSE, ApprovalSignGate.resolveServerNeedSign("yes", "true"));
            assertEquals(Boolean.FALSE, ApprovalSignGate.resolveServerNeedSign("FALSE", "true"));
        }

        @Test
        @DisplayName("属性真值形态宽松：\"TRUE\"/\"  true  \" 与布尔 true 等价")
        void attributeTruthyVariants() {
            assertEquals(Boolean.TRUE, ApprovalSignGate.resolveServerNeedSign("TRUE", null));
            assertEquals(Boolean.TRUE, ApprovalSignGate.resolveServerNeedSign("True", null));
            assertEquals(Boolean.TRUE, ApprovalSignGate.resolveServerNeedSign("  true  ", null));
            assertEquals(Boolean.TRUE, ApprovalSignGate.resolveServerNeedSign(null, Boolean.TRUE));
            assertEquals(Boolean.TRUE, ApprovalSignGate.resolveServerNeedSign(null, " TRUE "));
        }

        @Test
        @DisplayName("属性驱动签章但请求体为空：仍构造命令，凭证缺失交由签名服务校验失败阻断（fail closed）")
        void attributeDrivenSignWithEmptyBodyStillBuildsCommand() {
            Boolean serverNeedSign = ApprovalSignGate.resolveServerNeedSign("true", null);
            SignCommand cmd = ApprovalSignGate.buildCommand(DATA_ID, null, serverNeedSign);
            assertNotNull(cmd, "请求体为空不能成为跳过签章的途径");
            assertEquals(SignBizType.APPROVAL, cmd.bizType());
            assertEquals(Long.valueOf(DATA_ID), cmd.bizId());
            assertEquals("审批签批", cmd.signatureMeaning());
            assertEquals(SignAuthMethod.PASSWORD, cmd.authMethod());
            assertNull(cmd.sealId());
            assertNull(cmd.credential());
        }
    }

    @Nested
    @DisplayName("在办任务流程变量回显注入（getProcessVariables 用）")
    class NodeNeedSignEcho {

        @Test
        @DisplayName("属性未设置：原样返回同一个 map 实例（存量流程回显不变）")
        void absentAttributeReturnsSameInstance() {
            Map<String, Object> engineVariables = vars(null, "123", null, null, null);
            Map<String, Object> result = ApprovalSignGate.applyNodeNeedSign(engineVariables, null);
            assertSame(engineVariables, result, "属性未设置时不得复制或改写引擎返回值");
            assertFalse(result.containsKey(ApprovalSignGate.NEED_SIGN_KEY));
            assertNull(ApprovalSignGate.applyNodeNeedSign(null, null));
        }

        @Test
        @DisplayName("属性存在：注入布尔结论，供前端无改动地渲染签章表单")
        void presentAttributeInjectsBoolean() {
            assertEquals(Boolean.TRUE, ApprovalSignGate
                .applyNodeNeedSign(vars(null, null, null, null, null), " TRUE ")
                .get(ApprovalSignGate.NEED_SIGN_KEY));
            // 属性为 false 同样注入，且覆盖实例级变量，避免前端与后端关卡判定不一致
            assertEquals(Boolean.FALSE, ApprovalSignGate
                .applyNodeNeedSign(vars("true", null, null, null, null), "false")
                .get(ApprovalSignGate.NEED_SIGN_KEY));
            // 引擎返回 null 时也要能注入
            assertEquals(Boolean.TRUE, ApprovalSignGate
                .applyNodeNeedSign(null, "true")
                .get(ApprovalSignGate.NEED_SIGN_KEY));
        }

        @Test
        @DisplayName("注入返回副本，不修改引擎返回的原 map")
        void injectionDoesNotMutateEngineResult() {
            Map<String, Object> engineVariables = vars("true", "123", null, null, null);
            Map<String, Object> result = ApprovalSignGate.applyNodeNeedSign(engineVariables, "false");
            assertEquals(Boolean.FALSE, result.get(ApprovalSignGate.NEED_SIGN_KEY));
            assertEquals("true", engineVariables.get(ApprovalSignGate.NEED_SIGN_KEY), "原 map 必须保持引擎原值");
            assertEquals("123", result.get(ApprovalSignGate.SEAL_ID_KEY), "副本必须保留其余流程变量");
        }
    }

    @Nested
    @DisplayName("buildCommand 是否触发签章")
    class Trigger {

        @Test
        @DisplayName("未要求签章时返回 null（调用方据此完全跳过签章逻辑）")
        void noSignReturnsNull() {
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("false", null, null, null, null), null));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, new HashMap<>(), null));
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, null, null));
        }

        @Test
        @DisplayName("没有 dataId 时即要求签章也不触发（纯审批动作不签章）")
        void blankDataIdReturnsNull() {
            Map<String, Object> v = vars("true", null, null, null, "pw");
            assertNull(ApprovalSignGate.buildCommand(null, v, null));
            assertNull(ApprovalSignGate.buildCommand("", v, null));
            assertNull(ApprovalSignGate.buildCommand("   ", v, null));
            // 服务端要求签章，但缺 dataId 时同样不触发（保持原语义）
            assertNull(ApprovalSignGate.buildCommand(null, v, Boolean.TRUE));
        }

        @Test
        @DisplayName("要求签章且有 dataId：构造 APPROVAL 签名入参")
        void buildsApprovalCommand() {
            SignCommand cmd = ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", "1936000000000000999", "审批通过", SignAuthMethod.LOGIN_ONLY, null), null);
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
                vars("true", null, null, null, null), null).signatureMeaning());
            assertEquals("审批签批", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, "   ", null, null), null).signatureMeaning());
        }

        @Test
        @DisplayName("signMeaning 自定义值去首尾空白后使用")
        void customMeaningTrimmed() {
            assertEquals("同意", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, "  同意  ", null, null), null).signatureMeaning());
        }

        @Test
        @DisplayName("signAuthMethod 缺失或空白时回退 PASSWORD")
        void defaultAuthMethod() {
            assertEquals(SignAuthMethod.PASSWORD, ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, null, "pw"), null).authMethod());
            assertEquals(SignAuthMethod.PASSWORD, ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, "  ", "pw"), null).authMethod());
        }

        @Test
        @DisplayName("signSealId 缺失/空白/非法时为 null（仅逻辑签名不盖章），不阻断签章")
        void sealIdOptional() {
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", null, null, null, null), null).sealId());
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", "", null, null, null), null).sealId());
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", "   ", null, null, null), null).sealId());
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", "abc", null, null, null), null).sealId());
        }

        @Test
        @DisplayName("signSealId 兼容字符串与整型两种提交形态")
        void sealIdParsing() {
            assertEquals(Long.valueOf(123L), ApprovalSignGate.buildCommand(DATA_ID, vars("true", "123", null, null, null), null).sealId());
            assertEquals(Long.valueOf(123L), ApprovalSignGate.buildCommand(DATA_ID, vars("true", " 123 ", null, null, null), null).sealId());

            Map<String, Object> longSeal = vars("true", null, null, null, null);
            longSeal.put(ApprovalSignGate.SEAL_ID_KEY, 456L);
            assertEquals(Long.valueOf(456L), ApprovalSignGate.buildCommand(DATA_ID, longSeal, null).sealId());

            Map<String, Object> intSeal = vars("true", null, null, null, null);
            intSeal.put(ApprovalSignGate.SEAL_ID_KEY, 7);
            assertEquals(Long.valueOf(7L), ApprovalSignGate.buildCommand(DATA_ID, intSeal, null).sealId());

            // 浮点形态（雪花ID被误序列化为 Double）不能静默转成错误ID
            Map<String, Object> doubleSeal = vars("true", null, null, null, null);
            doubleSeal.put(ApprovalSignGate.SEAL_ID_KEY, 1.23E18);
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, doubleSeal, null).sealId());
        }
    }

    @Nested
    @DisplayName("凭证解析")
    class Credential {

        @Test
        @DisplayName("凭证缺失时为 null（LOGIN_ONLY 场景）")
        void absentCredential() {
            assertNull(ApprovalSignGate.buildCommand(DATA_ID, vars("true", null, null, null, null), null).credential());
        }

        @Test
        @DisplayName("凭证原样透传，不做 trim（首尾空白是密码的一部分）")
        void credentialPreservedExactly() {
            assertEquals("  pw 123  ", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, null, "  pw 123  "), null).credential());
            assertEquals("", ApprovalSignGate.buildCommand(DATA_ID,
                vars("true", null, null, null, ""), null).credential());
        }
    }

    @Nested
    @DisplayName("非数字 dataId 降级")
    class NonNumericDataId {

        @Test
        @DisplayName("UUID 形式 dataId：bizId 落 null 但仍可签章（es_signature.biz_id 可空）")
        void uuidDataIdStillSigns() {
            SignCommand cmd = ApprovalSignGate.buildCommand("9f1c1d5e-2b3a-4c5d-8e7f-0123456789ab",
                vars("true", null, null, null, "pw"), null);
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
