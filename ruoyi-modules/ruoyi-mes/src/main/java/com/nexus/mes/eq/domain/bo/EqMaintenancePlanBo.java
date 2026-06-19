package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqMaintenancePlan;

import java.util.Date;

/**
 * MES设备保养计划业务对象 eq_maintenance_plan
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqMaintenancePlan.class, reverseConvertGenerate = false)
public class EqMaintenancePlanBo extends BaseEntity {

    /** 计划ID */
    private Long planId;

    /** 计划名称 */
    @NotBlank(message = "计划名称不能为空")
    private String planName;

    /** 设备ID */
    @NotNull(message = "设备ID不能为空")
    private Long equipmentId;

    /** 保养类型: DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY */
    @NotBlank(message = "保养类型不能为空")
    private String maintenanceType;

    /** 周期(天) */
    private Integer cycleDays;

    /** 下次保养日期(为空则自动计算) */
    private Date nextDate;

    /** 保养内容 */
    private String content;

    /** 负责人ID */
    private Long responsibleId;

    /** 负责人姓名 */
    private String responsibleName;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
