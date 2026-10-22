package com.nexus.common.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * AI 聊天请求 DTO
 * <p>支持三种模式：普通流式对话、Agent 编排、RAG 增强</p>
 * <p>mode 字段用于前端显式指定模式，为空时由后端根据关键词自动路由</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
public class AiChatRequest implements Serializable {
    /** 服务端执行身份，不从JSON请求绑定。 */
    @JsonIgnore
    private String executionRequestId;

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话ID（新会话传 null） */
    private Long sessionId;

    /** PC在发送前生成的请求关联标识；只用于事件隔离，不作为身份或业务幂等凭据。 */
    private String clientRequestId;

    /** 校验可选关联标识，兼容未升级客户端，拒绝空白、控制符与超长输入。 */
    public static boolean validClientRequestId(String value) {
        return value == null || value.matches("[A-Za-z0-9][A-Za-z0-9_-]{15,79}");
    }

    /** 消息内容 */
    private String content;

    /** 模型名称（可选，为空使用默认模型） */
    private String model;

    /** 知识库ID（可选，指定后走 RAG 增强模式） */
    private Long knowledgeId;

    /**
     * 对话模式（可选，为空时由后端自动路由）
     * <ul>
     *   <li>{@code auto} — 自动路由（默认，根据关键词判断）</li>
     *   <li>{@code chat} — 普通流式对话</li>
     *   <li>{@code agent} — Agent 编排模式（数据查询/报表生成）</li>
     *   <li>{@code rag} — RAG 增强模式（需配合 knowledgeId 使用）</li>
     * </ul>
     */
    private String mode;

    /**
     * 本次对话启用的 MCP 工具 ID 列表
     * <p>null 表示使用会话默认工具集，非空时覆盖会话设置</p>
     */
    private List<Long> enabledToolIds;

    /**
     * 是否开启推理（Thinking）模式
     * <p>开启后输出推理 token 流（DeepSeek-R1 等推理模型），前端折叠展示思考过程</p>
     * <p>仅对支持 returnThinking 的厂商（OpenAI 兼容类）生效</p>
     */
    private Boolean enableThinking = false;

    /**
     * 是否开启联网搜索模式（S2-C）
     * <p>开启后路由到 WebSearchAgent，回答需标注信息来源链接</p>
     */
    private Boolean enableWebSearch = false;

    /**
     * 业务上下文（可选）
     * <p>移动端从业务详情页（工单/采购订单/收货单等）带入的当前业务对象摘要，
     * 注入到生成 prompt 中使 AI 知晓上下文（如"当前在查看采购订单 PO-2026-0001"）。
     * 仅参与 prompt 拼接，不参与关键词路由判断、不落库。</p>
     */
    private String context;

    /**
     * 单次请求最大输出 Token 覆盖（可选）
     * <p>覆盖模型配置 max_tokens：null 时用模型配置值；
     * 用于长输出调用场景按调用封顶输出体量（配合 prompt 体量约束双保险，防失控超长输出）。</p>
     */
    private Integer maxOutputTokens;
    /** 本次工程请求显式选择的本人设备；空值禁止自动选取其他设备。 */
    private Long engineeringDeviceId;

    /** 前端为本轮工程请求生成的稳定标识，服务端按 action 派生幂等键，模型无权覆盖。 */
    private String engineeringRequestId;
}
