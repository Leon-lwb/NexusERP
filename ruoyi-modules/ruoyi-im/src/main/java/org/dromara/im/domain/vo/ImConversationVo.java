package org.dromara.im.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class ImConversationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String chatType;

    private String targetId;

    private String title;

    private String lastContent;

    private Date lastTime;

    private Long unreadCount;
}
