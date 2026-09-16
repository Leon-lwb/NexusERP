package com.nexus.workflow.service.impl;

import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.util.Map;

/**
 * 审批办结「签章关卡」的决策逻辑。
 *
 * <p>只负责判断一次任务办结是否需要签章，以及需要时如何构造 {@link SignCommand}；
 * 真正的签名调用与异常传播在 {@code WfTaskServiceImpl#complete} 中完成
 * （签署失败必须抛出 {@code ServiceException} 阻止引擎推进，即「不签章不生效」）。</p>
 *
 * <p>抽成不依赖 Spring / Flowable 引擎 / 数据库的纯函数，便于穷举各类流程变量形态做单元测试。</p>
 *
 * @author nexus
 */
final class ApprovalSignGate {

    /** 流程变量：节点是否要求签章（{@code Boolean.TRUE} 或字符串 {@code "true"}，忽略大小写） */
    static final String NEED_SIGN_KEY = "needSign";
    /** 流程变量：所用印章ID，可空（空 = 仅逻辑签名不盖章） */
    static final String SEAL_ID_KEY = "signSealId";
    /** 流程变量：签署含义 */
    static final String MEANING_KEY = "signMeaning";
    /** 流程变量：二级认证方式，见 {@link SignAuthMethod} */
    static final String AUTH_METHOD_KEY = "signAuthMethod";
    /** 流程变量：二级认证凭证（登录密码）；签名成功后必须由调用方从流程变量中移除，避免落库 */
    static final String CREDENTIAL_KEY = "signCredential";

    /** 签署含义缺省值 */
    static final String DEFAULT_MEANING = "审批签批";

    private ApprovalSignGate() {
    }

    /**
     * 本次办结是否要求签章。
     *
     * @param variables 流程变量，可为 null
     * @return true 表示该节点配置了签章要求
     */
    static boolean isSignRequired(Map<String, Object> variables) {
        if (variables == null) {
            return false;
        }
        Object value = variables.get(NEED_SIGN_KEY);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean required) {
            return required;
        }
        // 变量经 JSON 传递，宽松接受字符串 "true"（忽略大小写与首尾空白）；"false"/"1"/"" 均为不需要
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    /**
     * 构造签名入参；<b>不需要签章时返回 null</b>，调用方据此完全跳过签名逻辑，
     * 未配置签章的历史流程因此零感知、行为不变。
     *
     * @param dataId    业务数据ID（可空；可能是 UUID 等非数字串）
     * @param variables 流程变量，可为 null
     * @return 签名入参；无需签章时为 null
     */
    static SignCommand buildCommand(String dataId, Map<String, Object> variables) {
        if (StringUtils.isBlank(dataId) || !isSignRequired(variables)) {
            return null;
        }
        // bizId 允许为 null：dataId 可能是 UUID，而 es_signature.biz_id 列可空，
        // 解析失败不能让审批无法签署
        Long bizId = parseLongQuietly(dataId);
        Long sealId = parseSealId(variables.get(SEAL_ID_KEY));
        String meaning = defaultIfBlank(variables.get(MEANING_KEY), DEFAULT_MEANING);
        String authMethod = defaultIfBlank(variables.get(AUTH_METHOD_KEY), SignAuthMethod.PASSWORD);
        // 凭证不做 trim：密码的首尾空白是有效字符，改写会导致校验失败
        Object credentialValue = variables.get(CREDENTIAL_KEY);
        String credential = credentialValue == null ? null : String.valueOf(credentialValue);
        // 严格按 SignCommand 的固定分量顺序构造：
        // bizType, bizId, bizNo, signatureMeaning, sealId, authMethod, credential
        // 审批场景没有单据编号，bizNo 置 null
        return new SignCommand(SignBizType.APPROVAL, bizId, null, meaning, sealId, authMethod, credential);
    }

    /**
     * 静默解析 long：null / 空白 / 非数字（如 UUID 形式的 dataId）一律返回 null，
     * 由调用方决定如何降级，避免因业务ID形态不同而中断审批。
     */
    static Long parseLongQuietly(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析印章ID：兼容前端以字符串（雪花ID经 Jackson 序列化为字符串）或整型提交；空白/非法值返回 null。
     */
    private static Long parseSealId(Object value) {
        if (value == null) {
            return null;
        }
        // 仅整型直接取值；其余（含字符串）按文本解析，避免把 1.23E18 这类浮点误转成错误的印章ID
        if (value instanceof Integer || value instanceof Long || value instanceof Short
            || value instanceof Byte || value instanceof BigInteger) {
            return ((Number) value).longValue();
        }
        return parseLongQuietly(String.valueOf(value));
    }

    /** 取流程变量值（去首尾空白）作为字符串；缺失或空白时回退默认值 */
    private static String defaultIfBlank(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
