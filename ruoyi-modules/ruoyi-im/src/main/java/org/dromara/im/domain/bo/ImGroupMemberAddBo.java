package org.dromara.im.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ImGroupMemberAddBo {

    @NotEmpty(message = "成员不能为空")
    private List<Long> memberUserIds;
}
