package com.nexus.im.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建群聊入参对象（BO）
 * <p>用于接收建群请求，包含群名称、头像及初始成员列表，由 Controller 绑定后传入 Service。</p>
 */
@Data
public class ImGroupCreateBo {

    /** 群名称，不能为空。 */
    @NotBlank(message = "群名称不能为空")
    private String groupName;

    /** 群头像 URL，可为空。 */
    private String avatar;

    /** 群描述，可为空（Service 层存入 im_group.remark 列）。 */
    private String description;

    /** 初始成员用户 ID 列表，不能为空。 */
    @NotEmpty(message = "成员不能为空")
    private List<Long> memberUserIds;
}
