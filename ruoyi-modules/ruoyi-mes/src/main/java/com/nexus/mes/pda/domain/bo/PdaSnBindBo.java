package com.nexus.mes.pda.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * PDA SN 绑定入参 (按批次码绑定: 物料取自批次)
 */
@Data
public class PdaSnBindBo {

    /** SN 码 */
    @NotBlank(message = "SN码不能为空")
    private String snCode;

    /** 批次码 (绑定目标) */
    @NotBlank(message = "批次码不能为空")
    private String lotCode;
}
