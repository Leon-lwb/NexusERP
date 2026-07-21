package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES巡检任务对象 eq_patrol_task
 *
 * <p>属于MES 设备管理模块，巡检定时任务。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_patrol_task")
public class EqPatrolTask extends TenantEntity {

    @TableId(value = "task_id")
    private Long taskId;

    /** 关联路线ID */
    private Long routeId;

    /** 路线名称(冗余) */
    private String routeName;

    /** 计划巡检日期 */
    private Date planDate;

    /** 实际完成时间 */
    private Date actualDate;

    /** 指派人ID */
    private Long assignedToId;

    /** 指派人姓名 */
    private String assignedToName;

    /** 状态: PENDING/IN_PROGRESS/COMPLETED/CANCELLED */
    private String status;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}
