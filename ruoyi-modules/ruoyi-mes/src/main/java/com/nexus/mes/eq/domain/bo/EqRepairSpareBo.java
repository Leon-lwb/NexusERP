package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqRepairSpare;

import java.math.BigDecimal;

/**
 * MES维修领用备件明细业务对象 eq_repair_spare
 *
 * <p>属于MES 设备管理模块，维修备件业务对象（新增/编辑入参）。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqRepairSpare.class, reverseConvertGenerate = false)
public class EqRepairSpareBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 维修单ID */
    @NotNull(message = "维修单ID不能为空")
    private Long repairId;

    /** 备件ID */
    @NotNull(message = "备件ID不能为空")
    private Long spareId;

    /** 领用数量 */
    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    private BigDecimal quantity;

    /** 备注 */
    private String remark;
}
