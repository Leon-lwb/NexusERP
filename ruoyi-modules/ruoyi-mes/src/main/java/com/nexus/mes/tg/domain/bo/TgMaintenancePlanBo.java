package com.nexus.mes.tg.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.tg.domain.TgMaintenancePlan;

import io.github.linpeilie.annotations.AutoMapper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装保养计划业务对象 tg_maintenance_plan
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = TgMaintenancePlan.class, reverseConvertGenerate = false)
public class TgMaintenancePlanBo extends BaseEntity {

    /** 计划ID */
    private Long planId;

    /** 工装ID */
    @NotNull(message = "工装ID不能为空")
    private Long toolingId;

    /** 计划名称 */
    @NotBlank(message = "计划名称不能为空")
    private String planName;

    /** 触发类型: BY_COUNT/BY_HOURS/PERIODIC */
    @NotBlank(message = "触发类型不能为空")
    private String triggerType;

    /** 触发阈值(次数/小时/天数) */
    private Integer triggerValue;

    /** 上次保养时间 */
    private Date lastMaintainTime;

    /** 下次保养时间(PERIODIC) */
    private Date nextMaintainTime;

    /** 下次保养次数阈值(BY_COUNT) */
    private Integer nextMaintainCount;

    /** 下次保养时长阈值(BY_HOURS) */
    private BigDecimal nextMaintainHours;

    /** 是否启用(1/0) */
    private String enabled;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}
