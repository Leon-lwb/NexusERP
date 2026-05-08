package org.dromara.im.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImGroupSendBo {

    @NotNull(message = "群ID不能为空")
    private Long groupId;

    @NotBlank(message = "内容不能为空")
    private String content;

    private String msgType = "text";

    private Long forwardFrom;
}
