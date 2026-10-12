package com.nexus.im.domain.vo;

import com.nexus.im.domain.ImMessage;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * P4-4: App syncToken 增量同步响应 VO。
 * <p>
 * 后端 {@code /im/app/messages/sync} 接口返回结构：
 * <ul>
 *   <li>{@code messages}: sinceMessageId 之后的新消息列表（按 messageId 升序，最多 500 条）</li>
 *   <li>{@code newSyncToken}: 本批最后一条消息的 messageId（无新消息时回传 sinceMessageId）</li>
 *   <li>{@code hiddenMessageIds}: 当前用户全量隐藏消息 ID，最多 10000 条；先清理本地缓存再合并消息</li>
 *   <li>{@code hasMore}: 是否还有更多消息未拉取（本批满 500 条时为 true，客户端应继续拉取）</li>
 * </ul>
 * <p>
 * 客户端持久化 {@code newSyncToken}，下次重连时作为 {@code sinceMessageId} 传入，实现增量同步。
 * <p>
 * 一致性约定：syncToken 仅前进，不回退。即使消息被撤回，syncToken 也不回退；
 * 撤回事件由 WS 推送独立通道处理（P4-8），不依赖 syncToken。
 * <p>
 * 返回的 ImMessage 不带 senderName/senderAvatar，与 {@code /message/unread} 接口保持一致；
 * 客户端 {@code normalizeMessage} 已对缺失字段做容错（自发消息回退到当前用户昵称，对方消息回退到空串）。
 *
 * @author nexus
 */
@Data
public class ImSyncResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本批拉取到的消息列表（按 messageId 升序）。
     * <p>包含私聊与群聊两类，客户端按 groupId 是否为空分发到对应会话。
     */
    private List<ImMessage> messages;

    /**
     * 当前租户、当前用户的全量隐藏消息 ID，最多 10000 条，按 ID 升序返回。
     * <p>不受消息游标和本批 500 条上限影响；客户端先从持久化与内存缓存移除这些消息，再合并本批消息。
     * <p>Long 沿用全局大整数序列化，避免客户端 ID 精度丢失；没有隐藏消息时返回空数组。
     */
    private List<Long> hiddenMessageIds = List.of();

    /**
     * 本批最后一条消息的 messageId，作为下次增量同步的 sinceMessageId。
     * <p>无新消息时回传入参 sinceMessageId（保持 syncToken 不变）。
     */
    private Long newSyncToken;

    /**
     * 是否还有更多消息未拉取。
     * <p>true 表示本批满 500 条，可能仍有后续消息，客户端应继续调用 sync 接口。
     */
    private Boolean hasMore;
}
