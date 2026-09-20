package com.nexus.iot.command.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * IoT下行指令业务对象。
 *
 * @author nexus
 */
@Data
public class IotDeviceCommandBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标设备ID
     */
    @NotNull(message = "设备ID不能为空")
    private Long deviceId;

    /**
     * 指令类型: PROPERTY_SET(属性设置) / SERVICE_INVOKE(服务调用)
     */
    @Pattern(regexp = "PROPERTY_SET|SERVICE_INVOKE", message = "指令类型仅支持 PROPERTY_SET/SERVICE_INVOKE")
    private String commandType;

    /**
     * 服务名（SERVICE_INVOKE 必填，如 reboot / calibrate / otaUpgrade）
     */
    @Size(max = 64, message = "服务名长度不能超过 64")
    private String serviceName;

    /**
     * 指令参数（属性设置时为 属性编码 → 期望值；服务调用时为 入参）
     */
    private Map<String, Object> params;

    /**
     * 备注（写入指令留痕的结果说明前缀）
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
