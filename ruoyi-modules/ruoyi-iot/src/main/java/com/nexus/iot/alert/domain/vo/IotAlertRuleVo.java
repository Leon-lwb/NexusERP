package com.nexus.iot.alert.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.iot.alert.domain.IotAlertRule;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * IoT告警规则视图对象 iot_alert_rule
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = IotAlertRule.class)
public class IotAlertRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @ExcelProperty(value = "规则ID")
    private Long ruleId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 规则名称
     */
    @ExcelProperty(value = "规则名称")
    private String ruleName;

    /**
     * 产品范围(0=不限)
     */
    @ExcelProperty(value = "产品范围")
    private Long productId;

    /**
     * 设备范围(0=不限)
     */
    @ExcelProperty(value = "设备范围")
    private Long deviceId;

    /**
     * 属性编码
     */
    @ExcelProperty(value = "属性编码")
    private String propertyCode;

    /**
     * 比较符
     */
    @ExcelProperty(value = "比较符")
    private String operator;

    /**
     * 阈值
     */
    @ExcelProperty(value = "阈值")
    private BigDecimal threshold;

    /**
     * 持续时长(秒)
     */
    @ExcelProperty(value = "持续时长(秒)")
    private Integer durationSec;

    /**
     * 去抖窗口(秒)
     */
    @ExcelProperty(value = "去抖窗口(秒)")
    private Integer debounceSec;

    /**
     * 告警类型
     */
    @ExcelProperty(value = "告警类型")
    private String alertType;

    /**
     * 告警级别
     */
    @ExcelProperty(value = "告警级别")
    private String alertLevel;

    /**
     * 通知渠道
     */
    @ExcelProperty(value = "通知渠道")
    private String notifyChannels;

    /**
     * 启用状态(1启用 0停用)
     */
    @ExcelProperty(value = "启用状态")
    private String enabled;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

}
