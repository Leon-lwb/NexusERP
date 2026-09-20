package com.nexus.iot.alert.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * IoT告警规则对象 iot_alert_rule
 * <p>
 * 取代原先硬编码在 {@code nexus.iot.pipeline.alert-thresholds} 的全局阈值映射（IOT-ALERT-02）：
 * 支持租户隔离、产品/设备范围、比较符、持续时长（去抖）与去抖推送窗口。
 * <p>
 * 范围语义：{@code productId=0} 且 {@code deviceId=0} 表示全局规则；
 * {@code deviceId!=0} 优先于 {@code productId!=0}，两者都命中时取最具体的一条。
 * <p>
 * 唯一键 {@code uk_rule_scope(tenant_id, product_id, device_id, property_code)} 保证同租户同范围同属性仅一条规则；
 * 因列均非 NULL（不限用 0 表达），唯一性对"不限范围"同样生效。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_alert_rule")
public class IotAlertRule extends TenantEntity {

    /**
     * 规则ID
     */
    @TableId(value = "rule_id")
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 产品范围(0=不限)
     */
    private Long productId;

    /**
     * 设备范围(0=不限)
     */
    private Long deviceId;

    /**
     * 属性编码
     */
    private String propertyCode;

    /**
     * 比较符: GT/GE/LT/LE/EQ/NE
     * <p>列名用 compare_operator，避免与 MySQL 关键字 OPERATOR 混淆。
     */
    @TableField("compare_operator")
    private String operator;

    /**
     * 阈值
     */
    private BigDecimal threshold;

    /**
     * 持续时长(秒)：超限需持续该时长才告警，0=立即告警
     */
    private Integer durationSec;

    /**
     * 去抖窗口(秒)：窗口内仅合并落库、不重复推送
     */
    private Integer debounceSec;

    /**
     * 告警类型: TEMPERATURE/VIBRATION/PRESSURE/OTHER
     */
    private String alertType;

    /**
     * 告警级别: HIGH/MEDIUM/LOW
     */
    private String alertLevel;

    /**
     * 通知渠道(逗号分隔): SSE/MES_ANDON
     */
    private String notifyChannels;

    /**
     * 启用状态(1启用 0停用)
     */
    private String enabled;

    /**
     * 备注
     */
    private String remark;

}
