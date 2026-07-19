package com.nexus.im.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class ImConversationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String chatType;

    private String targetId;

    private String title;

    private String avatar;

    private List<String> memberAvatars;

    private String lastContent;

    private Date lastTime;

    private Long unreadCount;

    /** 是否置顶: 0=否 1=是 */
    private Integer isPinned = 0;
    /** 是否免打扰: 0=否 1=是 */
    private Integer isMuted = 0;
    /** 置顶时间（用于多会话置顶排序） */
    private Date pinnedAt;
}
