package com.nexus.im.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** APP 添加群成员请求，PC 路径参数继续使用原成员 BO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppImGroupMemberAddBo extends ImGroupMemberAddBo {
    @NotNull(message = "群ID不能为空")
    private Long groupId;
}
