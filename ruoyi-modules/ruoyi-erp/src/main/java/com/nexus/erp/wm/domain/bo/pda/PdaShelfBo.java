package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * PDA 收货上架确认入参
 */
@Data
public class PdaShelfBo {

    /** 库位编码: 填充尚未指定库位的上架明细, 随后整单上架确认 */
    @NotBlank(message = "库位编码不能为空")
    private String locationCode;
}
