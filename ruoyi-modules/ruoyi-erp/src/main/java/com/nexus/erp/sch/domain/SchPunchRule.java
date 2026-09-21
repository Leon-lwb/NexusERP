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
 * 打卡规则对象 sch_punch_rule
 *
 * @author nexus
 * @date 2026-01-07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_punch_rule")
public class SchPunchRule extends TenantEntity {

    /**
     * 规则ID
     */
    @TableId(value = "rule_id")
    private Long ruleId;
    /**
     * 规则名称
     */
    @ExcelProperty("规则名称")
    private String ruleName;
    /**
     * 适用班次ID（NULL表示所有班次）
     */
    @ExcelProperty("适用班次ID")
    private Long shiftId;
    /**
     * 班次名称
     */
    @ExcelProperty("班次名称")
    private String shiftName;
    /**
     * 上班时间
     */
    @ExcelProperty("上班时间")
    private Date workStartTime;
    /**
     * 下班时间
     */
    @ExcelProperty("下班时间")
    private Date workEndTime;
    /**
     * 允许上班打卡开始时间
     */
    @ExcelProperty("允许上班打卡开始时间")
    private Date allowClockInStart;
    /**
     * 允许上班打卡结束时间
     */
    @ExcelProperty("允许上班打卡结束时间")
    private Date allowClockInEnd;
    /**
     * 允许下班打卡开始时间
     */
    @ExcelProperty("允许下班打卡开始时间")
    private Date allowClockOutStart;
    /**
     * 允许下班打卡结束时间
     */
    @ExcelProperty("允许下班打卡结束时间")
    private Date allowClockOutEnd;
    /**
     * 打卡中心点纬度
     */
    @ExcelProperty("打卡中心点纬度")
    private BigDecimal locationLat;
    /**
     * 打卡中心点经度
     */
    @ExcelProperty("打卡中心点经度")
    private BigDecimal locationLng;
    /**
     * 打卡半径（米）
     */
    @ExcelProperty("打卡半径")
    private Long locationRadius;
    /**
     * 打卡地点描述
     */
    @ExcelProperty("打卡地点描述")
    private String locationAddress;
    /**
     * 允许的Wi-Fi网络SSID列表（JSON数组）
     * <p><b>未实现（预留字段）</b>：打卡主流程未读取/校验该字段，配置后不会生效（ERP-SCH-04 复核结论）。
     * 保留仅为后续接入客户端 SSID 上报时使用（客户端可伪造，只能做弱校验）。</p>
     */
    @ExcelProperty("允许的Wi-Fi网络SSID列表")
    private String allowWifiSsids;
    /**
     * 允许的蓝牙设备列表（JSON数组）
     * <p><b>未实现（预留字段）</b>：同 {@link #allowWifiSsids}，打卡主流程未做任何校验（ERP-SCH-04）。</p>
     */
    @ExcelProperty("允许的蓝牙设备列表")
    private String allowBluetoothDevices;
    /**
     * 是否允许外勤打卡
     */
    @ExcelProperty("是否允许外勤打卡")
    private Integer allowOutdoorPunch;
    /**
     * 外勤打卡是否要求拍照
     */
    @ExcelProperty("外勤打卡是否要求拍照")
    private Integer outdoorRequirePhoto;
    /**
     * 是否允许补卡
     */
    @ExcelProperty("是否允许补卡")
    private Integer allowSupplement;
    /**
     * 补卡申请截止天数
     */
    @ExcelProperty("补卡申请截止天数")
    private Long supplementDeadline;
    /**
     * 迟到阈值（分钟）
     */
    @ExcelProperty("迟到阈值")
    private Long lateThreshold;
    /**
     * 早退阈值（分钟）
     */
    @ExcelProperty("早退阈值")
    private Long earlyLeaveThreshold;
    /**
     * 旷工阈值（分钟）
     */
    @ExcelProperty("旷工阈值")
    private Long absentThreshold;
    /**
     * 是否启用
     */
    @ExcelProperty("是否启用")
    private Integer isEnabled;

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
