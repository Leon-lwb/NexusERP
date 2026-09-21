package com.nexus.mes.eq.domain.bo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES备件退库单明细业务对象（MES-EQ-04 收口）
 * <p>注意：<b>不</b>提供 {@code unitCost} 入参——退库单价由服务端从
 * {@code eq_spare_part.unit_cost} 读取，避免客户端伪造成本。</p>
 *
 * @author Nexus
 */
@Data
public class EqSpareReturnLineBo implements Serializable {

    /** 备件ID */
    private Long spareId;

    /** 退库数量(必须大于0) */
    private BigDecimal returnQty;

    /** 备注 */
    private String remark;
}
