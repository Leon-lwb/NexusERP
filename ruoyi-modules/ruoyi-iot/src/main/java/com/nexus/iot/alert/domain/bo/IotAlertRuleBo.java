package com.nexus.iot.alert.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.iot.alert.domain.IotAlertRule;

import java.math.BigDecimal;

/**
 * IoT告警规则业务对象 iot_alert_rule
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = IotAlertRule.class, reverseConvertGenerate = false)
public class IotAlertRuleBo extends BaseEntity {

    /**
     * 规则ID（修改时必填，服务端二次校验）
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过128")
    private String ruleName;

    /**
     * 产品范围(0或空=不限)
     */
    private Long productId;

    /**
     * 设备范围(0或空=不限)
     */
    private Long deviceId;

    /**
     * 属性编码
     */
    @NotBlank(message = "属性编码不能为空")
    @Size(max = 64, message = "属性编码长度不能超过64")
    private String propertyCode;

    /**
     * 比较符: GT/GE/LT/LE/EQ/NE
     */
    @NotBlank(message = "比较符不能为空")
    @Pattern(regexp = "GT|GE|LT|LE|EQ|NE", message = "比较符仅支持 GT/GE/LT/LE/EQ/NE")
    private String operator;

    /**
     * 阈值
     */
    @NotNull(message = "阈值不能为空")
    private BigDecimal threshold;

    /**
     * 持续时长(秒)：0=立即告警
     */
    @Min(value = 0, message = "持续时长不能为负数")
    @Max(value = 86400, message = "持续时长不能超过86400秒")
    private Integer durationSec;

    /**
     * 去抖窗口(秒)
     */
    @Min(value = 0, message = "去抖窗口不能为负数")
    @Max(value = 86400, message = "去抖窗口不能超过86400秒")
    private Integer debounceSec;

    /**
     * 告警类型: TEMPERATURE/VIBRATION/PRESSURE/OTHER
     */
    @Pattern(regexp = "TEMPERATURE|VIBRATION|PRESSURE|OTHER", message = "告警类型仅支持 TEMPERATURE/VIBRATION/PRESSURE/OTHER")
    private String alertType;

    /**
     * 告警级别: HIGH/MEDIUM/LOW
     */
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "告警级别仅支持 HIGH/MEDIUM/LOW")
    private String alertLevel;

    /**
     * 通知渠道(逗号分隔): SSE/MES_ANDON
     */
    @Size(max = 128, message = "通知渠道长度不能超过128")
    private String notifyChannels;

    /**
     * 启用状态(1启用 0停用)
     */
    @Pattern(regexp = "0|1", message = "启用状态仅支持 1(启用)/0(停用)")
    private String enabled;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
