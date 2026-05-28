package com.nexus.im.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImPrivateSendBo {

    @NotNull(message = "接收者不能为空")
    private Long toUserId;

    @NotBlank(message = "内容不能为空")
    private String content;

    private String msgType = "text";

    private Long forwardFrom;
}
