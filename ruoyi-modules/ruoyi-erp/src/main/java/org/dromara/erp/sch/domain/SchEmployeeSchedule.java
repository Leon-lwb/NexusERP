package org.dromara.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 员工排班明细对象 sch_employee_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_employee_schedule")
public class SchEmployeeSchedule extends TenantEntity {

    /**
     * 员工排班ID
     */
    @TableId(value = "schedule_id")
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
    @ExcelProperty("人员姓名")
    private String workerName;
    /**
     * 班组ID
     */
    private Long teamId;
    /**
     * 班组名称
     */
    @ExcelProperty("班组名称")
    private String teamName;
    /**
     * 班次ID
     */
    private Long shiftId;
    /**
     * 班次名称
     */
    @ExcelProperty("班次名称")
    private String shiftName;
    /**
     * 排班日期
     */
    @ExcelProperty("排班日期")
    private Date scheduledDate;
    /**
     * 排班开始时间
     */
    @ExcelProperty("排班开始时间")
    private Date startTime;
    /**
     * 排班结束时间
     */
    @ExcelProperty("排班结束时间")
    private Date endTime;
    /**
     * 实际班次ID（用于调班覆盖）
     */
    @ExcelProperty("实际班次ID")
    private Long actualShiftId;
    /**
     * 排班状态
     */
    @ExcelProperty("排班状态")
    private String scheduleStatus;
    /**
     * 是否工作日
     */
    @ExcelProperty("是否工作日")
    private Integer isWorkingDay;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
