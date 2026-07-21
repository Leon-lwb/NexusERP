package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;
import java.io.Serial;

/**
 * 消息收藏实体
 * <p>记录用户收藏的 IM 消息，包含来源会话、消息类型与内容摘要，按用户+消息维度唯一。
 * 属于 IM（即时通讯）模块，继承租户实体自动带租户字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_favorite")
public class ImFavorite extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId("id")
    private Long id;
    /** 收藏所属用户 ID。 */
    private Long userId;
    /** 被收藏的消息 ID。 */
    private Long messageId;
    /** 来源会话类型：PRIVATE（私聊）/ GROUP（群聊）。 */
    private String chatType;
    /** 来源会话目标 ID：私聊为对端 userId，群聊为 groupId。 */
    private Long targetId;
    /** 被收藏消息的原始发送者 ID。 */
    private Long fromUserId;
    /** 被收藏消息的消息类型（text / image / file 等）。 */
    private String msgType;
    /** 内容摘要，最多 500 字。 */
    private String contentSnippet;
}
