package com.nexus.mes.eq.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqRepair;

import io.github.linpeilie.annotations.AutoMapper;

import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * MES设备维修记录业务对象 eq_repair
 *
 * <p>属于MES 设备管理模块，维修业务对象（新增/编辑入参）。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqRepair.class, reverseConvertGenerate = false)
public class EqRepairBo extends BaseEntity {

    /** 维修ID */
    private Long repairId;

    /** 维修单号 */
    private String repairNo;

    /** 设备ID */
    @NotNull(message = "设备ID不能为空")
    private Long equipmentId;

    /** 故障类型 */
    private String faultType;

    /** 故障描述 */
    private String faultDesc;

    /** 报修人ID */
    private Long reporterId;

    /** 报修人姓名 */
    private String reporterName;

    /** 维修人ID */
    private Long repairerId;

    /** 维修人姓名 */
    private String repairerName;

    /** 停机时长(小时) */
    private BigDecimal downtimeHours;

    /** 维修成本 */
    private BigDecimal repairCost;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
