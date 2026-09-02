package com.nexus.erp.prd.domain.vo;

import com.nexus.erp.prd.domain.PrdScheduleTask;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * APS排程工序任务视图对象
 *
 * @author nexus
 */
@Data
@AutoMapper(target = PrdScheduleTask.class)
public class PrdScheduleTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
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
     * 工序序号
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
     * 资源类型: WORKSTATION/WORKSHOP/OUTSOURCE
     */
    private String resourceType;

    /**
     * 资源ID
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
     * 加工工时(小时)
     */
    private BigDecimal processHours;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 占用天数
     */
    private Integer durationDays;

    /**
     * 拖期标记
     */
    private Integer delayFlag;
}
