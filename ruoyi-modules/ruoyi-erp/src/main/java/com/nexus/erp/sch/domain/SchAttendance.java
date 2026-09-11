package com.nexus.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 考勤记录对象 sch_attendance
 *
 * @author nexus
 * @date 2026-01-06
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_attendance")
public class SchAttendance extends TenantEntity {

    /**
     * 考勤记录ID
     */
    @TableId(value = "attendance_id")
    private Long attendanceId;
    /**
     * 人员ID
     */
    @ExcelProperty("人员ID")
    private Long workerId;
    /**
     * 人员姓名
     */
    @ExcelProperty("人员姓名")
    private String workerName;
    /**
     * 考勤日期
     */
    @ExcelProperty("考勤日期")
    private Date recordDate;
    /**
     * 关联排班ID
     */
    @ExcelProperty("关联排班ID")
    private Long scheduleId;
    /**
     * 上班打卡时间
     */
    @ExcelProperty("上班打卡时间")
    private Date clockInTime;
    /**
     * 下班打卡时间
     */
    @ExcelProperty("下班打卡时间")
    private Date clockOutTime;
    /**
     * 迟到分钟数
     */
    @ExcelProperty("迟到分钟数")
    private Long lateMinutes;
    /**
     * 早退分钟数
     */
    @ExcelProperty("早退分钟数")
    private Long leaveEarlyMinutes;
    /**
     * 加班时长
     */
    @ExcelProperty("加班时长")
    private BigDecimal overtimeHours;
    /**
     * 考勤结果: normal正常, late迟到, early_leave早退, absent旷工, leave请假
     */
    @ExcelProperty("考勤结果")
    private String attendanceStatus;
    /**
     * 数据来源: manual手动, device设备同步
     */
    @ExcelProperty("数据来源")
    private String sourceType;
    /**
     * 打卡设备ID
     */
    @ExcelProperty("打卡设备ID")
    private String deviceId;
    /**
     * 设备名称
     */
    @ExcelProperty("设备名称")
    private String deviceName;

    /**
     * 上班打卡纬度
     */
    private BigDecimal clockInLatitude;

    /**
     * 上班打卡经度
     */
    private BigDecimal clockInLongitude;

    /**
     * 上班打卡地址/定位描述
     */
    private String clockInAddress;

    /**
     * 下班打卡纬度
     */
    private BigDecimal clockOutLatitude;

    /**
     * 下班打卡经度
     */
    private BigDecimal clockOutLongitude;

    /**
     * 下班打卡地址/定位描述
     */
    private String clockOutAddress;

    /**
     * 人脸验证状态: NONE未验证, PASS通过, FAIL失败
     */
    @ExcelProperty("人脸验证状态")
    private String faceVerifyStatus;

    /**
     * 人脸验证时间
     */
    @ExcelProperty("人脸验证时间")
    private Date faceVerifyTime;

    /**
     * 人脸相似度
     */
    @ExcelProperty("人脸相似度")
    private BigDecimal faceSimilarity;

    /**
     * 打卡人脸照片ossId(MinIO存证)
     */
    private Long faceImageOssId;

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
