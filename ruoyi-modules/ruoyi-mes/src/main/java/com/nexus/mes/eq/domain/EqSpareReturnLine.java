package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES备件退库单明细对象 eq_spare_return_line（MES-EQ-04 收口）
 * <p>
 * 同一单内同一 {@code spare_id} 的重复行在服务端<b>合并</b>（数量累加），避免同单重复过账。
 * {@code unit_cost} 为退库时的备件参考单价快照，由服务端从
 * {@code eq_spare_part.unit_cost} 读取，不采信客户端传参，供成本追溯。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_spare_return_line")
public class EqSpareReturnLine extends TenantEntity {

    /** 明细行ID(主键) */
    @TableId(value = "line_id")
    private Long lineId;

    /** 退库单ID */
    private Long returnId;

    /** 备件ID */
    private Long spareId;

    /** 备件编码(冗余) */
    private String spareCode;

    /** 备件名称(冗余) */
    private String spareName;

    /** 退库数量(必须大于0) */
    private BigDecimal returnQty;

    /** 备件参考单价快照(服务端从 eq_spare_part 读取) */
    private BigDecimal unitCost;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
