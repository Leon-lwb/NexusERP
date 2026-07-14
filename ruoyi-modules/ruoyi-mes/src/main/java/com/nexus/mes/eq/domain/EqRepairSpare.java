package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES维修领用备件明细对象 eq_repair_spare
 * <p>
 * 维修完成时录入的更换备件明细, 同时写一条 REPAIR_USE 流水扣减库存。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_repair_spare")
public class EqRepairSpare extends TenantEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 维修单ID */
    private Long repairId;

    /** 备件ID */
    private Long spareId;

    /** 备件编码(冗余) */
    private String spareCode;

    /** 备件名称(冗余) */
    private String spareName;

    /** 规格(冗余) */
    private String spec;

    /** 单位(冗余) */
    private String unit;

    /** 领用数量 */
    private BigDecimal quantity;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
