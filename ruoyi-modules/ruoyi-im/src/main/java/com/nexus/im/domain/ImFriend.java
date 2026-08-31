package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * IM好友关系
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_friend")
public class ImFriend extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 好友关系持有者（用户）ID。 */
    private Long userId;

    /** 该用户的好友（对端）用户 ID。 */
    private Long friendUserId;

    /** 我对该好友的备注（存于关系持有方一侧）。 */
    private String remark;
}
