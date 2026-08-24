package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 群组实体
 * <p>记录 IM 群组的基本信息，包括群名、头像、群主与状态（0 正常 / 1 已解散）。
 * 属于 IM（即时通讯）模块，继承租户实体自动带租户字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_group")
public class ImGroup extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 群组 ID。 */
    @TableId("group_id")
    private Long groupId;

    /** 群名称。 */
    private String groupName;

    /** 群头像 URL。 */
    private String avatar;

    /** 群主用户 ID。 */
    private Long ownerUserId;

    /** 群组状态：0 正常，1 已解散。 */
    private String status;

    /** 群描述（复用 im_group.remark 列存储，App 端群详情展示）。 */
    private String remark;
}
