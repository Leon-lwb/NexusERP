package com.nexus.im.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 会话列表视图对象（VO）
 * <p>聚合单个会话（私聊/群聊）的展示信息：会话类型、对端、标题、头像、
 * 最近一条消息摘要与时间、未读数及置顶/免打扰配置，供前端会话列表直接渲染。</p>
 */
@Data
public class ImConversationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话类型：PRIVATE（私聊）/ GROUP（群聊）。 */
    private String chatType;

    /** 会话目标 ID：私聊为对端 userId，群聊为 groupId。 */
    private String targetId;

    /** 会话标题（私聊为昵称，群聊为群名）。 */
    private String title;

    /** 会话头像 URL（私聊为对端头像，群聊为群头像）。 */
    private String avatar;

    /** 群聊成员头像列表（私聊为 null）。 */
    private List<String> memberAvatars;

    /** 最近一条消息的内容摘要。 */
    private String lastContent;

    /** 最近一条消息的时间。 */
    private Date lastTime;

    /** 该会话的未读消息数量。 */
    private Long unreadCount;

    /** 是否置顶: 0=否 1=是 */
    private Integer isPinned = 0;
    /** 是否免打扰: 0=否 1=是 */
    private Integer isMuted = 0;
    /** 置顶时间（用于多会话置顶排序） */
    private Date pinnedAt;
}
