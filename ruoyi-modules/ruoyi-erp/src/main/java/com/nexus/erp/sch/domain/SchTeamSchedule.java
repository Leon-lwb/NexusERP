package com.nexus.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 班组排班（模板）对象 sch_team_schedule
 *
 * @author nexus
 * @date 2025-12-30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_team_schedule")
public class SchTeamSchedule extends TenantEntity {

    /**
     * 班组排班ID
     */
    @TableId(value = "team_schedule_id")
    private Long teamScheduleId;
    /**
     * 排班计划ID
     */
    @ExcelProperty("排班计划ID")
    private Long planId;
    /**
     * 班组ID
     */
    @ExcelProperty("班组ID")
    private Long teamId;
    /**
     * 班次ID
     */
    @ExcelProperty("班次ID")
    private Long shiftId;
    /**
     * 排班日期
     */
    @ExcelProperty("排班日期")
    private Date scheduledDate;
    /**
     * 星期几(1-7)
     */
    @ExcelProperty("星期几")
    private Long weekDay;
    /**
     * 是否节假日
     */
    @ExcelProperty("是否节假日")
    private Integer isHoliday;

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
