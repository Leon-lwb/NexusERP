package com.nexus.erp.prd.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** 由调度引擎分配的一个工序/内部资源/工作日的可信计划负荷。 */
@Data
@TableName("prd_schedule_task_day_load")
public class PrdScheduleTaskDayLoad implements Serializable {
    @TableId(value = "load_id", type = IdType.ASSIGN_ID)
    private Long loadId;
    private String tenantId;
    private Long planId;
    private Long taskId;
    private String resourceType;
    private Long resourceId;
    private Date loadDate;
    private BigDecimal plannedHours;
    private Date createTime;
}
