package org.dromara.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_group")
public class ImGroup extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("group_id")
    private Long groupId;

    private String groupName;

    private String avatar;

    private Long ownerUserId;

    private String status;
}
