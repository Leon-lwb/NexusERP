package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchPunchRule;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 打卡规则业务对象 sch_punch_rule
 *
 * @author nexus
 * @date 2026-01-07
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchPunchRule.class, reverseConvertGenerate = false)
public class SchPunchRuleBo extends BaseEntity {

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    /**
     * 适用班次ID
     */
    private Long shiftId;

    /**
     * 班次名称
     */
    private String shiftName;

    /**
     * 上班时间
     */
    private Date workStartTime;

    /**
     * 下班时间
     */
    private Date workEndTime;

    /**
     * 允许上班打卡开始时间
     */
    private Date allowClockInStart;

    /**
     * 允许上班打卡结束时间
     */
    private Date allowClockInEnd;

    /**
     * 允许下班打卡开始时间
     */
    private Date allowClockOutStart;

    /**
     * 允许下班打卡结束时间
     */
    private Date allowClockOutEnd;

    /**
     * 打卡中心点纬度
     */
    private BigDecimal locationLat;

    /**
     * 打卡中心点经度
     */
    private BigDecimal locationLng;

    /**
     * 打卡半径（米）
     */
    private Long locationRadius;

    /**
     * 打卡地点描述
     */
    private String locationAddress;

    /**
     * 允许的Wi-Fi网络SSID列表
     */
    private String allowWifiSsids;

    /**
     * 允许的蓝牙设备列表
     */
    private String allowBluetoothDevices;

    /**
     * 是否允许外勤打卡
     */
    private Integer allowOutdoorPunch;

    /**
     * 外勤打卡是否要求拍照
     */
    private Integer outdoorRequirePhoto;

    /**
     * 是否允许补卡
     */
    private Integer allowSupplement;

    /**
     * 补卡申请截止天数
     */
    private Long supplementDeadline;

    /**
     * 迟到阈值（分钟）
     */
    private Long lateThreshold;

    /**
     * 早退阈值（分钟）
     */
    private Long earlyLeaveThreshold;

    /**
     * 旷工阈值（分钟）
     */
    private Long absentThreshold;

    /**
     * 是否启用
     */
    private Integer isEnabled;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
