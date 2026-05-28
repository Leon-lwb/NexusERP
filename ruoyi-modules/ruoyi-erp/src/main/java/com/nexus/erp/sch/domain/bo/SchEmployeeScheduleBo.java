package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;

import java.util.Date;

/**
 * 员工排班明细业务对象 sch_employee_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchEmployeeSchedule.class, reverseConvertGenerate = false)
public class SchEmployeeScheduleBo extends BaseEntity {

    /**
     * 员工排班ID
     */
    private Long scheduleId;

    /**
     * 排班计划ID
     */
    private Long planId;

    /**
     * 人员ID
     */
    private Long workerId;

    /**
     * 人员姓名
     */
    private String workerName;

    /**
     * 班组ID
     */
    private Long teamId;

    /**
     * 班组名称
     */
    private String teamName;

    /**
     * 班次ID
     */
    private Long shiftId;

    /**
     * 班次名称
     */
    private String shiftName;

    /**
     * 排班日期
     */
    private Date scheduledDate;

    /**
     * 排班开始时间
     */
    private Date startTime;

    /**
     * 排班结束时间
     */
    private Date endTime;

    /**
     * 实际班次ID
     */
    private Long actualShiftId;

    /**
     * 排班状态
     */
    private String scheduleStatus;

    /**
     * 是否工作日
     */
    private Integer isWorkingDay;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
