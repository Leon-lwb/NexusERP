package com.nexus.iot.forward.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * IoT数据转发规则对象 iot_forward_rule（§4#6 数据转发）。
 * <p>
 * 最小可用能力：把设备数据（命中范围与属性白名单）异步投递到 HTTP Webhook，
 * 失败按退避重试并留痕（{@code iot_forward_log}）。
 * <p>
 * 范围语义与告警规则一致：{@code product_id=0} 表示不限产品、{@code device_id=0} 表示不限设备
 * （用 0 而非 NULL，使唯一键 {@code uk_rule_scope} 真正生效）。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_forward_rule")
public class IotForwardRule extends TenantEntity {

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
     * 属性编码白名单(逗号分隔,空=全部)
     */
    private String propertyCodes;

    /**
     * 目标类型: HTTP
     */
    private String targetType;

    /**
     * Webhook URL(仅http/https, 内网/保留地址被拒)
     */
    private String targetUrl;

    /**
     * HTTP方法: POST/PUT
     */
    private String httpMethod;

    /**
     * 自定义请求头(JSON)
     */
    private String headers;

    /**
     * 单次投递超时(毫秒)
     */
    private Integer timeoutMs;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 启用状态(1启用 0停用)
     */
    private String enabled;

    /**
     * 备注
     */
    private String remark;
}
