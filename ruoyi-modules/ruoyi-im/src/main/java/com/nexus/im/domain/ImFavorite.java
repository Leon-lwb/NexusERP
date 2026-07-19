package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;
import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_favorite")
public class ImFavorite extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;
    private Long userId;
    private Long messageId;
    /** PRIVATE / GROUP */
    private String chatType;
    private Long targetId;
    private Long fromUserId;
    private String msgType;
    /** 内容摘要，最多 500 字 */
    private String contentSnippet;
}
