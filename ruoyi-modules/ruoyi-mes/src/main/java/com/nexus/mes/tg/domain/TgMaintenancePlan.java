package com.nexus.mes.tg.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装保养计划对象 tg_maintenance_plan
 * <p>触发类型: BY_COUNT(达次数) / BY_HOURS(达时长) / PERIODIC(按周期)</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tg_maintenance_plan")
public class TgMaintenancePlan extends TenantEntity {

    /** 计划ID(主键) */
    @TableId(value = "plan_id")
    private Long planId;

    /** 工装ID */
    private Long toolingId;

    /** 计划名称 */
    private String planName;

    /** 触发类型: BY_COUNT/BY_HOURS/PERIODIC */
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

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
