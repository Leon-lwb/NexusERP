package com.nexus.erp.prd.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/** 一次已发布排程任务对特定班次日历的实际占用；不从资源汇总倒推归属。 */
@Data
@TableName("prd_schedule_capacity_booking")
public class PrdScheduleCapacityBooking implements Serializable {
    @TableId(value = "booking_id", type = IdType.ASSIGN_ID)
    private Long bookingId;
    private String tenantId;
    private Long planId;
    private Long taskId;
    private Long calendarId;
    private Date loadDate;
    private BigDecimal bookedHours;
    private Date createTime;
}
