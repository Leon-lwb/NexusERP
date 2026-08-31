package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 群成员关系实体
 * <p>记录用户与群组的从属关系（一个用户可加入多个群组），含加入时间。
 * 属于 IM（即时通讯）模块，继承租户实体自动带租户字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_group_member")
public class ImGroupMember extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId("id")
    private Long id;

    /** 所属群组 ID。 */
    private Long groupId;

    /** 群成员用户 ID。 */
    private Long userId;

    /** 加入群组的时间。 */
    private Date joinTime;
}
