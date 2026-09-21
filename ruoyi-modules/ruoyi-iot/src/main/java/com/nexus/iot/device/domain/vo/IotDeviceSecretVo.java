package com.nexus.iot.device.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * IoT 设备密钥重置结果视图对象。
 * <p>
 * 专用 DTO：{@code IotDeviceVo.deviceSecret} 被 {@code @JsonIgnore} 屏蔽以防泄露，
 * 密钥重置接口需要**一次性**把新密钥返回给操作者，故单独承载，避免破坏该屏蔽约定。
 *
 * @author nexus
 */
@Data
public class IotDeviceSecretVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(MQTT ClientID)
     */
    private String deviceCode;

    /**
     * 新生成的设备密钥（仅本次响应返回一次，请立即保存；旧密钥已失效）
     */
    private String deviceSecret;

}
