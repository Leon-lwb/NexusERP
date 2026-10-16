package com.nexus.ai.engineering.security;

import com.nexus.ai.engineering.service.EngineeringDeviceService;
import com.nexus.common.ai.engineering.EngineeringDevicePrincipal;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 连接器访问凭证认证入口，不读取浏览器 JWT 或正文身份。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringDeviceAuthenticator {
    private final EngineeringDeviceService devices;

    /** 从访问凭证解析当前有效设备。 */
    public EngineeringDevicePrincipal authenticate(String token) {
        return devices.authenticate(token);
    }
}
