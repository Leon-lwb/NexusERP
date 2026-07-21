package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES设备保养计划对象 eq_maintenance_plan
 *
 * <p>属于MES 设备管理模块，保养计划持久化实体。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_maintenance_plan")
public class EqMaintenancePlan extends TenantEntity {

    /** 计划ID(主键) */
    @TableId(value = "plan_id")
    private Long planId;

    /** 计划名称 */
    private String planName;

    /** 设备ID */
    private Long equipmentId;

    /** 保养类型: DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY */
    private String maintenanceType;

    /** 周期(天) */
    private Integer cycleDays;

    /** 下次保养日期(自动计算) */
    private Date nextDate;

    /** 保养内容 */
    private String content;

    /** 负责人ID */
    private Long responsibleId;

    /** 负责人姓名 */
    private String responsibleName;

    /** 状态: ACTIVE/INACTIVE */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
