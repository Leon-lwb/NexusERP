package com.nexus.ai.engineering.controller;

import cn.dev33.satoken.annotation.SaIgnore;

import com.nexus.ai.engineering.security.EngineeringConnectorFilter;
import com.nexus.ai.engineering.service.EngineeringDeviceService;
import com.nexus.common.core.domain.R;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 设备专属认证接口；SaIgnore 仅跳过浏览器 JWT，心跳仍强制检查设备 Filter。 */
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/engineering/connector")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringConnectorAuthController {
    private final EngineeringDeviceService devices;

    /** 原子兑换一次性配对码。 */
    @PostMapping("/pair")
    public R<EngineeringDeviceService.Credentials> pair(
            @RequestBody EngineeringDeviceService.PairRequest request) {
        return R.ok(devices.pair(request));
    }

    /** 旋转刷新凭证；旧值立即失效。 */
    @PostMapping("/refresh")
    public R<EngineeringDeviceService.Credentials> refresh(@RequestBody Refresh request) {
        return R.ok(devices.refresh(request.refreshToken()));
    }

    /** 上报本设备能力，不接收所有者身份。 */
    @PostMapping("/heartbeat")
    public R<EngineeringDeviceService.DeviceView> heartbeat(
            HttpServletRequest http, @RequestBody EngineeringDeviceService.Heartbeat request) {
        return R.ok(devices.heartbeat(EngineeringConnectorFilter.principal(http), request));
    }

    /** 设备主动解绑，不接受正文身份和其他设备编号。 */
    @PostMapping("/unpair")
    public R<Void> unpair(HttpServletRequest http) {
        devices.unpair(EngineeringConnectorFilter.principal(http));
        return R.ok();
    }

    /**
     * 刷新正文，不包含浏览器身份。
     *
     * @param refreshToken 单次旋转刷新凭证，客户端必须安全保存
     */
    public record Refresh(String refreshToken) {
        @Override
        public String toString() {
            return "Refresh[已隐藏]";
        }
    }
}
