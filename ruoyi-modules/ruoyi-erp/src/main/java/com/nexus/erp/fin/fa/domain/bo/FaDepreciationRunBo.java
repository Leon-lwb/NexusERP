package com.nexus.erp.fin.fa.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 折旧计提 BO
 *
 * @author nexus
 * @date 2026-08-01
 */
@Data
public class FaDepreciationRunBo {

    /** 期间ID */
    @NotNull(message = "期间不能为空")
    private Long periodId;
}
