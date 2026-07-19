package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;
import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_conversation_setting")
public class ImConversationSetting extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;
    private Long userId;
    /** PRIVATE / GROUP */
    private String chatType;
    /** 私聊=对方 userId，群聊=groupId */
    private Long targetId;
    private Integer isPinned;
    private java.util.Date pinnedAt;
    private Integer isMuted;
}
