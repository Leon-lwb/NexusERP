package com.nexus.iot.device.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * IoT 设备生命周期状态变更请求体。
 * <p>
 * 状态取值白名单在 BO 与服务端两侧同时约束（规范 §2.1：不能只在 Controller 校验）：
 * BO 用 {@code @Pattern} 挡住非法值，服务端再用 {@code IotDeviceStatus.canTransition} 校验流转合法性。
 *
 * @author nexus
 */
@Data
public class IotDeviceStatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标状态: ACTIVE(激活) / INACTIVE(禁用) / DECOMMISSIONED(注销)
     */
    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "ACTIVE|INACTIVE|DECOMMISSIONED", message = "目标状态仅支持 ACTIVE/INACTIVE/DECOMMISSIONED")
    private String status;

    /**
     * 变更原因/备注（写入设备备注，便于审计追溯）
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
