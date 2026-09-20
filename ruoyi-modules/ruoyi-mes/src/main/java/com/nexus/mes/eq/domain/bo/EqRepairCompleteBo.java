package com.nexus.mes.eq.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 维修完成业务对象(P1-2 R1 维修-备件联动)
 * <p>
 * 替代原 complete 接口的多个 @RequestParam, 支持携带更换备件明细。
 * 备件明细复用 {@link EqRepairSpareBo}(repairId 由服务端按路径补填, 前端无需传)。
 *
 * @author Nexus
 */
@Data
public class EqRepairCompleteBo {

    /**
     * 停机时长(小时)
     * <p>负值会使 OEE 分子大于分母(availability/oee &gt; 1), 故限定非负; 允许 null(未填报)。
     */
    @DecimalMin(value = "0", message = "停机时长不能为负")
    private BigDecimal downtimeHours;

    /**
     * 维修成本(元)
     * <p>限定非负, 允许 null(未填报)。
     */
    @DecimalMin(value = "0", message = "维修成本不能为负")
    private BigDecimal repairCost;

    /** 维修说明/备注 */
    private String remark;

    /** 更换备件明细(可选, 每条事务内扣减库存并写 eq_repair_spare) */
    private List<EqRepairSpareBo> spares;
}
