package com.nexus.erp.prd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * APS排程工序任务对象 prd_schedule_task
 * <p>
 * 排程方案下的工序级任务明细：一条工艺路线步骤在一个资源上的一段占用。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("prd_schedule_task")
public class PrdScheduleTask extends TenantEntity {

    /**
     * 任务ID
     */
    @TableId(value = "task_id")
    private Long taskId;

    /**
     * 排程方案ID
     */
    private Long planId;

    /**
     * 生产工单ID
     */
    private Long woId;

    /**
     * 工单编号
     */
    private String woNo;

    /**
     * 产品编码
     */
    private String materialCode;

    /**
     * 产品名称
     */
    private String materialName;

    /**
     * 工序序号(工艺路线步骤序号)
     */
    private Integer seqNum;

    /**
     * 工艺路线步骤ID
     */
    private Long stepId;

    /**
     * 工序编码
     */
    private String processCode;

    /**
     * 工序名称
     */
    private String processName;

    /**
     * 资源类型: WORKSTATION-工作站, WORKSHOP-车间, OUTSOURCE-委外
     */
    private String resourceType;

    /**
     * 资源ID(工作站/车间)
     */
    private Long resourceId;

    /**
     * 资源名称
     */
    private String resourceName;

    /**
     * 准备工时(小时)
     */
    private BigDecimal setupHours;

    /**
     * 加工工时(小时)=标准工时×剩余数量
     */
    private BigDecimal processHours;

    /**
     * 开始时间(起始日00:00)
     */
    private Date startTime;

    /**
     * 结束时间(末日23:59:59)
     */
    private Date endTime;

    /**
     * 占用天数
     */
    private Integer durationDays;

    /**
     * 拖期标记(所属工单拖期时置1)
     */
    private Integer delayFlag;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
