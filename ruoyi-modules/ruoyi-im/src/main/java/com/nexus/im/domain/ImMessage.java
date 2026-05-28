package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_message")
public class ImMessage extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("message_id")
    private Long messageId;

    private Long fromUserId;

    private Long toUserId;

    private Long groupId;

    private String content;

    private String msgType;

    private String isRead;

    private String revoked;

    private Long forwardFrom;
}
