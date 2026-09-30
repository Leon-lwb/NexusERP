package com.nexus.erp.prd.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 产能日历业务对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdCapacityCalendar.class, reverseConvertGenerate = false)
public class PrdCapacityCalendarBo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 日历ID
     */
    @NotNull(message = "日历ID不能为空", groups = { EditGroup.class })
    private Long calendarId;

    /**
     * 日期
     */
    @NotNull(message = "日期不能为空", groups = { AddGroup.class, EditGroup.class })
    private Date calendarDate;

    /**
     * 对象类型: WORKSHOP-车间, WORKSTATION-工位, LINE-生产线
     */
    @NotBlank(message = "对象类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private String objectType;

    /**
     * 对象ID
     */
    @NotNull(message = "对象ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long objectId;

    /**
     * 班次ID
     */
    private Long shiftId;

    /**
     * 可用工时(小时)
     */
    private BigDecimal availableHours;

    /**
     * 效率系数(%)
     */
    private BigDecimal efficiency;

    /**
     * 状态: ACTIVE-有效, CLOSED-已关闭
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}
