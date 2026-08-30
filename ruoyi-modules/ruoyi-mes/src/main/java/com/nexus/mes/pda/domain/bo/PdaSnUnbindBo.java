package com.nexus.mes.pda.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * PDA SN 解绑入参
 */
@Data
public class PdaSnUnbindBo {

    /** SN 码 */
    @NotBlank(message = "SN码不能为空")
    private String snCode;
}
