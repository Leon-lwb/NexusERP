package com.nexus.erp.fin.report.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 关账操作请求(损益结转/外币重估/月结整体执行)
 *
 * @author nexus
 * @date 2026-07-31
 */
@Data
public class ClosingRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会计期间ID */
    @NotNull(message = "期间不能为空")
    private Long periodId;
}
