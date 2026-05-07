package org.dromara.im.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ImGroupCreateBo {

    @NotBlank(message = "群名称不能为空")
    private String groupName;

    private String avatar;

    @NotEmpty(message = "成员不能为空")
    private List<Long> memberUserIds;
}
