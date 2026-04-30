package org.dromara.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.sch.domain.SchPunchRule;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 打卡规则视图对象 sch_punch_rule
 *
 * @author nexus
 * @date 2026-01-07
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchPunchRule.class)
public class SchPunchRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @ExcelProperty(value = "规则ID")
    private Long ruleId;

    /**
     * 规则名称
     */
    @ExcelProperty(value = "规则名称")
    private String ruleName;

    /**
     * 适用班次ID
     */
    @ExcelProperty(value = "适用班次ID")
    private Long shiftId;

    /**
     * 班次名称
     */
    @ExcelProperty(value = "班次名称")
    private String shiftName;

    /**
     * 上班时间
     */
    @ExcelProperty(value = "上班时间")
    private Date workStartTime;

    /**
     * 下班时间
     */
    @ExcelProperty(value = "下班时间")
    private Date workEndTime;

    /**
     * 允许上班打卡开始时间
     */
    @ExcelProperty(value = "允许上班打卡开始时间")
    private Date allowClockInStart;

    /**
     * 允许上班打卡结束时间
     */
    @ExcelProperty(value = "允许上班打卡结束时间")
    private Date allowClockInEnd;

    /**
     * 允许下班打卡开始时间
     */
    @ExcelProperty(value = "允许下班打卡开始时间")
    private Date allowClockOutStart;

    /**
     * 允许下班打卡结束时间
     */
    @ExcelProperty(value = "允许下班打卡结束时间")
    private Date allowClockOutEnd;

    /**
     * 打卡中心点纬度
     */
    @ExcelProperty(value = "打卡中心点纬度")
    private BigDecimal locationLat;

    /**
     * 打卡中心点经度
     */
    @ExcelProperty(value = "打卡中心点经度")
    private BigDecimal locationLng;

    /**
     * 打卡半径（米）
     */
    @ExcelProperty(value = "打卡半径")
    private Long locationRadius;

    /**
     * 打卡地点描述
     */
    @ExcelProperty(value = "打卡地点描述")
    private String locationAddress;

    /**
     * 允许的Wi-Fi网络SSID列表
     */
    @ExcelProperty(value = "允许的Wi-Fi网络SSID列表")
    private String allowWifiSsids;

    /**
     * 允许的蓝牙设备列表
     */
    @ExcelProperty(value = "允许的蓝牙设备列表")
    private String allowBluetoothDevices;

    /**
     * 是否允许外勤打卡
     */
    @ExcelProperty(value = "是否允许外勤打卡")
    private Integer allowOutdoorPunch;

    /**
     * 外勤打卡是否要求拍照
     */
    @ExcelProperty(value = "外勤打卡是否要求拍照")
    private Integer outdoorRequirePhoto;

    /**
     * 是否允许补卡
     */
    @ExcelProperty(value = "是否允许补卡")
    private Integer allowSupplement;

    /**
     * 补卡申请截止天数
     */
    @ExcelProperty(value = "补卡申请截止天数")
    private Long supplementDeadline;

    /**
     * 迟到阈值（分钟）
     */
    @ExcelProperty(value = "迟到阈值")
    private Long lateThreshold;

    /**
     * 早退阈值（分钟）
     */
    @ExcelProperty(value = "早退阈值")
    private Long earlyLeaveThreshold;

    /**
     * 旷工阈值（分钟）
     */
    @ExcelProperty(value = "旷工阈值")
    private Long absentThreshold;

    /**
     * 是否启用
     */
    @ExcelProperty(value = "是否启用")
    private Integer isEnabled;
}
