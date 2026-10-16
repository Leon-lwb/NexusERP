package com.nexus.ai.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.engineering.security.EngineeringAccess;
import com.nexus.ai.engineering.service.EngineeringDeviceService;
import com.nexus.common.ai.engineering.EngineeringAction;
import com.nexus.common.core.domain.R;
import com.nexus.common.tenant.helper.TenantHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 当前登录用户的设备管理接口；所有请求固定 owner。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/engineering/devices")
@SaCheckPermission("ai:engineering:device")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringDeviceController {
    private final EngineeringDeviceService devices;
    private final EngineeringAccess access;

    /** 创建固定用途的五分钟配对码。 */
    @PostMapping("/pairing-code")
    public R<EngineeringDeviceService.PairingCode> pairing(
            @RequestBody(required = false) Pairing request) {
        var actor = access.current("ai:engineering:device", false);
        return TenantHelper.dynamic(
                actor.tenantId(),
                () ->
                        R.ok(
                                devices.createPairingCode(
                                        actor, request == null ? null : request.capabilities())));
    }

    /** 返回当前用户设备与实际能力。 */
    @GetMapping
    public R<List<EngineeringDeviceService.DeviceView>> list() {
        var actor = access.current("ai:engineering:device", false);
        return TenantHelper.dynamic(actor.tenantId(), () -> R.ok(devices.list(actor)));
    }

    /** 撤销本人设备全部凭证。 */
    @DeleteMapping("/{deviceId}")
    public R<Void> revoke(@PathVariable Long deviceId) {
        var actor = access.current("ai:engineering:device", false);
        TenantHelper.dynamic(actor.tenantId(), () -> devices.revoke(actor, deviceId));
        return R.ok();
    }

    /**
     * 浏览器限制配对设备允许动作。
     *
     * @param capabilities 设备如实报告或配对允许的能力集合
     */
    public record Pairing(Set<EngineeringAction> capabilities) {}
}
