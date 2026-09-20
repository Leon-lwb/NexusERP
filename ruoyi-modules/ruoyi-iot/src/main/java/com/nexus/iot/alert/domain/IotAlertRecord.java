package com.nexus.iot.alert.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * IoT告警记录对象 iot_alert_record
 * <p>
 * IOT-ALERT-01：告警不再"仅落日志"——触发即持久化，无在线用户也不丢；带确认/恢复状态机
 * （{@link IotAlertStatus}）。
 * <p>
 * 幂等：唯一键 {@code uk_active_alert(tenant_id, device_id, property_code, active_flag)}。
 * 未恢复（ACTIVE/ACKNOWLEDGED）时 {@code active_flag=1}，恢复后置 NULL；
 * MySQL 唯一索引允许多个 NULL，因此同一设备+属性同时只可能有一条未恢复告警，
 * 而历史（已恢复）告警可任意多条。再次触发时合并更新 {@code trigger_value/last_trigger_time} 而非新增。
 * <p>
 * 阈值/比较符以快照形式冗余存储（{@code compareOperator}/{@code threshold}），
 * 使规则被修改或删除后仍能判定"属性是否已恢复正常"并自动 RESOLVED。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_alert_record")
public class IotAlertRecord extends TenantEntity {

    /**
     * 告警ID
     */
    @TableId(value = "alert_id")
    private Long alertId;

    /**
     * 命中规则ID(空=YAML兜底默认或EWMA异常)
     */
    private Long ruleId;

    /**
     * 来源: RULE/DEFAULT/ANOMALY
     */
    private String source;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(冗余)
     */
    private String deviceCode;

    /**
     * 产品ID(冗余, 0=未知)
     */
    private Long productId;

    /**
     * 属性编码
     */
    private String propertyCode;

    /**
     * 告警类型
     */
    private String alertType;

    /**
     * 告警级别
     */
    private String alertLevel;

    /**
     * 比较符快照
     */
    @TableField("compare_operator")
    private String operator;

    /**
     * 阈值快照(用于恢复判定)
     */
    private BigDecimal threshold;

    /**
     * 触发值(最近一次超限值)
     */
    private BigDecimal triggerValue;

    /**
     * 告警描述
     */
    private String alertMessage;

    /**
     * 状态机: ACTIVE/ACKNOWLEDGED/RESOLVED
     */
    private String status;

    /**
     * 未恢复标记(1=未恢复, NULL=已恢复)；唯一键组成，见类注释
     */
    private Integer activeFlag;

    /**
     * 首次触发时间
     */
    private Date triggerTime;

    /**
     * 最近触发时间
     */
    private Date lastTriggerTime;

    /**
     * 确认时间
     */
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
    private Date resolveTime;

    /**
     * 恢复操作人(空=系统自动恢复)
     */
    private Long resolveBy;

    /**
     * 恢复值
     */
    private BigDecimal resolveValue;

    /**
     * 恢复备注
     */
    private String resolveRemark;

}
