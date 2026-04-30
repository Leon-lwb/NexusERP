package org.dromara.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.sch.domain.SchAttendance;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 考勤记录视图对象 sch_attendance
 *
 * @author nexus
 * @date 2026-01-06
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchAttendance.class)
public class SchAttendanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 考勤记录ID
     */
    @ExcelProperty(value = "考勤记录ID")
    private Long attendanceId;

    /**
     * 人员ID
     */
    @ExcelProperty(value = "人员ID")
    private Long workerId;

    /**
     * 人员姓名
     */
    @ExcelProperty(value = "人员姓名")
    private String workerName;

    /**
     * 考勤日期
     */
    @ExcelProperty(value = "考勤日期")
    private Date recordDate;

    /**
     * 关联排班ID
     */
    @ExcelProperty(value = "关联排班ID")
    private Long scheduleId;

    /**
     * 上班打卡时间
     */
    @ExcelProperty(value = "上班打卡时间")
    private Date clockInTime;

    /**
     * 下班打卡时间
     */
    @ExcelProperty(value = "下班打卡时间")
    private Date clockOutTime;

    /**
     * 迟到分钟数
     */
    @ExcelProperty(value = "迟到分钟数")
    private Long lateMinutes;

    /**
     * 早退分钟数
     */
    @ExcelProperty(value = "早退分钟数")
    private Long leaveEarlyMinutes;

    /**
     * 加班时长
     */
    @ExcelProperty(value = "加班时长")
    private BigDecimal overtimeHours;

    /**
     * 考勤结果
     */
    @ExcelProperty(value = "考勤结果")
    private String attendanceStatus;

    /**
     * 数据来源
     */
    @ExcelProperty(value = "数据来源")
    private String sourceType;

    /**
     * 打卡设备ID
     */
    @ExcelProperty(value = "打卡设备ID")
    private String deviceId;

    /**
     * 设备名称
     */
    @ExcelProperty(value = "设备名称")
    private String deviceName;
}
