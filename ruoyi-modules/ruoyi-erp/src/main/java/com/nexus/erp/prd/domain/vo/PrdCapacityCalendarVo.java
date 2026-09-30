package com.nexus.erp.prd.domain.vo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 产能日历视图对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdCapacityCalendar.class)
public class PrdCapacityCalendarVo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 日历ID
     */
    private Long calendarId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 日期
     */
    private Date calendarDate;

    /**
     * 对象类型: WORKSHOP-车间, WORKSTATION-工位, LINE-生产线
     */
    private String objectType;

    /**
     * 对象ID
     */
    private Long objectId;

    /**
     * 对象编码
     */
    private String objectCode;

    /**
     * 对象名称
     */
    private String objectName;

    /**
     * 班次ID
     */
    private Long shiftId;

    /**
     * 班次编码
     */
    private String shiftCode;

    /**
     * 班次名称
     */
    private String shiftName;

    /**
     * 可用工时(小时)
     */
    private BigDecimal availableHours;

    /**
     * 已计划工时(小时)
     */
    private BigDecimal plannedHours;

    /**
     * 实际已用工时(小时)
     */
    private BigDecimal actualHours;

    /**
     * 效率系数(%)
     */
    private BigDecimal efficiency;

    /**
     * 有效产能
     */
    private BigDecimal effectiveHours;

    /**
     * 剩余产能
     */
    private BigDecimal remainingHours;

    /**
     * 状态: ACTIVE-有效, CLOSED-已关闭
     */
    private String status;
}
