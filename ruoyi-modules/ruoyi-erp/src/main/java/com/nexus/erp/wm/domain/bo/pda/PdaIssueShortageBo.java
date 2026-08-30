package com.nexus.erp.wm.domain.bo.pda;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PDA 发料缺货声明行 (随完成提交)
 */
@Data
public class PdaIssueShortageBo {

    /** 发料单行ID */
    @NotNull(message = "行ID不能为空")
    private Long lineId;

    /** 缺货数量 (参考值, 服务端以 应领-已领 为准) */
    private BigDecimal shortageQty;

    /** 缺货原因: 缺货/待补料/其他 */
    private String reason;
}
