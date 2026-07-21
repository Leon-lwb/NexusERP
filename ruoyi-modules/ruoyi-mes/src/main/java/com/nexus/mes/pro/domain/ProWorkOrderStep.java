package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单工序步骤对象 pro_work_order_step
 * <p>属于 MES 生产管理模块，表达 工单 的领域模型。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_work_order_step")
public class ProWorkOrderStep extends TenantEntity {

    /** 工序步骤ID(主键) */
    @TableId(value = "step_id")
    private Long stepId;

    /** 所属MES工单ID */
    private Long mesWoId;

    /** 工序序号(从1开始) */
    private Integer sequenceNum;

    /** ERP工序ID */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 工序名称(冗余) */
    private String processName;

    /** 工作中心ID */
    private Long workcenterId;

    /** 工作中心编码(冗余) */
    private String workcenterCode;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 工序状态: PENDING/READY/IN_PROGRESS/COMPLETED/SKIPPED */
    private String stepStatus;

    /** 是否关键工序(0否 1是) */
    private Integer isKeyProcess;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
