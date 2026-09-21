package com.nexus.iot.forward.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.iot.forward.domain.IotForwardRule;

/**
 * IoT数据转发规则业务对象。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = IotForwardRule.class, reverseConvertGenerate = false)
public class IotForwardRuleBo extends BaseEntity {

    /**
     * 规则ID（新增为空）
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
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
    @Size(max = 500, message = "属性编码列表长度不能超过 500")
    private String propertyCodes;

    /**
     * 目标类型: HTTP
     */
    @Pattern(regexp = "HTTP", message = "目标类型当前仅支持 HTTP")
    private String targetType;

    /**
     * Webhook URL
     */
    @NotBlank(message = "Webhook URL 不能为空")
    @Size(max = 500, message = "Webhook URL 长度不能超过 500")
    private String targetUrl;

    /**
     * HTTP方法: POST/PUT
     */
    @Pattern(regexp = "POST|PUT", message = "HTTP方法仅支持 POST/PUT")
    private String httpMethod;

    /**
     * 自定义请求头(JSON)
     */
    private String headers;

    /**
     * 单次投递超时(毫秒)
     */
    @Min(value = 500, message = "超时时间不能小于 500ms")
    @Max(value = 60000, message = "超时时间不能大于 60000ms")
    private Integer timeoutMs;

    /**
     * 最大重试次数
     */
    @Min(value = 0, message = "最大重试次数不能小于 0")
    @Max(value = 10, message = "最大重试次数不能大于 10")
    private Integer maxRetry;

    /**
     * 启用状态(1启用 0停用)
     */
    @Pattern(regexp = "0|1", message = "启用状态仅支持 0/1")
    private String enabled;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
