package com.nexus.workflow.service.impl;

import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * 审批办结「签章关卡」的决策逻辑。
 *
 * <p>只负责判断一次任务办结是否需要签章，以及需要时如何构造 {@link SignCommand}；
 * 真正的签名调用与异常传播在 {@code WfTaskServiceImpl#complete} 中完成
 * （签署失败必须抛出 {@code ServiceException} 阻止引擎推进，即「不签章不生效」）。</p>
 *
 * <p>「本节点是否需要签章」按三级优先级解析，命中即止：</p>
 * <ol>
 *   <li>BPMN 用户任务扩展属性 {@code flowable:needSign} —— 节点级、随流程定义发布，
 *       客户端无法篡改；<b>属性「存在」即权威</b>（写 {@code false} 同样覆盖请求体）</li>
 *   <li>服务端流程变量 {@code needSign} —— 实例级，由引擎读取，同样优先于请求体</li>
 *   <li>请求体 {@code variables["needSign"]} —— 历史兼容行为，仅当服务端未作任何规定时生效</li>
 * </ol>
 *
 * <p>前两级需要访问引擎，由调用方（{@code WfTaskServiceImpl}）解析后以 {@code nodeNeedSign}
 * 传入：本类保持为不依赖 Spring / Flowable 引擎 / 数据库的纯函数，便于穷举各类取值形态做单元测试。</p>
 *
 * @author nexus
 */
final class ApprovalSignGate {

    /** 流程变量：节点是否要求签章（{@code Boolean.TRUE} 或字符串 {@code "true"}，忽略大小写） */
    static final String NEED_SIGN_KEY = "needSign";

    /**
     * BPMN 用户任务扩展属性名：节点是否要求签章（与 {@code localScope} 同族的自定义属性，
     * XML 中写作 {@code flowable:needSign}，由 {@code ModelUtils.getUserTaskAttributeValue} 按本地名读取）。
     * 与 {@link #NEED_SIGN_KEY} 同名但不同源：前者随流程定义发布，后者是运行期流程变量。
     */
    static final String NEED_SIGN_ATTRIBUTE = "needSign";

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
     * 服务端侧「本节点是否需要签章」的解析：节点属性 &gt; 引擎流程变量，都没有则返回 {@code null}。
     *
     * <p>两者都是服务端可控来源（属性随流程定义发布，流程变量由引擎持有），故无需也不应参考请求体。</p>
     *
     * @param nodeAttributeValue   {@code flowable:needSign} 属性值；{@code null} = 属性未设置
     * @param processVariableValue 引擎流程变量 {@code needSign} 的值；{@code null} = 未配置
     * @return {@code TRUE}/{@code FALSE} 表示服务端已作出权威结论（请求体必须被忽略）；
     *         {@code null} 表示两级都未配置，交由请求体按历史行为决定
     */
    static Boolean resolveServerNeedSign(String nodeAttributeValue, Object processVariableValue) {
        // 属性「存在即权威」：即便是 "false" 也必须覆盖流程变量与请求体，
        // 否则节点级配置形同虚设，客户端换个字段仍能翻盘
        if (nodeAttributeValue != null) {
            return parseFlag(nodeAttributeValue);
        }
        if (processVariableValue != null) {
            return parseFlag(processVariableValue);
        }
        return null;
    }

    /**
     * 本次办结是否要求签章。
     *
     * @param variables    请求体中的流程变量，可为 null
     * @param nodeNeedSign 服务端结论，见 {@link #resolveServerNeedSign}；{@code null} = 服务端未配置
     * @return true 表示该节点要求签章
     */
    static boolean isSignRequired(Map<String, Object> variables, Boolean nodeNeedSign) {
        // 服务端一旦表态，请求体一律忽略——这正是「不签章不生效」能防恶意客户端、
        // 而不只是防误操作的关键：客户端提交 needSign=false 不再有任何效果
        if (nodeNeedSign != null) {
            return nodeNeedSign;
        }
        return bodyRequiresSign(variables);
    }

    /**
     * 历史行为（第三优先级）：仅依据请求体变量的真值判定。
     *
     * @param variables 请求体中的流程变量，可为 null
     * @return true 表示请求体声明需要签章
     */
    private static boolean bodyRequiresSign(Map<String, Object> variables) {
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
     * 统一的真值解析（三态）：{@code null} = 未配置；其余为权威结论。
     * 「存在但不是 true」一律判定为不要求（含空串、"1"、"yes" 等非约定真值），
     * 属性与流程变量共用同一规则，避免两级口径不一致。
     */
    private static Boolean parseFlag(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    /**
     * 在办任务的流程变量回显：节点属性存在时，把节点级结论注入返回给前端的变量 map。
     *
     * <p>属性未设置时<b>原样返回入参</b>（同一实例、同一键集），存量流程的接口回显与改造前完全一致；
     * 属性存在时返回副本并以属性结论覆盖同名实例级变量，使前端「是否渲染签章表单」与后端关卡同源，
     * 前端无需改动（{@code detail.vue} 读的就是 {@code taskVariables.needSign}）。</p>
     *
     * @param variables         引擎返回的流程变量，可为 null
     * @param nodeAttributeValue {@code flowable:needSign} 属性值；{@code null} = 属性未设置
     * @return 用于回显的流程变量
     */
    static Map<String, Object> applyNodeNeedSign(Map<String, Object> variables, String nodeAttributeValue) {
        if (nodeAttributeValue == null) {
            return variables;
        }
        Map<String, Object> result = new HashMap<>();
        if (variables != null) {
            result.putAll(variables);
        }
        // 注入布尔值而非原始字符串：前端与后端共用「true 才签章」的真值口径，避免 "TRUE"/" true " 之类形态分歧
        result.put(NEED_SIGN_KEY, parseFlag(nodeAttributeValue));
        return result;
    }

    /**
     * 构造签名入参；<b>不需要签章时返回 null</b>，调用方据此完全跳过签名逻辑，
     * 未配置签章的历史流程因此零感知、行为不变。
     *
     * <p>签章要求可由服务端（属性/流程变量）给定，但印章、含义、认证方式与凭证仍来自本次请求：
     * 属性驱动签章时请求体可能完全没有 variables，此处按缺省值构造，凭证缺失会由签名服务在
     * PASSWORD 认证下校验失败并阻断审批（fail closed，即「不签章不生效」）。</p>
     *
     * @param dataId       业务数据ID（可空；可能是 UUID 等非数字串）
     * @param variables    请求体中的流程变量，可为 null
     * @param nodeNeedSign 服务端结论，见 {@link #resolveServerNeedSign}；{@code null} = 服务端未配置
     * @return 签名入参；无需签章时为 null
     */
    static SignCommand buildCommand(String dataId, Map<String, Object> variables, Boolean nodeNeedSign) {
        if (StringUtils.isBlank(dataId) || !isSignRequired(variables, nodeNeedSign)) {
            return null;
        }
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        // bizId 允许为 null：dataId 可能是 UUID，而 es_signature.biz_id 列可空，
        // 解析失败不能让审批无法签署
        Long bizId = parseLongQuietly(dataId);
        Long sealId = parseSealId(vars.get(SEAL_ID_KEY));
        String meaning = defaultIfBlank(vars.get(MEANING_KEY), DEFAULT_MEANING);
        String authMethod = defaultIfBlank(vars.get(AUTH_METHOD_KEY), SignAuthMethod.PASSWORD);
        // 凭证不做 trim：密码的首尾空白是有效字符，改写会导致校验失败
        Object credentialValue = vars.get(CREDENTIAL_KEY);
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
