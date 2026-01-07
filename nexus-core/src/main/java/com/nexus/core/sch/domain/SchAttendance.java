package com.nexus.core.sch.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 考勤记录对象 sch_attendance
 *
 * @author nexus
 * @date 2026-01-06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchAttendanceMapper.SchAttendanceResult")
public class SchAttendance extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 考勤记录ID
     */
    @TableId(value = "attendance_id", type = IdType.AUTO)
    private Long attendanceId;
    /**
     * 人员ID
     */
    @Excel(name = "人员ID")
    private Long workerId;
    /**
     * 人员姓名
     */
    @Excel(name = "人员姓名")
    private String workerName;
    /**
     * 考勤日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "考勤日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date recordDate;
    /**
     * 关联排班ID
     */
    @Excel(name = "关联排班ID")
    private Long scheduleId;
    /**
     * 上班打卡时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "上班打卡时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date clockInTime;
    /**
     * 下班打卡时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "下班打卡时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date clockOutTime;
    /**
     * 迟到分钟数
     */
    @Excel(name = "迟到分钟数")
    private Long lateMinutes;
    /**
     * 早退分钟数
     */
    @Excel(name = "早退分钟数")
    private Long leaveEarlyMinutes;
    /**
     * 加班时长
     */
    @Excel(name = "加班时长")
    private BigDecimal overtimeHours;
    /**
     * 考勤结果: normal正常, late迟到, early_leave早退, absent旷工, leave请假
     */
    @Excel(name = "考勤结果: normal正常, late迟到, early_leave早退, absent旷工, leave请假")
    private String attendanceStatus;
    /**
     * 数据来源: manual手动, device设备同步
     */
    @Excel(name = "数据来源: manual手动, device设备同步")
    private String sourceType;
    /**
     * 打卡设备ID
     */
    @Excel(name = "打卡设备ID")
    private String deviceId;
    /**
     * 设备名称
     */
    @Excel(name = "设备名称")
    private String deviceName;
}