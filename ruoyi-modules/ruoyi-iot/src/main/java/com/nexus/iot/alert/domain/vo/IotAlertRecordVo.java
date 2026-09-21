package com.nexus.iot.alert.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.iot.alert.domain.IotAlertRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * IoT告警记录视图对象 iot_alert_record
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = IotAlertRecord.class)
public class IotAlertRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 告警ID
     */
    @ExcelProperty(value = "告警ID")
    private Long alertId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 命中规则ID
     */
    private Long ruleId;

    /**
     * 来源: RULE/DEFAULT/ANOMALY
     */
    @ExcelProperty(value = "来源")
    private String source;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码
     */
    @ExcelProperty(value = "设备编码")
    private String deviceCode;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 属性编码
     */
    @ExcelProperty(value = "属性编码")
    private String propertyCode;

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
     * 比较符快照
     */
    private String operator;

    /**
     * 阈值快照
     */
    @ExcelProperty(value = "阈值")
    private BigDecimal threshold;

    /**
     * 触发值
     */
    @ExcelProperty(value = "触发值")
    private BigDecimal triggerValue;

    /**
     * 告警描述
     */
    @ExcelProperty(value = "告警描述")
    private String alertMessage;

    /**
     * 状态: ACTIVE/ACKNOWLEDGED/RESOLVED
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 未恢复标记(1=未恢复, NULL=已恢复)
     */
    private Integer activeFlag;

    /**
     * 首次触发时间
     */
    @ExcelProperty(value = "触发时间")
    private Date triggerTime;

    /**
     * 最近触发时间
     */
    @ExcelProperty(value = "最近触发时间")
    private Date lastTriggerTime;

    /**
     * 确认时间
     */
    @ExcelProperty(value = "确认时间")
    private Date ackTime;

    /**
     * 确认人
     */
    private Long ackBy;

    /**
     * 确认备注
     */
    private String ackRemark;

    /**
     * 恢复时间
     */
    @ExcelProperty(value = "恢复时间")
    private Date resolveTime;

    /**
     * 恢复操作人
     */
    private Long resolveBy;

    /**
     * 恢复值
     */
    @ExcelProperty(value = "恢复值")
    private BigDecimal resolveValue;

    /**
     * 恢复备注
     */
    private String resolveRemark;

    /**
     * 创建时间
     */
    private Date createTime;

}
