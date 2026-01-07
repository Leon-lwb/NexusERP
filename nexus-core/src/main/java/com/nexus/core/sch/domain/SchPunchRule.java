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
 * 打卡规则对象 sch_punch_rule
 *
 * @author nexus
 * @date 2026-01-07
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchPunchRuleMapper.SchPunchRuleResult")
public class SchPunchRule extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @TableId(value = "rule_id", type = IdType.AUTO)
    private Long ruleId;
    /**
     * 规则名称
     */
    @Excel(name = "规则名称")
    private String ruleName;
    /**
     * 适用班次ID（NULL表示所有班次）
     */
    @Excel(name = "适用班次ID", readConverterExp = "NULL表示所有班次")
    private Long shiftId;
    /**
     * 班次名称
     */
    @Excel(name = "班次名称")
    private String shiftName;
    /**
     * 上班时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "上班时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date workStartTime;
    /**
     * 下班时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "下班时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date workEndTime;
    /**
     * 允许上班打卡开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "允许上班打卡开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date allowClockInStart;
    /**
     * 允许上班打卡结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "允许上班打卡结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date allowClockInEnd;
    /**
     * 允许下班打卡开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "允许下班打卡开始时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date allowClockOutStart;
    /**
     * 允许下班打卡结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "允许下班打卡结束时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date allowClockOutEnd;
    /**
     * 打卡中心点纬度
     */
    @Excel(name = "打卡中心点纬度")
    private BigDecimal locationLat;
    /**
     * 打卡中心点经度
     */
    @Excel(name = "打卡中心点经度")
    private BigDecimal locationLng;
    /**
     * 打卡半径（米）
     */
    @Excel(name = "打卡半径", readConverterExp = "米=")
    private Long locationRadius;
    /**
     * 打卡地点描述
     */
    @Excel(name = "打卡地点描述")
    private String locationAddress;
    /**
     * 允许的Wi-Fi网络SSID列表（JSON数组）
     */
    @Excel(name = "允许的Wi-Fi网络SSID列表", readConverterExp = "J=SON数组")
    private String allowWifiSsids;
    /**
     * 允许的蓝牙设备列表（JSON数组）
     */
    @Excel(name = "允许的蓝牙设备列表", readConverterExp = "J=SON数组")
    private String allowBluetoothDevices;
    /**
     * 是否允许外勤打卡
     */
    @Excel(name = "是否允许外勤打卡")
    private Integer allowOutdoorPunch;
    /**
     * 外勤打卡是否要求拍照
     */
    @Excel(name = "外勤打卡是否要求拍照")
    private Integer outdoorRequirePhoto;
    /**
     * 是否允许补卡
     */
    @Excel(name = "是否允许补卡")
    private Integer allowSupplement;
    /**
     * 补卡申请截止天数
     */
    @Excel(name = "补卡申请截止天数")
    private Long supplementDeadline;
    /**
     * 迟到阈值（分钟）
     */
    @Excel(name = "迟到阈值", readConverterExp = "分=钟")
    private Long lateThreshold;
    /**
     * 早退阈值（分钟）
     */
    @Excel(name = "早退阈值", readConverterExp = "分=钟")
    private Long earlyLeaveThreshold;
    /**
     * 旷工阈值（分钟）
     */
    @Excel(name = "旷工阈值", readConverterExp = "分=钟")
    private Long absentThreshold;
    /**
     * 是否启用
     */
    @Excel(name = "是否启用")
    private Integer isEnabled;
}