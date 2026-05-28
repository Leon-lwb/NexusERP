package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchAttendance;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 考勤记录业务对象 sch_attendance
 *
 * @author nexus
 * @date 2026-01-06
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchAttendance.class, reverseConvertGenerate = false)
public class SchAttendanceBo extends BaseEntity {

    /**
     * 考勤记录ID
     */
    private Long attendanceId;

    /**
     * 人员ID
     */
    private Long workerId;

    /**
     * 人员姓名
     */
    private String workerName;

    /**
     * 考勤日期
     */
    private Date recordDate;

    /**
     * 关联排班ID
     */
    private Long scheduleId;

    /**
     * 上班打卡时间
     */
    private Date clockInTime;

    /**
     * 下班打卡时间
     */
    private Date clockOutTime;

    /**
     * 迟到分钟数
     */
    private Long lateMinutes;

    /**
     * 早退分钟数
     */
    private Long leaveEarlyMinutes;

    /**
     * 加班时长
     */
    private BigDecimal overtimeHours;

    /**
     * 考勤结果
     */
    private String attendanceStatus;

    /**
     * 数据来源
     */
    private String sourceType;

    /**
     * 打卡设备ID
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
